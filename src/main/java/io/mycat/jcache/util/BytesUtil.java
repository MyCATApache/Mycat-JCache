package io.mycat.jcache.util;

public class BytesUtil {
	
	public static byte[] LongToBytes(long values) {  
	     byte[] buffer = new byte[8]; 
	     for (int i = 0; i < 8; i++) {   
	          int offset = 64 - (i + 1) * 8;    
	          buffer[i] = (byte) ((values >> offset) & 0xff); 
	      }
	     return buffer;  
	}

	 public static long BytesToLong(byte[] buffer) {   
	    long  values = 0;   
	    for (int i = 0; i < 8; i++) {    
	        values <<= 8; values|= (buffer[i] & 0xff);   
	    }   
	    return values;  
	 }
}
