package io.mycat.jcache.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.conn.Connection;


/**
 * 
 * @author liyanjun
 */
public final class NIOReactor extends Thread{
	private static final Logger logger = LoggerFactory.getLogger(NIOReactor.class);
	
	private final Selector selector;

	private final LinkedTransferQueue<Connection> registerQueue;
	
	final ExecutorService executor;

	public NIOReactor(String name,ExecutorService executorService) throws IOException {
		super.setName(name);
		this.selector = Selector.open();
		this.registerQueue = new LinkedTransferQueue<>();  // 这里不使用 ConcurrentLinkedQueue 的原因在于,可能acceptor 和reactor同时操作队列
		this.executor = executorService;
	}
	
	/**
	 * 将新的连接请求 放到 reactor 的请求队列中，同时唤醒 reactor selector
	 * @param socketChannel
	 */
	final void registerNewClient(Connection conn) {
		registerQueue.offer(conn);
		selector.wakeup();
	}
	
	@Override
	public void run() {
		final Selector selector = this.selector;
		Set<SelectionKey> keys = null;
		int readys=0;
		for (;;) {
			try {
//				400/(readys+1)
				readys=selector.select();  //借鉴mycat-core
				if(readys==0) // 没有需要处理的事件时，处理新连接请求  注册 read 事件
				{
					handlerEvents(selector);  
					continue;
				}
				keys = selector.selectedKeys();
				for(SelectionKey key:keys)
				{
					Connection con = (Connection)key.attachment();
					logger.debug("select-key-readyOps = {}, attachment = {}", key.readyOps(), con);
					this.executor.execute(con);
//					con.run();
				}
			} catch (Throwable e) {
				logger.warn(getName(), e);
			} finally {
				if (keys != null) {
					keys.clear();
				}
			}
			handlerEvents(selector); //处理完成事件后,处理新里连接请求 注册 read 事件
		}
	}

//	private void processEvents() {
// TODO 
//		if(events.isEmpty())
//		{
//			return;
//		}
//		Object[] objs=events.toArray();
//		if(objs.length>0)
//		{
//		for(Object obj:objs)
//		{
//			((Runnable)obj).run();
//		}
//		events.removeAll(Arrays.asList(objs));
//		}		
//	}

	private void handlerEvents(Selector selector)
	{
		try
		{
		register(selector);  //注册 selector  读写事件
		}catch(Exception e)
		{
			logger.warn("caught user event err:",e);
		}
	}
	
	/**
	 * 注册 io 读写事件
	 * @param selector
	 */
	private void register(Selector selector) {
		if (registerQueue.isEmpty()) {
			return;
		}
		Connection c = null;
		while ((c = registerQueue.poll()) != null) {
			try {
				c.register(selector);
			} catch (Throwable e) {
				logger.warn("register error ", e);
				c.close("register err");
			}
		}
	}
}