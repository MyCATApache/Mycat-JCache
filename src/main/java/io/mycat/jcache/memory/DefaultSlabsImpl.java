package io.mycat.jcache.memory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.context.Stats;
import io.mycat.jcache.context.StatsState;
import io.mycat.jcache.enums.memory.MOVE_STATUS;
import io.mycat.jcache.items.Items;
import io.mycat.jcache.net.JcacheGlobalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.memory.REASSIGN_RESULT_TYPE;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemChunkUtil;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.SlabClassUtil;
import io.mycat.jcache.util.UnSafeUtil;

@SuppressWarnings("restriction")
public class DefaultSlabsImpl implements Slabs,Runnable {
	
	private Logger logger = LoggerFactory.getLogger(DefaultSlabsImpl.class);	
	
	private static long slabclass;     //slabclass 数组内存首地址
	private static long mem_limit;     //总内存大小,收到配置参数 -m 约束.如果没有配置默认 64M
	private static long mem_malloced;   //已分配内存,每分配一个slab ,item 该值均会增加.
	/* If the memory limit has been hit once. Used as a hint to decide when to
	 * early-wake the LRU maintenance thread */
	private static boolean mem_limit_reached = false;

	private static int power_largest;   // 根据当前内存参数配置,分配的最大slabcalss 数量.受到增长因子的影响.
	
	private static long mem_base; // 当前内存池 内存首地址
	private static long mem_current;  // 当前未分配的内存的首地址
	private static long mem_avail;    // 当前可用内存大小.
	
	/**
	 * Access to the slab allocator is protected by this lock
	 */
	private volatile static AtomicBoolean slabs_lock = new AtomicBoolean(false);
	private  static Lock slabs_rebalance_lock = new ReentrantLock();

	private static Condition slab_rebalance_cond = slabs_rebalance_lock.newCondition();
	private volatile  static int do_run_slab_thread=1;
	private volatile  static int do_run_slab_rebalance_thread=1;
	private volatile int slab_rebalance_signal;
	private static final int  DEFAULT_SLAB_BULK_CHECK =1;
	private int slab_bulk_check = DEFAULT_SLAB_BULK_CHECK;
	private static Thread rebalance_tid;
	private SlabRebalance slab_rebal;
	
	/**
	 * 为  slabclass 分配一个新的slab
	 * @param id
	 * @return
	 */
	private boolean do_slabs_newslab(int id){
		long p = getSlabClass(id);
		long g = getSlabClass(Settings.SLAB_GLOBAL_PAGE_POOL);
		/* 获取当前slab 大小   */
		int len = (Settings.slabReassign || Settings.slabChunkSizeMax != Settings.slabPageSize) 
				? Settings.slabPageSize :   /* 默认slab 大小 */
					SlabClassUtil.getSize(p)*SlabClassUtil.getPerslab(p);  /* 当前 slabclass 中,chunk 大小  乘以 chunk 的数量   */
		
		long ptr;   /* 新分配的slab 可以 由 当前slabclass 分配,可能由 全局pool 分配,可能从总内存中分配 */
		
		if(mem_limit>0 && (mem_malloced + len) > mem_limit 
				&& SlabClassUtil.getSlabs(p) > 0
				&& SlabClassUtil.getSlabs(g)==0  /* 这个条件 */
				){
			mem_limit_reached = true;
			return false;
		}
		
		if(!grow_slab_list(id)   // 如果需要扩容slablist 扩容
				||((ptr = get_page_from_global_pool())==0)  //全局pool 中没有可用的slab
				&&(ptr = memory_allocate(len))==0){   //当前可用内存不足
			mem_limit_reached = true;
			return false;
		}
		
		/*初始化内存*/
		UnSafeUtil.unsafe.setMemory(ptr, len, (byte)0);
		split_slab_page_into_freelist(ptr,id);  /*为每个slab  */
		int slabs = SlabClassUtil.getSlabs(p);
		SlabClassUtil.incrSlabs(p);
		SlabClassUtil.setSlablistIndexValue(p, slabs, ptr);
		return true;
	}
	
	//将ptr指向的内存空间按第id个slabclass的size进行切分
	private void split_slab_page_into_freelist(long ptr,int id){
		long p = getSlabClass(id);  /* 当前  slabclass 内存首地址 */
		long perslab = SlabClassUtil.getPerslab(p); /* 每个slab 可以有多少个chunk */
		int x;
		for(x = 0;x<perslab;x++){
			do_slabs_free(ptr, 0, id);
			ptr += SlabClassUtil.getSize(p); /* 当前slab  , chunk 的内存首地址*/
		}
	}
	
	/**
	 * 从当前可用内存中 分配一块内存
	 * @param size
	 * @return
	 */
	public long memory_allocate(long size){
		long ret;
		
		if(mem_base == 0){
			/* We are not using a preallocated large memory chunk */
			ret = UnSafeUtil.unsafe.allocateMemory(size);
		}else{
			ret = mem_current;
			
			if(size > mem_avail){
				return 0;
			}
			
			 /* mem_current pointer _must_ be aligned!!! */
			if(size % Settings.CHUNK_ALIGN_BYTES != 0){
				size += Settings.CHUNK_ALIGN_BYTES - (size % Settings.CHUNK_ALIGN_BYTES);
			}
			
			mem_current  += size;
			
			if(size < mem_avail){
				mem_avail -=size;
			}else{
				mem_avail = 0;
			}
		}
		
		mem_malloced +=size;
		
		return ret;
	}
	
	
	/* Fast FIFO queue */
	/**
	 * 先进先出（FIFO） 将最后一个 slab,即:最先分配的slab 返回
	 * @return
	 */
	private long get_page_from_global_pool(){
		long p = getSlabClass(Settings.SLAB_GLOBAL_PAGE_POOL);
		int slabs = SlabClassUtil.getSlabs(p);
		if(slabs < 1){
			return 0;
		}
		
		long slab_list = SlabClassUtil.getSlabList(p);
		
		long ret = slab_list + (slabs - 1)*UnSafeUtil.addresssize;  //获取 在 global pool 中 分配的最后一个 slab
		SlabClassUtil.decrSlabs(p); //已分配的slabs 数量减一
		return ret;
	}
	
