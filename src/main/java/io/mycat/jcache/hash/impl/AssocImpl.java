package io.mycat.jcache.hash.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.context.StatsState;
import io.mycat.jcache.enums.PAUSE_THREAD_TYPES;
import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

/**
 * memcached 高仿版 hashtable
 * @author liyanjun
 *
 */
@SuppressWarnings("restriction")
public class AssocImpl implements Assoc,Runnable{
	
	private static Logger logger = LoggerFactory.getLogger(AssocImpl.class);
	
	/* how many powers of 2's worth of buckets we use */
	private static long hashpower = Settings.hashpower_default;
	
	/* Main hash table. This is where we look except during expansion. */
	private static long primary_hashtable;
	
	/*
	 * Previous hash table. During expansion, we look here for keys that haven't
	 * been moved over to the primary yet.
	 */
	private static long old_hashtable;
	
	/* Number of items in the hash table. */
	private static AtomicLong hash_items = new AtomicLong(0);
	
	/* Flag: Are we in the middle of expanding now? */
	private static volatile boolean expanding = false;
	private static volatile boolean started_expanding = false;
	
	/*
	 * During expansion we migrate values with bucket granularity; this is how
	 * far we've gotten so far. Ranges from 0 .. hashsize(hashpower - 1) - 1.
	 */
	private static long expand_bucket = 0;
	
	private final static AtomicBoolean hash_items_counter_lock = new AtomicBoolean(false);
	
	private static Lock maintenance_lock = new ReentrantLock();
	
	private static Condition maintenance_cond = maintenance_lock.newCondition();
	
	private static volatile int do_run_maintenance_thread = 1;
	
	private static final int DEFAULT_HASH_BULK_MOVE = 1;
	
	private static int hash_bulk_move = DEFAULT_HASH_BULK_MOVE;
	
	private Thread assoc_maintenance_thread;

	@Override
	public void assoc_init(int hashpower_init) {
		if(hashpower_init > 0){
			hashpower = hashpower_init;
		}
//		int powerum = hashpower*UnSafeUtil.unsafe.addressSize();
		int powerum = JcacheContext.hashsize(hashpower) * UnSafeUtil.addresssize;
		primary_hashtable = UnSafeUtil.unsafe.allocateMemory(powerum);
		UnSafeUtil.unsafe.setMemory(primary_hashtable, powerum, (byte)0);
		assoc_maintenance_thread = new Thread(this);
		assoc_maintenance_thread.start();
	}
	
	/**
	 * hashtable 分配 情况，测试使用
	 */
	public void printHashtable(){
		int cout = 1;
		System.out.println("=======================");
		for(int i = 0;i<JcacheContext.hashsize(hashpower);i++){
			if(cout%50==0){
				cout = 1;
				System.out.println(cout);
			}
			long index = primary_hashtable+i*UnSafeUtil.addresssize;
			long aa = UnSafeUtil.unsafe.getLong(index);
			if(aa>0){
				System.out.print(index+"="+aa+",");
				cout++;
			}
		}
		System.out.println();
		System.out.println("=======================");
	}

	@Override
	public long assoc_find(String key, int nkey, long hv) {
		long it;
		long oldbucket;
		long hashtableindex;
		if(expanding&&(oldbucket = (hashnum(hv,hashpower-1)))>=expand_bucket){
			hashtableindex = old_hashtable+oldbucket;
			it = UnSafeUtil.unsafe.getAddress(hashtableindex);
		}else{
			hashtableindex = primary_hashtable+(hashnum(hv,hashpower));
			it = UnSafeUtil.unsafe.getAddress(hashtableindex);
		}
		
		if(logger.isDebugEnabled()){
			logger.debug("assoc find key = {},hashindex = {} , hashaddr = {}",key,hashtableindex,it);
		}
		
		long ret = 0;
		int depth = 0;
		while(it>0){  //一个桶中  多个item时, 遍历链表
			if((nkey==ItemUtil.getNskey(it))&&
					(key.compareTo(ItemUtil.getKey(it))==0)){
				ret = it;
				break;
			}
			it = ItemUtil.getHNext(it);
			++depth;
		}
//		MEMCACHED_ASSOC_FIND(key, nkey, depth);
		return ret;
	}
	
