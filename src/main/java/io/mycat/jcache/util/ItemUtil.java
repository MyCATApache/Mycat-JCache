package io.mycat.jcache.util;

import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.setting.Settings;

/**
 * item 工具
 * @author liyanjun
 * @author PigBrother
 * bytebuffer 组织形式， header 和 data 部分。                                                                                                                                                                       header 部分结束
 * prev,next,hnext,flushTime,expTime,nbytes,refCount,slabsClisd,it_flags,nsuffix,nskey,//    CAS,key,suffix,value
 * 0    8    16    24        32      40     44       46         47       48      49          50  58  58+key
 *
 * item   cas  key  suffix  data
 *
 * suffix  由   '' "flag" '' "nbytes" \r \n 组成。
 * 其中 "flag"  为 flag 的字符形式， "nbytes"  是 nbytes 的字符形式
 */
public class ItemUtil {

	/**
	 * PigBrother
	 * 字段偏移
	 */
	private static final byte PREV=0;
	private static final byte NEXT=8;
	private static final byte HNEXT=16;
	private static final byte FLUSHTIME=24;
	private static final byte EXPTIME=32;
	private static final byte NBYTES=40;
	private static final byte REFCOUNT=44;
	private static final byte SLABSCLISD=46;
	private static final byte IT_FLAGS=47;
	private static final byte NSUFFIX=48;
	private static final byte NSKEY=49;
	private static final byte CAS=50;
	private static final byte KEY=58;
//	private static final byte SUFFER=0;这两个字段偏移是动态的
//	private static final byte VALUE=0;


	////////////////////////////////// header begin ////////////////////////////////////////////////
	/**
	 * 获取 /记录上一个item的地址,主要用于LRU链和freelist链   
	 * @param item
	 * @return
	 */
	/**
	 * This place had huge bug ,  byte  didn't enough to contain the information the address
	 * This place must be long
	 * @author PigBrother
	 * @param addr
	 * @return
     */
	public static long getPrev(long addr){
		return UnSafeUtil.getLongVolatile(addr+PREV);
	}

	/**
	 * This place had huge bug ,  byte  didn't enough to contain the information the address
	 * This place must be long
	 * @author PigBrother
	 * @param addr
	 * @return
	 */
	public static void setPrev(long addr,long prev){
		//This place used Volatile is not necessary.
		//after codes didn't  use   Volatile
		// setNest()
		UnSafeUtil.putLongVolatile(addr+PREV, prev);
	}
	
	/**
	 * 记录下一个item的地址,主要用于LRU链和freelist链
	 * @param //item
	 * @return
	 */
	public static long getNext(long addr){
		//return UnSafeUtil.getByte(addr+1);
		return UnSafeUtil.getLongVolatile(addr+NEXT);
	}
	/**
	 * This place had huge bug ,  byte  didn't enough to contain the information the address
	 * This place must be long
	 * @author PigBrother
	 * @param addr
	 * @return
	 */
	public static void setNext(long addr,long next){
		///UnSafeUtil.putByte(addr+1, next);   old
		//PigBrother
		UnSafeUtil.putLongVolatile(addr+NEXT, next);
	}
	
	/**
	 * 记录HashTable的下一个Item的地址 
	 * @param item
	 * @return
	 */
	/**
	 * This place had huge bug ,  byte  didn't enough to contain the information the address
	 * This place must be long
	 * @author PigBrother
	 * @param addr
	 * @return
	 */
	public static long getHNext(long addr){
		//return UnSafeUtil.getByte(addr+2);
		return UnSafeUtil.getLongVolatile(addr+HNEXT);
	}
	// fixed setHNext(long addr, long next)
	//PigBrohter
	public static void setHNext(long addr,long hnext){
		UnSafeUtil.putLongVolatile(addr+HNEXT, hnext);
	}
	/**
	          最近访问的时间，只有set/add/replace等操作才会更新这个字段
	 * 当执行flush命令的时候，需要用这个时间和执行flush命令的时间相比较，来判断是否失效  
	 * typedef unsigned int rel_time_t;
	 * Time relative to server start. Smaller than time_t on 64-bit systems. 
	 * @param //item
	 * @return
	 */
	public static long getTime(long addr){
		//PigBrother
		//return UnSafeUtil.getInt(addr+3);
		return UnSafeUtil.getIntVolatile(addr+FLUSHTIME);
	}

	public static void setTime(long addr,long time){
		UnSafeUtil.lazySetLong(addr+FLUSHTIME, time);
	}

	/**
	 * 缓存的过期时间。设置为0的时候，则永久有效。
	 * 如果Memcached不能分配新的item的时候，设置为0的item也有可能被LRU淘汰
	 * typedef unsigned int rel_time_t;
	 * Time relative to server start. Smaller than time_t on 64-bit systems.
	 * @param //item
	 * @return
	 */
	public static long getExpTime(long addr){
		//PigBrother
		//return UnSafeUtil.getLong(addr+7);
		return UnSafeUtil.getLongVolatile(addr+EXPTIME);
	}
	
