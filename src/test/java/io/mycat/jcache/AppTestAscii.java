package io.mycat.jcache;

import org.junit.BeforeClass;
import org.junit.Test;

import com.whalin.MemCached.MemCachedClient;
import com.whalin.MemCached.SockIOPool;

import junit.framework.Assert;

/**
 * Unit test for simple App.
 */
public class AppTestAscii {
	
	MemCachedClient mcc = new MemCachedClient(false);  //true 代表 二进制协议，false 代表 文本协议
	
	@BeforeClass
    public static void setup() throws Exception{
		
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
	 * 运行单元测试   需要先启动    jcache server   入口类        io.mycat.jcache.net.JcacheMain 
	 * 
	 *  
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
		long castToken = mcc.gets(key).casUnique;
		Assert.assertEquals(mcc.cas(key, value,castToken), true);
		Assert.assertEquals(mcc.get(key), value);
		Assert.assertEquals(mcc.append(key, "234"), true);
		Assert.assertEquals(mcc.prepend(key, "34"), true);
		Assert.assertEquals(mcc.incr(key,2l), 34123236);
		Assert.assertEquals(mcc.decr(key,2l), 34123234);
		Assert.assertEquals(mcc.addOrIncr(key,1l), 34123235);
		Assert.assertEquals(mcc.addOrDecr(key,2l), 34123233);
		Assert.assertEquals(mcc.getCounter(key), 34123233);
		Assert.assertEquals(mcc.getMulti(new String[]{key}).get(key), "34123233");
		Assert.assertEquals(mcc.get(key), "34123233");
		Assert.assertEquals(mcc.keyExists(key), true);
		Assert.assertEquals(mcc.delete(key), true);
		Assert.assertNull(mcc.get(key));
		Assert.assertEquals(mcc.keyExists(key), false);
		
		Assert.assertEquals(mcc.set(key, value), true);
		Assert.assertEquals(mcc.get(key), value);
	}
}
