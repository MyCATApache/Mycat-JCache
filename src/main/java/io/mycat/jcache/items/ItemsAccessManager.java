package io.mycat.jcache.items;

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
			
	private Items items;
	
	public ItemsAccessManager(){
		this.items = new ItemsImpl();
	}
	
	public void item_stats_sizes_init(){
		items.item_stats_sizes_init();
	}
	
	/*
	 * Returns an item if it hasn't been marked as expired,
	 * lazy-expiring as needed.
	 */
	public long item_get(String key,int nkey,Connection conn){
		
		long it;
		long hv = JcacheContext.getHash().hash(key, nkey);
		JcacheContext.getSegment().item_lock(hv);
		it = items.do_item_get(key,nkey,hv,conn);
		JcacheContext.getSegment().item_unlock(hv);
		return it;
	}
	
	/*
	 * Moves an item to the back of the LRU queue.
	 */
	public long item_touch(String key,int nkey,long exptime,Connection conn){
		long it = 0;
		long hv = JcacheContext.getHash().hash(key, nkey);
		JcacheContext.getSegment().item_lock(hv);
		it = items.do_item_touch(key, nkey,exptime, hv,conn);
		JcacheContext.getSegment().item_unlock(hv);
		return it;
	}
	
	public boolean item_link(long item){
		boolean ret;
		long hv = JcacheContext.getHash().hash(ItemUtil.getKey(item), ItemUtil.getNskey(item));
		JcacheContext.getSegment().item_lock(hv);
		ret = items.do_item_link(item, hv);
		JcacheContext.getSegment().item_unlock(hv);
		return ret;
	}
	
	/**
	 * Allocates a new item.
	 * @param key     key
	 * @param flags
	 * @param exptime  过期时间
	 * @param nbytes  value length
	 * @return
	 */
	public long item_alloc(String key,int nkey,int flags,long exptime,int nbytes){
		return items.do_item_alloc(key,nkey,flags,exptime,nbytes);
	}
	
	/*
	 * Stores an item in the cache (high level, obeys set/add/replace semantics)
	 */
	public Store_item_type store_item(long addr,Connection conn){
		String key = ItemUtil.getKey(addr);
		byte length = ItemUtil.getNskey(addr);
		long hv = JcacheContext.getHash().hash(key, length);
		return do_store_item(addr,conn,hv);
	}
	
	/*
	 * Replaces one item with another in the hashtable.
	 * Unprotected by a mutex lock since the core server does not require
	 * it to be thread-safe.
	 */
	public boolean item_replace(long oldaddr,long addr,long hv){
		return items.do_item_replace( oldaddr, addr, hv);
	}
	
	public boolean item_size_ok(int nkey,int flags,int nbytes){
		int ntotal = ItemUtil.item_make_header(nkey, flags, nbytes);
		if(Settings.useCas){
			ntotal += 8;
		}
		return JcacheContext.getSlabPool().slabs_clsid(ntotal)!=0;
	}
	
	/*
	 * Unlinks an item from the LRU and hashtable.
	 */
	public void item_unlink(long addr){
		long hv = JcacheContext.getHash().hash(ItemUtil.getKey(addr), ItemUtil.getNskey(addr));
		JcacheContext.getSegment().item_lock(hv);
		items.do_item_unlink(addr,hv);
		JcacheContext.getSegment().item_unlock(hv);
	}
	
	public void item_remove(long addr){
		
		long hv = JcacheContext.getHash().hash(ItemUtil.getKey(addr), ItemUtil.getNskey(addr));
		JcacheContext.getSegment().item_lock(hv);
		items.do_item_remove(addr);
		JcacheContext.getSegment().item_unlock(hv);
	}
	
	public void item_update(long item){
		long hv = JcacheContext.getHash().hash(ItemUtil.getKey(item), ItemUtil.getNskey(item));
		JcacheContext.getSegment().item_lock(hv);
		items.do_item_update(item);
		JcacheContext.getSegment().item_unlock(hv);
	}
	
	//=================================================================
	
	/*
	 * Stores an item in the cache according to the semantics of one of the set
	 * commands. In threaded mode, this is protected by the cache lock.
	 *
	 * Returns the state of storage.
	 */
	public Store_item_type do_store_item(long addr, Connection conn, long hv){
		Store_item_type stored = Store_item_type.NOT_STORED;
		String key = ItemUtil.getKey(addr);
		int nkey = ItemUtil.getNskey(addr);
		long oldaddr = items.do_item_get(key,nkey,hv,conn);

		/*
		 * 只实现了 set 命令的处理
		 */
		if(oldaddr!=0){
		}

		int failed_alloc = 0;
		if(Store_item_type.NOT_STORED.equals(stored)&&failed_alloc==0){
			if(oldaddr!=0){
				item_replace(oldaddr,addr,hv); //todo replace 
			}else{
				items.do_item_link(addr,hv);
				stored = Store_item_type.STORED;
			}
		}

		if(oldaddr!=0){
			items.do_item_remove(oldaddr);  /* release our reference */
		}

		if(Store_item_type.STORED.equals(stored)){
//			c->cas = ITEM_get_cas(it);
		}

		return stored;
	}
}
