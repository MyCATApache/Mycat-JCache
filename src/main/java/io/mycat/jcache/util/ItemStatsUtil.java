package io.mycat.jcache.util;

public class ItemStatsUtil {

	public static final int ntotal = 15;
	
	public static final int evicted = 0;
	public static final int  evicted_nonzero = 1;
	public static final int reclaimed = 2;
	public static final int outofmemory = 3;
	public static final int tailrepairs = 4;
    public static final int expired_unfetched = 5; /* items reclaimed but never touched */
    public static final int evicted_unfetched = 6; /* items evicted but never touched */
    public static final int crawler_reclaimed = 7;
    public static final int crawler_items_checked = 8;
    public static final int lrutail_reflocked = 9;
    public static final int moves_to_cold = 10;
    public static final int moves_to_warm = 11;
    public static final int moves_within_lru = 12;
    public static final int direct_reclaims = 13;
    public static final int evicted_time = 14;
	
//	private static final int ntotal = 120;
	
//	public static final byte evicted = 0;
//	public static final byte  evicted_nonzero = 8;
//	public static final byte reclaimed = 16;
//	public static final byte outofmemory = 24;
//	public static final byte tailrepairs = 32;
//    public static final byte expired_unfetched = 40; /* items reclaimed but never touched */
//    public static final byte evicted_unfetched = 48; /* items evicted but never touched */
//    public static final byte crawler_reclaimed = 56;
//    public static final byte crawler_items_checked = 64;
//    public static final byte lrutail_reflocked = 72;
//    public static final byte moves_to_cold = 80;
//    public static final byte moves_to_warm = 88;
//    public static final byte moves_within_lru = 96;
//    public static final byte direct_reclaims = 104;
//    public static final byte evicted_time = 112;
    
//	public static int getNtotal() {
//		return ntotal;
//	}
	
//	public static long getEvicted(long addr) {
//		return UnSafeUtil.getLong(addr+evicted);
//	}
//	public static long getEvictedNonzero(long addr) {
//		return UnSafeUtil.getLong(addr+evicted_nonzero);
//	}
//	public static long getReclaimed(long addr) {
//		return UnSafeUtil.getLong(addr+reclaimed);
//	}
//	public static long getOutofmemory(long addr) {
//		return UnSafeUtil.getLong(addr+outofmemory);
//	}
//	public static long getTailrepairs(long addr) {
//		return UnSafeUtil.getLong(addr+tailrepairs);
//	}
//	public static long getExpiredUnfetched(long addr) {
//		return UnSafeUtil.getLong(addr+expired_unfetched);
//	}
//	public static long getEvictedUnfetched(long addr) {
//		return UnSafeUtil.getLong(addr+evicted_unfetched);
//	}
//	public static long getCrawlerReclaimed(long addr) {
//		return UnSafeUtil.getLong(addr+crawler_reclaimed);
//	}
//	public static long getCrawlerItemsChecked(long addr) {
//		return UnSafeUtil.getLong(addr+crawler_items_checked);
//	}
//	public static long getLrutailReflocked(long addr) {
//		return UnSafeUtil.getLong(addr+lrutail_reflocked);
//	}
//	public static long getMovesToCold(long addr) {
//		return UnSafeUtil.getLong(addr+moves_to_cold);
//	}
//	public static long getMovesToWarm(long addr) {
//		return UnSafeUtil.getLong(addr+moves_to_warm);
//	}
//	public static long getMovesWithinLru(long addr) {
//		return UnSafeUtil.getLong(addr+moves_within_lru);
//	}
//	public static long getDirectReclaims(long addr) {
//		return UnSafeUtil.getLong(addr+direct_reclaims);
//	}
//	public static long getEvictedTime(long addr) {
//		return UnSafeUtil.getLong(addr+evicted_time);
//	}
//	
////	=========
//	
//	public static void setEvicted(long addr,long value) {
//		UnSafeUtil.putLong(addr+evicted,value);
//	}
//	public static void setEvictedNonzero(long addr,long value) {
//		UnSafeUtil.putLong(addr+evicted_nonzero,value);
//	}
//	public static void setReclaimed(long addr,long value) {
//		UnSafeUtil.putLong(addr+reclaimed,value);
//	}
//	public static void setOutofmemory(long addr,long value) {
//		UnSafeUtil.putLong(addr+outofmemory,value);
//	}
//	public static void setTailrepairs(long addr,long value) {
//		UnSafeUtil.putLong(addr+tailrepairs,value);
//	}
//	public static void setExpiredUnfetched(long addr,long value) {
//		UnSafeUtil.putLong(addr+expired_unfetched,value);
//	}
//	public static void setEvictedUnfetched(long addr,long value) {
//		UnSafeUtil.putLong(addr+evicted_unfetched,value);
//	}
//	public static void setCrawlerReclaimed(long addr,long value) {
//		UnSafeUtil.putLong(addr+crawler_reclaimed,value);
//	}
//	public static void setCrawlerItemsChecked(long addr,long value) {
//		UnSafeUtil.putLong(addr+crawler_items_checked,value);
//	}
//	public static void setLrutailReflocked(long addr,long value) {
//		UnSafeUtil.putLong(addr+lrutail_reflocked,value);
//	}
//	public static void setMovesToCold(long addr,long value) {
//		UnSafeUtil.putLong(addr+moves_to_cold,value);
//	}
//	public static void setMovesToWarm(long addr,long value) {
//		UnSafeUtil.putLong(addr+moves_to_warm,value);
//	}
//	public static void setMovesWithinLru(long addr,long value) {
//		UnSafeUtil.putLong(addr+moves_within_lru,value);
//	}
//	public static void setDirectReclaims(long addr,long value) {
//		UnSafeUtil.putLong(addr+direct_reclaims,value);
//	}
//	public static void setEvictedTime(long addr,long value) {
//		UnSafeUtil.putLong(addr+evicted_time,value);
//	}
	
}