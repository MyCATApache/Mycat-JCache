package io.mycat.jcache.memory;

/**
 * 
 * @author tangww
 * @version newEDM
 * @since 2016年11月29日 
 *
 */
@Deprecated
public class Item {
	byte prev;   //记录上一个item的地址,主要用于LRU链和freelist链   这两个属性还有必要？？TODO
	byte next;   //记录下一个item的地址,主要用于LRU链和freelist链  这两个属性还有必要？？TODO
	byte hNext;  //记录HashTable的下一个Item的地址 
	/*
	 * 最近访问的时间，只有set/add/replace等操作才会更新这个字段
	 * 当执行flush命令的时候，需要用这个时间和执行flush命令的时间相比较，来判断是否失效  
	 * typedef unsigned int rel_time_t;
	 * Time relative to server start. Smaller than time_t on 64-bit systems. */
	int flushTime;
	/*
	 * 缓存的过期时间。设置为0的时候，则永久有效。
	 * 如果Memcached不能分配新的item的时候，设置为0的item也有可能被LRU淘汰
	 * typedef unsigned int rel_time_t;
	 * Time relative to server start. Smaller than time_t on 64-bit systems. */
	int expTime;
	/*
	 * value数据大小
	 */
	int nbytes;
	/*
	 * 引用的次数。通过这个引用的次数，可以判断item是否被其它的线程在操作中。
	 * 也可以通过refcount来判断当前的item是否可以被删除，只有refcount -1 = 0的时候才能被删除  
	 */
	short refCount;
	/*
	 * which slab class we're in 标记item属于哪个slabclass下
	 */
	byte slabsClsid;
	
	byte it_flags; /* ITEM_* above */
	/*
	 *length of flags-and-length string
	 */
	byte nsuffix;
	
	/*
	 * key length, w/terminating null and padding 
	 */
	byte nskey;
	
	byte[] bytes; /*   定义空数组 , 指向 数据部分 首地址     是否还有存在的必要 ？ TODO */
	
	/**
	 * #define ITEM_key(item) (((char*)&((item)->data)) \
         + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @return
	 */
	public static String itemKey(){
		
		return null;
	}
	
	/**
	 * #define ITEM_data(item) ((char*) &((item)->data) + (item)->nkey + 1 \
         + (item)->nsuffix \
         + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @return
	 */
	public static String itemData(){
		
		return null;
	}
	
	/**
	 * #define ITEM_suffix(item) ((char*) &((item)->data) + (item)->nkey + 1 \
         + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @return
	 */
	public static String itemSuffix(){
		
		return null;
	}
	
	/**
	 * #define ITEM_ntotal(item) (sizeof(struct _stritem) + (item)->nkey + 1 \
         + (item)->nsuffix + (item)->nbytes \
         + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 */
	public static String itemNtotal(){
		
		return null;
	}

	public long getFlushTime() {
		return flushTime;
	}
	public void setFlushTime(int flushTime) {
		this.flushTime = flushTime;
	}
	public long getExpTime() {
		return expTime;
	}
	public void setExpTime(int expTime) {
		this.expTime = expTime;
	}
	public int getNbytes() {
		return nbytes;
	}
	public void setNbytes(int nbytes) {
		this.nbytes = nbytes;
	}
	public short getRefCount() {
		return refCount;
	}
	public void setRefCount(short refCount) {
		this.refCount = refCount;
	}
	public int getSlabsClsid() {
		return slabsClsid;
	}
	public int getNskey() {
		return nskey;
	}
	public byte[] getBytes() {
		return bytes;
	}
	public void setBytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public byte getPrev() {
		return prev;
	}

	public void setPrev(byte prev) {
		this.prev = prev;
	}

	public byte getNext() {
		return next;
	}

	public void setNext(byte next) {
		this.next = next;
	}

	public byte gethNext() {
		return hNext;
	}

	public void sethNext(byte hNext) {
		this.hNext = hNext;
	}

	public byte getIt_flags() {
		return it_flags;
	}

	public void setIt_flags(byte it_flags) {
		this.it_flags = it_flags;
	}

	public byte getNsuffix() {
		return nsuffix;
	}

	public void setNsuffix(byte nsuffix) {
		this.nsuffix = nsuffix;
	}

	public void setSlabsClsid(byte slabsClsid) {
		this.slabsClsid = slabsClsid;
	}

	public void setNskey(byte nskey) {
		this.nskey = nskey;
	}
}
