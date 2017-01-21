package io.mycat.jcache.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.Protocol;
import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.hash.Hash;
import io.mycat.jcache.hash.Hash_func_type;
import io.mycat.jcache.hash.impl.AssocImpl;
import io.mycat.jcache.hash.impl.HashImpl;
import io.mycat.jcache.items.ItemsAccessManager;
import io.mycat.jcache.memory.SlabPool;
import io.mycat.jcache.net.strategy.ReactorSelectEnum;
import io.mycat.jcache.net.strategy.ReactorStrategy;
import io.mycat.jcache.net.strategy.RoundRobinStrategy;
import io.mycat.jcache.setting.Settings;
import sun.misc.VM;


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
    	
    	initReactorStrategy();
    	/**
    	 * 后期可能变更为从环境变量获取
    	 */
    	ConfigLoader.loadProperties(null);
    	
    	initGlobalConfig();

    	/** 初始化 内存模块 配置   */
    	initMemoryConfig();
    	
    	assoc_init(Settings.hashPowerInit);

    	startJcacheServer();
    }
    
    private static void startJcacheServer() throws IOException {
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
     * 初始化hashtable  TODO 待hashtable 完善后,完成此部分初始化,目前hashtable 设定为固定值
     */
    private static void assoc_init(int hashpower_init) {
    	Hash hash = new HashImpl(Hash_func_type.JENKINS_HASH);
    	JcacheContext.setHash(hash);
    	
    	Assoc assoc = new AssocImpl();
    	assoc.assoc_init(hashpower_init);
    	JcacheContext.setAssoc(assoc);

	}

	/**
     * 初始化reactorStrategy
     */
    private static void initReactorStrategy(){
    	reactorStrategy.put(ReactorSelectEnum.ROUND_ROBIN, new RoundRobinStrategy());
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
    	String defaultMapfile = ConfigLoader.class.getClassLoader().getResource("").getPath()+"mapfile";
    	Settings.mapfile = ConfigLoader.getStringProperty("memory.mapfile", defaultMapfile);
    }
    
    /**
     * TODO hashtable 初始化配置部分需要优化
     * 初始化内存模块配置
     */
    @SuppressWarnings("restriction")
    public static void initMemoryConfig(){
		long limit = VM.maxDirectMemory();
//		long hashsize = (long) (limit*0.2); 
//		Settings.hashsize = hashsize > (Integer.MAX_VALUE-1)?(Integer.MAX_VALUE-1):(int)hashsize;
		SlabPool slabPool = new SlabPool(limit,Settings.mapfile);
    	JcacheContext.setSlabPool(slabPool);
    	JcacheContext.setItemsAccessManager(new ItemsAccessManager());
    }
}
