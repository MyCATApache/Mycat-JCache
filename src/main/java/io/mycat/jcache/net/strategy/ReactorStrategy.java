package io.mycat.jcache.net.strategy;

import io.mycat.jcache.net.NIOReactor;

/**
 * acceptor 线程分派连接给rector 线程的策略
 * @author liyanjun
 *
 */
public interface ReactorStrategy {

	/**
	 * 获取下一个reactor
	 * @param reactors
	 * @return
	 */
	public int getNextReactor(NIOReactor[] reactors,int lastreactor);
	
}
