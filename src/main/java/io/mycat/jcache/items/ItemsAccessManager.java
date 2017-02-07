package io.mycat.jcache.items;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.DELTA_RESULT_TYPE;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.BytesUtil;
import io.mycat.jcache.util.ItemChunkUtil;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

/**
 * 
 * 
 * @author liyanjun
 *
 */
@SuppressWarnings("restriction")
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
	 * Does arithmetic on a numeric item value.
	 */
	public DELTA_RESULT_TYPE add_delta(Connection c,String key,int nkey,boolean incr,long delta,byte[] tmpbuf) throws IOException{
//		UnSafeUtil.unsafe.freeMemory;
		DELTA_RESULT_TYPE ret;
		long hv = JcacheContext.getHash().hash(key,nkey);
		JcacheContext.getSegment().item_lock(hv);
		ret = do_add_delta(c,key,nkey,incr,delta,tmpbuf,hv);
		JcacheContext.getSegment().item_unlock(hv);
		return ret;
	}
	
	/*
	 * adds a delta value to a numeric item.
	 *
	 * c     connection requesting the operation
	 * it    item to adjust
	 * incr  true to increment value, false to decrement
	 * delta amount to adjust value by
	 * buf   buffer for response string
	 *
	 * returns a response string to send back to the client.
	 */
	public DELTA_RESULT_TYPE do_add_delta(Connection conn,String key,int nkey,boolean incr,long delta,byte[] buf,long hv) throws IOException{
		Long value;
		long ptr;
		int res;
		long cas = (long) JcacheContext.getLocal("cas");
				
		long it = items.do_item_get(key, nkey, hv, conn);
		if(it==0){
			return DELTA_RESULT_TYPE.DELTA_ITEM_NOT_FOUND;
		}
		
		if(cas!=0&&ItemUtil.ITEM_get_cas(it)!=cas){
			items.do_item_remove(it);
			return DELTA_RESULT_TYPE.DELTA_ITEM_CAS_MISMATCH;
		}
		
		ptr = ItemUtil.ITEM_data(it);
		
		String itvalue = new String(ItemUtil.getValue(it));
		Pattern pattern = Pattern.compile("[0-9]*");
		Matcher isNum = pattern.matcher(itvalue);
		
		if(!isNum.matches()){
			items.do_item_remove(it);
			return DELTA_RESULT_TYPE.NON_NUMERIC;
		}else{
			value = Long.valueOf(itvalue);
		}
		
		if(incr){
			value += delta;
		}else{
			if(delta > value){
				value  = 0L;
			}else{
				value -= delta;
			}
		}
		System.arraycopy(BytesUtil.LongToBytes(value), 0, buf, 0, 8);
		byte[] valueByte = value.toString().getBytes(JcacheGlobalConfig.defaultCahrset);
		res = valueByte.length;
		/* refcount == 2 means we are the only ones holding the item, and it is
	     * linked. We hold the item's lock in this function, so refcount cannot
	     * increase. */
		if((res +2) <= ItemUtil.getNbytes(it) && ItemUtil.getRefCount(it)==2){
			/* When changing the value without replacing the item, we
	           need to update the CAS on the existing item. */
	        /* We also need to fiddle it in the sizes tracker in case the tracking
	         * was enabled at runtime, since it relies on the CAS value to know
	         * whether to remove an item or not. */
			items.item_stats_sizes_remove(it);
			ItemUtil.ITEM_set_cas(it, Settings.useCas?items.get_cas_id():0);
			items.item_stats_sizes_add(it);
			
			String pad = StringUtils.rightPad("", ItemUtil.getNbytes(it)-2-res, ' '); 
			UnSafeUtil.setBytes(ItemUtil.ITEM_data(it),
								(value.toString() + pad).getBytes(JcacheGlobalConfig.defaultCahrset),
								0, 
								ItemUtil.getNbytes(it)-2);
			
			items.do_item_update(it);
		}else if(ItemUtil.getRefCount(it)>1){
			long new_it;
			int flags = ItemUtil.ITEM_suffix_flags(it);
			new_it = items.do_item_alloc(key, nkey, flags, ItemUtil.getExpTime(it), res+2);
			if(new_it==0){
				items.do_item_remove(it);
				return DELTA_RESULT_TYPE.EOM;
			}
			ItemUtil.setValue(new_it, (value.toString() + "\r\n").getBytes(JcacheGlobalConfig.defaultCahrset));
			item_replace(it, new_it, hv);
			// Overwrite the older item's CAS with our new CAS since we're
	        // returning the CAS of the old item below.
			ItemUtil.ITEM_set_cas(it, Settings.useCas?ItemUtil.ITEM_get_cas(new_it):0);
			items.do_item_remove(new_it);   /* release our reference */  
		}else{
			
			if(ItemUtil.getRefCount(it)==1){
				items.do_item_remove(it);
			}
			return DELTA_RESULT_TYPE.DELTA_ITEM_NOT_FOUND;
		}
		
		if(cas >0){
			JcacheContext.setLocal("cas", ItemUtil.ITEM_get_cas(it));/* swap the incoming CAS value */
		}
		items.do_item_remove(it);
		return DELTA_RESULT_TYPE.OK;
	}
	
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
		
		long new_it = 0;
		int flags;
		
		if(oldaddr!=0&& conn.getSubCmd()==Command.NREAD_ADD){
			/* add only adds a nonexistent item, but promote to head of LRU */
			items.do_item_update(oldaddr);
		}
