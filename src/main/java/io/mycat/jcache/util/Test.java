package io.mycat.jcache.util;

import io.mycat.jcache.context.JcacheContext;

/**
 * 
 * @author liyanjun
 *
 */
public class Test {
	
	private static final long _1G = 1024 * 1024 * 1024; 
	
    public static void main(String[] args) {
    	int hv = 0B10101011;
    	System.out.println(hv%0B0111);
    	System.out.println(hv&0B0111);
    }
}  