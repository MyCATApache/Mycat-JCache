package io.mycat.jcache.net.conn.handler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.conn.Connection;


public interface IOHandler{
	
    Logger logger = LoggerFactory.getLogger(IOHandler.class);

	default void onClosed(Connection conn,String reason){}

	default void onConnected(Connection conn) throws IOException {
		logger.debug("onConnected(): {}", conn);
	}

	public boolean doReadHandler(Connection conn) throws IOException;

}