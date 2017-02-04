package io.mycat.jcache.enums.protocol.binary;

/**
 * 二进制协议
 * 
 * @author liyanjun
 *
 */
public final class BinaryProtocol {
	
    public final static int memcache_packetHeaderSize = 24;  //二进制协议头 长度固定为 24字节
    
	public final byte[] BLAND_DATA_SIZE = "       ".getBytes();
	public static final int MARKER_BYTE = 1;
	public static final int MARKER_BOOLEAN = 8192;
	public static final int MARKER_INTEGER = 4;
	public static final int MARKER_LONG = 16384;
	public static final int MARKER_CHARACTER = 16;
	public static final int MARKER_STRING = 32;
	public static final int MARKER_STRINGBUFFER = 64;
	public static final int MARKER_FLOAT = 128;
	public static final int MARKER_SHORT = 256;
	public static final int MARKER_DOUBLE = 512;
	public static final int MARKER_DATE = 1024;
	public static final int MARKER_STRINGBUILDER = 2048;
	public static final int MARKER_BYTEARR = 4096;
	public static final int MARKER_OTHERS = 0;	
}
