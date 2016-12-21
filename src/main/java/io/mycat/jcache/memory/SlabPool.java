package io.mycat.jcache.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.UnSafeUtil;

/*
 * 
 * @author tangww
 * @author liyanjun
 * @author PigBrother
 *
 */
public class SlabPool {
	Logger log = LoggerFactory.getLogger(SlabPool.class);
	
	final SlabClass[] slabClassArr;
	long memBase;      //当前 bytebuffer  内存首地址
	long mem_current;  //当前bytebuffer 位置 偏移量   内存首地址
	long mem_avail;    //当前可用内存
	long memAlloced;   //已分配内存
	long mem_limit;     //总内存大小
	int powerLargest;  //最大 slabclass 数量
	ByteBuffer[] baseBuf = null;  //预分配内存数组
	int currByteIndex;  //当前用到了第几个bytebuffer;
	
	final AtomicBoolean allocLockStatus = new AtomicBoolean(false);
	
	public SlabPool(){
		this.slabClassArr = new SlabClass[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
	}
	
	public void init(long memLimit){
		int size = Settings.chunkSize+Settings.ITEM_HEADER_LENGTH;
		if(!Settings.prealloc){
			log.info(" prealloc ? "+Settings.prealloc );
			return;
		}
		
		try{
			
			if(memLimit<=Integer.MAX_VALUE){
				baseBuf = new ByteBuffer[1];
				baseBuf[0] = ByteBuffer.allocateDirect((int) memLimit);
			}else{
				int bufcount = (int) Math.ceil((memLimit/Integer.MAX_VALUE));
				int suffixbuff = (int)((memLimit-bufcount)%Integer.MAX_VALUE);
				baseBuf = new ByteBuffer[bufcount];
				for(int i=0;i<bufcount-1;i++){
					baseBuf[i] = ByteBuffer.allocateDirect(Integer.MAX_VALUE);
				}
				
				if(suffixbuff==0){
					baseBuf[bufcount-1] = ByteBuffer.allocateDirect(Integer.MAX_VALUE);
				}else{
					if(suffixbuff<Settings.slabPageSize){  //最后一个大小不足  slabPageSize 时， 不计算
						suffixbuff = 0;
					}
					
					if(bufcount>1&&suffixbuff>0){
						baseBuf[bufcount-1] = ByteBuffer.allocateDirect(suffixbuff);
					}
				}
			}
			mem_avail = memLimit;
			mem_limit = memLimit;
			memBase = ((sun.nio.ch.DirectBuffer) baseBuf[0]).address();
			mem_current = memBase;
			

		}catch(Exception e){
			log.error(" Init allocate direct buffer fail.Will allocate in smaller chunks. For "+e.getMessage(), e);
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

//		if(baseBuf == null){
//			ByteBuffer buf = ByteBuffer.allocateDirect(size);
//			slab = new Slab(buf,Settings.slabPageSize, size/Settings.slabPageSize);
//		}else{
		if(size > (mem_limit-memAlloced)){
			return 0;
		}
		
		if(size % Settings.CHUNK_ALIGN_BYTES != 0){
			size += Settings.CHUNK_ALIGN_BYTES - (size % Settings.CHUNK_ALIGN_BYTES);
		}
		
		if((mem_avail - size )>0){ //说明还有空间
			if((mem_current+size) <= (memBase+Integer.MAX_VALUE)){ //当前bytebuffer 有空间
				//当期bytebuffer 还有空间
				mem_current += size;  //当前内存地址 后移 size 位
				mem_avail -= size;    //可用内存 减少          size
			}else{
				mem_avail -= ((memBase+Integer.MAX_VALUE) - mem_current);  //当前bytebuffer 最后几位被浪费掉了。
				memBase = ((sun.nio.ch.DirectBuffer) baseBuf[++currByteIndex]).address();
				mem_current = memBase;
			}
			return mem_current;
		}else{
			mem_avail = 0;
		}
		
		memAlloced += size;
		if(log.isInfoEnabled()){
			log.info("memory allocated "+size+" B");
		}
		return mem_current;
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
	
}
