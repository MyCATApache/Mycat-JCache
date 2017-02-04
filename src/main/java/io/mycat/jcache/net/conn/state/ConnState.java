package io.mycat.jcache.net.conn.state;

import io.mycat.jcache.net.conn.Connection;

/**
 * 
 * 连接处理状态
 * @author liyanjun
 *
 */
public interface ConnState {
	
	void doWork(Connection conn);

}