	/**
	 * 判断 slabclass 中 slablist 是否需要扩容 ,
	 * 如果扩容失败 返回 false.
	 * 不需要扩容 或扩容成功,返回成功
	 * @param id
	 * @return
	 */
	private boolean grow_slab_list(int id){
		long p = getSlabClass(id);
		//当前slabclass 中已分配的slab 数，已达到  listsize 上限, 对 slablist 扩容
		if(SlabClassUtil.getSlabs(p)==SlabClassUtil.getListSize(p)){
			int list_size = SlabClassUtil.getListSize(p);
			int new_size = list_size!=0?list_size*2:16;
			long slablist = SlabClassUtil.getSlabList(p);
			if(slablist==0){
				slablist = UnSafeUtil.unsafe.allocateMemory(new_size);
			}else{
				slablist = UnSafeUtil.unsafe.reallocateMemory(slablist, new_size);
			}
			if(slablist==0){  //分配失败 
				return false;
			}
			SlabClassUtil.setListSize(p, new_size);
			SlabClassUtil.setSlabList(p, slablist);
		}
		return true;
	}
	
	/**
	 * 将当前 ptr 对应的item 挂载到 slabclass  空闲链表 slots head 上.
	 * @param ptr
	 * @param size
	 * @param id
	 */
	private void do_slabs_free(long ptr,int size,int id){
		long p = getSlabClass(id);
		long it = ptr;
		
		int it_flags = ItemUtil.getItflags(it);
		if((it_flags & ItemFlags.ITEM_CHUNKED.getFlags()) == 0){
			ItemUtil.setItflags(it, ItemFlags.ITEM_SLABBED.getFlags());
			ItemUtil.setSlabsClsid(it, (byte)0);
			ItemUtil.setPrev(it, 0);
			ItemUtil.setNext(it, SlabClassUtil.getSlots(p));
			long next = ItemUtil.getNext(it);
			if(next!=0){
				ItemUtil.setPrev(next, it);
			}
			SlabClassUtil.setSlots(p, it);
			SlabClassUtil.incrSlCurr(p);
			SlabClassUtil.decrRequested(p, size);
		}else{
			do_slabs_free_chunked(it,size,id,p); 
		}
		return;
	}
	
	/**
	 * @param it
	 * @param size
	 * @param id
	 * @param p
	 */
	private void do_slabs_free_chunked(long it,int size,int id,long p){
		long chunk = ItemUtil.ITEM_data(it);
		int realsize = size;
		while(chunk!=0){
			realsize += ItemChunkUtil.getNtotal();
			chunk = ItemChunkUtil.getNext(chunk);
		}
		chunk = ItemUtil.ITEM_data(it);
		
		int chunks_found = 1;
		ItemUtil.setItflags(it, ItemFlags.ITEM_SLABBED.getFlags());
		ItemUtil.setSlabsClsid(it, (byte)0);
		ItemUtil.setPrev(it, 0);
		ItemUtil.setNext(it, ItemChunkUtil.getNext(chunk));
		
		chunk = ItemChunkUtil.getNext(chunk);
		ItemChunkUtil.setPrev(chunk, it);
		
		while(chunk!=0){
			ItemChunkUtil.setItFlags(chunk, ItemFlags.ITEM_SLABBED.getFlags());
			ItemChunkUtil.setSlabsClsid(chunk, (byte)0);
			chunks_found++;
			long chunknext = ItemChunkUtil.getNext(chunk);
			if(chunknext!=0){
				chunk = chunknext;
			}else{
				break;
			}
		}
		
		long chunknext = ItemChunkUtil.getNext(chunk);
		ItemChunkUtil.setNext(chunknext, SlabClassUtil.getSlots(p));
		chunknext = ItemChunkUtil.getNext(chunk);
		if(chunknext!=0){
			ItemChunkUtil.setPrev(chunknext, chunk);
		}
		
		SlabClassUtil.setSlots(p, it);
		SlabClassUtil.incrSlCurr(p,chunks_found);
		SlabClassUtil.decrRequested(p, size);
	}
	
	/* Preallocate as many slab pages as possible (called from slabs_init)
	   on start-up, so users don't get confused out-of-memory errors when
	   they do have free (in-slab) space, but no space to make new slabs.
	   if maxslabs is 18 (POWER_LARGEST - POWER_SMALLEST + 1), then all
	   slab types can be made.  if max memory is less than 18 MB, only the
	   smaller ones will be made.  */
	private void slabs_preallocate(int maxslabs){
		int i;
		int prealloc = 0;
		for(i=Settings.POWER_SMALLEST; i<Settings.MAX_NUMBER_OF_SLAB_CLASSES; i++){
			if(++prealloc > maxslabs)
				return;
			if(!do_slabs_newslab(i)){  //为每个slabclass 预分配一个 slab
				if(logger.isErrorEnabled()){
					logger.error("Error while preallocating slab memory!\n If using -L or other prealloc options, max memory must be "
							+"at least {} megabytes.\n",power_largest);
				}
				Runtime.getRuntime().exit(1);
			}
		}
	}
	
	@Override
	public long getSlabClass(int id){
		return slabclass + id*SlabClassUtil.getNtotal();
	}
	
