package io.mycat.jcache.net.conn.handler.redis;

import io.mycat.jcache.memory.redis.RedisStorage;
import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.RedisCommandHandler;
import io.mycat.jcache.net.conn.handler.RedisIOHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * get 命令处理器
 * @author yangll
 * @create 2017-07-18 21:36
 */

public class RedisGetCommandHandler extends AbstractRedisComandHandler {

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);

    @Override
    public void handle(Connection conn,RedisMessage message) {
        logger.debug("handle get command....");
        String[] cmdParams = message.cmdParams();
        String params = cmdParams[1];

        ConcurrentMap<String, Object> strStorage = RedisStorage.getStringStorage();
        if(!strStorage.containsKey(params)){
            message.addNilReply(message);
        }
    }
}
