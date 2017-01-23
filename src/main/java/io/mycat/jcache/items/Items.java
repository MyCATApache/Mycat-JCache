package io.mycat.jcache.items;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.LRU_TYPE_MAP;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.memory.SlabClass;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;

/**
 * 
 * @author liyanjun
 * @author tangww
 * @author  yangll
 *
 */
public class Items {
	
	public static Logger logger = LoggerFactory.getLogger(Items.class);
	
	private static int LRU_PULL_EVICT = 1;
	private static int LRU_PULL_CRAWL_BLOCKS = 2;
	
	private static  AtomicLong casIdGeneraytor = new AtomicLong();
	final static AtomicBoolean[] allocItemStatus = new AtomicBoolean[Settings.POWER_LARGEST];
	static {
        try {
           for(int i=0; i<allocItemStatus.length; i++){
        	   allocItemStatus[i] = new AtomicBoolean(false);
           }
        } catch (Exception ex) { throw new Error(ex); }
    }
	
	/**
	 * Allocates a new item.
	 * @param key     key
	 * @param flags
	 * @param exptime  过期时间
	 * @param nbytes  value length
	 * @return
	 */
	public static long do_item_alloc(String key,int flags,long exptime,int nbytes){
		String suffixStr = ItemUtil.item_make_header_suffix(key.length(), flags, nbytes);
		int ntotal = ItemUtil.item_make_header(key.length(), flags, nbytes,suffixStr);
		long itemaddr = 0;

		if(Settings.useCas){
			ntotal += 8;
		}

		int clsid = JcacheContext.getSlabPool().slabsClassid(ntotal);
		if(clsid == 0){
			return 0;
		}

	    /* If no memory is available, attempt a direct LRU juggle/eviction */
	    /* This is a race in order to simplify lru_pull_tail; in cases where
	     * locked items are on the tail, you want them to fall out and cause
	     * occasional OOM's, rather than internally work around them.
	     * This also gives one fewer code path for slab alloc/free
	     */
	    /* TODO: if power_largest, try a lot more times? or a number of times
	     * based on how many chunks the new object should take up?
	     * or based on the size of an object lru_pull_tail() says it evicted?
	     * This is a classical GC problem if "large items" are of too varying of
	     * sizes. This is actually okay here since the larger the data, the more
	     * bandwidth it takes, the more time we can loop in comparison to serving
	     * and replacing small items.
	     */
		for(int i=0;i<10;i++){
			long total_bytes = 0;
			if(Settings.lruMaintainerThread){
				lru_pull_tail(clsid,LRU_TYPE_MAP.COLD_LRU,0,0);
			}

			itemaddr = JcacheContext.getSlabPool().slabs_alloc(ntotal, clsid, false);

			if(Settings.expireZeroDoesNotEvict){
				total_bytes -= noexp_lru_size(clsid);  //TODO
			}

			if(itemaddr==0){
				if(Settings.lruMaintainerThread){
					lru_pull_tail(clsid,LRU_TYPE_MAP.HOT_LRU,0,0);
					lru_pull_tail(clsid,LRU_TYPE_MAP.WARM_LRU,0,0);
					if(lru_pull_tail(clsid,LRU_TYPE_MAP.COLD_LRU,total_bytes,LRU_PULL_EVICT)<= 0){
						break;
					}
				}else{
					if(lru_pull_tail(clsid,LRU_TYPE_MAP.COLD_LRU,0,LRU_PULL_EVICT)<= 0){
						break;
					}
				}
			}else{
				break;
			}
		}
		
		ItemUtil.setNext(itemaddr, 0);
		ItemUtil.setPrev(itemaddr, 0);

		if(Settings.lruMaintainerThread){
			if(exptime==0&& Settings.expireZeroDoesNotEvict){
				clsid = clsid|LRU_TYPE_MAP.NOEXP_LRU.ordinal();
			}else{
				clsid = clsid|LRU_TYPE_MAP.HOT_LRU.ordinal();
			}
		}else{
			clsid = clsid|LRU_TYPE_MAP.COLD_LRU.ordinal();
		}

		ItemUtil.setSlabsClsid(itemaddr, (byte)clsid);
		byte flag = ItemUtil.getItflags(itemaddr);
		ItemUtil.setItflags(itemaddr, (byte)(flag|(Settings.useCas?ItemFlags.ITEM_CAS.getFlags():0)));
		ItemUtil.setNskey(itemaddr,(byte)key.length());
		ItemUtil.setNbytes(itemaddr, nbytes);
		try {
			ItemUtil.setKey(key.getBytes(JcacheGlobalConfig.defaultCahrset), itemaddr);
			ItemUtil.setExpTime(itemaddr, exptime);
			byte[] suffixBytes = suffixStr.getBytes(JcacheGlobalConfig.defaultCahrset);
			ItemUtil.setSuffix(itemaddr, suffixBytes);
			ItemUtil.setNsuffix(itemaddr, (byte)suffixBytes.length);
			if((flag&ItemFlags.ITEM_CHUNKED.getFlags())>0){
				//TODO
//					long item_chunk = ItemUtil.ITEM_data(itemaddr);
			}
			ItemUtil.setHNext(itemaddr, 0);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return itemaddr;
	}

	public static long do_item_get(String key,int nkey,long hv,Connection conn){
		long addr = JcacheContext.getAssoc().assoc_find(key, nkey, hv);
		if(logger.isDebugEnabled()){
			logger.debug("do_item_get key : {}  addr : {}", key,addr);
		}
		
		int was_found = 0;
		if(addr!=0){
			refcount_incr(addr);
			was_found = 1;
			long exptime = ItemUtil.getExpTime(addr);
			if(item_is_flushed(addr)){
				do_item_unlink(addr);
				do_item_remove(addr);
				addr = 0;
				//TODO  STATS
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.get_flushed++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				was_found = 2;
			}else if(exptime!=0&& exptime <= System.currentTimeMillis()){  //已过期
				do_item_unlink(addr);
				do_item_remove(addr);
	            addr = 0;
	            //TODO STATS
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.get_expired++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				was_found = 3;
			}else{
				byte flags = ItemUtil.getItflags(addr);
				ItemUtil.setItflags(addr, (byte)(flags|ItemFlags.ITEM_FETCHED.getFlags()|ItemFlags.ITEM_ACTIVE.getFlags()));
			}
		}

	    /* For now this is in addition to the above verbose logging. */
//	    LOGGER_LOG(c->thread->l, LOG_FETCHERS, LOGGER_ITEM_GET, NULL, was_found, key, nkey);
		return addr;
	}

	/*
	 * Stores an item in the cache according to the semantics of one of the set
	 * commands. In threaded mode, this is protected by the cache lock.
	 *
	 * Returns the state of storage.
	 */
	public static Store_item_type do_store_item(long addr, Connection conn, long hv){
		Store_item_type stored = Store_item_type.NOT_STORED;
		String key = ItemUtil.getKey(addr);
		int nkey = ItemUtil.getNskey(addr);
		long oldaddr = do_item_get(key,nkey,hv,conn);

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
				do_item_link(addr,hv);
				stored = Store_item_type.STORED;
			}
		}

