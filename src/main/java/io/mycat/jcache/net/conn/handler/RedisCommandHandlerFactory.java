package io.mycat.jcache.net.conn.handler;

import io.mycat.jcache.net.conn.handler.redis.strings.RedisGetCommandHandler;
import io.mycat.jcache.net.conn.handler.redis.strings.RedisSetCommandHandler;

/**
 * command handler 工厂类
 * @author yangll
 * @create 2017-07-18 21:08
 */

public final class RedisCommandHandlerFactory {

    /**
     * 根据不同的cmd，返回不同的命令处理器
     * @param cmd
     * @return
     */
    public static RedisCommandHandler getHandler(String cmd){
        switch (cmd){
            case "get":
                return  new RedisGetCommandHandler();
            case "set":
                return new RedisSetCommandHandler();
        }
        return null;
    }
}
