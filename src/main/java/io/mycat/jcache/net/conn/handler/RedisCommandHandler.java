package io.mycat.jcache.net.conn.handler;

import io.mycat.jcache.message.RedisMessage;
import io.mycat.jcache.net.conn.Connection;

/**
 * Created by yangll on 2017/7/18.
 */
public interface RedisCommandHandler {

    /**
     * 处理command
     * @param conn
     * @param message
     */
    void handle(Connection conn, RedisMessage message);
}
