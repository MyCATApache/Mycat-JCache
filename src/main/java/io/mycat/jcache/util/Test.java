package io.mycat.jcache.util;

/**
 * 
 * @author liyanjun
 *
 */
public class Test {
	
	private static final long _1G = 1024 * 1024 * 1024; 
	
    public static void main(String[] args) {
    	Long aa = UnSafeUtil.unsafe.allocateMemory(4);
    	System.out.println(Long.toBinaryString(aa));
    }
}  