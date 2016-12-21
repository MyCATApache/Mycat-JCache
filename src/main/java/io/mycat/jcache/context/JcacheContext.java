package io.mycat.jcache.context;

import io.mycat.jcache.items.ItemsAccessManager;
import io.mycat.jcache.memory.SlabPool;

/**
 * jcache 上下文
 * @author liyanjun
 *
 */
public class JcacheContext {
		
	private static SlabPool slabPool;
	
	private static ItemsAccessManager itemsAccessManager;

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
	
}
