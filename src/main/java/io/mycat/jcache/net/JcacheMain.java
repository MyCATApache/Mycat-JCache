package io.mycat.jcache.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.context.StatsState;
import io.mycat.jcache.enums.hash.Hash_func_type;
import io.mycat.jcache.enums.protocol.Protocol;
import io.mycat.jcache.hash.Assoc;
import io.mycat.jcache.hash.Segment;
import io.mycat.jcache.hash.impl.AssocImpl;
import io.mycat.jcache.hash.impl.algorithm.Jenkins_hash;
import io.mycat.jcache.hash.impl.algorithm.Murmur3_32hash;
import io.mycat.jcache.hash.impl.algorithm.Murmur3_64hash;
import io.mycat.jcache.memory.DefaultSlabsImpl;
import io.mycat.jcache.memory.Slabs;
import io.mycat.jcache.net.strategy.ReactorSelectEnum;
import io.mycat.jcache.net.strategy.ReactorStrategy;
import io.mycat.jcache.net.strategy.RoundRobinStrategy;
import io.mycat.jcache.setting.Settings;


/**
 * 
 *@author liyanjun
 */
public class JcacheMain 
{
	private static final Logger logger = LoggerFactory.getLogger(JcacheMain.class);
	
    private static boolean udp_specified = false;
    private static boolean tcp_specified = false;
    private static boolean protocol_specified = false;
    private static boolean start_lru_maintainer = false;
    private static boolean start_lru_crawler = false;
    private static boolean lock_memory = false;
    private static boolean do_daemonize = false;
    private static boolean preallocate = false;
    
