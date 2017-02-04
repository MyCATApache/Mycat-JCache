package io.mycat.jcache.util;

@SuppressWarnings("restriction")
/* Header when an item is actually a chunk of another item. */
public class ItemChunkUtil {
	
	private static int ntotal = 41; 
	/* points within its own chain. */
	private static final byte next=0;
	/* can potentially point to the head. */
	private static final byte prev=8;
	/* always points to the owner chunk */
	private static final byte head=16;
	/* available chunk space in bytes */
	private static final byte size=24;
	/* chunk space used */
	private static final byte used=28;
	/* used. */
	private static final byte nbytes=32;
	
	private static final byte refcount=36;
	/* unused */
	private static final byte nsuffix=38;
	/* ITEM_* above. */
	private static final byte it_flags=39;
	/* Same as above. */
	private static final byte slabs_clsid=40;
	
	private static final byte data = 41;

	public static int getNtotal() {
		return ntotal;
	}

	public static long getNext(long addr) {
		return UnSafeUtil.getLong(addr+next);
	}
	
	public static void setNext(long addr,long value){
		UnSafeUtil.putAddress(addr+next, value);
	}

	public static long getPrev(long addr) {
		return UnSafeUtil.getLong(addr+prev);
	}
	
	public static void setPrev(long addr,long value){
		UnSafeUtil.putAddress(addr+prev, value);
	}

	public static long getHead(long addr) {
		return UnSafeUtil.getLong(addr+head);
	}
	
	public static void setHead(long addr,long value){
		UnSafeUtil.putAddress(addr+head, value);
	}

	public static int getSize(long addr) {
		return UnSafeUtil.getInt(addr+size);
	}
	
	public static void setSize(long addr,int value){
		UnSafeUtil.putInt(addr+size, value);
	}

	public static int getUsed(long addr) {
		return UnSafeUtil.getInt(addr+used);
	}
	
	public static void setUsed(long addr,int value){
		UnSafeUtil.putInt(addr+used, value);
	}
	
	public static void incrUsed(long addr,int value){
		UnSafeUtil.unsafe.getAndAddInt(null, addr+used, value);
	}
	
	public static void descUsed(long addr,int value){
		UnSafeUtil.unsafe.getAndAddInt(null, addr+used, -value);
	}

	public static int getNbytes(long addr) {
		return UnSafeUtil.getInt(addr+nbytes);
	}
	
	public static void setNbytes(long addr,int value){
		UnSafeUtil.putInt(addr+nbytes, value);
	}

	public static short getRefcount(long addr) {
		return UnSafeUtil.getShort(addr+refcount);
	}
	
	public static void setRefcount(long addr,short value){
		UnSafeUtil.putShort(addr+refcount, value);
	}

	public static byte getNsuffix(long addr) {
		return UnSafeUtil.getByte(addr+nsuffix);
	}
	
	public static void setNsuffix(long addr,byte value){
		UnSafeUtil.putByte(addr+nsuffix, value);
	}

	public static byte getItFlags(long addr) {
		return UnSafeUtil.getByte(addr+it_flags);
	}
	
	public static void setItFlags(long addr,byte value){
		UnSafeUtil.putByte(addr+it_flags, value);
	}

	public static byte getSlabsClsid(long addr) {
		return UnSafeUtil.getByte(addr+slabs_clsid);
	}
	
	public static void setSlabsClsid(long addr,byte value){
		UnSafeUtil.putByte(addr+slabs_clsid, value);
	}

	public static void setData(long addr,byte[] datas) {
		UnSafeUtil.setBytes(addr+data, datas, 0, datas.length);;
	}
	
	public static long getDataAddr(long addr) {
		return UnSafeUtil.getLong(addr+data);
	}
}
