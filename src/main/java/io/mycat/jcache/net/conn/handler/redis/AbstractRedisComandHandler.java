package io.mycat.jcache.net.conn.handler.redis;

import io.mycat.jcache.net.conn.handler.RedisCommandHandler;
import io.mycat.jcache.net.conn.handler.RedisIOHandler;

/**
 * @author qd
 * @create 2017-07-18 22:10
 */

public abstract class AbstractRedisComandHandler implements RedisCommandHandler{

    protected void addErrReplay(RedisIOHandler.RedisMessage redisMessage, String replay) {
        redisMessage.replay("-ERR " + replay + "\r\n");
    }

    protected void addOkReplay(RedisIOHandler.RedisMessage redisMessage) {

        redisMessage.replay("+OK\r\n");
    }

    protected void addNilReply(RedisIOHandler.RedisMessage redisMessage){
        redisMessage.replay("(nil)\r\n");
    }
}
