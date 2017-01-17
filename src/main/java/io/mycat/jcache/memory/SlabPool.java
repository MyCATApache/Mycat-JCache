package io.mycat.jcache.memory;

import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.UnSafeUtil;
import sun.misc.Unsafe;
import sun.nio.ch.FileChannelImpl;

/*
 * 
 * @author tangww
 * @author liyanjun
 * @author PigBrother
 *
 */
@SuppressWarnings("restriction")
public class SlabPool {

	private Logger log = LoggerFactory.getLogger(SlabPool.class);
	
	private static final Method mmap;
	private static final Method unmmap;
	private FileChannel channel;
	private RandomAccessFile randomFile;
	
	private final SlabClass[] slabClassArr;
	private long mem_current;  //当前位置 偏移量 
	private long mem_avail;    //当前可用内存
	private long memAlloced;   //已分配内存
	private long mem_limit;     //总内存大小
	private int powerLargest;  //最大 slabclass 数量
//	ByteBuffer[] baseBuf = null;  //预分配内存数组
//	int currByteIndex;  //当前用到了第几个bytebuffer;
	private long addr;
	private long totalSize;

	
	static {
		try {
			Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
			singleoneInstanceField.setAccessible(true);
			mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
			mmap.setAccessible(true);
			unmmap = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
			unmmap.setAccessible(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	final AtomicBoolean allocLockStatus = new AtomicBoolean(false);
	
	public SlabPool(long memLimit,String fileName){
		
		totalSize = memLimit;
		this.slabClassArr = new SlabClass[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
		
		int size = Settings.chunkSize+Settings.ITEM_HEADER_LENGTH;
		if(!Settings.prealloc){
			log.info(" prealloc ? "+Settings.prealloc );
			return;
		}
		
		try{
			randomFile = new RandomAccessFile(fileName, "rw");
			randomFile.setLength(totalSize);
			channel = randomFile.getChannel();
			addr = (long) mmap.invoke(channel, 1, 0, totalSize);
			mem_avail = memLimit;
			mem_limit = memLimit;
			mem_current = addr;

		}catch(Exception e){
			log.error(" Init allocate direct buffer fail. For "+e.getMessage(), e);
			return;
		}
		
		/* 此处留下了第一个没有初始化   magic slab class for storing pages for reassignment see Settings.SLAB_GLOBAL_PAGE_POOL 
		 * 根据配置 分配 slabclass 的数量
		 */
		int i = Settings.POWER_SMALLEST;
		for(; i<Settings.MAX_NUMBER_OF_SLAB_CLASSES-1 && size<=Settings.slabChunkSizeMax/Settings.factor; i++){
			
			if(size % Settings.CHUNK_ALIGN_BYTES != 0)
				size += Settings.CHUNK_ALIGN_BYTES-(size % Settings.CHUNK_ALIGN_BYTES);
			
			slabClassArr[i] = new SlabClass(size,Settings.slabPageSize/size);
			log.info("slab class "+i+": chunk size "+size+" item count "+slabClassArr[i].perSlab);
			size *= Settings.factor;
		}
		
		this.powerLargest = i; 
		slabClassArr[powerLargest] = new SlabClass(Settings.itemSizeMax,Settings.slabPageSize/Settings.slabChunkSizeMax);
		log.info("slab class "+i+": chunk size "+size+" item count "+slabClassArr[powerLargest].perSlab);
		
		/* 预分配slab 在 slabclasss 中 */
		slabsPreallocate(powerLargest);
		
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {
				try {
					unmmap.invoke(null, addr, getTotalSize());
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		});
	}
	
	private static Method getMethod(Class<?> cls, String name, Class<?>... params) throws Exception {
		Method m = cls.getDeclaredMethod(name, params);
		m.setAccessible(true);
		return m;
	}
	
	protected void unmap() throws Exception {
		unmmap.invoke(null, addr, getTotalSize());
	
	}
	
	/**
	 * 预分配slab 在 slabclasss 中 
	 * @param slabCount
	 */
	public void slabsPreallocate(int slabCount){
		int prealloc = 0;
		for(int i=Settings.POWER_SMALLEST; i<Settings.MAX_NUMBER_OF_SLAB_CLASSES; i++){
			if(++prealloc > slabCount)
				return;
			if(!doSlabsNewSlab(i)){
				log.error("Error while preallocating slab memory!\n If using -L or other prealloc options, max memory must be "
						+"at least "+slabCount+" megabytes.\n");
				return;
			}
		}
	}
	
	/**
	 * 
	 */
	public boolean doSlabsNewSlab(int id){
		SlabClass slabc = slabClassArr[id];
//		SlabClass globalc = slabClassArr[Settings.SLAB_GLOBAL_PAGE_POOL];
		int len = (Settings.slabReassign || Settings.slabChunkSizeMax != Settings.slabPageSize) 
				? Settings.slabPageSize : slabc.chunkSize*slabc.perSlab;
		if(mem_limit > 0 && memAlloced+len > mem_limit && slabc.perSlab > 0 )
//				&& globalc.perSlab == 0)
			return false;
		
		long slab;
		if(((slab=getPagefromGlobalPool())==0&&(slab = memoryAllocate(len))==0)){
			log.error("new slab fail from class id "+id);
			return false;
		}
		
		memoryInitSet(slab,(byte)0,len);
		splitSlabPageInfoFreelist(slab, id);
		
		slabc.slab_list.add(slab);
		return true;
	}
	
	//将ptr指向的内存空间按第id个slabclass的size进行切分
	public void splitSlabPageInfoFreelist(long slab, int id){
		SlabClass slabc = slabClassArr[id];
		//每个slabclass有多个slab,对每个slab按slabclass对应的size进行切分
		for(int i=0; i<slabc.perSlab; i++){
			slabc.doSlabsFree(slab+(i*slabc.chunkSize), 0, id);
		}
	}
	
	/**
	 * TODO  可能被 slab_rebalance_move 使用，
	 *       在释放chunk 的时候，也会被调用。 暂时放下。
	 * @param addr
	 * @param size
	 * @param id
	 * @param slabc
	 */
	public void doSlabsFreeChunked(long addr,int size,int id,SlabClass slabc){
		
	}
	
	/* Fast FIFO queue */
	public long getPagefromGlobalPool(){
		SlabClass slabc = slabClassArr[Settings.SLAB_GLOBAL_PAGE_POOL];
		if(slabc==null||slabc.slab_list.size() < 1){
			return 0;
		}
		//PigBrother。
		//原先是removeLast（） ，slabs 是空的 可以这样  但是 如果是 usedslab就不可以了
		return slabc.slab_list.remove();
	}
	
	public long memoryAllocate(int size){
		long ret;
		if(size > (mem_limit-memAlloced)){
			return 0;
		}
		
		ret = mem_current;
		
		if(size % Settings.CHUNK_ALIGN_BYTES != 0){
			size += Settings.CHUNK_ALIGN_BYTES - (size % Settings.CHUNK_ALIGN_BYTES);
		}
		
		mem_current += size;  //当前内存地址 后移 size 位
		if((mem_avail - size )>0){ //说明还有空间
			mem_avail -= size;    //可用内存 减少          size
		}else{
			mem_avail = 0;
		}
		
		memAlloced += size;
		if(log.isInfoEnabled()){
			log.info("memory allocated "+size+" B");
		}
		return ret;
	}
	
	
	public void memoryInitSet(long addr,byte value,int len){
		IntStream.range(0, len).forEach(f->{
			UnSafeUtil.putByte(addr+f, value);
		});
	}
	
	/**
	 * Given object size, return id to use when allocating/freeing memory for object
	 * 0 means error: can't store such a large object
	 */
	public int slabsClassid(int size){
		int res = Settings.POWER_SMALLEST;
		if(size <= 0 || size > Settings.itemSizeMax)
			return 0;
		while(size > slabClassArr[res].chunkSize){
			res ++;
			if(res == powerLargest)
				return powerLargest;
		}
		
		return res;
	}
	
	/**
	 * 获取当前slab 的 总共请求的总byte
	 * @param slabsClassid
	 * @return
	 */
	public int getRequested(int slabsClassid){
		SlabClass slabc = slabClassArr[slabsClassid];
		return slabc.requested.get();
	}
	
	/**
	 * 分配item，
	 * @param size
	 * @param slabsClassid
	 * @param flags
	 * @return 返回 item 内存首地址
	 */
	public long slabs_alloc(int size,int slabsClassid,int flags){
		SlabClass slabc = slabClassArr[slabsClassid];
		return slabc.slabs_alloc(size,flags);
	}
	
	/**
	 * 释放item
	 * @param addr
	 * @param ntotal
	 * @param slabsClassid
	 */
	public void slabs_free(long addr,int ntotal,int slabsClassid){
		SlabClass slabc = slabClassArr[slabsClassid];
		slabc.doSlabsFree(addr,ntotal,slabsClassid);
	}

	public long getTotalSize() {
		return totalSize;
	}
	
}
