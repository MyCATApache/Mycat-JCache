package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;

/**
 * Created by qd on 2016/11/29.
 * @author  yanglinlin
 */
public class BinaryAddCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(BinaryAddCommand.class);

    @Override
    public void execute(Connection conn) throws IOException {
        logger.info("add command");
        ByteBuffer key = readkey(conn);
        byte[] ds = new byte[key.remaining()];
        key.get(ds);
        if(ds.length>JcacheGlobalConfig.KEY_MAX_LENGTH) {
            writeResponse(conn, BinaryProtocol.OPCODE_ADD, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(), 0L);
        }

        String keys = new String(cs.decode(key).array());
        //Add MUST fail if the item already exist.
//        if(ReadWritePool.get(new String[]{keys}).length>0) {
//            writeResponse(conn,BinaryProtocol.OPCODE_ADD,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_NOT_STORED.getStatus(),0l);
//        }
        //extras
        ByteBuffer extras = readExtras(conn);
        extras.limit(extras.position()+4);
        ByteBuffer flags = extras.slice();
        extras.position(extras.limit());
        ByteBuffer expiry = extras.slice();

        ByteBuffer value = readValue(conn);
        byte[] data = new byte[value.remaining()];
        value.get(data);

        System.out.println("执行add 命令   key: "+new String(cs.decode (key).array()));
        System.out.println("执行add 命令   value: "+new String(cs.decode (value).array()));

//        int result = ReadWritePool.add(keys,flags.getInt(),data.length,expiry.getInt(),data);
//        System.out.println("add command result : "+result);
        writeResponse(conn,BinaryProtocol.OPCODE_ADD,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(),1l);
    }
}
