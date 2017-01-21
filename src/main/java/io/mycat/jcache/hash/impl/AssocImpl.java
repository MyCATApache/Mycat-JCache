package io.mycat.jcache.hash.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.items.Items;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

@SuppressWarnings("restriction")
public class AssocImpl implements Assoc{
	
	private static Logger logger = LoggerFactory.getLogger(AssocImpl.class);
	
	/* how many powers of 2's worth of buckets we use */
	private int hashpower = Settings.hashpower_default;
	
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
	private static boolean expanding = false;
	private static boolean started_expanding = false;
	
	/*
	 * During expansion we migrate values with bucket granularity; this is how
	 * far we've gotten so far. Ranges from 0 .. hashsize(hashpower - 1) - 1.
	 */
	private static long expand_bucket = 0;
	
	private final static AtomicBoolean hash_items_counter_lock = new AtomicBoolean(false);

	@Override
	public void assoc_init(int hashpower_init) {
		if(hashpower_init > 0){
			hashpower = hashpower_init;
		}
//		int powerum = hashpower*UnSafeUtil.unsafe.addressSize();
		int powerum = hashsize(hashpower) * 8;
		primary_hashtable = UnSafeUtil.unsafe.allocateMemory(powerum);
		UnSafeUtil.unsafe.setMemory(primary_hashtable, powerum, (byte)0);
	}
	
	public void printHashtable(){
		int cout = 1;
		System.out.println("=======================");
		for(int i = 0;i<hashsize(hashpower);i++){
			if(cout%50==0){
				cout = 1;
				System.out.println(cout);
			}
			long index = primary_hashtable+i*8;
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
			it = UnSafeUtil.getLong(hashtableindex);
		}else{
			hashtableindex = primary_hashtable+(hashnum(hv,hashpower));
			it = UnSafeUtil.getLong(hashtableindex);
		}
		logger.debug("assoc find key = {},hashindex = {} , hashaddr = {}",key,hashtableindex,it);
		long ret = 0;
		int depth = 0;
		while(it>0){
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
			pos = old_hashtable+oldbucket;
		}else{
			pos = primary_hashtable+(hashnum(hv,hashpower));
		}
		
		while(pos>0&&((nkey!=ItemUtil.getNskey(pos))||(key.compareTo(ItemUtil.getKey(pos))!=0))){
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
		ItemUtil.setHNext(it, UnSafeUtil.getLong(hashtableindex));
		UnSafeUtil.unsafe.putAddress(hashtableindex, it);
		
		logger.debug("assoc insert key = {},hashindex = {} , hashaddr = {}",ItemUtil.getKey(it),hashtableindex,it);
		while(hash_items_counter_lock.compareAndSet(false, true)){}
		try{
			hash_items.incrementAndGet();
		    if (! expanding && hash_items.get() > ((hashsize(hashpower) * 3) / 2)) {
//		        assoc_start_expand();   // hashtable 扩容
		    	System.out.println("hashtable 扩容");
		    }
		}finally{
			hash_items_counter_lock.lazySet(false);
		}
		return true;
	}

	@Override
	public void assoc_delete(String key, int nkey, long hv) {
		long before = _hashitem_before(key,nkey,hv);  // before is hashtable address
		if(before>0){
			long nxt;
			hash_items.incrementAndGet();
			/* The DTrace probe cannot be triggered as the last instruction
	         * due to possible tail-optimization by the compiler
	         */
			long item = UnSafeUtil.getLong(before);
			nxt = ItemUtil.getHNext(item);
			ItemUtil.setHNext(item,0);
			UnSafeUtil.unsafe.putLong(before, nxt);
		}
		/* Note:  we never actually get here.  the callers don't delete things
	       they can't find. */
	    assert(before != 0);
	}
	
	private int hashsize(int n){
		return 1<<n;
	}
	
	private int hashmask(int n){
		return hashsize(n) - 1;
	}
	
	private long hashnum(long hv,int n){
		return (hv&hashmask(hashpower))*8;
	}

}