//		else if(oldaddr!=0&&(conn.getSubCmd()==Command.NREAD_REPLACE
//							  ||conn.getSubCmd()==Command.NREAD_APPEND
//							  ||conn.getSubCmd()==Command.NREAD_PREPEND)){
//			/* replace only replaces an existing value; don't store */
//		}
		else if(conn.getSubCmd()==Command.NREAD_CAS){
			if(oldaddr==0){
				// LRU expired
				stored = Store_item_type.NOT_FOUND;
//				pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.cas_misses++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
			}else if(ItemUtil.ITEM_get_cas(addr)==ItemUtil.ITEM_get_cas(oldaddr)){
				// cas validates
	            // it and old_it may belong to different classes.
	            // I'm updating the stats for the one that's getting pushed out
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.slab_stats[ITEM_clsid(old_it)].cas_hits++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				item_replace(oldaddr, addr, hv);
				stored = Store_item_type.STORED;
			}else{
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.slab_stats[ITEM_clsid(old_it)].cas_badval++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				stored = Store_item_type.EXISTS;
			}
		}else{
			int failed_alloc = 0;
			/*
	         * Append - combine new and old record into single one. Here it's
	         * atomic and thread-safe.
	         */
			if(conn.getSubCmd()==Command.NREAD_APPEND||conn.getSubCmd()==Command.NREAD_PREPEND){
				
				if(oldaddr!=0){
					/*
		             * Validate CAS
		             */
					if(ItemUtil.ITEM_get_cas(addr)!=0){
						if(ItemUtil.ITEM_get_cas(addr)!=ItemUtil.ITEM_get_cas(oldaddr)){
							stored = Store_item_type.EXISTS;
						}
					}
					
					if(stored.equals(Store_item_type.NOT_STORED)){
						/* we have it and old_it here - alloc memory to hold both */
		                /* flags was already lost - so recover them from ITEM_suffix(it) */
						flags = ItemUtil.ITEM_suffix_flags(oldaddr);
						new_it = items.do_item_alloc(key, nkey, flags, 
													 ItemUtil.getExpTime(oldaddr), 
													 (ItemUtil.getNbytes(addr)+ItemUtil.getNbytes(oldaddr)-2));
						if(new_it==0){
							failed_alloc = 1;
							stored = Store_item_type.NOT_STORED;
						}else{
							 /* copy data from it and old_it to new_it */
		                    _store_item_copy_data(conn, oldaddr, new_it, addr);
							addr = new_it;
						}
					}
				}else{
					failed_alloc = 1;
					stored = Store_item_type.NOT_STORED;
				}
			}
			
			if(stored.equals(Store_item_type.NOT_STORED)&& failed_alloc==0){
				if(oldaddr!=0){
					item_replace(oldaddr,addr,hv);  
				}else{
					items.do_item_link(addr,hv);
				}
				stored = Store_item_type.STORED;
			}
			
		}

		if(oldaddr!=0){
			items.do_item_remove(oldaddr);  /* release our reference */
		}
		
		if(new_it!=0){
			items.do_item_remove(new_it);  /* release our reference */
		}

		if(Store_item_type.STORED.equals(stored)){
			conn.setCas(ItemUtil.ITEM_get_cas(addr));
		}

		return stored;
	}
	
	private void _store_item_copy_data(Connection conn,long old_it,long new_it,long add_it){
		if(conn.getSubCmd()== Command.NREAD_APPEND){
			if((ItemUtil.getItflags(new_it)&ItemFlags.ITEM_CHUNKED.getFlags()) >0){
				_store_item_copy_chunks(new_it, old_it, ItemUtil.getNbytes(old_it) - 2);
	            _store_item_copy_chunks(new_it, add_it, ItemUtil.getNbytes(add_it));
			}else{
				System.out.println(ItemUtil.ItemToString(new_it));
				UnSafeUtil.unsafe.copyMemory(ItemUtil.ITEM_data(old_it) , // old 
						ItemUtil.ITEM_data(new_it),  //new 
						ItemUtil.getNbytes(old_it));
				System.out.println(ItemUtil.ItemToString(new_it));
				System.out.println(ItemUtil.ItemToString(add_it));
				UnSafeUtil.unsafe.copyMemory(ItemUtil.ITEM_data(add_it), // old 
						ItemUtil.ITEM_data(new_it) + ItemUtil.getNbytes(old_it)-2,  //new 
						ItemUtil.getNbytes(add_it));
				System.out.println(ItemUtil.ItemToString(new_it));
			}
		}else{
			/* NREAD_PREPEND */
			if((ItemUtil.getItflags(new_it) & ItemFlags.ITEM_CHUNKED.getFlags()) > 0){
	            _store_item_copy_chunks(new_it, old_it, ItemUtil.getNbytes(add_it) - 2);
	            _store_item_copy_chunks(new_it, add_it, ItemUtil.getNbytes(old_it));
			}else{
				System.out.println(ItemUtil.ItemToString(new_it));
	            UnSafeUtil.unsafe.copyMemory(ItemUtil.ITEM_data(add_it) , // old 
						ItemUtil.ITEM_data(new_it),  //new 
						ItemUtil.getNbytes(add_it));
	            System.out.println(ItemUtil.ItemToString(new_it));
				UnSafeUtil.unsafe.copyMemory(ItemUtil.ITEM_data(old_it), // old 
						ItemUtil.ITEM_data(new_it) + ItemUtil.getNbytes(add_it)-2,  //new 
						ItemUtil.getNbytes(old_it));
				System.out.println(ItemUtil.ItemToString(new_it));
			}
		}
	}
	
	/* Destination must always be chunked */
	/* This should be part of item.c */
	private void _store_item_copy_chunks(long new_it,long old_it,int len){
		long dch = ItemUtil.ITEM_data(new_it);  /* item_chunk */
		while(ItemChunkUtil.getSize(dch)==ItemChunkUtil.getUsed(dch)){
			dch = ItemChunkUtil.getNext(dch);
		}
		
		if((ItemUtil.getItflags(old_it)&ItemFlags.ITEM_CHUNKED.getFlags()) > 0){
			int remain = len;
			long sch = ItemUtil.ITEM_data(old_it);
			int copied = 0;
			
			/* Fills dch's to capacity, not straight copy sch in case data is
	         * being added or removed (ie append/prepend)
	         */
			while(sch>0&&dch>0&&remain>0){
				int todo = (ItemChunkUtil.getSize(dch) - ItemChunkUtil.getUsed(dch))
							< (ItemChunkUtil.getUsed(sch) - copied)?
									(ItemChunkUtil.getSize(dch) - ItemChunkUtil.getUsed(dch))
									:(ItemChunkUtil.getUsed(sch) - copied);
				if(remain < todo){
					todo = remain;
				}
				UnSafeUtil.unsafe.copyMemory(ItemChunkUtil.getDataAddr(sch) + copied, // old 
											ItemChunkUtil.getDataAddr(dch)+ItemChunkUtil.getUsed(dch),  //new 
											todo);
				
				ItemChunkUtil.incrUsed(dch, todo);
				copied += todo;
				remain -= todo;
				if(ItemChunkUtil.getSize(dch)==ItemChunkUtil.getUsed(dch)){
					dch = ItemChunkUtil.getNext(dch);
				}
				if(copied == ItemChunkUtil.getUsed(sch)){
					copied = 0;
					sch = ItemChunkUtil.getNext(sch);
				}
			}
		}else{
			int done = 0;
			/* Fill dch's via a non-chunked item. */
			while(len>done&&dch>0){
				int todo = (ItemChunkUtil.getSize(dch) - ItemChunkUtil.getUsed(dch))
						< (len - done)?
								(ItemChunkUtil.getSize(dch) - ItemChunkUtil.getUsed(dch))
								:(len - done);
				
				UnSafeUtil.unsafe.copyMemory(ItemChunkUtil.getDataAddr(old_it) + done, // old 
						ItemChunkUtil.getDataAddr(dch)+ItemChunkUtil.getUsed(dch),  //new 
						todo);
				
				done += todo;
				ItemChunkUtil.incrUsed(dch, todo);
				if(ItemChunkUtil.getSize(dch)==ItemChunkUtil.getUsed(dch)){
					dch = ItemChunkUtil.getNext(dch);
				}
			}
		}
	}
}
