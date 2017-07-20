package io.mycat.jcache.net.conn.handler.redis.strings;

import io.mycat.jcache.memory.redis.RedisStorage;
import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.redis.AbstractRedisComandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Append 命令处理器
 *
 * @author qd
 * @create 2017-07-19 15:59
 */

public class RedisAppendCommandHandler extends AbstractRedisComandHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedisAppendCommandHandler.class);

    @Override
    public void handle(Connection conn, RedisMessage message) {
        logger.debug("Append command handler...");

        String key = message.cmdParams()[1];
        String value = message.cmdParams()[2];
        String oldValue = (String)RedisStorage.getStringStorage().get(key);
        if(oldValue==null || "".equals(oldValue)){
            RedisStorage.getStringStorage().put(key,value);
            message.replay(":"+value.length()+"\r\n");
            writeResponseToClient(conn,message);
        }else{
            RedisStorage.getStringStorage().put(key,oldValue+value);
            message.replay(":"+(oldValue.length()+value.length())+"\r\n");
            writeResponseToClient(conn,message);
        }
    }
}
