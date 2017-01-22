package io.mycat.jcache.context;

import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.hash.Hash;
import io.mycat.jcache.hash.Segment;
import io.mycat.jcache.items.ItemsAccessManager;
import io.mycat.jcache.memory.SlabPool;

/**
 * jcache 上下文
 * @author liyanjun
 *
 */
public class JcacheContext {
	
	/**
	 * 内存池
	 */
	private static SlabPool slabPool;
	
	/**
	 * item 访问器
	 */
	private static ItemsAccessManager itemsAccessManager;
	
	/**
	 * hashtable
	 */
	private static Assoc assoc;
	
	/**
	 * hash
	 */
	private static Hash hash;
	
	/**
	 * 分段锁
	 */
	private static Segment segment;
	

	public static Hash getHash() {
		return hash;
	}

	public static void setHash(Hash hash) {
		JcacheContext.hash = hash;
	}

	public static SlabPool getSlabPool() {
		return slabPool;
	}

	public static void setSlabPool(SlabPool slabPool) {
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
}