		if(oldaddr!=0){
			do_item_remove(oldaddr);  /* release our reference */
		}

		if(Store_item_type.STORED.equals(stored)){
//			c->cas = ITEM_get_cas(it);
		}

		return stored;
	}
	
	public static boolean item_replace(long oldaddr,long addr,long hv){
		return do_item_replace( oldaddr, addr, hv);
	}

	public static boolean do_item_link(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		ItemUtil.setItflags(addr, (byte)(flags|ItemFlags.ITEM_LINKED.getFlags()));
		ItemUtil.setTime(addr, System.currentTimeMillis());

		//TODO STATS_LOCK 
		
		 /* Allocate a new CAS ID on link. */
		ItemUtil.ITEM_set_cas(addr, Settings.useCas?get_cas_id():0);
		JcacheContext.getAssoc().assoc_insert(addr, hv);
		item_link_q(addr);
		refcount_incr(addr);
//		item_stats_sizes_add(addr); TODO
		return true;
	}

	public static void item_link_q(long addr){
		int clsid = ItemUtil.getSlabsClsid(addr);
		while(allocItemStatus[clsid].compareAndSet(false, true)){}
		try {
			do_item_link_q(addr);
		} finally {
			allocItemStatus[clsid].set(false);
		}
	}



	/**
	 * TODO
//	 * @param addr
	 */
