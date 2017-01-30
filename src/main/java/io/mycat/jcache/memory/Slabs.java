package io.mycat.jcache.memory;

import io.mycat.jcache.enums.memory.REASSIGN_RESULT_TYPE;

public interface Slabs {
	
	

	
	/** Allocate object of given length. 0 on error */ /*@null@*/
	public static final int SLABS_ALLOC_NO_NEWPAGE = 1;  /* 不允许 slabclass 分配新的 page */
	
	public static final int SLABS_ALLOC_NEWPAGE = 0;  /* 允许 slabclass 分配新的 page */ 
	
	/** Init the subsystem. 1st argument is the limit on no. of bytes to allocate,
	    0 if no limit. 2nd argument is the growth factor; each slab will use a chunk
	    size equal to the previous slab's chunk size times this factor.
	    3rd argument specifies if the slab allocator should allocate all memory
	    up front (if true), or allocate memory in chunks as it is needed (if false)
	*/
	public void slabs_init(long limit,double factor,boolean prealloc,int[] slab_sizes);
	
	/**
	 * Figures out which slab class (chunk size) is required to store an item of
	 * a given size.
	 *
	 * Given object size, return id to use when allocating/freeing memory for object
	 * 0 means error: can't store such a large object
	 */
	public int slabs_clsid(int size);
	
	/**
	 * 获取 slabclass 的内存首地址
	 * @param id
	 * @return
	 */
	public long getSlabClass(int id);
	
	/** Allocate object of given length. 0 on error 
	 * @param size
	 * @param id
	 * @param total_bytes  内存首地址
	 * @param flags
	 * @return
	 */
	public long slabs_alloc(int size,int id,long total_bytes,int flags);
	
	/** Free previously allocated object */
	public void slabs_free(long addr,int size,int id);
	
	/** Adjust the stats for memory requested */
	public void slabs_adjust_mem_requested(int id,int old,int ntotal);
	
	/** Adjust global memory limit up or down */
	public boolean slabs_adjust_mem_limit(int new_mem_limit);
	
	/** Return a datum for stats in binary protocol */
//	public boolean get_stats(long stat_type,int nkey,ADD_STAT add_stats,long c);
	
	/** Fill buffer with stats */ /*@null@*/
//	public void slabs_stats(ADD_STAT add_stats,long c);
	
	/** Hints as to freespace in slab class
	 * @param id
	 * @param mem_flag
	 * @param total_bytes   内存首地址
	 * @param chunks_perslab 内存首地址
	 * @return
	 */
	public int slabs_available_chunks(int id,long mem_flag,long total_bytes,long chunks_perslab);
	
	public int start_slab_maintenance_thread();
	
	public void stop_slab_maintenance_thread();
	
	REASSIGN_RESULT_TYPE slabs_reassign(int src,int dst);
	
	public void slabs_rebalancer_pause();
	
	public void slabs_rebalancer_resume();
}
