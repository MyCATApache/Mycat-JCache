package io.mycat.jcache.items;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.memhashtable.HashTable;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;

/**
 * 
 * 
 * @author liyanjun
 *
 */
public class ItemsAccessManager {
	/*
	 * Returns an item if it hasn't been marked as expired,
	 * lazy-expiring as needed.
	 */
	public long item_get(String key,Connection conn){
		return Items.do_item_get(key,conn);
	}
	
	/**
	 * Allocates a new item.
	 * @param key     key
	 * @param flags
	 * @param exptime  过期时间
	 * @param nbytes  value length
	 * @return
	 */
	public long item_alloc(String key,int flags,long exptime,int nbytes){
		return Items.do_item_alloc(key,flags,exptime,nbytes);
	}
	
	public Store_item_type store_item(long addr,Connection conn){
		String key = ItemUtil.getKey(addr);
		byte length = ItemUtil.getNskey(addr);
		long hv = HashTable.hash(key, length);
		return Items.do_item_store(addr,conn,hv);
	}
	
	public boolean item_size_ok(int nkey,int flags,int nbytes){
		int ntotal = ItemUtil.item_make_header(nkey, flags, nbytes);
		if(Settings.useCas){
			ntotal += 8;
		}
		return JcacheContext.getSlabPool().slabsClassid(ntotal)!=0;
	}
	
	public void item_unlink(long addr){
		Items.do_item_unlink(addr);
	}
	
	public void item_remove(long addr){
		Items.do_item_remove(addr);
	}
}
