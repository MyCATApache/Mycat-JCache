package io.mycat.jcache.net.conn.handler.redis.strings;

import io.mycat.jcache.memory.redis.RedisStorage;
import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.redis.AbstractRedisComandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * set 命令处理器
 * @author yangll
 * @create 2017-07-18 22:04
 */

public class RedisSetCommandHandler extends AbstractRedisComandHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedisGetCommandHandler.class);

    @Override
    public void handle(Connection conn, RedisMessage message) {
        logger.debug("set command handler....");
        String key = message.cmdParams()[1];
        Object value = message.cmdParams()[2];

        RedisStorage.getStringStorage().put(key,value);

        message.replay("OK\r\n");
        writeResponseToClient(conn,message);
    }
}