    private static Hash_func_type hash_type = Hash_func_type.JENKINS_HASH;
    private static int tocrawl;
    private static int slab_sizes[] = new int[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
    private static boolean use_slab_sizes = false;
    private static String slab_sizes_unparsed = null;
    private static boolean slab_chunk_size_changed = false;
    
    private static int maxcore = 0;
    private static String username;
    private static String pid_file;
    
    static int lru_crawler_initialized = 0;
	/**
	 * 主线程 将新连接分派给 reactor 的策略
	 */
	private static Map<ReactorSelectEnum,ReactorStrategy> reactorStrategy = new HashMap<>();
	
	public static void main( String[] args ) throws IOException 
    {	
    	/**
    	 * reactor模型初始化
    	 */
    	initReactorStrategy();
    	/**
    	 * 后期可能变更为从环境变量获取
    	 */
    	ConfigLoader.loadProperties(null);
    	
        /* lru爬出初始化 */
        init_lru_crawler();
        init_lru_maintainer();
    	
    	/* 获取程序参数 */
        if(args.length > 0){
        	initGlobalConfig(args);
        }
    	
    	initRlim();
    	
	   	 /* Initialize Sasl if -S was specified */
	   	if(Settings.sasl){
	//   		init_sasl(); //TODO 
	   	}
	   	
	   	/* daemonize if requested */
	       /* if we want to ensure our ability to dump core, don't chdir to / */
	   	if(do_daemonize){
	   		//TODO
	   	}
	   	
	   	/* lock paged memory if needed */
	   	if(lock_memory){
	   		//TODO
	   	}
	   	
	   	/* initialize other stuff */
	   	logger_init();
	   	
	   	stats_init();
	   	
	   	inithash();
	   	
	   	assoc_init(Settings.hashPowerInit);

    	/** 初始化 内存模块 配置   */
	   	slabs_init(Settings.maxbytes,Settings.factor,Settings.prealloc,use_slab_sizes?slab_sizes:null);
    	
	   	/* start up worker threads if MT mode */
    	memcached_thread_init(Settings.numThreads);

    	startJcacheServer();
    }
	
	private static void inithash(){
		String jdkBit = System.getProperty("sun.arch.data.model");
		if(Hash_func_type.MURMUR3_HASH.equals(hash_type)){
			if("32".equals(jdkBit)){
				JcacheContext.setHash(new Murmur3_32hash());
			}else if("64".equals(jdkBit)){
				JcacheContext.setHash(new Murmur3_64hash());
			}
		}else if(Hash_func_type.JENKINS_HASH.equals(hash_type)){
			JcacheContext.setHash(new Jenkins_hash());
		}else{
			if(logger.isErrorEnabled()){
        		logger.error("Failed to initialize hash_algorithm!\n");
        	}
			System.exit(1);
		}
	}
	
	private static void stats_init(){
		StatsState.accepting_conns.set(true);  /* assuming we start in this state. */
		Settings.process_started = System.currentTimeMillis();
//		stats_prefix_init();  //TODO
	}
	
	private static void logger_init(){
		//TODO
	}
	
	private static void initRlim(){
		//TODO
	}
    
    private static int init_lru_crawler(){
    	if (lru_crawler_initialized == 0) {
            lru_crawler_initialized = 1;
        }
        return 0;
    }
    
    private static void init_lru_maintainer(){
    	//TODO
    }
    
    
    private static void startJcacheServer() throws IOException {
    	int port = ConfigLoader.getIntProperty("port",Settings.port);
    	int poolsize = ConfigLoader.getIntProperty("reactor.pool.size",JcacheGlobalConfig.defaulePoolSize);
    	String bindIp = ConfigLoader.getStringProperty("reactor.pool.bindIp", JcacheGlobalConfig.defaultPoolBindIp);
    	String reaStrategy = ConfigLoader.getStringProperty("reactor.pool.selectStrategy", JcacheGlobalConfig.defaultReactorSelectStrategy);
    	int backlog = ConfigLoader.getIntProperty("acceptor.max_connect_num", JcacheGlobalConfig.defaultMaxAcceptNum);
    	NIOReactorPool reactorPool = new NIOReactorPool(poolsize,reactorStrategy.get(ReactorSelectEnum.valueOf(reaStrategy)));
    	
    	TCPNIOAcceptor acceptor=new TCPNIOAcceptor(bindIp,port, reactorPool,backlog);
		acceptor.start();
    }
    
    /*
     * Initializes the thread subsystem, creating various worker threads.
     *
     * nthreads  Number of worker event handler threads to spawn
     */
    private static void memcached_thread_init(int nthreads){
    	int i;
    	int power;
   	
        if (nthreads < 3) {
            power = 10;
        } else if (nthreads < 4) {
            power = 11;
        } else if (nthreads < 5) {
            power = 12;
        } else {
            /* 8192 buckets, and central locks don't scale much past 5 threads */
            power = 13;
        }
        
        if (power >= Settings.hashPowerInit) {
        	if(logger.isErrorEnabled()){
        		logger.error("Hash table power size {} cannot be equal to or less than item lock table {}", Settings.hashPowerInit, power);
        		logger.error("Item lock table grows with `-t N` (worker threadcount)");
        		logger.error("Hash table grows with `-o hashpower=N` \n");
        	}
            System.exit(0);
        }
        
        Segment segment = new Segment(power);
        JcacheContext.setSegment(segment);
        JcacheContext.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }
    
    /**
     * 初始化hashtable
     */
    private static void assoc_init(int hashpower_init) {    	
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
     */
    public static void initGlobalConfig(String[] args){
    	
    	StringBuffer sb = new StringBuffer();
    	for(int i = 0; i < args.length; i++){
    	 sb.append(args[i]).append(" ");
    	}
    	String tmpparams = sb.toString();
    	
    	String[] commandLineparams = tmpparams.split("-");
    	
    	IntStream.range(0, commandLineparams.length).forEach(i->{
    		String[] params = commandLineparams[i].split("\\s+");
    		switch (params[0]) {
    		case "":
    			break;
    		case "A": /* enable admin shutdown commannd  是否运行客户端使用shutdown命令。默认是不允许的 */
    			Settings.shutdownCommand = true;
    			break;
    		case "a": /* access mask for unix socket ,unix socket的权限位信息(访问掩码)。*/
    			Settings.shutdownCommand = true;
    			break;
    		case "U": /* UDP port number to listen on  memcached监听的UDP端口值，默认端口为11211*/
    			Settings.udpport = Integer.parseInt(params[1]);
        		udp_specified = true;
    			break;
    		case "p": /* save PID in file memcached监听的tcp端口。默认端口为11211 */
    			Settings.port = Integer.parseInt(params[1]);
        		tcp_specified = true;
    			break;
    		case "s": /* unix socket path to listen on unix socket监听的socket路径*/
    			Settings.socketPath = params[1];
    			break;
    		case "m": /* max memory to use for items in megabytes */
    			Settings.maxbytes = Long.parseLong(params[1])*1024*1024;
    			break;
    		case "M": /* return error on memory exhausted 
    			                               默认情况下，
    			                               当memcached的内存使用完后，
    			                               将进行LRU机制淘汰item以腾出空间。
    			                               如果使用本选项那么将关闭LRU功能。
    			                               当然关闭LRU不代表不能存储新数据。
    			                               如果memcached里面存有过期失效的item，
    			                               那么就可以存储新数据。否则将无法存储。
    		 		   */
    			Settings.evictToFree = 0;
    			break;
    		case "c": /* max simultaneous connections 
    					  最多允许多少个客户端同时在线(这个值不等价于listen函数的第二个参数)，
    					  该选项和后面的b选项有所不同。 默认值为1024个
    		          */
    			Settings.maxConns = Integer.parseInt(params[1]);
        		if(Settings.maxConns <= 0){
        			if(logger.isErrorEnabled()){
                		logger.error("Maximum connections must be greater than 0\n");
                	}
        			System.exit(1);
        		}
    			break;
    		case "h": /* 显示帮助信息 */
    			break;
    		case "i":  /* 显示memcached和libevent的版权信息 */
    			break;
    		case "V": 
    			break;
    		case "k": /* lock down all paged memory 
    					   将memcached使用到的内存锁定在内存中，
    					   不准OS把memcached的内存移动到虚拟内存。
    					   因为当OS把memcached的内存移动到虚拟内存可能会导致页错误，
    					   降低memcached的响应时间
    				  */
    			lock_memory = true;
    			break;
    		case "v":  /* verbose */
    			Settings.verbose++;
    			break;
    		case "l":
    			/*
    			 * memcached绑定的ip地址。如果不设置这个选项，那么memcached将使用INADDR_ANY。
    			 * 如果想指定多个IP地址，那么该选项的参数可以由多个ip组成，
    			 * ip之间用逗号分隔。也可以多次使用这个选项，
    			 * 此时端口应该尾随ip而不是单独用-p选项指定。
    			 * 例如-l 127.0.0.1:8888,192.168.1.112:9999 或者 -l 127.0.0.1:8888 -l 192.168.1.112:9999
    			 */
    			Settings.inter = params[1];
    			break;
    		case "d": /* 以守护进程的形式运行memcached*/
    			do_daemonize = true;
    			break;
    		case "r": /* 将core文件大小设置为不受限制 */
    			maxcore = 1;
    			break;
    		case "R": /* worker线程连续为某个客户端执行命令的最大命令数。 */
    			Settings.reqsPerEvent = Integer.parseInt(params[1]);
        		if(Settings.reqsPerEvent==0){
        			if(logger.isErrorEnabled()){
                		logger.error("Number of requests per event must be greater than 0\n");
                	}
        			System.exit(1);
        		}
    			break;
    		case "u": /* user identity to run as 当以root用户启动memcached的时候需要指定memcached的所属用户，其他用户启动memcached不需要此选项*/
    			username = params[1];
    			break;
    		case "P": /* 该选项的参数指明memcached的pid保存文件。要和-d选项配合使用。注意运行的用户是否有权限写对应的文件 */
    			pid_file = params[1];
    			break;
    		case "f": /* factor?  item的扩容因子。默认值为1.25。该选项的参数值可以是小数但必须大于1.0。 */
    			Settings.factor = Double.parseDouble(params[1]);
        		if(Settings.factor<=1.0){
        			if(logger.isErrorEnabled()){
                		logger.error("Factor must be greater than 1\n");
                	}
        			System.exit(1);
        		}
    			break;
    		case "n": /* minimum space allocated for key+value+flags 设置最小的item能存储多少字节的数据。 */
    			Settings.chunkSize = Integer.parseInt(params[1]);
        		if(Settings.chunkSize==0){
        			if(logger.isErrorEnabled()){
                		logger.error("Chunk size must be greater than 0\n");
                	}
        			System.exit(1);
        		}
    			break;
    		case "t": /* 该选项的参数用于指定worker线程的个数，不建议超过64个。如果不设置该选项默认有4个线程。 */
    			Settings.numThreads = Integer.parseInt(params[1]);
        		if(Settings.numThreads<=0){
        			if(logger.isErrorEnabled()){
                		logger.error("Number of threads must be greater than 0\n");
                	}
        			System.exit(1);
        		}
        		
        		if(Settings.numThreads>64){
        			if(logger.isWarnEnabled()){
                		logger.warn("WARNING: Setting a high number of worker threads is not recommended.\n"
                				+ " Set this value to the number of cores in  your machine or less.\n");
                	}
        		}
    			break;
    		case "D":
    			if(params.length==1){
    				if(logger.isErrorEnabled()){
                		logger.error("No delimiter specified\n");
                	}
    				System.exit(1);
    			}
    			Settings.prefixDelimiter=params[1];
        		Settings.detailEnabled = true;
    			break;
    		case "L":
    			preallocate = true;
    			break;
    		case "C": /* memcached默认是使用CAS的，本选项是禁用CAS */
    			Settings.useCas = false;
    			break;
    		case "B": /*
    		          memcached支持文本协议和二进制协议。该选项的参数用于指定使用的协议。
    		                                   默认情况下是根据客户端的命令而自动判断(也叫协商)，
    		                                   参数只能取auto、binary、ascii这三个字符串值
    		          */
    			protocol_specified = true;
    			if("auto".equals(params[1])){
    	    		Settings.binding_protocol = Protocol.negotiating;
    	    	}else if("binary".equals(params[1])){
    	    		Settings.binding_protocol = Protocol.binary;
    	    	}else if("ascii".equals(params[1])){
    	    		Settings.binding_protocol = Protocol.ascii;
    	    	}else{
    	    		if(logger.isErrorEnabled()){
                		logger.error("Invalid value for binding protocol: {} \n"
                				+ " -- should be one of auto, binary, or ascii\n",params[1]);
                	}
    				System.exit(1);
    	    	}
    			break;
    		case "I":
    			String value = params[1];
    			String unit = params[1].substring(params[1].length()-1);
    			if (unit == "k" || unit == "m" ||
    	                unit == "K" || unit == "M") {
					value = params[1].substring(0, params[1].length()-1);
					Settings.itemSizeMax = Integer.parseInt(value);
	                if (unit == "k" || unit == "K")
	                	Settings.itemSizeMax *= 1024;
	                if (unit == "m" || unit == "M")
	                	Settings.itemSizeMax *= 1024 * 1024;
	            } else {
	            	Settings.itemSizeMax = Integer.parseInt(params[1]);
	            }
    			
    			if(Settings.itemSizeMax < 1024){
    				if(logger.isErrorEnabled()){
                		logger.error("Item max size cannot be less than 1024 bytes.\n");
                	}
    				System.exit(1);
    			}
    			
    			if(Settings.itemSizeMax > Settings.maxbytes/4){
    				if(logger.isErrorEnabled()){
                		logger.error("Cannot set item size limit higher than 1/4 of memory max.\n");
                	}
    				System.exit(1);
    			}
    			
    			if(Settings.itemSizeMax > (1024 * 1024 * 1024)){
    				if(logger.isErrorEnabled()){
                		logger.error("Cannot set item size limit higher than a gigabyte.\n");
                	}
    				System.exit(1);
    			}
    			
    			if(Settings.itemSizeMax > (1024 * 1024)){
    				if(!slab_chunk_size_changed){
    					// Ideal new default is 16k, but needs stitching.
    					Settings.slabChunkSizeMax = 524288;
    				}
    			}
    			break;
    		case "S":  /* set Sasl authentication to true. Default is false */
    			break;
    		case "F":  /* 禁止客户端的flush_all命令。默认是允许客户端的flush_all命令的 */
    			Settings.flushEnabled = false;
    			break;
    		case "o":  /* /* It's sub-opts time! */
//    			[MAXCONNS_FAST] = "maxconns_fast",
//    	        [HASHPOWER_INIT] = "hashpower",
//    	        [SLAB_REASSIGN] = "slab_reassign",
//    	        [SLAB_AUTOMOVE] = "slab_automove",
//    	        [TAIL_REPAIR_TIME] = "tail_repair_time",
//    	        [HASH_ALGORITHM] = "hash_algorithm",
//    	        [LRU_CRAWLER] = "lru_crawler",
//    	        [LRU_CRAWLER_SLEEP] = "lru_crawler_sleep",
//    	        [LRU_CRAWLER_TOCRAWL] = "lru_crawler_tocrawl",
//    	        [LRU_MAINTAINER] = "lru_maintainer",
//    	        [HOT_LRU_PCT] = "hot_lru_pct",
//    	        [WARM_LRU_PCT] = "warm_lru_pct",
//    	        [NOEXP_NOEVICT] = "expirezero_does_not_evict",
//    	        [IDLE_TIMEOUT] = "idle_timeout",
//    	        [WATCHER_LOGBUF_SIZE] = "watcher_logbuf_size",
//    	        [WORKER_LOGBUF_SIZE] = "worker_logbuf_size",
//    	        [SLAB_SIZES] = "slab_sizes",
//    	        [SLAB_CHUNK_MAX] = "slab_chunk_max",
//    	        [TRACK_SIZES] = "track_sizes",
//    	        [MODERN] = "modern",
    			/*
    			 * maxconns_fast:   如果连接数超过了最大同时在线数(由-c选项指定)，立即关闭新连接上的客户端。该选项将settings.maxconns_fast赋值为true
    			 * hashpower:   	哈希表的长度是2^n。可以通过选项hashpower设置指数n的初始值。如果不设置将取默认值16。
    			 * 					该选项必须有参数，参数取值范围只能为[12, 64]。
    			 * 					本选项参数值赋值给settings.hashpower_init
    			 * slab_reassign:   该选项没有参数。用于调节不同类型的item所占的内存。不同类型是指大小不同。
    			 * 					某一类item已经很少使用了，但仍占用着内存。
    			 * 					可以通过开启slab_reassign调度内存，
    			 * 					减少这一类item的内存。如果使用了本选项，
    			 * 					settings.slab_reassign赋值为true
    			 * slab_automove:   依赖于slab_reassign。用于主动检测是否需要进行内存调度。
    			 * 					该选项的参数是可选的。参数的取值范围只能为0、1、2。参数2是不建议的。
    			 * 					本选项参数赋值给settings.slab_automove。
    			 * 					如果本选项没有参数，那么settings.slab_automove赋值为1
    			 * hash_algorithm:   用于指定哈希算法。该选项必须带有参数。并且参数只能是字符串jenkins或者murmur3
    			 * tail_repair_time:   用于检测是否有item被已死线程所引用。
    			 * 					          一般不会出现这种情况，所以默认不开启这种检测。
    			 * 					          如果需要开启这种检测，那么需要使用本选项。本选项需要一个参数，参数值必须不小于10。该参数赋值给settings.tail_repair_time
    			 * lru_crawler:        本选项用于启动LRU爬虫线程。该选项不需要参数。本选项会导致settings.lru_crawler赋值为true
    			 * lru_crawler_sleep:  LRU爬虫线程工作时的休眠间隔。本选项需要一个参数作为休眠时间，单位为微秒，取值范围是[0, 1000000]。该参数赋值给settings.lru_crawler_sleep
    			 * lru_crawler_tocrawl:   LRU爬虫检查每条LRU队列中的多少个item。该选项带有一个参数。参数会赋值给settings.lru_crawler_tocrawl
    			 */
    			String[] subparams = params[1].split(",");
    			IntStream.range(0, subparams.length).forEach(f->{
    				String[] ops = subparams[f].split("=");
    				switch (ops[0]) {
					case "maxconns_fast":
						Settings.maxConnsFast = true;
						break;
					case "hashpower":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing numeric argument for hashpower\n");
		                	}
		    				System.exit(1);
						}
						Settings.hashPowerInit = Integer.parseInt(ops[1]);
						if(Settings.hashPowerInit <12){
							if(logger.isErrorEnabled()){
		                		logger.error("Initial hashtable multiplier of {}  is too low\n",Settings.hashPowerInit);
		                	}
		    				System.exit(1);
						}else if(Settings.hashPowerInit > 64){
							if(logger.isErrorEnabled()){
		                		logger.error("Initial hashtable multiplier of {} is too high\n"
		                				+ "Choose a value based on \"STAT hash_power_level\" from a running instance\n",Settings.hashPowerInit);
		                	}
		    				System.exit(1);
						}
						break;
					case "slab_reassign":
						Settings.slabReassign= true;
						break;
					case "slab_automove":
						if(ops.length==1){
							Settings.slabAutoMove = 1;
							break;
						}
						
						Settings.slabAutoMove = (short)Integer.parseInt(ops[1]);
						if(Settings.slabAutoMove < 0 || Settings.slabAutoMove > 2){
							if(logger.isErrorEnabled()){
		                		logger.error("slab_automove must be between 0 and 2\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "tail_repair_time":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing numeric argument for tail_repair_time\n");
		                	}
		    				System.exit(1);
						}
						Settings.tailRepairTime = Long.parseLong(ops[1]);
						if(Settings.tailRepairTime < 10){
							if(logger.isErrorEnabled()){
		                		logger.error("Cannot set tail_repair_time to less than 10 seconds\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "hash_algorithm":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing hash_algorithm argument\n");
		                	}
		    				System.exit(1);
						}
						if(Hash_func_type.JENKINS_HASH.name().equalsIgnoreCase(ops[1])){
							hash_type = Hash_func_type.JENKINS_HASH;
						}else if (Hash_func_type.MURMUR3_HASH.name().equalsIgnoreCase(ops[1])){
							hash_type = Hash_func_type.MURMUR3_HASH;
						}else{
							if(logger.isErrorEnabled()){
		                		logger.error("Unknown hash_algorithm option (jenkins, murmur3)\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "lru_crawler":
						start_lru_crawler = true;
						break;
					case "lru_crawler_sleep":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing lru_crawler_sleep value\n");
		                	}
		    				System.exit(1);
						}
						Settings.lruCrawlerSleep = Integer.parseInt(ops[1]);
						if(Settings.lruCrawlerSleep > 1000000||Settings.lruCrawlerSleep < 0){
							if(logger.isErrorEnabled()){
		                		logger.error("LRU crawler sleep must be between 0 and 1 second\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "lru_crawler_tocrawl":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing lru_crawler_tocrawl value\n");
		                	}
		    				System.exit(1);
						}
						Settings.lruCrawlerTocrawl = Integer.parseInt(ops[1]);
						break;
					case "lru_maintainer":
						start_lru_maintainer = true;
						break;
					case "hot_lru_pct":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing hot_lru_pct argument\n");
		                	}
		    				System.exit(1);
						}
						Settings.hotLruPct = Integer.parseInt(ops[1]);
						if(Settings.hotLruPct < 1 || Settings.hotLruPct >= 80){
							if(logger.isErrorEnabled()){
		                		logger.error("hot_lru_pct must be > 1 and < 80\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "warm_lru_pct":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing warm_lru_pct argument\n");
		                	}
		    				System.exit(1);
						}
						Settings.warmLruPct = Integer.parseInt(ops[1]);
						if(Settings.warmLruPct < 1 || Settings.warmLruPct >= 80){
							if(logger.isErrorEnabled()){
		                		logger.error("warm_lru_pct must be > 1 and < 80\n");
		                	}
		    				System.exit(1);
						}
						break;
					case "expirezero_does_not_evict":
						Settings.expireZeroDoesNotEvict = true;
						break;
					case "idle_timeout":
						Settings.idleTimeout = Integer.parseInt(ops[1]);
						break;
					case "watcher_logbuf_size":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing watcher_logbuf_size argument\n");
		                	}
		    				System.exit(1);
						}

						Settings.loggerWatcherBufSize *=1024;
						break;
					case "worker_logbuf_size":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing worker_logbuf_size argument\n");
		                	}
		    				System.exit(1);
						}
						Settings.loggerBufSize *=1024;
						break;
					case "slab_sizes":
						slab_sizes_unparsed = ops[1];
						break;
					case "slab_chunk_max":
						if(ops.length==1){
							if(logger.isErrorEnabled()){
		                		logger.error("Missing slab_chunk_max argument\n");
		                	}
		    				System.exit(1);
						}
						if(Long.parseLong(ops[1])>Integer.MAX_VALUE){
							if(logger.isErrorEnabled()){
		                		logger.error("could not parse argument to slab_chunk_max\n");
		                	}
						}
						slab_chunk_size_changed = true;
						break;
					case "track_sizes":
						JcacheContext.getItemsAccessManager().item_stats_sizes_init();
						break;
					case "modern":
						/* Modernized defaults. Need to add equivalent no_* flags
		                 * before making truly default. */
		                // chunk default should come after stitching is fixed.
		                //settings.slab_chunk_size_max = 16384;

		                // With slab_ressign, pages are always 1MB, so anything larger
		                // than .5m ends up using 1m anyway. With this we at least
		                // avoid having several slab classes that use 1m.
						if(!slab_chunk_size_changed){
							Settings.slabChunkSizeMax = 524288;
						}
						Settings.slabReassign = true;
						Settings.slabAutoMove = 1;
						Settings.maxConnsFast = true;
						hash_type = Hash_func_type.MURMUR3_HASH;
						start_lru_crawler = true;
						start_lru_maintainer = true;
						break;
					default:
						if(logger.isErrorEnabled()){
	                		logger.error("Illegal suboption \"{}\"\n",ops[1]);
	                	}
	    				System.exit(1);
					}
    			});
    			
    			break;
    		default:
    			if(logger.isErrorEnabled()){
            		logger.error("Illegal argument \"%c\"\n",params[0]);
            	}
				System.exit(1);
    		}
    	});
    
    	
    	if(Settings.slabChunkSizeMax > Settings.itemSizeMax){
    		if(logger.isErrorEnabled()){
        		logger.error("slab_chunk_max (bytes: {}) cannot be larger than -I (item_size_max {})\n",Settings.slabChunkSizeMax,Settings.itemSizeMax);
        	}
			System.exit(1);
    	}
    	
    	if(Settings.itemSizeMax % Settings.slabChunkSizeMax!=0){
    		if(logger.isErrorEnabled()){
        		logger.error("-I (item_size_max: %d) must be evenly divisible by slab_chunk_max (bytes: %d)\n",Settings.itemSizeMax,Settings.slabChunkSizeMax);
        	}
			System.exit(1);
    	}
    	
    	if(Settings.slabPageSize % Settings.slabChunkSizeMax!=0){
    		if(logger.isErrorEnabled()){
        		logger.error("slab_chunk_max (bytes: %d) must divide evenly into %d (slab_page_size)\n",Settings.slabChunkSizeMax,Settings.slabPageSize);
        	}
			System.exit(1);
    	}
    	
    	if(slab_sizes_unparsed !=null){
    		if(_parse_slab_sizes(slab_sizes_unparsed,slab_sizes)){
    			use_slab_sizes = true;
    		}else{
    			System.exit(1);
    		}
    	}
    	
    	if(Settings.lruMaintainerThread&&Settings.hotLruPct + Settings.warmLruPct > 80){
    		if(logger.isErrorEnabled()){
        		logger.error("hot_lru_pct + warm_lru_pct cannot be more than 80%% combined\n");
        	}
			System.exit(1);
    	}
    	
    	if(Settings.inter!=null&&Settings.inter.split(",").length>0){
    		Settings.numThreadsPerUdp = 1;
    	}else{
    		Settings.numThreadsPerUdp = Settings.numThreads;
    	}
    	
    	if(Settings.sasl){
    		if(!protocol_specified){
    			Settings.binding_protocol = Protocol.binary;
    		}else{
    			if(Settings.binding_protocol != Protocol.binary){
    				if(logger.isErrorEnabled()){
    	        		logger.error("ERROR: You cannot allow the ASCII protocol while using SASL.\n");
    	        	}
    				System.exit(1);
    			}
    		}
    	}
    	
    	if(tcp_specified&&!udp_specified){
    		Settings.udpport = Settings.port;
    	}else if(udp_specified && !tcp_specified){
    		Settings.port = Settings.udpport;
    	}
    }
    
    private static boolean _parse_slab_sizes(String slab_sizes_unparsed,int[] slab_sizes){
    	int size = 0;
    	int i = 0;
    	int last_size = 0;
    	if(slab_sizes_unparsed.length()<1){
    		return false;
    	}
    	String[] ps = slab_sizes_unparsed.split("-");
    	for(int f=0;f<ps.length;f++){
    		if(Long.parseLong(ps[f])>Integer.MAX_VALUE
    				||(size = Integer.parseInt(ps[f]))< Settings.chunkSize
    				||size > Settings.slabChunkSizeMax){
    			if(logger.isErrorEnabled()){
            		logger.error("slab size {} is out of valid range\n",size);
            	}
    			return false;
    		}
    		
    		if(last_size >= size){
    			if(logger.isErrorEnabled()){
            		logger.error("slab size {} cannot be lower than or equal to a previous class size\n",size);
            	}
    			return false;
    		}
    		
    		if(size <= last_size + Settings.CHUNK_ALIGN_BYTES){
    			if(logger.isErrorEnabled()){
            		logger.error("slab size {} must be at least {} bytes larger than previous class\n",size,Settings.CHUNK_ALIGN_BYTES);
            	}
    			return false;
    		}
    		
    		slab_sizes[i++] = size;
    		last_size = size;
    		if(i>= Settings.MAX_NUMBER_OF_SLAB_CLASSES-1){
    			if(logger.isErrorEnabled()){
            		logger.error("too many slab classes specified\n");
            	}
    			return false;
    		}
    	}
    	
    	slab_sizes[i] = 0;
    	return true;
    }
    
    /**
     * 初始化内存模块配置
     */
    public static void slabs_init(long maxbytes,double factor,boolean preallocate,int[] slab_sizes){
    	if(logger.isDebugEnabled()){
    		logger.debug("start slabs_init.....");
    	}
		Slabs slabs = new DefaultSlabsImpl();
		slabs.slabs_init(maxbytes, factor, preallocate, slab_sizes);
    	JcacheContext.setSlabPool(slabs);
    }
}