	/**
	 * 内存模块初始化
	 */
	@Override
	public void slabs_init(long limit, double factor, boolean prealloc, int[] slab_sizes) {		
		
		int i = Settings.POWER_SMALLEST -1;
		int size = Settings.chunkSize+Settings.ITEM_HEADER_LENGTH;
		
		mem_limit = limit;
		
		/*
		 * 如果是预分配方式
		 */
		if(Settings.prealloc){
			/* Allocate everything in a big chunk with malloc */
			mem_base = UnSafeUtil.unsafe.allocateMemory(mem_limit);
			if(mem_base !=0){
				mem_current = mem_base;
				mem_avail = limit;
			}else{
				logger.error("Warning: Failed to allocate requested memory in  one large chunk.\nWill allocate in smaller chunks\n");
			}
		}
		
		/* 初始化 slabclass 数组, 并将内存首地址 赋值给 slabclass  */
		long scSize = Settings.MAX_NUMBER_OF_SLAB_CLASSES * SlabClassUtil.getNtotal();
		slabclass = UnSafeUtil.unsafe.allocateMemory(scSize);
		UnSafeUtil.unsafe.setMemory(slabclass, scSize, (byte)0);
		
		/* 默认 循环处理   1 - 62  号 slabclass , 0号,63号,64号 slabclass 没有初始化  */
		while(++i < Settings.MAX_NUMBER_OF_SLAB_CLASSES -1){
			if(slab_sizes!=null){
				if(slab_sizes[i-1]==0){
					break;
				}
				size = slab_sizes[i-1];
			}else if(size >= Settings.slabChunkSizeMax/factor){
				break;
			}
			
			/* Make sure items are always n-byte aligned  */
			if((size % Settings.CHUNK_ALIGN_BYTES)!=0){
				size += Settings.CHUNK_ALIGN_BYTES-(size % Settings.CHUNK_ALIGN_BYTES);
			}
			/* 指定当前 slabclass 中, 每个slab中  每个 chunk 的大小; 并指定每个slab 可以 存储 多少个 chunk */
			long slabclass = getSlabClass(i);
			SlabClassUtil.setSize(slabclass, size);
			int perslab = Settings.slabPageSize/size;
			SlabClassUtil.setPerslab(slabclass,perslab);
			/* 当前size 附加增长因子     */
			if(slab_sizes==null){
				size *=factor;
			}
			if(Settings.verbose > 1){
				logger.info("slab addr {},class {},chunk size {},perslab {}",slabclass,i,size,perslab);
			}
		}
		
		/* 指定当前最大的 slabclass 编号  */
		power_largest = i;
		/* 给最后一个 slabclass 指定   每个slab中  每个 chunk 的大小; 并指定每个slab 可以 存储 多少个 chunk */
		long largestAddr = getSlabClass(power_largest);
		SlabClassUtil.setSize(largestAddr, Settings.slabChunkSizeMax);
		SlabClassUtil.setPerslab(largestAddr, Settings.slabPageSize/Settings.slabChunkSizeMax);
		
		if(logger.isInfoEnabled()){
			logger.info("slab addr {}, class {},chunk size {},perslab {}",largestAddr,i,Settings.slabChunkSizeMax,Settings.slabPageSize/Settings.slabChunkSizeMax);
		}
		
		/* 如果指定预分配,则为  Settings.POWER_SMALLEST -- power_largest 的所有 slabclass,预分配一个 slab  */
		if(prealloc){
			slabs_preallocate(power_largest);
		}
	}

	@Override
	public int slabs_clsid(int size) {
		int res = Settings.POWER_SMALLEST;
		
		if(size ==0 || size > Settings.itemSizeMax){
			return 0;
		}
				
		while(size > SlabClassUtil.getSize(getSlabClass(res))){
			if(res++==Settings.POWER_LARGEST){
				return Settings.POWER_LARGEST;
			}
		}
		return res;
	}

	@Override
	public long slabs_alloc(int size, int id, long total_bytes, int flags) {
		long ret = 0;
		while(!slabs_lock.compareAndSet(false, true)){}
		try {
			ret = do_slabs_alloc(size,id,total_bytes,flags);
		} finally {
			slabs_lock.lazySet(false);
		}
		return ret;
	}
	
	private long do_slabs_alloc(int size,int id,long total_bytes,int flags){
		long p;
		long ret;
		long it;
		if(id<Settings.POWER_SMALLEST || id > power_largest){
			return 0;
		}
		
		p = getSlabClass(id);
		if(total_bytes!=0){
			UnSafeUtil.putLong(total_bytes, SlabClassUtil.getRequested(p));
		}
		
		if(size <= SlabClassUtil.getSize(p)){
			int sl_curr = SlabClassUtil.getSlCurr(p);
			/* fail unless we have space at the end of a recently allocated page,
	           we have something on our freelist, or we could allocate a new page */
			if(sl_curr==0&&flags!=SLABS_ALLOC_NO_NEWPAGE){
				do_slabs_newslab(id); //如果没有可用的 item. 新分配一个slab
			}
			
			sl_curr = SlabClassUtil.getSlCurr(p); //重新获取 当前可用item 数量
			if(sl_curr!=0){
				/* return off our freelist */
				it = SlabClassUtil.getSlots(p);  //将it 从空闲链表头中取出
				logger.debug(ItemUtil.ItemToString(it));
				long next = ItemUtil.getNext(it);  //将链表第二个元素移动到链表头, 并将 prev 设置为0,因为已经没有上一个元素了
				SlabClassUtil.setSlots(p, next);
				if(next!=0){
					ItemUtil.setPrev(next, 0); 
				}
				
				/* Kill flag and initialize refcount here for lock safety in slab
	             * mover's freeness detection. */
				byte it_flags = ItemUtil.getItflags(it);
				ItemUtil.setItflags(it, (byte)(it_flags &~ItemFlags.ITEM_SLABBED.getFlags()));
				ItemUtil.setRefCount(it, 1);
				SlabClassUtil.decrSlCurr(p);
				ret = it;
			}else{
				ret = 0;
			}
		}else{
			/* Dealing with a chunked item. */
	        ret = do_slabs_alloc_chunked(size, p, id);
		}
		if(ret!=0){
			SlabClassUtil.incrRequested(p, size);
		}
		return ret;
	}
	
