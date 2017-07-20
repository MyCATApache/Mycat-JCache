package io.mycat.jcache.message;

import java.nio.ByteBuffer;

/**
 * redis 命令message
 * @author yangll
 * @create 2017-07-19 11:00
 */

public class RedisMessage {

    private final ByteBuffer connReadBuf;
    private int position, limit;
    private String replay;
    private String[] cmdParams;

    public RedisMessage(ByteBuffer connReadBuf, int position) {
        this.connReadBuf = connReadBuf;
        this.position = position;
        this.limit = this.connReadBuf.position();
    }


    public void addErrReplay(RedisMessage redisMessage, String replay) {
        redisMessage.replay("-ERR " + replay + "\r\n");
    }

    public void addOkReplay(RedisMessage redisMessage) {

        redisMessage.replay("+OK\r\n");
    }

    public void addNilReply(RedisMessage redisMessage){
        redisMessage.replay("(nil)\r\n");
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

    public String replay(){return  replay;}

    public void cmdParams(String[] cmdParams) {
        this.cmdParams = cmdParams;
    }

    public String[] cmdParams() {
        return this.cmdParams;
    }
}
