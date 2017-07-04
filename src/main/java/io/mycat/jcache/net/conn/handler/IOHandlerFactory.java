package io.mycat.jcache.net.conn.handler;

import io.mycat.jcache.enums.protocol.Protocol;

/**
 * @author dragonwu
 * @date 17/1/20
 **/
public class IOHandlerFactory {

    private static final IOHandler ASCII_IO_HANDLER = new AsciiIOHanlder();
    private static final IOHandler BINARY_IO_HANDLER = new BinaryIOHandler();
    private static final IOHandler REDIS_IO_HANDLER = new RedisIOHandler();

    private IOHandlerFactory() {

    }

    public static IOHandler getHandler(Protocol protocol) {
        switch (protocol) {
            case ascii:
                return ASCII_IO_HANDLER;
            case binary:
                return BINARY_IO_HANDLER;
            case negotiating:
                return null; //todo 返回null是否有安全隐患 请大神们确认。
            case resp:
                return REDIS_IO_HANDLER;
            default:
                throw new RuntimeException("Unsupported Protocol");
        }
    }
}
