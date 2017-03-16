package io.mycat.jcache.items;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.context.Stats;
import io.mycat.jcache.context.StatsState;
import io.mycat.jcache.crawler.Crawler;
import io.mycat.jcache.crawler.CrawlerExpiredData;
import io.mycat.jcache.crawler.CrawlerImpl;
import io.mycat.jcache.crawler.CrawlerResultType;
import io.mycat.jcache.crawler.CrawlerstatsT;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.LRU_TYPE_MAP;
import io.mycat.jcache.memory.Slabs;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemChunkUtil;
import io.mycat.jcache.util.ItemStatsUtil;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.SlabClassUtil;
import io.mycat.jcache.util.UnSafeUtil;

/**
 * 
 * @author liyanjun
 * @author tangww
 * @author  yangll
 *
 */
@SuppressWarnings({"restriction","unused"})
public class ItemsImpl implements Items{
	
	public static Logger logger = LoggerFactory.getLogger(ItemsImpl.class);
	
	private static long[] heads = new long[LARGEST_ID];  //itemUtil []
 	private static long[] tails = new long[LARGEST_ID];  //itemUtil []
	private static long[][] itemstats = new long[LARGEST_ID][ItemStatsUtil.ntotal];  //itemstatsUtil []
	private static AtomicInteger[] sizes = new AtomicInteger[LARGEST_ID];       // int []
	private static AtomicLong[] sizes_bytes = new AtomicLong[LARGEST_ID]; // long []
	private static long stats_sizes_hist = 0; // int
	
	private static long stats_sizes_cas_min = 0;  //long
	private static int  stats_sizes_buckets = 0;  //int
	
	
	private static volatile int do_run_lru_maintainer_thread = 0;
	private static int lru_maintainer_initialized = 0;
	private int lru_maintainer_check_clsid = 0;
	
	private static AtomicBoolean lru_maintainer_lock = new AtomicBoolean(false);
	private static AtomicBoolean cas_id_lock = new AtomicBoolean(false);
	private static AtomicBoolean stats_sizes_lock = new AtomicBoolean(false);
	//每次循环执行之后最大延时时间
	private static long MAX_LRU_MAINTAINER_SLEEP = 1000000;
	//每次循环执行之后最小延时时间
	private static long MIN_LRU_MAINTAINER_SLEEP = 1000;
	
	
	private static  AtomicLong casIdGeneraytor = new AtomicLong();
	
	static {
		IntStream.range(0, LARGEST_ID).forEach(f->{
			sizes[f] = new AtomicInteger(0);
			sizes_bytes[f] = new AtomicLong(0);
		});
    }
	
	@Override
	public void item_stats_sizes_init(){
		if(stats_sizes_hist!=0){
			return;
		}
		stats_sizes_buckets = Settings.itemSizeMax/32 +1;
		long size = stats_sizes_buckets *4;
		stats_sizes_hist = UnSafeUtil.unsafe.allocateMemory(size);
		UnSafeUtil.unsafe.setMemory(stats_sizes_hist, size, (byte)0);
		stats_sizes_cas_min = Settings.useCas?get_cas_id():0;
	}
	
	private long getStats_sizes_hist(int index){
		return stats_sizes_hist + index*4;
	}
	
