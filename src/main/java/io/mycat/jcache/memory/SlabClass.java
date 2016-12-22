package io.mycat.jcache.memory;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.util.ItemUtil;
 
	//typedef struct {
	//    unsigned int size;      /* sizes of items */
	//    unsigned int perslab;   /* how many items per slab */
	//
	//    void *slots;           /* list of item ptrs */
	//    unsigned int sl_curr;   /* total free items in list */
	//
	//    unsigned int slabs;     /* how many slabs were allocated for this class */
	//
	//    void **slab_list;       /* array of slab pointers */
	//    unsigned int list_size; /* size of prev array */
	//
	//    size_t requested; /* The number of requested bytes */
	//} slabclass_t;
/**
 * @author tangww
 * @author liyanjun
 *
 */
public class SlabClass {
	
	int chunkSize;  /* sizes of items 每个chunkSize 的大小  */

	int perSlab;    /* perslab 表示每个 slab 可以切分成多少个 chunk, 如果一个 slab 等于1M, 那么就有 perslab = 1M / size . how many items per slab */
	
	
	LinkedList<Long> used;  /* 已经分配出去的item 链表*/
	
	/**
	 * 当前空闲 slots 链表
	 */
	LinkedList<Long> slots = new LinkedList<>();
//    BitSet chunkAllocateTrack;
	
	/**
	 * 分配的slab链表  存放每个slab的 首地址
	 */
	LinkedBlockingQueue<Long> slab_list = new LinkedBlockingQueue<>();
	
	/**
	 * 当前总共剩余多少个空闲的item
	 * 当sl_curr=0的时候，说明已经没有空闲的item，需要分配一个新的slab（每个1M，可以切割成N多个Item结构）
	 */
	AtomicInteger sl_curr; /* total free items in list */
	
	/**
	 * 总共分配多少个slabs
	 */
	AtomicInteger allocatedslabs; /* how many slabs were allocated for this class */
		
	/**
	 * 还不清楚是干什么用的
	 */
	//TODO 还不清楚是干什么用的
	AtomicInteger list_size;  /* size of prev array */
	
	/**
	 * 总共请求的总byte  用于统计
	 */
	AtomicInteger requested;  /* The number of requested bytes */
			
	final AtomicBoolean allocLockStatus = new AtomicBoolean(false);
	
	public SlabClass(int chunkSize, int perSlab){
		this.chunkSize = chunkSize;
		this.perSlab = perSlab;
		sl_curr = new AtomicInteger(0);
		allocatedslabs = new AtomicInteger(0);
		list_size = new AtomicInteger(0);
		requested = new AtomicInteger(0);
	}	
	
	/**
	 * Allocate object of given length. 0 on error 
	 * @param ntotal   item 总长度
	 * @param slabsClassid 
	 * @param flags 
	 * @return  item 内存地址
	 */
	public long slabs_alloc(int size,int flags){
		while (!allocLockStatus.compareAndSet(false, true)) {
		}
		
		long addr = 0;
		
		try {
			if(sl_curr.get()==0){
				return 0;
			}
			
			if(size <= chunkSize){
				/* fail unless we have space at the end of a recently allocated page,
		           we have something on our freelist, or we could allocate a new page */
//				if(slabc.sl_curr==0&&flags!=Settings.SLABS_ALLOC_NO_NEWPAGE){
//					doSlabsNewSlab(slabsClassid);
//				}
				addr = slots.removeFirst();
				byte it_flags = ItemUtil.getItflags(addr);
				ItemUtil.setItflags(addr, (byte)(it_flags &~ItemFlags.ITEM_SLABBED.getFlags()));
				ItemUtil.setRefCount(addr,(short)1);
				sl_curr.decrementAndGet();
			}else{
				 /* Dealing with a chunked item. */
//				addr = do_slabs_alloc_chunked(size, p, id);
			}
			
			requested.addAndGet(size);
			return addr;
		} finally {
			allocLockStatus.set(false);
		}
	}
	
	//创建空闲item  
	public void doSlabsFree(long addr, int size,int id){
		while (!allocLockStatus.compareAndSet(false, true)) { }
		
		try {
			int it_flags = ItemUtil.getItflags(addr);
			if((it_flags & ItemFlags.ITEM_CHUNKED.getFlags()) == 0){
				ItemUtil.setItflags(addr, ItemFlags.ITEM_SLABBED.getFlags());
				ItemUtil.setSlabsClsid(addr, (byte)id);
				/**
				 * PigBrother
				slabc.sl_curr ++;
				slabc.requested -= size; //已经申请到的空间数量更新

				还是多线程问题
				 *改变了下面二句代码
				 */			
				sl_curr.incrementAndGet();
				requested.addAndGet(-size);
				slots.add(addr);
				
			}else{
//				doSlabsFreeChunked(addr, size, id, slabc);
			}
		} finally {
			allocLockStatus.set(false);
		}
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public int getPerSlab() {
		return perSlab;
	}

	public void setPerSlab(int perSlab) {
		this.perSlab = perSlab;
	}

}
