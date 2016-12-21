package io.mycat.jcache.net;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.mycat.jcache.net.strategy.ReactorStrategy;



/**
 * rector 线程池  也是acceptor线程分配 连接给 具体某一个rector 线程的策略上下文
 * @author liyanjun
 */
public class NIOReactorPool {

	private final NIOReactor[] reactors;
	
	private final String rectorname = "rector";
	
	private final ReactorStrategy startegy;
	/**
	 * 多个 reactor 共用一个线程池
	 */
	private final ExecutorService executor=Executors.newCachedThreadPool();
	
	private volatile int lastreactor; // 上一次处理连接的reactor，使用volatile保证多线程操作时内存可见
	
	public NIOReactorPool(int poolSize,ReactorStrategy startegy) throws IOException{
		this.reactors = new NIOReactor[poolSize];
		this.startegy = startegy;
		for (int i = 0; i < poolSize; i++) {
			NIOReactor reactor = new NIOReactor(rectorname + "-" + i,executor);
			reactors[i] = reactor;
			reactor.start();
		}
	}

	/**
	 * 获取下一个处理连接的reactor
	 * @return
	 */
	public NIOReactor getNextReactor(){
		lastreactor = startegy.getNextReactor(reactors,lastreactor);
		return reactors[lastreactor];
	}
}
