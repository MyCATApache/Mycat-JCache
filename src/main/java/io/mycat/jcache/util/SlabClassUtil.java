package io.mycat.jcache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.JcacheMain;
import io.mycat.jcache.setting.Settings;

/**
 * 
 * @author liyanjun
 * size,perslab,slots,sl_curr,slabs,slab_list,list_size,requested
 * 0    4       8     16      20    24        32        36       | 44
 * 
 */
public class SlabClassUtil {
	
	private static final Logger logger = LoggerFactory.getLogger(JcacheMain.class);
	
	/**
	 * SlabClass 总长度
	 */
	public static final int ntotal = 44;
	
	/* sizes of items */
	private static final byte size = 0;
	/* how many items per slab */
	private static final byte perslab = 4;
	/* list of item ptrs  当前空闲 slots head  */
	private static final byte slots = 8;
	/* total free items in list */
	private static final byte sl_curr = 16;
	/* how many slabs were allocated for this class */
	private static final byte slabs = 20;
	/* array of slab pointers */
	private static final byte slab_list = 24;
	/* size of prev array */
	private static final byte list_size = 32;
	/* The number of requested bytes */
	private static final byte requested = 36;
	
	/**
	 * 测试使用
	 * 输出当前slabclass 使用状况
	 * @param addr
	 */
	public static String SlabClassToString(long addr){
		return "{size="+getSize(addr)+",perslab = "+getPerslab(addr)+",sl_curr ="+getSlCurr(addr)+","
				+"slabs= "+ getSlabs(addr) 
				+ "slots="+getSlots(addr)
				+ ",list_size = "+getListSize(addr)+"}";
	}
	
	public static int getNtotal() {
		return ntotal;
	}
	
	public static int getSize(long addr){
		return UnSafeUtil.getInt(addr+size);
	}
	
	public static void setSize(long addr,int value){
		UnSafeUtil.putInt(addr+size, value);
	}
	
	public static int getPerslab(long addr) {
		return UnSafeUtil.getInt(addr+perslab);
	}
	
	public static void setPerslab(long addr,int value){
		UnSafeUtil.putInt(addr+perslab, value);
	}
	
	public static long getSlots(long addr) {
		return UnSafeUtil.getLong(addr+slots);
	}
	
	public static void setSlots(long addr,long slotaddr){
		UnSafeUtil.putAddress(addr+slots, slotaddr);
	}
	
	
	public static int getSlCurr(long addr) {
		return UnSafeUtil.getInt(addr+sl_curr);
	}
	
	public static int incrSlCurr(long addr){
		return UnSafeUtil.incrementAndGetInt(addr+sl_curr);
	}
	
	public static int incrSlCurr(long addr,int delta){
		return UnSafeUtil.addAndGetInt(addr+sl_curr, delta);
	}
	
	public static int decrSlCurr(long addr){
		return UnSafeUtil.decrementAndGetInt(addr+sl_curr);
	}
	
	public static int getSlabs(long addr) {
		return UnSafeUtil.getInt(addr+slabs);
	}
	
	public static int incrSlabs(long addr){
		return UnSafeUtil.incrementAndGetInt(addr+slabs);
	}
	
	public static int decrSlabs(long addr){
		return UnSafeUtil.decrementAndGetInt(addr+slabs);
	}
	
	public static long getSlabList(long addr) {
		return UnSafeUtil.getLong(addr+slab_list);
	}
	
	public static void setSlabList(long addr,long value){
		UnSafeUtil.putLongVolatile(addr+slab_list, value);
	}
	
	/**
	 * 将slab 内存首地址，放入到 slablist 指定的位置
	 * @param addr
	 * @param index
	 * @param value
	 */
	public static void setSlablistIndexValue(long addr,int index,long value){
		long slabindex = addr +slab_list+ index *UnSafeUtil.addresssize;
		UnSafeUtil.putLongVolatile(slabindex, value);
	}

	/**
	 * slab 内存首地址，从slablist 指定的位置取出值
	 * @param addr
	 * @param index
     */
	public static Long getSlabListIndexValue(long addr,int index){
		long slabindex = addr +slab_list+ index *UnSafeUtil.addresssize;
		return UnSafeUtil.getLongVolatile(slabindex);
	}
	
	
	public static int getListSize(long addr) {
		return  UnSafeUtil.getInt(addr+list_size);
	}
	
	public static void setListSize(long addr,int value){
		UnSafeUtil.putInt(addr+list_size, value);
	}
	
	public static long getRequested(long addr) {
		return UnSafeUtil.getLong(addr+requested);
	}
	
	public static long incrRequested(long addr,long totalsizes){
		return UnSafeUtil.addAndGetLong(addr+requested, totalsizes);
	}
	
	public static long decrRequested(long addr,long totalsizes){
		return UnSafeUtil.addAndGetLong(addr+requested, -totalsizes);
	}
}
