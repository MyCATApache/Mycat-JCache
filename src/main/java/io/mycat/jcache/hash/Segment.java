package io.mycat.jcache.hash;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * 锁分段
 * @author liyanjun
 *
 */
public class Segment {
	
	/* size of the item lock hash table */
	private static int item_lock_count;
	private long item_lock_hashpower;
	
	private ReentrantLock[] item_locks = null;
	
	public Segment(int power){
		item_lock_count = hashsize(power);
		item_lock_hashpower = power;
		item_locks = new ReentrantLock[item_lock_count];
		
		IntStream.range(0, item_lock_count).forEach(f->{
			item_locks[f] = new ReentrantLock();
		});
	}
	
	/* item_lock() must be held for an item before any modifications to either its
	 * associated hash bucket, or the structure itself.
	 * LRU modifications must hold the item lock, and the LRU lock.
	 * LRU's accessing items must item_trylock() before modifying an item.
	 * Items accessible from an LRU must not be freed or modified
	 * without first locking and removing from the LRU.
	 */
	public void item_lock(long hv){
		int index = (int)hv&hashmask(item_lock_hashpower);
		item_locks[index].lock();
	}
	
	public void item_unlock(long hv){
		item_locks[(int)hv&hashmask(item_lock_hashpower)].unlock();
	}
	
	public ReentrantLock item_trylock(long hv){
		ReentrantLock lock = item_locks[(int)hv&hashmask(item_lock_hashpower)];
		if(lock.tryLock()){
			return lock;
		}
		return null;
	}
	
	public int hashsize(long n){
		return 1<<n;
	}
	
	public int hashmask(long n){
		return hashsize(n) - 1;
	}

}