//	private static void do_item_link_q(long addr){ /* item is the new head */
//
//	}

	/* Get the next CAS id for a new item. */
	public static long get_cas_id(){
		return casIdGeneraytor.incrementAndGet();
	}

	public static void do_item_unlink(long addr){
		byte flags = ItemUtil.getItflags(addr);
		if((flags&ItemFlags.ITEM_LINKED.getFlags())!=0){
			ItemUtil.setItflags(addr, (byte)(flags&~ItemFlags.ITEM_LINKED.getFlags()));
			// TODO  stats modify
			String key = ItemUtil.getKey(addr);
			// HashTable.delect(key, addr);  TODO  这个方法可鞥有问题
		}
	}

	public static void do_item_remove(long addr){
		byte itflags = ItemUtil.getItflags(addr);
		if((itflags&ItemFlags.ITEM_SLABBED.getFlags())!=0)  return;
		int refcount = ItemUtil.getRefCount(addr);
		if(refcount==0) return;
		if(refcount_decr(addr)==0){
			item_free(addr);
		}
	}

	public static void item_free(long addr){
		int ntotal = ItemUtil.ITEM_ntotal(addr);
		
		byte itflags = ItemUtil.getItflags(addr);
		if((itflags&ItemFlags.ITEM_LINKED.getFlags())!=0)  return;
		int refcount = ItemUtil.getRefCount(addr);
		if(refcount!=0) return;
		
		/* so slab size changer can tell later if item is already free or not */
		int clsid  = ItemUtil.getSlabsClsid(addr);
		JcacheContext.getSlabPool().slabs_free(addr, ntotal, clsid);
	}

	/**
	 * TODO
	 * @param clsid
	 * @return
	 */
	public static int noexp_lru_size(int clsid){
//	    int id = CLEAR_LRU(slabs_clsid);
//	    id |= NOEXP_LRU;
//	    unsigned int ret;
//	    pthread_mutex_lock(&lru_locks[id]);
//	    ret = sizes_bytes[id];
//	    pthread_mutex_unlock(&lru_locks[id]);
//	    return ret;
		return 0;
	}

	/**
	 * 计数器 减一
	 * @param addr
	 */
	public static int refcount_decr(long addr){
		return ItemUtil.decrRefCount(addr);
	}

	/**
	 * 计数器 加一
	 * @param addr
	 */
	public static int refcount_incr(long addr){
		return ItemUtil.incrRefCount(addr);
	}

	public static void do_item_link_q(long addr) {
		
		byte itflags = ItemUtil.getItflags(addr);
		if((itflags&ItemFlags.ITEM_SLABBED.getFlags())!=0)  return;
		
		int classid = ItemUtil.getSlabsClsid(addr);
		SlabClass sc = JcacheContext.getSlabPool().getSlabClassArr()[classid];
		long head = sc.getHead();
		long tail = sc.getTail();
		
		if(addr==head) return;
		if(!((head==0&&tail==0)||(head!=0&&tail!=0))) return;
		ItemUtil.setPrev(addr, (byte) 0);
		ItemUtil.setNext(addr,head);
		if(head!=0){
			ItemUtil.setPrev(head, addr);
		}
		sc.setHead(addr);
		if(tail==0){
			sc.setTail(addr);
		}
		sc.incrSizes();
		sc.incrSize_bytes(ItemUtil.ITEM_ntotal(addr));
//	    sizes[it->slabs_clsid]++;
//	    sizes_bytes[it->slabs_clsid] += ITEM_ntotal(it);
		return;
	}


	/* Get the next CAS id for a new item. */
