package io.mycat.jcache.items;

import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.Store_item_type;
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
	
	private final AtomicBoolean item_lock = new AtomicBoolean(false);
	/*
	 * Returns an item if it hasn't been marked as expired,
	 * lazy-expiring as needed.
	 */
	public long item_get(String key,int nkey,Connection conn){
		
		long it;
		long hv = JcacheContext.getHash().hash(key, nkey);
		
		while(item_lock.compareAndSet(false, true)){}
		try{
			it = Items.do_item_get(key,nkey,hv,conn);
		}finally{
			item_lock.lazySet(false);
		}
		return it;
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
		long hv = JcacheContext.getHash().hash(key, length);
		return Items.do_store_item(addr,conn,hv);
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
