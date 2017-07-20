package io.mycat.jcache.net.conn.handler.redis.key;

import io.mycat.jcache.memory.redis.RedisStorage;
import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.redis.AbstractRedisComandHandler;
import io.mycat.jcache.net.conn.handler.redis.strings.RedisGetCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * exists 命令处理器
 *
 * @author yangll
 * @create 2017-07-19 11:38
 */

public class RedisExistsCommandHandler extends AbstractRedisComandHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedisGetCommandHandler.class);

    @Override
    public void handle(Connection conn, RedisMessage message) {
        logger.debug("exists command handler....");

        String key = message.cmdParams()[1];

        boolean exists = RedisStorage.getStringStorage().containsKey(key);
        if(exists){
            message.replay(":1\r\n");
        }else{
            message.replay(":0\r\n");
        }
        writeResponseToClient(conn,message);
    }
}