//	public static long get_cas_id() {
//		return casIdGeneraytor.getAndIncrement();
//	}

	public static boolean item_is_flushed(long itemaddr){
	    long oldest_live = Settings.oldestLive;
	    long cas = ItemUtil.getCAS(itemaddr);
	    long oldest_cas = Settings.oldestCas;
	    long time = ItemUtil.getTime(itemaddr);

	    if (oldest_live == 0 || oldest_live > System.currentTimeMillis())
	        return false;
	    if ((time <= oldest_live)
	            || (oldest_cas != 0 && cas != 0 && cas < oldest_cas)) {
	        return true;
	    }
		return false;
	}

    /*** LRU MAINTENANCE THREAD
	 * Returns number of items remove, expired, or evicted.
     * Callable from worker threads or the LRU maintainer thread
	 * @param orig_id
	 * @param cur_lru
	 * @param total_bytes
	 * @param flags
	 * @return
	 */
	public static int lru_pull_tail(int orig_id,LRU_TYPE_MAP cur_lru,long total_bytes,int flags){
		long it =0;
		int id = orig_id;
		int removed = 0;
		if(id==0){
			return 0;
		}
		int tries = 5;
		long search =0;
		long next_it=0;
		ReentrantLock hold_lock = null;
		LRU_TYPE_MAP move_to_lru = LRU_TYPE_MAP.HOT_LRU;
		long limit = 0;
		id = id | cur_lru.ordinal();
		
		try {
			while(allocItemStatus[id].compareAndSet(false, true)){}
			SlabClass sc = JcacheContext.getSlabPool().getSlabClassArr()[id];
			search = sc.getTail();
			try {
				/* We walk up *only* for locked items, and if bottom is expired. */
				for(;tries > 0&& search!=0;tries--,search=next_it){
					/* we might relink search mid-loop, so search->prev isn't reliable */
					next_it = ItemUtil.getPrev(search);
					if(ItemUtil.getNbytes(search)==0
					  &&ItemUtil.getNskey(search)==0
					  &&ItemUtil.getItflags(search)==1){
						/* We are a crawler, ignore it. */
						if((flags&LRU_PULL_CRAWL_BLOCKS) >0){
							return 0;
						}
						tries++;
						continue;
					}
					
					long hv = JcacheContext.getHash().hash(ItemUtil.getKey(search), ItemUtil.getNskey(search));
					/* Attempt to hash item lock the "search" item. If locked, no
			         * other callers can incr the refcount. Also skip ourselves. */
					hold_lock = JcacheContext.getSegment().item_trylock(hv);
					if(hold_lock==null){
						continue;
					}
					/* Now see if the item is refcount locked */
					if(refcount_incr(ItemUtil.getRefCount(search))!=2){
						/* Note pathological case with ref'ed items in tail.
			             * Can still unlink the item, but it won't be reusable yet */
//							itemstats[id].lrutail_reflocked++;  TODO STATS
						/* In case of refcount leaks, enable for quick workaround. */
			            /* WARNING: This can cause terrible corruption */
						if(Settings.tailRepairTime>0&&
						   (ItemUtil.getTime(search)+Settings.tailRepairTime)<System.currentTimeMillis()){
//								itemstats[id].tailrepairs++;    TODO STATS
							ItemUtil.setRefCount(search, 1);
							/* This will call item_remove -> item_free since refcnt is 1 */
							do_item_unlink_nolock(search,hv);
							hold_lock.unlock();
							continue;
						}
					}
					
					/* Expired or flushed */
					if((ItemUtil.getExpTime(search)!=0
							&&(ItemUtil.getExpTime(search)<System.currentTimeMillis()))
						||item_is_flushed(search)){
						//TODO STATS
//							itemstats[id].reclaimed++;
//				            if ((search->it_flags & ITEM_FETCHED) == 0) {
//				                itemstats[id].expired_unfetched++;
//				            }
						 /* refcnt 2 -> 1 */
						do_item_unlink_nolock(search,hv);
						/* refcnt 1 -> 0 -> item_free */
						do_item_remove(search);
						hold_lock.unlock();
						removed++;
						/* If all we're finding are expired, can keep going */
						continue;
					}
					
					/* If we're HOT_LRU or WARM_LRU and over size limit, send to COLD_LRU.
			         * If we're COLD_LRU, send to WARM_LRU unless we need to evict
			         */
					switch(cur_lru){
						case HOT_LRU:
							limit = total_bytes * Settings.hotLruPct / 100;
						case WARM_LRU:
							if(limit == 0)
								limit = total_bytes * Settings.warmLruPct / 100;
							if(sc.getSizes_bytes() > limit){
//									itemstats[id].moves_to_cold++;  TODO STATS
								move_to_lru = LRU_TYPE_MAP.COLD_LRU;
								do_item_unlink_q(search);
								it = search;
								removed++;
								break;
							}else if((ItemUtil.getItflags(search)&ItemFlags.ITEM_ACTIVE.getFlags())!=0){
								/* Only allow ACTIVE relinking if we're not too large. */
//									itemstats[id].moves_within_lru++;  TODO STATS
								ItemUtil.setItflags(search, (byte)~ItemFlags.ITEM_ACTIVE.getFlags());
								do_item_update_nolock(search);
								do_item_remove(search);
								hold_lock.unlock();
							}else {
								 /* Don't want to move to COLD, not active, bail out */
								it = search;
							}
							break;
						case COLD_LRU:
							it = search; /* No matter what, we're stopping */
							if((flags&LRU_PULL_EVICT)>0){
								if(Settings.evictToFree==0){
									/* Don't think we need a counter for this. It'll OOM.  */
									break;
								}
//									itemstats[id].evicted++;
//				                    itemstats[id].evicted_time = current_time - search->time;
//				                    if (search->exptime != 0)
//				                        itemstats[id].evicted_nonzero++;
//				                    if ((search->it_flags & ITEM_FETCHED) == 0) {
//				                        itemstats[id].evicted_unfetched++;
//				                    }
								do_item_unlink_nolock(search, hv);
								removed++;
								if(Settings.slabAutoMove==2){
//										slabs_reassign(-1,orig_id); //TODO
								}
							}else if((ItemUtil.getItflags(search)&ItemFlags.ITEM_ACTIVE.getFlags())!=0
									  &&Settings.lruMaintainerThread){
//									itemstats[id].moves_to_warm++;  //TODO
								ItemUtil.setItflags(search, (byte)~ItemFlags.ITEM_ACTIVE.getFlags());
								move_to_lru = LRU_TYPE_MAP.WARM_LRU;
								do_item_unlink_q(search);
								removed++;
							}
							break;
					}
					
					if(it!=0){
						break;
					}
				}
			} finally {
				allocItemStatus[id].lazySet(false);
			}
			
			if(it!=0){
				if(!LRU_TYPE_MAP.HOT_LRU.equals(move_to_lru)){
					ItemUtil.setSlabsClsid(it, (byte)((ItemUtil.ITEM_clsid(it))|move_to_lru.ordinal()));
					item_link_q(it);
				}
				do_item_remove(it);
				if(hold_lock.isLocked()){
					hold_lock.unlock();
				}
			}
		} finally {
			if(hold_lock!=null&&hold_lock.isLocked()){
				hold_lock.unlock();
			}
		}
		
		return removed;
	}
	
	private static void do_item_unlink_nolock(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		if((flags & ItemFlags.ITEM_LINKED.getFlags())!=0){
			ItemUtil.setItflags(addr, (byte)~ItemFlags.ITEM_LINKED.getFlags());
			//TODO STATS
//	        STATS_LOCK();
//	        stats_state.curr_bytes -= ITEM_ntotal(it);
//	        stats_state.curr_items -= 1;
//	        STATS_UNLOCK();
//			item_stats_sizes_remove(it);
			JcacheContext.getAssoc()
						 .assoc_delete(ItemUtil.getKey(addr),
					                   ItemUtil.getNskey(addr),
					                   hv);
			do_item_unlink_q(addr);
			do_item_remove(addr);
		}
	}

	/* Copy/paste to avoid adding two extra branches for all common calls, since
 	* _nolock is only used in an uncommon case where we want to relink. */
	private static  void do_item_update_nolock(long addr){
			if(ItemUtil.getTime(addr)<System.currentTimeMillis()-Settings.ITEM_UPDATE_INTERVAL){
				if((ItemUtil.getItflags(addr)&ItemFlags.ITEM_SLABBED.getFlags())!=0) return;
				
				if ((ItemUtil.getItflags(addr) & ItemFlags.ITEM_LINKED.getFlags()) != 0) {
					do_item_unlink_q(addr);
					ItemUtil.setTime(addr,System.currentTimeMillis());
					do_item_link_q(addr);
				}
			}
	}

	/* Bump the last accessed time, or relink if we're in compat mode */
	private void do_item_update(long addr) {
		if(ItemUtil.getTime(addr)<System.currentTimeMillis()-Settings.ITEM_UPDATE_INTERVAL){
			if((ItemUtil.getItflags(addr)&ItemFlags.ITEM_SLABBED.getFlags())!=0) return;
			
			if ((ItemUtil.getItflags(addr) & ItemFlags.ITEM_LINKED.getFlags()) != 0) {
				ItemUtil.setTime(addr,System.currentTimeMillis());
				if (!Settings.lruMaintainerThread) {
					item_unlink_q(addr);
					item_link_q(addr);
				}
			}
		}
	}

	private static void item_unlink_q(long addr) {
		int clsid = ItemUtil.getSlabsClsid(addr);
		while(allocItemStatus[clsid].compareAndSet(false, true)){}
		try {
			do_item_unlink_q(addr);
		} finally {
			allocItemStatus[clsid].set(false);
		}
	}
	
	private static void do_item_unlink_q(long addr){
		int scIndex = ItemUtil.getSlabsClsid(addr);
		SlabClass sc = JcacheContext.getSlabPool().getSlabClassArr()[scIndex];
		long head = sc.getHead();
		long tail = sc.getTail();
		
		if(head==addr){
			if(ItemUtil.getPrev(addr)!=0) return;
			sc.setHead(ItemUtil.getNext(addr));
		}
		
		if(tail==addr){
			if(ItemUtil.getNext(addr)!=0) return;
			sc.setTail(ItemUtil.getPrev(addr));
		}
		
		long prev = ItemUtil.getPrev(addr);
		long next = ItemUtil.getNext(addr);
		if(prev==addr) return;
		if(next==addr) return;
		
		if(next>0) ItemUtil.setPrev(next, ItemUtil.getPrev(addr));
		if(prev>0) ItemUtil.setNext(prev, ItemUtil.getNext(addr));
		sc.decrSizes();
		sc.decrSize_bytes(ItemUtil.ITEM_ntotal(addr));
	}

	private static void do_item_unlink(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		if((flags&ItemFlags.ITEM_LINKED.getFlags())!=0){
			ItemUtil.setItflags(addr, (byte)~ItemFlags.ITEM_LINKED.getFlags());
			//TODO STATS_LOCK
//	        STATS_LOCK();
//	        stats_state.curr_bytes -= ITEM_ntotal(it);
//	        stats_state.curr_items -= 1;
//	        STATS_UNLOCK();
			item_stats_sizes_remove(addr);
			JcacheContext.getAssoc()
			 			 .assoc_delete(ItemUtil.getKey(addr),
			 					       ItemUtil.getNskey(addr),
			 					       hv);
			
			item_unlink_q(addr);
			do_item_remove(addr);
		}
	}
	
	
	private static void item_stats_sizes_add(long addr){
//	    if (stats_sizes_hist == NULL || stats_sizes_cas_min > ITEM_get_cas(it))
//	        return;
//	    int ntotal = ITEM_ntotal(it);
//	    int bucket = ntotal / 32;
//	    if ((ntotal % 32) != 0) bucket++;
//	    if (bucket < stats_sizes_buckets) stats_sizes_hist[bucket]++;
	}
	
	/* I think there's no way for this to be accurate without using the CAS value.
	 * Since items getting their time value bumped will pass this validation.
	 */
	private static void item_stats_sizes_remove(long addr){
		//TODO STATS 
//	    if (stats_sizes_hist == NULL || stats_sizes_cas_min > ITEM_get_cas(it))
//	        return;
//	    int ntotal = ITEM_ntotal(it);
//	    int bucket = ntotal / 32;
//	    if ((ntotal % 32) != 0) bucket++;
//	    if (bucket < stats_sizes_buckets) stats_sizes_hist[bucket]--;
	} 

	public static boolean do_item_replace(long oldAddr,long newAddr,long hv){
		
		byte itflags = ItemUtil.getItflags(oldAddr);
		if((itflags&ItemFlags.ITEM_SLABBED.getFlags())!=0)  return false;
		do_item_unlink(oldAddr,hv);
		return  do_item_link(newAddr, hv);
	}


	public  static long  do_item_touch(long addr,long expiretime,Connection conn){
		String key = ItemUtil.getKey(addr);
		//TODO 
//		Long item  = do_item_get(key,conn);
//		if(item!=null){
//			ItemUtil.setExpTime(addr,expiretime);
//		}
		return 0;
	}