	/**
	 * This calculation ends up adding sizeof(void *) to the item size.
	 * @param size
	 * @param p
	 * @param id
	 * @return
	 */
	public long do_slabs_alloc_chunked(int size,long p,int id){
		long ret;
		long it;
		int cSize = SlabClassUtil.getSize(p)-ItemChunkUtil.getNtotal();
		int chunks_req= size/cSize;
		if(size % cSize!=0){
			chunks_req++;
		}
		int slCurr = SlabClassUtil.getSlCurr(p);
		while(slCurr<chunks_req){
			if(!do_slabs_newslab(id)){
				break;
			}
		}
		if(slCurr>=chunks_req){
			long chunk;
			/* Configure the head item in the chain. */
			it = SlabClassUtil.getSlots(p);
			long nextIt = ItemUtil.getNext(it);
			SlabClassUtil.setSlots(p,nextIt);

			/* Squirrel away the "top chunk" into h_next for now */
			ItemUtil.setHNext(it,SlabClassUtil.getSlots(p));
			chunk = ItemUtil.ITEM_data(it);


			/* roll down the chunks, marking them as such. */
			for(int x = 0;x<chunks_req-1;x++){
				ItemChunkUtil.setItFlags(chunk,(byte)(ItemChunkUtil.getItFlags(chunk) & ~ItemFlags.ITEM_SLABBED.getFlags()));
				ItemChunkUtil.setItFlags(chunk,(byte)(ItemChunkUtil.getItFlags(chunk) | ItemFlags.ITEM_CHUNK.getFlags()));
				/* Chunks always have a direct reference to the head item */
				ItemChunkUtil.setHead(chunk,it);
				ItemChunkUtil.setSize(chunk,SlabClassUtil.getSize(p)-ItemChunkUtil.getNtotal());
				ItemChunkUtil.setUsed(chunk,0);
				ItemChunkUtil.getNext(chunk);
			}
			 /* The final "next" is now the top of the slab freelist */
			SlabClassUtil.setSlots(p,chunk);
			if(chunk!=0 && ItemChunkUtil.getPrev(chunk)!=0){
				long prev = ItemChunkUtil.getPrev(chunk);
				long preChunk = ItemChunkUtil.getPrev(chunk);
				ItemChunkUtil.setNext(preChunk,0);
				ItemChunkUtil.setPrev(chunk,0);
			}

			ItemUtil.setItflags(it,(byte)(ItemUtil.getItflags(it) & ~ItemFlags.ITEM_SLABBED.getFlags()));
			ItemUtil.setItflags(it,(byte)(ItemUtil.getItflags(it) | ItemFlags.ITEM_CHUNKED.getFlags()));
			ItemUtil.setRefCount(it,1);
			slCurr = SlabClassUtil.getSlCurr(p);
			SlabClassUtil.incrSlCurr(p,slCurr-chunks_req);
			ret = it;
		}else{
			ret = 0;
		}
		return ret;
	}
	

	@Override
	public void slabs_free(long ptr, int size, int id) {
		while(!slabs_lock.compareAndSet(false, true)){}
		try {
			do_slabs_free(ptr,size,id);
		} finally {
			slabs_lock.lazySet(false);
		}
	}

	@Override
	public void slabs_adjust_mem_requested(int id, int old, int ntotal) {
		while(!slabs_lock.compareAndSet(false,true)){}
		try{
			if(id<Settings.POWER_SMALLEST || id > power_largest){
				return;
			}
			long p = getSlabClass(id);
			long requested = SlabClassUtil.getRequested(p) - old + ntotal;
			SlabClassUtil.incrRequested(p,requested);
		}finally {
			slabs_lock.lazySet(false);
		}
	}

	@Override
	public boolean slabs_adjust_mem_limit(int new_mem_limit) {
		 /* Cannot adjust memory limit at runtime if prealloc'ed */
		if(mem_base!=0){
			return false;
		}
		
		Settings.maxbytes = new_mem_limit;
		mem_limit = new_mem_limit;
		mem_limit_reached = false; /* Will reset on next alloc */
		memory_release(); /* free what might already be in the global pool */
		return true;
	}
	
	/* Must only be used if all pages are item_size_max */
	private void memory_release(){
		long p;
		if(mem_base!=0){
			return;
		}
		
		if(!Settings.slabReassign){
			return;
		}
		
		while(mem_malloced > mem_limit 
				&&(p = get_page_from_global_pool())!=0){
			UnSafeUtil.unsafe.freeMemory(p);
			mem_malloced -= Settings.itemSizeMax;
		}
	}

	@Override
	public int slabs_available_chunks(int id, long mem_flag, long total_bytes, long chunks_perslab) {
		int ret;
		while(!slabs_lock.compareAndSet(false,true)){}
		try{
			long p = getSlabClass(id);
			ret = SlabClassUtil.getSlCurr(p);
			if(mem_flag!=0){
				mem_flag = mem_limit_reached?1:0;
			}
			if(total_bytes!=0){
				total_bytes = SlabClassUtil.getRequested(p);
			}
			if(chunks_perslab!=0){
				chunks_perslab = SlabClassUtil.getPerslab(p);
			}
		}finally{
			slabs_lock.lazySet(false);
		}
		return ret;
	}

	@Override
	public int start_slab_maintenance_thread() {
		int ret;
		slab_rebalance_signal=0;
		slab_bulk_check =Integer.valueOf(System.getenv("MEMCACHED_SLAB_BULK_CHECK"));
		if(slab_bulk_check==0){
			slab_bulk_check = DEFAULT_SLAB_BULK_CHECK;
		}
		rebalance_tid = new Thread(this);
		rebalance_tid.start();
		return 0;
	}

	@Override
	public void run() {
		slab_rebalance_thead();
	}

