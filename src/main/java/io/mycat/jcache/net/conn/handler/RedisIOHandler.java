package io.mycat.jcache.net.conn.handler;

import io.mycat.jcache.net.conn.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * redis io handler
 */
public class RedisIOHandler implements IOHandler {

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);

    public static final int REDIS_OK = 0, REDIS_ERR = -1, REDIS_INLINE_MAX_SIZE = 1024 * 64;

    @Override
    public boolean doReadHandler(Connection conn) throws IOException {
        RedisMessage redisMessage = new RedisMessage(conn.getReadDataBuffer(),
            conn.getLastMessagePos());

        while (redisMessage.position() < redisMessage.limit()) {
            redisMessage.replay = null;
            int result = processMultibulkBuffer(redisMessage);
            conn.getReadDataBuffer().position(redisMessage.position());
            conn.setLastMessagePos(redisMessage.position());

            if (result == REDIS_OK) {
                processCommand(redisMessage);
            }

            if (redisMessage.replay != null) {
                ByteBuffer writeBuf = ByteBuffer.wrap(redisMessage.replay.getBytes());
                writeBuf.compact();
                conn.addWriteQueue(writeBuf);
                conn.enableWrite(true);
            }
        }

        return true;
    }

    private int processMultibulkBuffer(RedisMessage redisMessage) {
        String message = redisMessage.message();
        logger.debug(message);

        if (message.charAt(0) != '*') {
            discardCurrentCmd(redisMessage, message);
            addErrReplay(redisMessage,
                "Protocol error: expected '*', got '" + message.charAt(0) + "'");
            return REDIS_ERR;
        }

        int lineEndPos = message.indexOf("\r\n");
        if (lineEndPos < 0) {
            return REDIS_ERR;
        }

        int multibulkLen;
        try {
            multibulkLen = Integer.parseInt(message.substring(1, lineEndPos));
            lineEndPos += 2;// 读取/r/n
        } catch (NumberFormatException e) {
            discardCurrentCmd(redisMessage, message);
            return REDIS_ERR;
        }

        if (multibulkLen > 1024 * 1024) {
            addErrReplay(redisMessage, "Protocol error: invalid multibulk length");
            discardCurrentCmd(redisMessage, message);
            return REDIS_ERR;
        }

        if (multibulkLen <= 0) {
            return REDIS_OK; // "Multibulk processing could see a <= 0 length"
        }

        String[] cmdParams = new String[multibulkLen];
        while (multibulkLen > 0) {
            int index = lineEndPos;
            lineEndPos = message.indexOf("\r\n", index);
            if (lineEndPos < 0) {
                return REDIS_ERR;
            }
            if (lineEndPos - index > REDIS_INLINE_MAX_SIZE) {
                discardCurrentCmd(redisMessage, message);
                addErrReplay(redisMessage, "Protocol error: too big bulk count string");
                return REDIS_ERR;
            }

            if (message.charAt(index) != '$') {
                discardCurrentCmd(redisMessage, message);
                addErrReplay(redisMessage,
                    "Protocol error: expected '$', got '" + message.charAt(index) + "'");
                return REDIS_ERR;
            }
            index++;// 读取$

            int bulkLen = 0;
            try {
                bulkLen = Integer.parseInt(message.substring(index, lineEndPos));
                lineEndPos += 2;// 读取/r/n
            } catch (NumberFormatException e) {
                discardCurrentCmd(redisMessage, message);
                addErrReplay(redisMessage, "Protocol error: invalid bulk length");
                return REDIS_ERR;
            }

            if (bulkLen < 0 || bulkLen > 512 * 1024 * 1024) {
                discardCurrentCmd(redisMessage, message);
                addErrReplay(redisMessage, "Protocol error: invalid bulk length");
                return REDIS_ERR;
            }

            index = lineEndPos;
            lineEndPos = message.indexOf("\r\n", index);
            if (lineEndPos < 0) {
                return REDIS_ERR;
            }

            cmdParams[cmdParams.length - multibulkLen] = message.substring(index, lineEndPos);
            lineEndPos += 2;// 读取/r/n
            multibulkLen--;
        }

        redisMessage.position(lineEndPos);
        redisMessage.cmdParams(cmdParams);
        return REDIS_OK;
    }

    /**
     * 丢弃当前指令
     *
     * @param redisMessage redis报文信息
     * @return 如果有下一条指令：返回true; 否则：返回false
     */
    private void discardCurrentCmd(RedisMessage redisMessage, String message) {
        int nextCmdPos = message.indexOf('*');
        if (nextCmdPos > 0) {
            // 清理nextCmdPos前无法解析的字符
            redisMessage.position(nextCmdPos);
        } else {
            // 清理无法解析的字符
            redisMessage.position(redisMessage.limit());
        }
    }

    private void addErrReplay(RedisMessage redisMessage, String replay) {
        redisMessage.replay("-ERR " + replay + "\r\n");
    }

    private void addOkReplay(RedisMessage redisMessage) {
        redisMessage.replay("+OK\r\n");
    }

    private void processCommand(RedisMessage redisMessage) {
        logger.debug(Arrays.toString(redisMessage.cmdParams()));
        addOkReplay(redisMessage);
    }

    class RedisMessage {

        private final ByteBuffer connReadBuf;
        private int position, limit;
        private String replay;
        private String[] cmdParams;

        public RedisMessage(ByteBuffer connReadBuf, int position) {
            this.connReadBuf = connReadBuf;
            this.position = position;
            this.limit = this.connReadBuf.position();
        }

        public String message() {
            return new String(this.connReadBuf.array(), position, limit - position);
        }

        public int limit() {
            return this.limit;
        }

        public int position() {
            return this.position;
        }

        public void position(int position) {
            this.position = position;
        }

        public void replay(String replay) {
            this.replay = replay;
        }

        public void cmdParams(String[] cmdParams) {
            this.cmdParams = cmdParams;
        }

        public String[] cmdParams() {
            return this.cmdParams;
        }
    }
}