package io.mycat.jcache.util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * 
 * @author liyanjun
 *
 */
@SuppressWarnings("restriction")
public class UnSafeUtil {
	
	public static final Unsafe unsafe;
	public static final int BYTE_ARRAY_OFFSET;
	/* see Unsafe  */
	public final static int addresssize = 8;
	
	static{
		try { 
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe) f.get(null);
			BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Reads a byte from the specified position.
	 * 
	 * @param pos
	 *            
	 * @return the value read
	 */
	public static byte getByte(long pos) {
		return unsafe.getByte(pos);
	}

	/**
	 * Reads a byte (volatile) from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static byte getByteVolatile(long pos) {
		return unsafe.getByteVolatile(null, pos );
	}

	/**
	 * Reads a short from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static short getShort(long pos) {
		return unsafe.getShort(pos);
	}

	/**
	 * Reads a short (volatile) from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static short getShortVolatile(long pos) {
		return unsafe.getShortVolatile(null, pos);
	}

	/**
	 * Reads an int from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static int getInt(long pos) {
		return unsafe.getInt(pos);
	}

	/**
	 * Reads an int (volatile) from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static int getIntVolatile(long pos) {
		return unsafe.getIntVolatile(null, pos);
	}

	/**
	 * Reads a long from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static long getLong(long pos) {
		return unsafe.getLong(pos);
	}

	/**
	 * Reads a long (volatile) from the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @return the value read
	 */
	public static long getLongVolatile(long pos) {
		return unsafe.getLongVolatile(null, pos);
	}
	
	/**
	 * Writes a byte to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putByte(long pos, byte val) {
		unsafe.putByte(pos, val);
	}

	/**
	 * Writes a byte (volatile) to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putByteVolatile(long pos, byte val) {
		unsafe.putByteVolatile(null, pos, val);
	}

	/**
	 * Writes an int to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putInt(long pos, int val) {
		unsafe.putInt(pos, val);
	}

	/**
	 * Writes an int (volatile) to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putIntVolatile(long pos, int val) {
		unsafe.putIntVolatile(null, pos, val);
	}

	/**
	 * Writes an short to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putShort(long pos, short val) {
		unsafe.putShort(null, pos, val);
	}

	/**
	 * Writes an short (volatile) to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putShortVolatile(long pos, short val) {
		unsafe.putShortVolatile(null, pos, val);
	}

	/**
	 * Writes a long to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putLong(long pos, long val) {
		unsafe.putLong(pos, val);
	}

	/**
	 * Writes a long (volatile) to the specified position.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param val
	 *            the value to write
	 */
	public static void putLongVolatile(long pos, long val) {
		unsafe.putLongVolatile(null, pos, val);

	}

	public static void lazySetLong(long pos, long val) {
		unsafe.putOrderedLong(null, pos, val);
	}

	public static void lazySetInt(long pos, int val) {
		unsafe.putOrderedInt(null, pos, val);
	}

	/**
	 * Reads a buffer of data.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param data
	 *            the input buffer
	 * @param offset
	 *            the offset in the buffer of the first byte to read data into
	 * @param length
	 *            the length of the data
	 */
	public static void getBytes(long pos, byte[] data, int offset, int length) {
		unsafe.copyMemory(null, pos, data, BYTE_ARRAY_OFFSET + offset, length);
	}

	/**
	 * Writes a buffer of data.
	 * 
	 * @param pos
	 *            the position in the memory addrress
	 * @param data
	 *            the output buffer
	 * @param offset
	 *            the offset in the buffer of the first byte to write
	 * @param length
	 *            the length of the data
	 */
	public static void setBytes(long pos, byte[] data, int offset, int length) {
		unsafe.copyMemory(data, BYTE_ARRAY_OFFSET + offset, null, pos, length);
	}

	public static boolean compareAndSwapInt(long pos, int expected, int value) {
		return unsafe.compareAndSwapInt(null, pos, expected, value);
	}

	public static boolean compareAndSwapLong(long pos, long expected, long value) {
		return unsafe.compareAndSwapLong(null, pos, expected, value);
	}

	public static long getAndAddLong(long pos, long delta) {
		return unsafe.getAndAddLong(null, pos, delta);
	}
	
	public static long addAndGetLong(long pos,long delta){
		return unsafe.getAndAddLong(null, pos, delta)+delta;
	}
	
    /**
     * Atomically increments by one the current value.
     *
     * @return the updated value
     */
    public static int incrementAndGetInt(long addr) {
        return unsafe.getAndAddInt(null, addr, 1) + 1;
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the updated value
     */
    public static int decrementAndGetInt(long addr) {
        return unsafe.getAndAddInt(null, addr, -1) - 1;
    }
    
    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public static int addAndGetInt(long addr,int delta) {
        return unsafe.getAndAddInt(null, addr, delta) + delta;
    }
    
    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public static int getAndAddInt(long addr,int delta) {
    	
        return unsafe.getAndAddInt(null, addr, delta);
    }
    
    public static void putAddress(long addr,long value){
    	unsafe.putAddress(addr, value);
    }

	public static void copyMemory(long srcAddr,long dstAddr,long bytes){
		unsafe.copyMemory(srcAddr,dstAddr,bytes);
	}
}
