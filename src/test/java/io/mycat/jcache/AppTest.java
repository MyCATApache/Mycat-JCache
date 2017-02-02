package io.mycat.jcache;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;

import com.whalin.MemCached.MemCachedClient;
import com.whalin.MemCached.SockIOPool;

import junit.framework.Assert;

/**
 * Unit test for simple App.
 */
public class AppTest {
	
	MemCachedClient mcc = new MemCachedClient(true);  //true 代表 二进制协议，false 代表 文本协议
	
	@BeforeClass
    public static void setup() throws Exception{
		
//		McacheMain.main(null);
		
		
		// 设置缓存服务器列表，当使用分布式缓存的时，可以指定多个缓存服务器。这里应该设置为多个不同的服务，我这里将两个服务设置为一样的，大家不要向我学习，呵呵。
        String[] servers =
                {
                        "127.0.0.1:11211"
                };

        // 设置服务器权重
        Integer[] weights = {3};

        // 创建一个Socked连接池实例
        SockIOPool pool = SockIOPool.getInstance();

      // 向连接池设置服务器和权重
        pool.setServers(servers);
        pool.setWeights(weights);

        // set some TCP settings
        // disable nagle
        // set the read timeout to 3 secs
        // and don't set a connect timeout
        pool.setNagle(false);
        pool.setSocketTO(3000);
        pool.setSocketConnectTO(0);

       // initialize the connection pool
        pool.initialize();
    }
	
	
	/**
	 * 测试lru 需要设置  tailRepairTime参数大于零，或者 item 过期时间,使memcached 可以删掉掉 item.
	 * 否则,达到内存上限时,将不能够再保存新的item
	 */
	@Test
	public void testsetCommand1(){
		String value = "123";
//		for(int i=0;i<3000;i++){
//			value += "p0-['This is a test String1qazxsw23edcvfr45tgbhy6ujm,ki89ol./";
//		}
		String key = "foo0";
		boolean result;
		int j;
		for(j=0;j<1;j++){
			result = mcc.set("foo"+j, value);
	        System.out.println(result+":"+j);
	        Assert.assertEquals(result, true);
		}
		
		System.out.println("get key "+key+" value is "+mcc.get(key));
		System.out.println(mcc.append(key, "234"));
		System.out.println(mcc.prepend(key, "34"));
		System.out.println(mcc.incr(key,2l));
		System.out.println(mcc.decr(key,2l));
		System.out.println(mcc.addOrIncr(key,1l));
		System.out.println(mcc.addOrDecr(key,2l));
		System.out.println(mcc.getCounter(key));
		System.out.println(mcc.getMulti(new String[]{key}));
		System.out.println(mcc.get(key));
		System.out.println(mcc.keyExists(key));
		System.out.println(mcc.delete(key));
		System.out.println(mcc.get(key));
		System.out.println(mcc.keyExists(key));
//		result = mcc.set("foo111"+j, value);
//        System.out.println(result+":"+j);
//        
//        result = mcc.set("foo111"+j, value);
//        System.out.println(result+":"+j);
        
//        mcc.flushAll();
	}
	
//	@Test
	public void testsetCommand(){
		Random ran = new Random();
		List<Thread> threads = new ArrayList<>();
		int teamnum = 10000;
		
		for(int j = 1;j<=10;j++){
			final int k = j;
			Thread thread = new Thread(new Runnable(){

				@Override
				public void run() {
					
					for(int i=teamnum*(k-1);i<teamnum*k;i++){
						boolean result = mcc.set("foo"+i, "This"+i);
						
				        System.out.println(result+":"+i);
				        Assert.assertEquals(result, true);
//				        Object bar = mcc.get("foo"+i);
//				        System.out.println(">>> " + bar);
//				        try {
//							Thread.sleep(ran.nextInt(400));
//						} catch (InterruptedException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
					}
				}
				
			});
			
			thread.start();
			threads.add(thread);
			
		}
		
		for(Thread thread:threads){
			try {
				thread.join();
			} catch (InterruptedException e) {
			}
		}
		System.out.println(mcc.get("foo0"));
	}

    @Test
    public void testAddCommand(){
        boolean result = mcc.set("foo1","add command");
        System.out.println(result);
        Object str = mcc.get("foo1");;
        System.out.println(str);
    }
    
    @Test
    public void getMulti(){
    	 Map<String,Object> bars = mcc.getMulti(new String[]{"foo0","foo1"});
         System.out.println(">>> " + bars);
    }
    
    @Test
    public void teadGets(){
    	 Object bars = mcc.gets("foo1");
         System.out.println(">>> " + bars);
    }
    
    @Test
    public void testgetMultiArray(){
    	 Object bars = mcc.getMultiArray(new String[]{"foo0","foo1"});
         System.out.println(">>> " + bars);
    }
    
    @Test
    public void testdelete(){
		System.out.println(mcc.delete("foo0"));
		System.out.println(mcc.get("foo0"));
    }
}