	/* returns the address of the item pointer before the key.  if *item == 0,
	   the item wasn't found */
	public long _hashitem_before(String key,int nkey,long hv){
		long pos;
		long oldbucket;
		
		if(expanding&&(oldbucket = (hashnum(hv,hashpower-1)))>=expand_bucket){
			pos = UnSafeUtil.unsafe.getAddress(old_hashtable+oldbucket);
		}else{
			pos = UnSafeUtil.unsafe.getAddress(primary_hashtable+(hashnum(hv,hashpower)));
		}
	    
	    while(pos>0 && (nkey != ItemUtil.getNskey(pos)
	    				||(key.compareTo(ItemUtil.getKey(pos))!=0))){
	    	pos = ItemUtil.getHNext(pos);
	    }
	    
		return pos;
	}
	

	/* Note: this isn't an assoc_update.  The key must not already exist to call this */
	@Override
	public boolean assoc_insert(long it, long hv) {
		long oldbucket;
		long hashtableindex;
		if(expanding&&(oldbucket = (hashnum(hv,hashpower-1)))>=expand_bucket){
			hashtableindex = old_hashtable+oldbucket;
		}else{
			hashtableindex = primary_hashtable+(hashnum(hv,hashpower));
		}
		ItemUtil.setHNext(it, UnSafeUtil.unsafe.getAddress(hashtableindex));
		UnSafeUtil.unsafe.putAddress(hashtableindex, it);
		
		if(logger.isDebugEnabled()){
			logger.debug("assoc insert key = {},hashindex = {} , hashaddr = {}",ItemUtil.getKey(it),hashtableindex,it);
		}
		
		while(!hash_items_counter_lock.compareAndSet(false, true)){}
		try{
			hash_items.incrementAndGet();
		    if (! expanding && hash_items.get() > ((JcacheContext.hashsize(hashpower) * 3) / 2)) {
		    	System.out.println("hashtable  扩容");
		        assoc_start_expand();  //hashtable 扩容
		    }
		}finally{
			hash_items_counter_lock.lazySet(false);
		}
		return true;
	}	

	@Override
	public void assoc_delete(String key, int nkey, long hv) {
		long before = _hashitem_before(key,nkey,hv);  // before is item address,当前桶内如果只有一个item ,before 返回的是这个item的addr
		if(before>0){
			long nxt;
			hash_items.decrementAndGet();
			/* The DTrace probe cannot be triggered as the last instruction
	         * due to possible tail-optimization by the compiler
	         * 
	         */
			nxt = ItemUtil.getHNext(before);
			ItemUtil.setHNext(before,0);
			if(nxt!=0){ //从hash 链表中删除 当前item
				long curnxt = ItemUtil.getHNext(nxt);
				if(curnxt!=0){
					ItemUtil.setHNext(curnxt, 0);
					ItemUtil.setHNext(before, curnxt);
				}
			}else{  //桶内只有一个元素时，就是当前元素,从桶中删除 当前元素
				long pos;
				long oldbucket;
				
				if(expanding&&(oldbucket = (hashnum(hv,hashpower-1)))>=expand_bucket){
					pos = old_hashtable+oldbucket;
				}else{
					pos = primary_hashtable+(hashnum(hv,hashpower));
				}
				UnSafeUtil.unsafe.putAddress(pos, 0);
			}
		}
		/* Note:  we never actually get here.  the callers don't delete things
	       they can't find. */
//	    assert(before != 0);
	}
	
	
	private void assoc_start_expand(){
		if(started_expanding){
			return;
		}
		started_expanding = true;
		maintenance_lock.lock();
		try{
			maintenance_cond.signal();
		} finally {
			maintenance_lock.unlock();
		}
	}
	
	/* grows the hashtable to the next power of 2. */
	private static void assoc_expand(){
		old_hashtable = primary_hashtable;
		int powerum = JcacheContext.hashsize(hashpower+1) * UnSafeUtil.addresssize;
		primary_hashtable = UnSafeUtil.unsafe.allocateMemory(powerum);
		UnSafeUtil.unsafe.setMemory(primary_hashtable, powerum, (byte)0);
		if(primary_hashtable > 0){
			if(Settings.verbose > 1){
				if(logger.isErrorEnabled()){
					logger.error("Hash table expansion starting");
				}
			}
			hashpower++;
			expanding = true;
			expand_bucket = 0;
			StatsState.hash_power_level.set(hashpower);
			StatsState.hash_bytes.addAndGet((JcacheContext.hashsize(hashpower)*UnSafeUtil.addresssize));
			while(!StatsState.hash_is_expanding.compareAndSet(false, true)){};
		}else{
			primary_hashtable = old_hashtable;
			/* Bad news, but we can keep running. */
		}
	}
	