//	public long lru_pull_tail(int slab_idx, int cur_lru, int total_chunks, boolean do_evict, int cur_hv){
//		long id;
//		int removed = 0;
//		if(slab_idx == 0)
//			return 0;
//
//		int tries = 5;
//
//		long search;
//		long next_it;
//		int move_to_lru = 0;
//		int limit;
//
//		int slabIdx = slab_idx | cur_lru;
//
//		synchronized (allocItemStatus[slabIdx]) {
//
//		}
//
//		return 0;
//	}

//  TODO
//	==========引用计数 部分   begin
//	unsigned short refcount_incr(unsigned short *refcount) {
//		#ifdef HAVE_GCC_ATOMICS
//		    return __sync_add_and_fetch(refcount, 1);
//		#elif defined(__sun)
//		    return atomic_inc_ushort_nv(refcount);
//		#else
//		    unsigned short res;
//		    mutex_lock(&atomics_mutex);
//		    (*refcount)++;
//		    res = *refcount;
//		    mutex_unlock(&atomics_mutex);
//		    return res;
//		#endif
//		}
//
//		unsigned short refcount_decr(unsigned short *refcount) {
//		#ifdef HAVE_GCC_ATOMICS
//		    return __sync_sub_and_fetch(refcount, 1);
//		#elif defined(__sun)
//		    return atomic_dec_ushort_nv(refcount);
//		#else
//		    unsigned short res;
//		    mutex_lock(&atomics_mutex);
//		    (*refcount)--;
//		    res = *refcount;
//		    mutex_unlock(&atomics_mutex);
//		    return res;
//		#endif
//		}
//	==========引用计数 部分   end
}