	public static void setExpTime(long addr,long expTime){
		UnSafeUtil.putLong(addr+EXPTIME, expTime);
	}
	
	/**
	 * value数据大小
	 * @param //item
	 * @return
	 */
	public static int getNbytes(long addr){
		//PigBrother
		//return UnSafeUtil.getInt(addr+11);
		return UnSafeUtil.getIntVolatile(addr+NBYTES);
	}
	
	public static void setNbytes(long addr,int nbytes){
		UnSafeUtil.putIntVolatile(addr+NBYTES,nbytes);
	}
	
	/**
	 * 引用的次数。通过这个引用的次数，可以判断item是否被其它的线程在操作中。
	 * 也可以通过refcount来判断当前的item是否可以被删除，只有refcount -1 = 0的时候才能被删除  
	 * @param //
	 * item
	 * @return
	 */
	public static short getRefCount(long addr){
		//PigBrother
		//return UnSafeUtil.getShort(addr+15);
		return UnSafeUtil.getShortVolatile(addr+REFCOUNT);
	}
	
	/**
	 * which slab class we're in 标记item属于哪个slabclass下
	 * @param //item
	 * @return
	 */
	public static byte getSlabsClsid(long addr){
		//PigBrother
		//return UnSafeUtil.getByte(addr+17);
		return UnSafeUtil.getByteVolatile(addr+SLABSCLISD);
	}
	
	public static void setSlabsClsid(long addr,byte clsid){
		// PigBrother
		// /UnSafeUtil.putByte(addr+17, clsid);
		UnSafeUtil.putByteVolatile(addr+SLABSCLISD, clsid);
	}
	
	/**
	 * ITEM_* above
	 * @param //item
	 * @return
	 */
	public static byte getItflags(long addr){
		// PigBrother
		// 这个memcached里 是一个int  用 byte表示是否够了？
		// /return UnSafeUtil.getByte(addr+18);
		return UnSafeUtil.getByteVolatile(addr+IT_FLAGS);
	}
	
	public static void setItflags(long addr,byte flags){
		// PigBrother
		//UnSafeUtil.putByte(addr+18,flags);
		UnSafeUtil.putByteVolatile(addr+IT_FLAGS,flags);
	}
	
	/**
	 * length of flags-and-length string
	 * @param //item
	 * @return
	 */
	public static byte getNsuffix(long addr){
		// PigBrother
		//return UnSafeUtil.getByte(addr+19);
		return UnSafeUtil.getByteVolatile(addr+NSUFFIX);
	}
	
	
	public static void setNsuffix(long addr,byte nsuffix){
		UnSafeUtil.putByteVolatile(addr+NSUFFIX,nsuffix);
	}
	
	/**
	 * length of flags-and-length string
	 * @param //item
	 * @return
	 */
	public static byte getNskey(long addr){
		// PigBrother
		//return UnSafeUtil.getByte(addr+20);
		return UnSafeUtil.getByteVolatile(addr+NSKEY);
	}
	
	
	public static void setNskey(long addr,byte nkey){
		UnSafeUtil.putByteVolatile(addr+NSKEY,nkey);
	}
	
	public static long getHeaderEnd(long addr){
		return addr + CAS;
	}
	
	////////////////////////////////// header end ////////////////////////////////////////////////
	
	//PigBrother
	/**
	 * length of CAS
	 * @param //item
	 * @return
	 */
	public static long getCAS(long addr){
		return UnSafeUtil.getLongVolatile(getHeaderEnd(addr));
	}
	//PigBrother
	/**
	 * set length of CAS
	 * @param //item
	 * @return
	 */
	public static void setCAS(long CAS, long addr){
		UnSafeUtil.putLongVolatile(getHeaderEnd(addr),CAS);
	}
	
	/**
	 * ((item)->slabs_clsid & ~(3<<6))
	 * @param addr
	 * @return
	 */
	public static long ITEM_clsid(long addr){
		return getSlabsClsid(addr) & ~(3<<6) ;
	}
	
	//PigBrother
	/**
	 * length of key
	 * @param //item
	 * @return
	 */
	public static String getKey(long addr){
		byte[] bs = new byte[getNskey(addr)&0xff];
		for (int i = 0; i < bs.length; i++) {
			bs[i]=UnSafeUtil.getByteVolatile(addr+KEY+i);
		}
		return new String(bs);
	}
	//PigBrother
	/**
	 * set length of CAS
	 * @param //item
	 * @return
	 */
	public static void setKey(byte[] key_bytes, long addr){
		if(key_bytes.length!=(getNskey(addr)&0xff)){
			throw new RuntimeException("Error, NSkey's values != key_bytes.length");
		}
		UnSafeUtil.setBytes(ITEM_key(addr), key_bytes, 0, key_bytes.length);
	}
	
	/**
	 * 设置 suffix
	 * @param addr
	 * @param suffix
	 */
	public static void setSuffix(long addr,byte[] suffix){
		UnSafeUtil.setBytes(ITEM_suffix(addr), suffix, 0, suffix.length);
	}
	
