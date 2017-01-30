package io.mycat.jcache.memory;

import java.util.concurrent.atomic.AtomicBoolean;

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
public class DefaultSlabsImpl implements Slabs {
	
	private Logger logger = LoggerFactory.getLogger(DefaultSlabsImpl.class);	
	
	private static long slabclass;
	private static long mem_limit;     //总内存大小
	private static long mem_malloced;   //已分配内存
	/* If the memory limit has been hit once. Used as a hint to decide when to
	 * early-wake the LRU maintenance thread */
	private static boolean mem_limit_reached = false;
	private static int power_largest;
	
	private static long mem_base;
	private static long mem_current;
	private static long mem_avail;
	
	/**
	 * Access to the slab allocator is protected by this lock
	 */
	private volatile static AtomicBoolean slabs_lock = new AtomicBoolean(false);
	private volatile static AtomicBoolean slabs_rebalance_lock = new AtomicBoolean(false);
	
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
		
		if(Settings.verbose > 1){
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
	        ret = do_slabs_alloc_chunked(size, p, id); //TODO 
		}
		
		if(ret!=0){
			SlabClassUtil.incrRequested(p, size);
		}
		return ret;
	}
	
	/**
	 * TODO
	 * @param size
	 * @param p
	 * @param id
	 * @return
	 */
	public long do_slabs_alloc_chunked(int size,long p,int id){
		return 0;
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
		// TODO Auto-generated method stub

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
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int start_slab_maintenance_thread() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void stop_slab_maintenance_thread() {
		// TODO Auto-generated method stub

	}

	@Override
	public REASSIGN_RESULT_TYPE slabs_reassign(int src, int dst) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void slabs_rebalancer_pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void slabs_rebalancer_resume() {
		// TODO Auto-generated method stub

	}

}