	/**
	 * Allocates a new item.
	 * @param key     key
	 * @param flags
	 * @param exptime  过期时间
	 * @param nbytes  value length
	 * @return
	 */
	@Override
	public long do_item_alloc(String key,int nkey,int flags,long exptime,int nbytes){
		int i;
		long it = 0;
		if(nbytes < 2 || nkey < 0){
			return 0;
		}
		
		String suffixStr = ItemUtil.item_make_header_suffix(key.length(), flags, nbytes);
		int ntotal = ItemUtil.item_make_header(key.length(), flags, nbytes,suffixStr);

		if(Settings.useCas){
			ntotal += 8;
		}

		int clsid = JcacheContext.getSlabPool().slabs_clsid(ntotal);
		if(clsid == 0){
			return 0;
		}
		
//		JcacheContext.getSlabPool().
		if(logger.isDebugEnabled()){
			logger.debug(" before "+SlabClassUtil.SlabClassToString(JcacheContext.getSlabPool().getSlabClass(clsid)));
		}

	    /* If no memory is available, attempt a direct LRU juggle/eviction */
	    /* This is a race in order to simplify lru_pull_tail; in cases where
	     * locked items are on the tail, you want them to fall out and cause
	     * occasional OOM's, rather than internally work around them.
	     * This also gives one fewer code path for slames? or a number of times
	     * based on how many chunks the new object should take up?
	     * or based on the size of an object lru_pull_tail() says it evicted?
	     * This is a classical GC problem if "large items" are of too varying of
	     * sizes. This is actually okay here since the larger the data, the more
	     * bandwidth it takes, the more time we can loop in comparison to serving
	     * and replacing small items.b alloc/free
	     */
	    /* TODO: if power_largest, try a lot more times? or a number of times
	     * based on how many chunks the new object should take up?
	     * or based on the size of an object lru_pull_tail() says it evicted?
	     * This is a classical GC problem if "large items" are of too varying of
	     * sizes. This is actually okay here since the larger the data, the more
	     * bandwidth it takes, the more time we can loop in comparison to serving
	     * and replacing small items.
	     */
		for(i=0;i<10;i++){
			long total_bytesAddr = UnSafeUtil.unsafe.allocateMemory(8);
			UnSafeUtil.unsafe.setMemory(total_bytesAddr, 8, (byte)0);
			try {
				if(Settings.lruMaintainerThread){
					lru_pull_tail(clsid,LRU_TYPE_MAP.COLD_LRU,0,0);
				}

				it = JcacheContext.getSlabPool().slabs_alloc(ntotal, clsid, total_bytesAddr, Slabs.SLABS_ALLOC_NEWPAGE);
				long total_bytes = UnSafeUtil.getLong(total_bytesAddr);
				if(Settings.expireZeroDoesNotEvict){
					total_bytes -= noexp_lru_size(clsid); 
				}
				
				if(Settings.verbose >= 2&&logger.isDebugEnabled()){
					logger.debug(" after "+SlabClassUtil.SlabClassToString(JcacheContext.getSlabPool().getSlabClass(clsid)));
					logger.debug("do_item_alloc slabs_alloc key : {},addr : {}  ", key,it);
				}

				if(it==0){
					if(Settings.lruMaintainerThread){
						lru_pull_tail(clsid,LRU_TYPE_MAP.HOT_LRU,total_bytes,0);
						lru_pull_tail(clsid,LRU_TYPE_MAP.WARM_LRU,total_bytes,0);
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
			} finally {
				UnSafeUtil.unsafe.freeMemory(total_bytesAddr);
			}
			
		}
		
		if(i>0){
			AtomicBoolean lru_lock = JcacheContext.getLRU_Lock(clsid);
			while(!lru_lock.compareAndSet(false, true)){}
			try {
				itemstats[clsid][ItemStatsUtil.direct_reclaims] +=i;
			} finally {
				lru_lock.lazySet(false);
			}
		}
		
		if(it==0){
			AtomicBoolean lru_lock = JcacheContext.getLRU_Lock(clsid);
			while(!lru_lock.compareAndSet(false, true)){}
			try {
				itemstats[clsid][ItemStatsUtil.outofmemory]++;
			} finally {
				lru_lock.lazySet(false);
			}
	        return 0;
		}
		
		ItemUtil.setNext(it, 0);
		ItemUtil.setPrev(it, 0);

		if(Settings.lruMaintainerThread){
			if(exptime==0&& Settings.expireZeroDoesNotEvict){
				clsid = clsid|LRU_TYPE_MAP.NOEXP_LRU.ordinal();
			}else{
				clsid = clsid|LRU_TYPE_MAP.HOT_LRU.ordinal();
			}
		}else{
			clsid = clsid|LRU_TYPE_MAP.COLD_LRU.ordinal();
		}

		ItemUtil.setSlabsClsid(it, (byte)clsid);
		byte flag = ItemUtil.getItflags(it);
		ItemUtil.setItflags(it, (byte)(flag|(Settings.useCas?ItemFlags.ITEM_CAS.getFlags():0)));
		ItemUtil.setNskey(it,(byte)key.length());
		ItemUtil.setNbytes(it, nbytes);
		try {
			
			ItemUtil.setKey(key.getBytes(JcacheGlobalConfig.defaultCahrset), it);
			ItemUtil.setExpTime(it, exptime);
			byte[] suffixBytes = suffixStr.getBytes(JcacheGlobalConfig.defaultCahrset);
			ItemUtil.setSuffix(it, suffixBytes);
			ItemUtil.getSuffix(it);
			ItemUtil.setNsuffix(it, (byte)suffixBytes.length);
			if((flag&ItemFlags.ITEM_CHUNKED.getFlags())>0){
				long chunk = ItemUtil.ITEM_data(it);
				ItemChunkUtil.setNext(chunk, ItemUtil.getHNext(it));
				ItemChunkUtil.setPrev(chunk, 0);
				ItemChunkUtil.setHead(chunk, it);
				/* Need to chain back into the head's chunk */
				long next = ItemChunkUtil.getNext(chunk);
				ItemChunkUtil.setPrev(next, chunk);
//		        chunk->size = chunk->next->size - ((char *)chunk - (char *)it);  TODO
				ItemChunkUtil.setUsed(chunk, 0);
			}
			
			ItemUtil.setHNext(it, 0);
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return it;
	}

	@Override
	public long do_item_get(String key,int nkey,long hv,Connection conn){
		long addr = JcacheContext.getAssoc().assoc_find(key, nkey, hv);
		if(Settings.verbose >= 2&& logger.isDebugEnabled()){
			logger.debug("do_item_get key : {}  addr : {}", key,addr);
		}
		
		int was_found = 0;
		
		if(Settings.verbose > 2){
			int ii;
			if(addr == 0){
				if(logger.isErrorEnabled()){
					logger.error("> NOT FOUND ");
				}
			}else{
				if(logger.isErrorEnabled()){
					logger.error("> FOUND KEY ");
				}
			}
			if(logger.isErrorEnabled()){
				logger.error(key);
			}
		}
		
		if(addr!=0){
			refcount_incr(addr);
			was_found = 1;
			long exptime = ItemUtil.getExpTime(addr);
			if(item_is_flushed(addr)){
				do_item_unlink(addr,hv);
				do_item_remove(addr);
				addr = 0;
				//TODO  STATS
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.get_flushed++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				was_found = 2;
			}else if(exptime!=0&& exptime <= System.currentTimeMillis()){  //已过期
				do_item_unlink(addr,hv);
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
	
	@Override
	public long do_item_touch(String key,int nkey,long exptime,long hv,Connection conn){
		long it = do_item_get(key,nkey,hv,conn);
		if(it!=0){
			ItemUtil.setExpTime(it, exptime);
		}
		return it;
	}
	
	@Override
	public void item_stats_reset(){
		IntStream.range(0, LARGEST_ID).forEach(f->{
			AtomicBoolean lru_locks = JcacheContext.getLRU_Lock(f);
			while(!lru_locks.compareAndSet(false, true)){}
			try {
				long[] itemstat = itemstats[f];
				for(int i = 0;i<itemstat.length;i++){
					itemstat[i] = 0;
				}
			} finally {
				lru_locks.lazySet(false);
			}
		});
	}

	@Override
	public boolean do_item_link(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		ItemUtil.setItflags(addr, (byte)(flags|ItemFlags.ITEM_LINKED.getFlags()));
		ItemUtil.setTime(addr, System.currentTimeMillis());

		StatsState.curr_bytes.addAndGet(ItemUtil.ITEM_ntotal(addr));
		StatsState.curr_items.incrementAndGet();
		Stats.total_items.incrementAndGet();
		
		 /* Allocate a new CAS ID on link. */
		ItemUtil.ITEM_set_cas(addr, Settings.useCas?get_cas_id():0);
		JcacheContext.getAssoc().assoc_insert(addr, hv);
		item_link_q(addr);
		refcount_incr(addr);
		item_stats_sizes_add(addr);
		return true;
	}

	private void item_link_q(long addr){
		int clsid = ItemUtil.getSlabsClsid(addr);
		AtomicBoolean lru_locks = JcacheContext.getLRU_Lock(clsid);
		while(!lru_locks.compareAndSet(false, true)){}
		try {
			do_item_link_q(addr);
		} finally {
			lru_locks.lazySet(false);
		}
	}

	/* Get the next CAS id for a new item. */
	@Override
	public long get_cas_id(){
		return casIdGeneraytor.incrementAndGet();
	}

	@Override
	public void do_item_remove(long addr){
		if(refcount_decr(addr)==0){
			item_free(addr);
		}
	}

	@Override
	public void item_free(long addr){
		int ntotal = ItemUtil.ITEM_ntotal(addr);		
		/* so slab size changer can tell later if item is already free or not */
		int clsid  = ItemUtil.getSlabsClsid(addr);
		JcacheContext.getSlabPool().slabs_free(addr, ntotal, clsid);
	}
	
	@Override
	public boolean item_size_ok(int nkey,int flags,int nbytes){
		String suffixStr = ItemUtil.item_make_header_suffix(nkey, flags, nbytes);
		int ntotal = ItemUtil.item_make_header(nkey, flags, nbytes,suffixStr);
		if(Settings.useCas){
			ntotal +=8;
		}

		return JcacheContext.getSlabPool().slabs_clsid(ntotal)!=0;
	}

	/**
	 * @param clsid
	 * @return
	 */
	public int noexp_lru_size(int slabs_clsid){
	    int id = CLEAR_LRU(slabs_clsid);
	    id |= LRU_TYPE_MAP.NOEXP_LRU.getValue();
//	    int ret;
//	    AtomicBoolean lru_lock = JcacheContext.getLRU_Lock(id);
//	    if(lru_lock!=null){
//	    	while(lru_lock.compareAndSet(false, true)){}
//	    	try {
//				ret = sizes_bytes[id];
//			} finally {
//				lru_lock.lazySet(false);
//			}
//	    }
		return sizes_bytes[id].intValue();
	}

	/**
	 * 计数器 减一
	 * @param addr
	 */
	private int refcount_decr(long addr){
		return ItemUtil.decrRefCount(addr);
	}

	/**
	 * 计数器 加一
	 * @param addr
	 */
	private int refcount_incr(long addr){
		return ItemUtil.incrRefCount(addr);
	}

	private void do_item_link_q(long addr) {
		
		byte itflags = ItemUtil.getItflags(addr);
		if((itflags&ItemFlags.ITEM_SLABBED.getFlags())!=0)  return;
		
		int classid = ItemUtil.getSlabsClsid(addr);
		long head = heads[classid];
		long tail = tails[classid];
		
		ItemUtil.setPrev(addr, (byte) 0);
		ItemUtil.setNext(addr,head);
		if(head!=0){
			ItemUtil.setPrev(head, addr);
		}
		heads[classid] = addr;
		if(tail==0){
			tails[classid] = addr;
		}
		sizes[classid].incrementAndGet();
		sizes_bytes[classid].addAndGet(ItemUtil.ITEM_ntotal(addr));
		return;
	}


	/* Get the next CAS id for a new item. */
//	public static long get_cas_id() {
//		return casIdGeneraytor.getAndIncrement();
//	}

	@Override
	public boolean item_is_flushed(long itemaddr){
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
	
	/* Tail linkers and crawler for the LRU crawler. */
	@Override
	public void do_item_linktail_q(long it){ /* item is the new tail */
		int clsid = ItemUtil.getSlabsClsid(it);
		long head = heads[clsid];
		long tail = tails[clsid];
		
		ItemUtil.setPrev(it, tail);
		ItemUtil.setNext(it, 0);
		long prev = ItemUtil.getPrev(it);
		if(prev!=0){
			ItemUtil.setNext(prev, it);
		}
		
		tails[clsid] = it;
		if(head==0){
			heads[clsid] = it;
		}
	}
	
	@Override
	public void do_item_unlinktail_q(long it){
		int clsid = ItemUtil.getSlabsClsid(it);
		long head = heads[clsid];
		long tail = tails[clsid];
		
		long next = ItemUtil.getNext(it);
		if(head==it){
			heads[clsid] = next;
		}
		long prev = ItemUtil.getPrev(it);
		if(tail==it){
			tails[clsid] = prev;
		}
		
		if(next!=0){
			ItemUtil.setPrev(next, ItemUtil.getPrev(it));
		}
		
		if(prev!=0){
			ItemUtil.setNext(prev, ItemUtil.getNext(it));
		}
	}
	
	/* This is too convoluted, but it's a difficult shuffle. Try to rewrite it
	 * more clearly. */
	@Override
	public long do_item_crawl_q(long it){
		int clsid = ItemUtil.getSlabsClsid(it);
		long head = heads[clsid];
		long tail = tails[clsid];
		long prev = ItemUtil.getPrev(it);
		/* We've hit the head, pop off */
		if(prev==0){
			long next = ItemUtil.getNext(it);
			if(next!=0){
				heads[clsid] = next;
				ItemUtil.setPrev(next, 0);
			}
			return 0;
		}
		
		/* Swing ourselves in front of the next item */
	    /* NB: If there is a prev, we can't be the head */
		if(prev!=0){
			if(head==prev){
				/* Prev was the head, now we're the head */
				heads[clsid] = it;
			}
			
			if(tail ==it){
				/* We are the tail, now they are the tail */
				tails[clsid] = ItemUtil.getPrev(it);
			}
			long next = ItemUtil.getNext(it);
			if(next!=0){
				ItemUtil.setNext(prev, next);
				ItemUtil.setPrev(next, prev);
			}else{
				/* Tail. Move this above? */
				ItemUtil.setNext(prev, 0);
			}
			
			/* prev->prev's next is it->prev */
			ItemUtil.setNext(it, ItemUtil.getPrev(it));
			ItemUtil.setPrev(it, ItemUtil.getPrev(ItemUtil.getNext(it)));
			ItemUtil.setPrev(ItemUtil.getNext(it), it);
	        /* New it->prev now, if we're not at the head. */
			if(ItemUtil.getPrev(it)!=0){
				ItemUtil.setNext(ItemUtil.getPrev(it), it);
			}
		}
		return ItemUtil.getNext(it);
	}
	
	@Override
	public void do_item_stats_add_crawl(int i,long reclaimed,
			long unfetched, long checked){
		itemstats[i][ItemStatsUtil.crawler_reclaimed] += reclaimed;
		itemstats[i][ItemStatsUtil.expired_unfetched] += unfetched;
		itemstats[i][ItemStatsUtil.crawler_items_checked] += checked;
	}


	
	@Override
	public void do_item_unlink_nolock(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		if((flags & ItemFlags.ITEM_LINKED.getFlags())!=0){
			ItemUtil.setItflags(addr, (byte)(ItemUtil.getItflags(addr)&~ItemFlags.ITEM_LINKED.getFlags()));
			StatsState.curr_bytes.addAndGet(-ItemUtil.ITEM_ntotal(addr));
			StatsState.curr_items.decrementAndGet();
			item_stats_sizes_remove(addr);
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
	@Override
	public void do_item_update_nolock(long addr){
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
	@Override
	public void do_item_update(long addr) {
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

	private void item_unlink_q(long addr) {
		int clsid = ItemUtil.getSlabsClsid(addr);
		AtomicBoolean lru_locks = JcacheContext.getLRU_Lock(clsid);
		while(!lru_locks.compareAndSet(false, true)){}
		try {
			do_item_unlink_q(addr);
		} finally {
			lru_locks.lazySet(false);
		}
	}
	
	private void do_item_unlink_q(long addr){
		int scid = ItemUtil.getSlabsClsid(addr);
		long head = heads[scid];
		long tail = tails[scid];
		
		if(head==addr){
			heads[scid] = ItemUtil.getNext(addr);
		}
		
		if(tail==addr){
			tails[scid] = ItemUtil.getPrev(addr);
		}
		
		long prev = ItemUtil.getPrev(addr);
		long next = ItemUtil.getNext(addr);
		
		if(next>0) ItemUtil.setPrev(next, ItemUtil.getPrev(addr));
		if(prev>0) ItemUtil.setNext(prev, ItemUtil.getNext(addr));
		sizes[scid].decrementAndGet();
		sizes_bytes[scid].addAndGet(-ItemUtil.ITEM_ntotal(addr));
	}

	@Override
	public void do_item_unlink(long addr,long hv){
		byte flags = ItemUtil.getItflags(addr);
		if((flags&ItemFlags.ITEM_LINKED.getFlags())!=0){
			ItemUtil.setItflags(addr, (byte)(flags&~ItemFlags.ITEM_LINKED.getFlags()));

			StatsState.curr_bytes.addAndGet(-ItemUtil.ITEM_ntotal(addr));
			StatsState.curr_items.decrementAndGet();
			
			item_stats_sizes_remove(addr);
			if(logger.isDebugEnabled()){
				logger.debug("do_item_replace do_item_unlink assoc_delete begin: {}  hv : {}", addr,hv);
			}
			JcacheContext.getAssoc()
			 			 .assoc_delete(ItemUtil.getKey(addr),
			 					       ItemUtil.getNskey(addr),
			 					       hv);
			if(logger.isDebugEnabled()){
				logger.debug("do_item_replace do_item_unlink item_unlink_q begin: {}  hv : {}", addr,hv);
			}
			
			item_unlink_q(addr);
			if(logger.isDebugEnabled()){
				logger.debug("do_item_replace do_item_unlink item_unlink_q end : {}  hv : {}", addr,hv);
			}
			do_item_remove(addr);
		}
	}
	
	@Override
	public void item_stats_sizes_add(long it){
	    if (stats_sizes_hist == 0 || stats_sizes_cas_min > ItemUtil.ITEM_get_cas(it))
	        return;
	    int ntotal = ItemUtil.ITEM_ntotal(it);
	    int bucket = ntotal / 32;
	    if ((ntotal % 32) != 0) bucket++;
	    if (bucket < stats_sizes_buckets) {
	    	UnSafeUtil.unsafe.getAndAddInt(null, getStats_sizes_hist(bucket), 1);
	    };
	}
	
	/* I think there's no way for this to be accurate without using the CAS value.
	 * Since items getting their time value bumped will pass this validation.
	 */
	@Override
	public void item_stats_sizes_remove(long it){
	    if (stats_sizes_hist == 0 || stats_sizes_cas_min > ItemUtil.ITEM_get_cas(it))
	        return;
	    int ntotal = ItemUtil.ITEM_ntotal(it);
	    int bucket = ntotal / 32;
	    if ((ntotal % 32) != 0) bucket++;
	    if (bucket < stats_sizes_buckets){
	    	UnSafeUtil.unsafe.getAndAddInt(null, getStats_sizes_hist(bucket), -1);
	    }
	}
	
	@Override
	public boolean item_stats_sizes_status(){
		boolean ret = false;
		while(!stats_sizes_lock.compareAndSet(false, true)){}
		try {
			if(stats_sizes_hist!=0){
				ret = true;
			}
		} finally {
			stats_sizes_lock.lazySet(false);
		}
		return ret;
	}

	@Override
	public boolean do_item_replace(long oldAddr,long newAddr,long hv){
		
		byte itflags = ItemUtil.getItflags(oldAddr);
		if((itflags&ItemFlags.ITEM_SLABBED.getFlags())!=0)  return false;
		if(logger.isDebugEnabled()){
			logger.debug("do_item_replace do_item_unlink begin : {}  newAddr : {}", oldAddr,newAddr);
		}
		do_item_unlink(oldAddr,hv);
		if(logger.isDebugEnabled()){
			logger.debug("do_item_replace do_item_unlink end : {}  newAddr : {}", oldAddr,newAddr);
		}
		return  do_item_link(newAddr, hv);
	}

	@Override
	public long item_cachedump(int slabs_clsid, int limit, long bytes) {
		// TODO Auto-generated method stub
		return 0;
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
	private int lru_pull_tail(int orig_id,LRU_TYPE_MAP cur_lru,long total_bytes,int flags){
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
			AtomicBoolean lru_locks = JcacheContext.getLRU_Lock(id);
			while(!lru_locks.compareAndSet(false, true)){}
			search = tails[id];
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
					if(refcount_incr(search)!=2){
						/* Note pathological case with ref'ed items in tail.
			             * Can still unlink the item, but it won't be reusable yet */
						itemstats[id][ItemStatsUtil.lrutail_reflocked]++;
						/* In case of refcount leaks, enable for quick workaround. */
			            /* WARNING: This can cause terrible corruption */
						if(Settings.tailRepairTime>0&&
						   (ItemUtil.getTime(search)+Settings.tailRepairTime)<System.currentTimeMillis()){
							itemstats[id][ItemStatsUtil.tailrepairs]++;
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
						itemstats[id][ItemStatsUtil.reclaimed]++;
			            if ((ItemUtil.getItflags(search) & ItemFlags.ITEM_FETCHED.getFlags()) == 0) {
			                itemstats[id][ItemStatsUtil.expired_unfetched]++;
			            }
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
							if(sizes_bytes[id].get() > limit){
									itemstats[id][ItemStatsUtil.moves_to_cold]++;
								move_to_lru = LRU_TYPE_MAP.COLD_LRU;
								do_item_unlink_q(search);
								it = search;
								removed++;
								break;
							}else if((ItemUtil.getItflags(search)&ItemFlags.ITEM_ACTIVE.getFlags())!=0){
								/* Only allow ACTIVE relinking if we're not too large. */
								itemstats[id][ItemStatsUtil.moves_within_lru]++;
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
								itemstats[id][ItemStatsUtil.evicted]++;
			                    itemstats[id][ItemStatsUtil.evicted_time] = System.currentTimeMillis() - ItemUtil.getTime(search);
			                    if (ItemUtil.getExpTime(search) != 0)
			                        itemstats[id][ItemStatsUtil.evicted_nonzero]++;
			                    if ((ItemUtil.getItflags(search) & ItemFlags.ITEM_FETCHED.getFlags()) == 0)  {
			                        itemstats[id][ItemStatsUtil.evicted_unfetched]++;
			                    }
								do_item_unlink_nolock(search, hv);
								removed++;
								if(Settings.slabAutoMove==2){
//										slabs_reassign(-1,orig_id); //TODO
								}
							}else if((ItemUtil.getItflags(search)&ItemFlags.ITEM_ACTIVE.getFlags())!=0
									  &&Settings.lruMaintainerThread){
								itemstats[id][ItemStatsUtil.moves_to_warm]++;
								ItemUtil.setItflags(search, (byte)~ItemFlags.ITEM_ACTIVE.getFlags());
								move_to_lru = LRU_TYPE_MAP.WARM_LRU;
								do_item_unlink_q(search);
								removed++;
							}
							break;
						case NOEXP_LRU:
					}
					
					if(it!=0){
						break;
					}
				}
			} finally {
				lru_locks.lazySet(false);
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

	/**
	 * lru线程实现类
	 * @author Tommy
	 */
	class LruMaintainerThread implements Runnable {

		@Override
		public void run() {
			int i;
			//每次循环执行之后延时时间
			long to_sleep = MIN_LRU_MAINTAINER_SLEEP;
			long last_crawler_check = 0;
			CrawlerExpiredData cdata = new CrawlerExpiredData();
			cdata.crawl_complete = false;
			if (Settings.verbose > 2)
				logger.info("Starting LRU maintainer background thread");
			//死循环,不断循环执行
			while (do_run_lru_maintainer_thread!=0) {
				int did_moves = 0;

				while(!lru_maintainer_lock.compareAndSet(false, true)){}
				try{
					//每次while循环之后延迟执行时间
					lru_maintainer_thread_warper.sleep(to_sleep);
				}catch (InterruptedException e) {
					logger.error("lru_maintainer_thread_warper sleep:{}",e);
				}finally{
					//解锁
					lru_maintainer_lock.lazySet(false);
				}
				
				Stats.lru_maintainer_juggles.incrementAndGet();
				
				//搜索源代码发现lru_maintainer_check_clsid一直都等于0
		        //所以默认应该不会命中该if条件
		        if (lru_maintainer_check_clsid != 0) {
		            did_moves = lru_maintainer_juggle(lru_maintainer_check_clsid);
		            lru_maintainer_check_clsid = 0;
		        } else {
		            //循环获取 slab id 然后依次调用
		            for (i = Settings.POWER_SMALLEST; i < Settings.MAX_NUMBER_OF_SLAB_CLASSES; i++) {
		                did_moves += lru_maintainer_juggle(i);
		            }
		        }
				
				if (did_moves == 0) {
		            if (to_sleep < MAX_LRU_MAINTAINER_SLEEP)
		                to_sleep += 1000;
		        } else {
		            to_sleep /= 2;
		            if (to_sleep < MIN_LRU_MAINTAINER_SLEEP)
		                to_sleep = MIN_LRU_MAINTAINER_SLEEP;
		        }
				
				//判断是否开启了item爬虫线程
				if (Settings.lru_crawler && last_crawler_check != Settings.current_time) {
					//如果开启了则调用该函数执行,判断是否符合触发item爬虫线程条件
		            //如果符合条件则触发信号
					lru_maintainer_crawler_check(cdata);
					last_crawler_check = Settings.current_time;
		        }
				
			}
			
			if (Settings.verbose > 2)
		        logger.info("LRU maintainer thread stopping");
		}
		
	} 
	
	//lru实现类
	LruMaintainerThread lru_maintainer_thread = new LruMaintainerThread();
	//线程容器
	Thread lru_maintainer_thread_warper;
	
	
	@Override
	public int start_lru_maintainer_thread() {
		int ret = 0;
		//自旋转锁
		while(!lru_maintainer_lock.compareAndSet(false, true)){}
		try{
			do_run_lru_maintainer_thread = 1;
		    Settings.lruMaintainerThread = true;
		    //开启lru主线程
		    lru_maintainer_thread_warper.start();
		    logger.info("Can't create LRU maintainer thread: {}",ret);
		}finally{
			//解锁
			lru_maintainer_lock.lazySet(false);
		}
		return ret;
	}

	@Override
	public int stop_lru_maintainer_thread() {
		int ret = 0;
		//自旋转锁
		while(!lru_maintainer_lock.compareAndSet(false, true)){}
		try{
			do_run_lru_maintainer_thread = 0;
		    Settings.lruMaintainerThread = false;
		    //开启lru主线程
		    lru_maintainer_thread_warper.stop();
		    logger.info("Failed to stop LRU maintainer thread: {}",ret);
		}finally{
			//解锁
			lru_maintainer_lock.lazySet(false);
		}
		return ret;
	}

	@Override
	public int init_lru_maintainer() {
		if (lru_maintainer_initialized == 0) {
			lru_maintainer_thread_warper = new Thread(lru_maintainer_thread);
	        lru_maintainer_initialized = 1;
	    }
		return 0;
	}

	@Override
	public void lru_maintainer_pause() {
		while(!lru_maintainer_lock.compareAndSet(false, true)){}
	}

	@Override
	public void lru_maintainer_resume() {
		lru_maintainer_lock.lazySet(true);
	}

	@Override
	public void lru_maintainer_crawler_check(CrawlerExpiredData cdata ) {
		 int i;
		 long next_crawls[] = new long[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
		 long next_crawl_wait[] = new long[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
		 int todo[] = new int[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
		 boolean do_run = false;
		 if ( !cdata.crawl_complete) {
		     return;
		 }
		 
		 for (i = Settings.POWER_SMALLEST; i < Settings.MAX_NUMBER_OF_SLAB_CLASSES; i++) {
			 CrawlerstatsT s = cdata.crawlerstats[i];
			 
			 if (s.run_complete) {
				while(!cdata.lock.compareAndSet(false, true)){}
				try{
					int x;
					long possible_reclaims = s.seen - s.noexp;
					long available_reclaims = 0;
					long low_watermark = (possible_reclaims / 100) + 1;
					long since_run = Settings.current_time - s.end_time;
					for (x = 0; x < 60; x++) {
			         	available_reclaims += s.histo[x];
			         	if (available_reclaims > low_watermark) {
			            	if (next_crawl_wait[i] < (x * 60)) {
			            		next_crawl_wait[i] += 60;
			             	} else if (next_crawl_wait[i] >= 60) {
			                	next_crawl_wait[i] -= 60;
			             	}
			            	break;
			           }
			        }
					
					if (available_reclaims == 0) {
		                next_crawl_wait[i] += 60;
		            }
					
					if (next_crawl_wait[i] > Settings.MAX_MAINTCRAWL_WAIT) {
			            next_crawl_wait[i] = Settings.MAX_MAINTCRAWL_WAIT;
			        }

			       	next_crawls[i] = Settings.current_time + next_crawl_wait[i] + 5;
					
			       	//日志
			       	logger.warn("i:{},low_watermark:{},available_reclaims:{},since_run:{},"
			       			+ "next_crawls:{},time:{},seen:{},reclaimed{}"
			       			, i,  low_watermark, available_reclaims,
		                     since_run, next_crawls[i] - Settings.current_time,
		                    s.end_time - s.start_time, s.seen, s.reclaimed);
			       	
					s.run_complete = false;
				}finally{
					//解锁
					cdata.lock.lazySet(false);
				}
				
			 }
			 
			 if (Settings.current_time > next_crawls[i]) {
				 todo[i] = 1;
		         do_run = true;
		         next_crawls[i] = Settings.current_time + 5; // minimum retry wait.
		     }
			 
		 }
		 if (do_run) {
			 Crawler crawler = new CrawlerImpl();
			 crawler.lru_crawler_start(todo, 0,CrawlerResultType.CRAWLER_EXPIRED,cdata, 0L, 0);
		 }
	}

	@Override
	public int lru_maintainer_juggle(int slabs_clsid) {
		int i;
	    int did_moves = 0;
	    boolean mem_limit_reached = false;
	    long total_bytes = 0L;
	    int chunks_perslab = 0;
	    int chunks_free = 0;
//	    chunks_free = slabs_available_chunks(slabs_clsid, &mem_limit_reached,
//	            &total_bytes, &chunks_perslab);
	    if (Settings.expirezero_does_not_evict)
	        total_bytes -= noexp_lru_size(slabs_clsid);
	    
	    if (Settings.slab_automove > 0 && chunks_free > (chunks_perslab * 2.5)) {
//	        slabs_reassign(slabs_clsid, Settings.SLAB_GLOBAL_PAGE_POOL);
	    }
	    
	    /* Juggle HOT/WARM up to N times */
	    for (i = 0; i < 1000; i++) {
	        int do_more = 0;
	        if (lru_pull_tail(slabs_clsid, LRU_TYPE_MAP.HOT_LRU, total_bytes, LRU_PULL_CRAWL_BLOCKS) > 0 ||
	            lru_pull_tail(slabs_clsid, LRU_TYPE_MAP.WARM_LRU, total_bytes, LRU_PULL_CRAWL_BLOCKS) > 0) {
	            do_more++;
	        }
	        do_more += lru_pull_tail(slabs_clsid, LRU_TYPE_MAP.COLD_LRU, total_bytes, LRU_PULL_CRAWL_BLOCKS);
	        if (do_more == 0)
	            break;
	        did_moves++;
	    }
	    
	    return did_moves;
	}

}
