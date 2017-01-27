package io.mycat.jcache.context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.hash.Hash;
import io.mycat.jcache.hash.Hash_func_type;
import io.mycat.jcache.hash.Segment;
import io.mycat.jcache.hash.impl.HashImpl;
import io.mycat.jcache.items.ItemsAccessManager;
import io.mycat.jcache.memory.Slabs;
import io.mycat.jcache.setting.Settings;

/**
 * jcache 上下文
 * @author liyanjun
 *
 */
public class JcacheContext {
	
	/**
	 * 内存池
	 */
	private static Slabs slabPool;
	
	/**
	 * item 访问器
	 */
	private static ItemsAccessManager itemsAccessManager = new ItemsAccessManager();
	
	/**
	 * hashtable
	 */
	private static Assoc assoc;
	
	/**
	 * hash
	 */
	private static Hash hash = new HashImpl(Hash_func_type.JENKINS_HASH);
	
	/**
	 * 分段锁
	 */
	private static Segment segment;
	
	/**
	 * worker threads
	 */
	private static ExecutorService executor;
	
	/* Locks for cache LRU operations */
	private final static AtomicBoolean[] lru_locks = new AtomicBoolean[Settings.POWER_LARGEST];
	
	/* Lock for global stats */
	private final static AtomicBoolean stats_lock = new AtomicBoolean(false);
	
	static {
       for(int i=0; i<lru_locks.length; i++){
    	   lru_locks[i] = new AtomicBoolean(false);
       }
    }
	
	public static AtomicBoolean getLRU_Lock(int id){
		if(id>(lru_locks.length-1)||id<0){
			return null;
		}
		return lru_locks[id];
	}
	
	public static AtomicBoolean getStatsLock(){
		return stats_lock;
	}

	
	public static Hash getHash() {
		return hash;
	}

	public static void setHash(Hash hash) {
		JcacheContext.hash = hash;
	}

	public static Slabs getSlabPool() {
		return slabPool;
	}

	public static void setSlabPool(Slabs slabPool) {
		JcacheContext.slabPool = slabPool;
	}

	public static ItemsAccessManager getItemsAccessManager() {
		return itemsAccessManager;
	}

	public static void setItemsAccessManager(ItemsAccessManager itemsAccessManager) {
		JcacheContext.itemsAccessManager = itemsAccessManager;
	}
	
	public static Assoc getAssoc(){
		return assoc;
	}
	
	public static void setAssoc(Assoc assoc){
		JcacheContext.assoc = assoc;
	}

	public static Segment getSegment() {
		return segment;
	}

	public static void setSegment(Segment segment) {
		JcacheContext.segment = segment;
	}

	public static ExecutorService getExecutor() {
		return executor;
	}

	public static void setExecutor(ExecutorService executor) {
		JcacheContext.executor = executor;
	}
	
	public static int hashsize(long n){
		return 1<<n;
	}
	
	public static int hashmask(long n){
		return hashsize(n) - 1;
	}
}
