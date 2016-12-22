package io.mycat.jcache.net;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.Protocol;
import io.mycat.jcache.items.ItemsAccessManager;
import io.mycat.jcache.memory.SlabPool;
import io.mycat.jcache.net.strategy.ReactorSelectEnum;
import io.mycat.jcache.net.strategy.ReactorStrategy;
import io.mycat.jcache.net.strategy.RoundRobinStrategy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * 
 *@author liyanjun
 */
public class JcacheMain 
{
	/**
	 * 主线程 将新连接分派给 reactor 的策略
	 */
	private static Map<ReactorSelectEnum,ReactorStrategy> reactorStrategy = new HashMap<>();
	
    public static void main( String[] args ) throws IOException 
    {    	
    	reactorStrategy.put(ReactorSelectEnum.ROUND_ROBIN, new RoundRobinStrategy());
    	/**
    	 * 后期可能变更为从环境变量获取
    	 */
    	ConfigLoader.loadProperties(null);
    	
    	initGlobalConfig();
    	/** 初始化 内存模块 配置   */
    	initMemoryConfig();

    	int port = ConfigLoader.getIntProperty("port",JcacheGlobalConfig.defaultPort);
    	int poolsize = ConfigLoader.getIntProperty("reactor.pool.size",JcacheGlobalConfig.defaulePoolSize);
    	String bindIp = ConfigLoader.getStringProperty("reactor.pool.bindIp", JcacheGlobalConfig.defaultPoolBindIp);
    	String reaStrategy = ConfigLoader.getStringProperty("reactor.pool.selectStrategy", JcacheGlobalConfig.defaultReactorSelectStrategy);
    	int backlog = ConfigLoader.getIntProperty("acceptor.max_connect_num", JcacheGlobalConfig.defaultMaxAcceptNum);
    	
    	NIOReactorPool reactorPool = new NIOReactorPool(poolsize,reactorStrategy.get(ReactorSelectEnum.valueOf(reaStrategy)));
    	
    	TCPNIOAcceptor acceptor=new TCPNIOAcceptor(bindIp,port, reactorPool,backlog);
		acceptor.start();
    }
    
    /**
     * TODO 配置文件的合并
     * 初始化全局配置，后期可能变更为从环境变量获取
     */
    public static void initGlobalConfig(){
    	String protStr = System.getProperty("B", "negotiating");  // -B 绑定协议 - 可能值：ascii,binary,auto（默认）
    	if("auto".equals(protStr)){
    		protStr = "negotiating";
    	}

    	JcacheGlobalConfig.prot = Protocol.valueOf(protStr);
    }
    
    /**
     * TODO hashtable 初始化配置部分需要优化
     * 初始化内存模块配置
     */
    public static void initMemoryConfig(){
    	SlabPool slabPool = new SlabPool();
    	int unit = 1024*1024;
    	long limit = ConfigLoader.getLongProperty("mem.limit", 64);
    	slabPool.init(limit*unit);
    	JcacheContext.setSlabPool(slabPool);
    	JcacheContext.setItemsAccessManager(new ItemsAccessManager());
    }
}