	/* Slab mover thread.
	 * Sits waiting for a condition to jump off and shovel some memory about
	 */
	private void slab_rebalance_thead() {
		int was_busy=0;
		/* So we first pass into cond_wait with the mutex held */
		slabs_rebalance_lock.lock();
		try {
			while (do_run_slab_rebalance_thread > 0) {
				if (slab_rebalance_signal == 1) {
					if (slab_rebalance_start() < 0) {
					 /* Handle errors with more specifity as required. */
						slab_rebalance_signal = 0;
					}

					was_busy = 0;
				} else if (slab_rebalance_signal > 0 && slab_rebal.getSlabStart() != 0) {
					was_busy = slab_rebalance_move();
				}

				if (slab_rebal.getDone() > 0) {
					slab_rebal_finish();
				} else if (was_busy > 0) {
				/* Stuck waiting for some items to unlock, so slow down a bit
             * to give them a chance to free up */
					try {
						Thread.sleep(50 / 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				if (slab_rebalance_signal == 0) {
				/* always hold this lock while we're running */
					try {
						slab_rebalance_cond.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}finally {
			slabs_rebalance_lock.unlock();
		}

	}

	private void slab_rebal_finish() {
		int rescues= 0;
		int evictions_nomem=0;
		int inline_reclaim=0;
		int chunk_rescues=0;
		long s_cls=0;
		long d_cls=0;

		while(!slabs_lock.compareAndSet(false,true)){}
		try{
			s_cls = getSlabClass(slab_rebal.getsClsid());
			d_cls = getSlabClass(slab_rebal.getdClsid());

			//TODO 是否需要这部分代码
		/*	#ifdef DEBUG_SLAB_MOVER
    *//* If the algorithm is broken, live items can sneak in. *//*
			slab_rebal.slab_pos = slab_rebal.slab_start;
			while (1) {
				item *it = slab_rebal.slab_pos;
				assert(it->it_flags == (ITEM_SLABBED|ITEM_FETCHED));
				assert(memcmp(ITEM_key(it), "deadbeef", 8) == 0);
				it->it_flags = ITEM_SLABBED|ITEM_FETCHED;
				slab_rebal.slab_pos = (char *)slab_rebal.slab_pos + s_cls->size;
				if (slab_rebal.slab_pos >= slab_rebal.slab_end)
					break;
			}
			#endif*/

			/* At this point the stolen slab is completely clear.
			 * We always kill the "first"/"oldest" slab page in the slab_list, so
			 * shuffle the page list backwards and decrement.
			 */
			SlabClassUtil.decrSlabs(s_cls);
			for(int x=0;x<SlabClassUtil.getSlabs(s_cls);x++){
				SlabClassUtil.setSlablistIndexValue(s_cls,x,SlabClassUtil.getSlabListIndexValue(s_cls,x+1));
			}
			SlabClassUtil.setSlablistIndexValue(d_cls,SlabClassUtil.getSlabs(d_cls),slab_rebal.getSlabStart());
			 /* Don't need to split the page into chunks if we're just storing it */
			if(slab_rebal.getdClsid()>Settings.SLAB_GLOBAL_PAGE_POOL){
				UnSafeUtil.unsafe.setMemory(slab_rebal.getSlabStart(),Settings.itemSizeMax,(byte)0);
				split_slab_page_into_freelist(slab_rebal.getSlabStart(),slab_rebal.getdClsid());
			}else if(slab_rebal.getdClsid()==Settings.SLAB_GLOBAL_PAGE_POOL){
				memory_release();
			}
			slab_rebal.setDone(0);
			slab_rebal.setsClsid(0);
			slab_rebal.setdClsid(0);
			slab_rebal.setSlabStart(0);
			slab_rebal.setSlabEnd(0);
			slab_rebal.setSlabPos(0);
			evictions_nomem = slab_rebal.getEvictionsNomem();
			inline_reclaim = slab_rebal.getInlineReclaim();
			rescues = slab_rebal.getRescues();
			chunk_rescues = slab_rebal.getChunkRescues();
			slab_rebal.setEvictionsNomem(0);
			slab_rebal.setInlineReclaim(0);
			slab_rebal.setRescues(0);

			Stats.slabs_moved.addAndGet(1);
			Stats.slab_reassign_rescues.addAndGet(rescues);
			Stats.slab_reassign_evictions_nomem.addAndGet(evictions_nomem);
			Stats.slab_reassign_inline_reclaim.addAndGet(inline_reclaim);
			Stats.slab_reassign_chunk_rescues.addAndGet(chunk_rescues);
			StatsState.slab_reassign_running.lazySet(false);
			if(Settings.verbose>1){
				logger.info("finished a slab move\n");
			}
		}finally {
			slabs_lock.lazySet(false);
		}



	}

	/* refcount == 0 is safe since nobody can incr while item_lock is held.
	 * refcount != 0 is impossible since flags/etc can be modified in other
	 * threads. instead, note we found a busy one and bail. logic in do_item_get
	 * will prevent busy items from continuing to be busy
	 * NOTE: This is checking it_flags outside of an item lock. I believe this
	 * works since it_flags is 8 bits, and we're only ever comparing a single bit
	 * regardless. ITEM_SLABBED bit will always be correct since we're holding the
	 * lock which modifies that bit. ITEM_LINKED won't exist if we're between an
	 * item having ITEM_SLABBED removed, and the key hasn't been added to the item
	 * yet. The memory barrier from the slabs lock should order the key write and the
	 * flags to the item?
	 * If ITEM_LINKED did exist and was just removed, but we still see it, that's
	 * still safe since it will have a valid key, which we then lock, and then
	 * recheck everything.
	 * This may not be safe on all platforms; If not, slabs_alloc() will need to
	 * seed the item key while holding slabs_lock.
	 */
	private int slab_rebalance_move() {
		int was_busy = 0;
		int refcount = 0;
		ReentrantLock hold_lock = null;
		MOVE_STATUS status = MOVE_STATUS.MOVE_PASS;
		long hv = 0;
	try{
		while (!slabs_lock.compareAndSet(false, true)) {
		}
		long s_cls = getSlabClass(slab_rebal.getsClsid());
		for (int x = 0; x < slab_bulk_check; x++) {
			long it = slab_rebal.getSlabPos();
			long ch = 0;
			status = MOVE_STATUS.MOVE_PASS;
			if ((ItemUtil.getItflags(it) & ItemFlags.ITEM_CHUNK.getFlags()) > 0) {
				/* This chunk is a chained part of a larger item. */
				ch = ItemUtil.ITEM_data(it);
				/* Instead, we use the head chunk to find the item and effectively
				 * lock the entire structure. If a chunk has ITEM_CHUNK flag, its
				 * head cannot be slabbed, so the normal routine is safe.
				 */
				it = ItemChunkUtil.getHead(ch);
			}

			/* ITEM_FETCHED when ITEM_SLABBED is overloaded to mean we've cleared
			 * the chunk for move. Only these two flags should exist.
			 */
			if (ItemUtil.getItflags(it) != (ItemFlags.ITEM_SLABBED.getFlags() | ItemFlags.ITEM_FETCHED.getFlags())) {
				 /* ITEM_SLABBED can only be added/removed under the slabs_lock */
				if ((ItemUtil.getItflags(it) & ItemFlags.ITEM_SLABBED.getFlags()) > 0) {
					slab_rebalance_cut_free(s_cls, it);
					status = MOVE_STATUS.MOVE_FROM_SLAB;
				} else if ((ItemUtil.getItflags(it) & ItemFlags.ITEM_LINKED.getFlags()) != 0) {
					/* If it doesn't have ITEM_SLABBED, the item could be in any
					 * state on its way to being freed or written to. If no
					 * ITEM_SLABBED, but it's had ITEM_LINKED, it must be active
					 * and have the key written to it already.
					 */
					hv = JcacheContext.getHash().hash(ItemUtil.getKey(it), ItemUtil.getNskey(it));
					hold_lock = JcacheContext.getSegment().item_trylock(hv);
					if (hold_lock == null) {
						status = MOVE_STATUS.MOVE_LOCKED;
					} else {
						if (ItemUtil.incrRefCount(it) == 2) {
							/* item is linked but not busy */
							/* Double check ITEM_LINKED flag here, since we're
							 * past a memory barrier from the mutex. */
							if ((ItemUtil.getItflags(it) & ItemFlags.ITEM_LINKED.getFlags()) != 0) {
								status = MOVE_STATUS.MOVE_FROM_LRU;
							} else {
								/* refcount == 1 + !ITEM_LINKED means the item is being
								 * uploaded to, or was just unlinked but hasn't been freed
								 * yet. Let it bleed off on its own and try again later */
								status = MOVE_STATUS.MOVE_BUSY;
							}
						} else {
							if (Settings.verbose > 2) {
								logger.debug("Slab reassign hit a busy item:refcount: {} src: {} dst: {}", ItemUtil.getRefCount(it), slab_rebal.getsClsid(), slab_rebal.getdClsid());
							}
							status = MOVE_STATUS.MOVE_BUSY;
						}
						/* Item lock must be held while modifying refcount */
						if (status == MOVE_STATUS.MOVE_BUSY) {
							ItemUtil.decrRefCount(it);
							JcacheContext.getSegment().item_unlock(hv);
						}
					}
				} else {
					/* See above comment. No ITEM_SLABBED or ITEM_LINKED. Mark
                 	* busy and wait for item to complete its upload. */
					status = MOVE_STATUS.MOVE_BUSY;
				}
			}

			long new_it = 0;
			int save_item = 0;
			int ntotal = 0;
			switch (status) {
				case MOVE_FROM_LRU:
					/* Lock order is LRU locks -> slabs_lock. unlink uses LRU lock.
					 * We only need to hold the slabs_lock while initially looking
					 * at an item, and at this point we have an exclusive refcount
					 * (2) + the item is locked. Drop slabs lock, drop item to
					 * refcount 1 (just our own, then fall through and wipe it
					 */
					/* Check if expired or flushed */
					ntotal = ItemUtil.ITEM_ntotal(it);
					/* REQUIRES slabs_lock: CHECK FOR cls->sl_curr > 0 */
					if (ch == 0 && (ItemUtil.getItflags(it) & ItemFlags.ITEM_CHUNKED.getFlags()) > 0) {
						/* Chunked should be identical to non-chunked, except we need
                     * to swap out ntotal for the head-chunk-total. */
						ntotal = SlabClassUtil.getSize(s_cls);
					}
					if ((ItemUtil.getExpTime(it) != 0 && ItemUtil.getExpTime(it) < System.currentTimeMillis())
							|| item_is_flushed(it)) {
						/* Expired, don't save. */
						save_item = 0;
					} else if (ch == 0 &&
							(new_it = slab_rebalance_alloc(ntotal, slab_rebal.getsClsid())) == 0) {
						/* Not a chunk of an item, and nomem. */
						save_item = 0;
						slab_rebal.setEvictionsNomem(slab_rebal.getEvictionsNomem() + 1);
					} else if (ch != 0 &&
							(new_it = slab_rebalance_alloc(SlabClassUtil.getSize(s_cls), slab_rebal.getsClsid())) == 0) {
						/* Is a chunk of an item, and nomem. */
						save_item = 0;
						slab_rebal.setEvictionsNomem(slab_rebal.getEvictionsNomem() + 1);
					} else {
						/* Was whatever it was, and we have memory for it. */
						save_item = 1;
					}
					int requested_adjust = 0;
					if (save_item > 0) {
						if (ch == 0) {
							UnSafeUtil.copyMemory(it, new_it, ntotal);
							ItemUtil.setPrev(new_it, 0);
							ItemUtil.setNext(new_it, 0);
							ItemUtil.setHNext(new_it, 0);
							 /* These are definitely required. else fails assert */
							ItemUtil.setItflags(it, (byte) (ItemUtil.getItflags(new_it) & ~ItemFlags.ITEM_LINKED.getFlags()));
							ItemUtil.setRefCount(new_it, 0);
							JcacheContext.getItemsAccessManager().item_replace(it, new_it, hv);
							/* Need to walk the chunks and repoint head  */
							if ((ItemUtil.getItflags(new_it) & ItemFlags.ITEM_CHUNKED.getFlags()) > 0) {
								long fch = ItemUtil.ITEM_data(it);
								ItemChunkUtil.setPrev(ItemChunkUtil.getNext(fch), fch);
								while (fch != 0) {
									ItemChunkUtil.setHead(fch, new_it);
									fch = ItemChunkUtil.getNext(fch);
								}
							}
							ItemUtil.setRefCount(it, 0);
							ItemUtil.setItflags(it, (byte) (ItemFlags.ITEM_SLABBED.getFlags() | ItemFlags.ITEM_FETCHED.getFlags()));
							slab_rebal.setRescues(slab_rebal.getRescues() + 1);
							requested_adjust = ntotal;
						} else {
							long nch = ItemUtil.ITEM_data(new_it);
							/* Chunks always have head chunk (the main it) */
							ItemChunkUtil.setNext(ItemChunkUtil.getPrev(ch), nch);
							if (ItemChunkUtil.getNext(ch) != 0) {
								ItemChunkUtil.setPrev(ItemChunkUtil.getNext(ch), nch);
							}
							UnSafeUtil.copyMemory(ch, nch, ItemChunkUtil.getUsed(ch) + ItemChunkUtil.getNtotal());
							ItemChunkUtil.setRefcount(ch, (short) 0);
							ItemChunkUtil.setItFlags(ch, (byte) (ItemFlags.ITEM_SLABBED.getFlags() | ItemFlags.ITEM_FETCHED.getFlags()));
							slab_rebal.setChunkRescues(slab_rebal.getChunkRescues() + 1);
							ItemUtil.decrRefCount(it);
							requested_adjust = SlabClassUtil.getSize(s_cls);
						}
					} else {
						/* restore ntotal in case we tried saving a head chunk. */
						ntotal = ItemUtil.ITEM_ntotal(it);
						JcacheContext.getItemsAccessManager().item_unlink(it);
						JcacheContext.getSlabPool().slabs_free(it, ntotal, slab_rebal.getsClsid());
						/* Swing around again later to remove it from the freelist. */
						slab_rebal.setBusyItems(slab_rebal.getBusyItems() + 1);
						was_busy++;
					}
					/* Always remove the ntotal, as we added it in during
					 * do_slabs_alloc() when copying the item.
					 */
					SlabClassUtil.decrRequested(s_cls, SlabClassUtil.getRequested(s_cls) - requested_adjust);
					break;
				case MOVE_FROM_SLAB:
					ItemUtil.setRefCount(it, 0);
					ItemUtil.setItflags(it, (byte) (ItemFlags.ITEM_SLABBED.getFlags() | ItemFlags.ITEM_FETCHED.getFlags()));
					break;
				case MOVE_BUSY:
				case MOVE_LOCKED:
					slab_rebal.setBusyItems(slab_rebal.getBusyItems() + 1);
					was_busy++;
					break;
				case MOVE_PASS:
					break;
			}
			slab_rebal.setSlabPos(slab_rebal.getSlabPos() + SlabClassUtil.getSize(s_cls));
			if (slab_rebal.getSlabPos() > slab_rebal.getSlabEnd()) {
				break;
			}
		}
		if (slab_rebal.getSlabPos() >= slab_rebal.getSlabEnd()) {
			/* Some items were busy, start again from the top */
			if (slab_rebal.getBusyItems() > 0) {
				slab_rebal.setSlabPos(slab_rebal.getSlabStart());
				Stats.slab_reassign_busy_items.addAndGet(slab_rebal.getBusyItems());
				slab_rebal.setBusyItems(0);
			} else {
				slab_rebal.setDone(slab_rebal.getDone() + 1);
			}
		}
	}finally{
		slabs_lock.lazySet(false);
	}
		return was_busy;
	}

	/* CALLED WITH slabs_lock HELD */
	private long slab_rebalance_alloc(int size, int id) {
		long s_cls = getSlabClass(slab_rebal.getsClsid());
		long new_it=0;
		int slabs = SlabClassUtil.getPerslab(s_cls);
		for(int x=0;x<slabs;x++){
			new_it = JcacheContext.getSlabPool().slabs_alloc(size,id,0,Slabs.SLABS_ALLOC_NO_NEWPAGE);
			 /* check that memory isn't within the range to clear */
			if(new_it==0){
				break;
			}
			if(new_it>=slab_rebal.getSlabStart() && new_it<slab_rebal.getSlabEnd()){
				/* Pulled something we intend to free. Mark it as freed since
				 * we've already done the work of unlinking it from the freelist.
				 */
				SlabClassUtil.decrRequested(s_cls,Long.valueOf(String.valueOf(size)).longValue());
				ItemUtil.setRefCount(new_it,0);
				ItemUtil.setItflags(new_it,(byte)(ItemFlags.ITEM_SLABBED.getFlags() | ItemFlags.ITEM_FETCHED.getFlags()));
				new_it=0;
				slab_rebal.setInlineReclaim(slab_rebal.getInlineReclaim()+ 1);
			}else{
				break;
			}
		}
		return new_it;
	}

	private boolean item_is_flushed(long itemaddr){
		long oldest_live = Settings.oldestLive;
		long cas = ItemUtil.getCAS(itemaddr);
		long oldest_cas = Settings.oldestCas;
		long time = ItemUtil.getTime(itemaddr);

		if (oldest_live == 0 || oldest_live > System.currentTimeMillis())
			return false;
		if ((time <= oldest_live)
				|| (oldest_cas != 0 && cas != 0 && cas < oldest_cas)) {
			return true;
		}
		return false;
	}

	/* CALLED WITH slabs_lock HELD */
	/* detatches item/chunk from freelist. */
	private void slab_rebalance_cut_free(long s_cls, long it) {
	 	/* Ensure this was on the freelist and nothing else. */
		if(SlabClassUtil.getSlots(s_cls) == it){
			long next = ItemUtil.getNext(it);
			SlabClassUtil.setSlots(s_cls,next);
		}
		//if (it->next) it->next->prev = it->prev;
		ItemUtil.setPrev(ItemUtil.getNext(it),ItemUtil.getPrev(it));
		//if (it->prev) it->prev->next = it->next;
		ItemUtil.setNext(ItemUtil.getPrev(it),ItemUtil.getNext(it));
		SlabClassUtil.decrSlCurr(s_cls);
	}

	private int slab_rebalance_start() {
		int no_go=0;
		long s_cls;
		while(!slabs_lock.compareAndSet(false,true)){}
		try{
			try {
				if (slab_rebal.getsClsid() < Settings.POWER_LARGEST
						|| slab_rebal.getsClsid() > power_largest
						|| slab_rebal.getdClsid() < Settings.SLAB_GLOBAL_PAGE_POOL
						|| slab_rebal.getdClsid() > power_largest
						|| slab_rebal.getsClsid() == slab_rebal.getdClsid()) {
					no_go = 2;
				}
				s_cls = getSlabClass(slab_rebal.getsClsid());

				if (!grow_slab_list(slab_rebal.getdClsid())) {
					no_go = -1;
				}

				if (SlabClassUtil.getSlabs(s_cls) < 2) {
					no_go = -3;
				}
			}finally{
					if(no_go!=0) {
						slabs_lock.lazySet(false);
						return no_go;
					}
			}


			/* Always kill the first available slab page as it is most likely to
			* contain the oldest items
			*/
			slab_rebal.setSlabStart(SlabClassUtil.getSlabListIndexValue(s_cls,0));
			slab_rebal.setSlabEnd(slab_rebal.getSlabStart()+
					(SlabClassUtil.getSize(s_cls)+SlabClassUtil.getPerslab(s_cls)));
			slab_rebal.setSlabPos(slab_rebal.getSlabStart());
			slab_rebal.setDone(0);

			 /* Also tells do_item_get to search for items in this slab */
			slab_rebalance_signal=2;

			if(Settings.verbose>1){
				logger.error("Started a slab rebalance\n");
			}
		}finally{
				slabs_lock.lazySet(false);
		}
		while(!StatsState.slab_reassign_running.compareAndSet(false,true)){};
		return 0;
	}

	/*
	 * The maintenance thread is on a sleep/loop cycle, so it should join after a
 	* short wait
 	* */
	@Override
	public void stop_slab_maintenance_thread() {
		slabs_rebalance_lock.lock();
		try{
			do_run_slab_thread=0;
			do_run_slab_rebalance_thread=0;
			slab_rebalance_cond.signal();
		}finally {
			slabs_rebalance_lock.unlock();
		}
		/* Wait for the maintenance thread to stop */
		try {
			rebalance_tid.join();
		} catch (InterruptedException e) {
			logger.error("stop thread error",e);
			e.printStackTrace();
		}
	}

	@Override
	public REASSIGN_RESULT_TYPE slabs_reassign(int src, int dst) {
		REASSIGN_RESULT_TYPE ret;
		try {
			if (!slabs_rebalance_lock.tryLock()) {
				return REASSIGN_RESULT_TYPE.REASSIGN_RUNNING;
			}
			ret = do_slabs_reassign(src, dst);
		}finally {
			slabs_rebalance_lock.unlock();
		}

		return ret;
	}

	private REASSIGN_RESULT_TYPE do_slabs_reassign(int src, int dst) {
		if(slab_rebalance_signal!=0){
			return REASSIGN_RESULT_TYPE.REASSIGN_RUNNING;
		}
		if(src==dst){
			return REASSIGN_RESULT_TYPE.REASSIGN_SRC_DST_SAME;
		}

		  /* Special indicator to choose ourselves. */
		if(src==-1){
			src = slabs_reassign_pick_any(dst);
			/* TODO: If we end up back at -1, return a new error type */
		}
		if (src < Settings.POWER_SMALLEST  || src > power_largest ||
				dst < Settings.SLAB_GLOBAL_PAGE_POOL || dst > power_largest){
			return REASSIGN_RESULT_TYPE.REASSIGN_BADCLASS;
		}
		if(SlabClassUtil.getSlabs(src)<2){
			return REASSIGN_RESULT_TYPE.REASSIGN_NOSPARE;
		}
		slab_rebal.setsClsid(src);
		slab_rebal.setdClsid(dst);

		slab_rebalance_signal=1;
		slab_rebalance_cond.signal();

		return REASSIGN_RESULT_TYPE.REASSIGN_OK;
	}

	/* Iterate at most once through the slab classes and pick a "random" source.
 	* I like this better than calling rand() since rand() is slow enough that we
 	* can just check all of the classes once instead.
 	*/
	private int slabs_reassign_pick_any(int dst) {
		int cur = Settings.POWER_SMALLEST-1;
		int tries = power_largest-Settings.POWER_SMALLEST+1;
		for(;tries>0;tries--){
			cur++;
			if(cur>power_largest){
				cur=Settings.POWER_SMALLEST;
			}
			if(cur==dst){
				continue;
			}
			if(SlabClassUtil.getSlabs(cur)>1){
				return cur;
			}
		}
		return -1;
	}

	/* If we hold this lock, rebalancer can't wake up or move */
	@Override
	public void slabs_rebalancer_pause() {
		slabs_rebalance_lock.lock();
	}

	@Override
	public void slabs_rebalancer_resume() {
		slabs_rebalance_lock.unlock();

	}

}
