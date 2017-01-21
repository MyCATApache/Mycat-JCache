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

	public void doReadHandler(Connection conn) throws IOException;
	
//	logger.debug("handleReadEvent(): {}", this);
//    final ByteBuffer buffer = conn.getReadDataBuffer();
//
//    if(Protocol.negotiating.equals(McacheGlobalConfig.prot)){
//    	byte magic = buffer.get(0);
//    	if((magic & 0xff)==(BinaryProtocol.MAGIC_REQ & 0xff)){
//    		conn.setProtocol(Protocol.binary);
//    	}else{
//    		conn.setProtocol(Protocol.ascii);
//    	}
//    } 
//    if(Protocol.binary.equals(conn.getProtocol())){ // 如果是二进制协议
//    	binaryHandler(conn,buffer);
//    }else{  //如果是文本协议
//    	asciiHandler(conn,buffer);
//    }

}