	@Override
	public void run() {
		assoc_maintenance_thread();
	}
	
	/**
	 * hashtable 扩容线程,涉及到 item hash 值的重新计算 
	 */
	private void assoc_maintenance_thread(){
		maintenance_lock.lock();
		try {
			while(true){
				int ii = 0;
				
				/* There is only one expansion thread, so no need to global lock. */
				for(ii=0;ii<hash_bulk_move&&expanding;++ii){
					long it =0l;
					long next = 0l;
					long bucket;
					Lock item_lock;
					
					/* bucket = hv & hashmask(hashpower) =>the bucket of hash table
		             * is the lowest N bits of the hv, and the bucket of item_locks is
		             *  also the lowest M bits of hv, and N is greater than M.
		             *  So we can process expanding with only one item_lock. cool! */
					item_lock = JcacheContext.getSegment().item_trylock(expand_bucket);
					if(item_lock!=null){
						try {
							for(it = UnSafeUtil.getLong(old_hashtable + (expand_bucket*UnSafeUtil.addresssize));
									it!=0;
									it = next){
									next = ItemUtil.getHNext(it);
									long hash = JcacheContext.getHash().hash(ItemUtil.getKey(it), ItemUtil.getNskey(it));
									bucket = hash&JcacheContext.hashmask(hashpower);  //计算新的桶
									long bucketaddr = primary_hashtable+(bucket*UnSafeUtil.addresssize);
									ItemUtil.setHNext(it, UnSafeUtil.unsafe.getAddress(bucketaddr));
									UnSafeUtil.unsafe.putAddress(bucketaddr, it);
								}
								
								UnSafeUtil.unsafe.setMemory(old_hashtable + (expand_bucket*UnSafeUtil.addresssize), UnSafeUtil.addresssize, (byte)0);						
								expand_bucket ++;
								
								if(expand_bucket == JcacheContext.hashsize(hashpower - 1)){
									expanding = false;
									UnSafeUtil.unsafe.freeMemory(old_hashtable);
									StatsState.hash_bytes.addAndGet(-(JcacheContext.hashsize(hashpower-1)*UnSafeUtil.addresssize));
									while(!StatsState.hash_is_expanding.compareAndSet(true, false)){};
									if(Settings.verbose > 1){
										if(logger.isErrorEnabled()){
											logger.error("Hash table expansion done");
										}
									}
								}
						} finally {
							item_lock.unlock();
						}
					}else{
						try {
							Thread.sleep(10*1000);
						} catch (InterruptedException e) {
						}
					}
				}
				
				if(!expanding){
					/* We are done expanding.. just wait for next invocation */
					started_expanding = false;
					try {
						maintenance_cond.await();
					} catch (InterruptedException e) {
					}
					
					/* assoc_expand() swaps out the hash table entirely, so we need
		             * all threads to not hold any references related to the hash
		             * table while this happens.
		             * This is instead of a more complex, possibly slower algorithm to
		             * allow dynamic hash table expansion without causing significant
		             * wait times.
		             */
					pause_threads(PAUSE_THREAD_TYPES.PAUSE_ALL_THREADS);
					assoc_expand();
					pause_threads(PAUSE_THREAD_TYPES.RESUME_ALL_THREADS);
				}
			}
		} finally {
			maintenance_lock.unlock();
		}
	}
	
	/* Must not be called with any deeper locks held 
	 * TODO 
	 */
	private static void pause_threads(PAUSE_THREAD_TYPES type){
		switch(type){
		case PAUSE_ALL_THREADS:
			JcacheContext.getSlabPool().slabs_rebalancer_pause();
//			lru_crawler_pause();
//			lru_maintainer_pause();
			return;
		case PAUSE_WORKER_THREADS:
			break;
		case RESUME_ALL_THREADS:
			return;
		case RESUME_WORKER_THREADS:
			break;
		default:
		}
	}
	
	public static long hashnum(long hv,long n){
		return (hv&JcacheContext.hashmask(n))*UnSafeUtil.addresssize;
	}

}
