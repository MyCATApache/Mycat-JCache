package io.mycat.jcache.net.conn.handler.redis;

import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.RedisCommandHandler;
import io.mycat.jcache.net.conn.handler.RedisIOHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @author yangll
 * @create 2017-07-18 22:10
 */

public abstract class AbstractRedisComandHandler implements RedisCommandHandler{

    private static final Logger logger = LoggerFactory.getLogger(AbstractRedisComandHandler.class);

    /**
     * 统一回复client
     * @param conn
     * @param message
     */
    protected  void writeResponseToClient(Connection conn,RedisMessage message){
        if(message.replay()!=null) {
            ByteBuffer writeBuf = ByteBuffer.wrap(message.replay().getBytes());
            writeBuf.compact();
            conn.addWriteQueue(writeBuf);
            conn.enableWrite(true);
        }else{
            if(logger.isWarnEnabled()){
                logger.warn("{} is not reply",message);
            }
        }
    }
}