	/**
	 * 获取suffix
	 * @param addr
	 * @return
	 */
	public static byte[] getSuffix(long addr){
		int length = getNsuffix(addr);
		byte[] data = new byte[length];
		UnSafeUtil.getBytes(addr, data, (int)ITEM_suffix(addr),length);
		return data;
	}
	
	/**
	 * 获取value
	 * @param addr
	 * @return
	 */
	public static byte[] getValue(long addr){
		int length = getNbytes(addr);
		byte[] data = new byte[length];
		UnSafeUtil.getBytes(ITEM_data(addr), data,0,length);
		return data;
	}
	
	/**
	 * 设置 value
	 * @param addr
	 * @param value
	 */
	public static void setValue(long addr,byte[] value){
		UnSafeUtil.setBytes(ITEM_data(addr), value, 0, value.length);
	}
	
	/**
	 * Generates the variable-sized part of the header for an object.
	 *
	 * key     - The key
	 * nkey    - The length of the key
	 * flags   - key flags
	 * nbytes  - Number of bytes to hold value and addition CRLF terminator
	 * suffix  - Buffer for the "VALUE" line suffix (flags, size).
	 * nsuffix - The length of the suffix is stored here.
	 *
	 * Returns the total size of the header.
	 */	
	public static int item_make_header(int nkey,int flags,int nbytes){
	    /* suffix is defined at 40 chars elsewhere.. */
//	    *nsuffix = (uint8_t) snprintf(suffix, 40, " %u %d\r\n", flags, nbytes - 2);
//	    return sizeof(item) + nkey + *nsuffix + nbytes;
		
		String suffixStr = item_make_header_suffix(nkey,flags,nbytes);
		return Settings.ITEM_HEADER_LENGTH + nkey + suffixStr.length() + nbytes;
	}
	
	public static int item_make_header(int nkey,int flags,int nbytes,String suffixStr){
		return Settings.ITEM_HEADER_LENGTH + nkey + suffixStr.length() + nbytes;
	}
	
	public static String item_make_header_suffix(int nkey,int flags,int nbytes){
		StringBuffer sb = new StringBuffer();
		sb.append(" ").append(flags).append(" ").append((nbytes-2));
		String suffixStr = sb.toString();
		
		if(suffixStr.length()>40){
			suffixStr = suffixStr.substring(0, 40);
		}
		return suffixStr;
	}
	
	/**
	 * 获取 suffix 中 flags
	 * @param addr
	 * @return
	 */
	public static int ITEM_suffix_flags(long addr){
//		int length = getNsuffix(addr);
//		int nbytes = String.valueOf(getNbytes(addr)).length();
//		length -= (nbytes + 2 + 1);  //减去   /r /r  ''  总共三个字符
//		byte[] flagsBytes = new byte[length];
//
//		UnSafeUtil.getBytes(ITEM_suffix(addr), flagsBytes, 0, length);
		return UnSafeUtil.getByte(ITEM_suffix(addr));
	}

	/**
	 * 获取key 开始地址
	 * ITEM_key(item) (((char*)&((item)->data)) + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @param addr
	 * @return
	 */
	public static long ITEM_key(long addr){
		return getHeaderEnd(addr) + (((getItflags(addr)&ItemFlags.ITEM_CAS.getFlags())==0)?0:8);
	}

	/**
	 * 获取 suffix 开始地址
	 * ITEM_suffix(item) ((char*) &((item)->data) + (item)->nkey + 1 + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * 获取 suffix 开始地址
	 * @param addr
	 * @return
	 */
	public static long ITEM_suffix(long addr){
		return getHeaderEnd(addr) + getNskey(addr) + 1 + (((getItflags(addr)&ItemFlags.ITEM_CAS.getFlags())==0)?0:8);
	}
	
	/**
	 * 获取data 开始地址
	 * ((char*) &((item)->data) + (item)->nkey + 1 + (item)->nsuffix + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @param addr
	 * @return
	 */
	public static long ITEM_data(long addr){
		return getHeaderEnd(addr) + getNskey(addr) + 1 + getNsuffix(addr) + (((getItflags(addr)&ItemFlags.ITEM_CAS.getFlags())==0)?0:8);
	}
	
	public static void ITEM_set_cas(long addr,long cas){
		byte flags = getItflags(addr);
		if((flags &ItemFlags.ITEM_CAS.getFlags())>0){
			setCAS(cas, addr);
		}
	}

	public static long ITEM_get_cas(long addr){
		byte flags = getItflags(addr);
		return (flags &ItemFlags.ITEM_CAS.getFlags())>0?getCAS(addr):0;
	}

	/**
	 * 获取ntotal
	 * (sizeof(struct _stritem) + (item)->nkey + 1 + (item)->nsuffix + (item)->nbytes + (((item)->it_flags & ITEM_CAS) ? sizeof(uint64_t) : 0))
	 * @param addr
	 * @return
	 */
	public static int ITEM_ntotal(long addr){
		//TODO 
		return 0;
	}

	public static void setRefCount(long addr, short value) {
		UnSafeUtil.putShort(addr+REFCOUNT,value);
	}
}
