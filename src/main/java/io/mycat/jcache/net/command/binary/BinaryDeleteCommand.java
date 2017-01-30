package io.mycat.jcache.net.command.binary;


import java.io.IOException;
import java.nio.ByteBuffer;

import io.mycat.jcache.context.JcacheContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.util.ItemUtil;

/**
 * Created by qd on 2016/12/2.
 * @author  yanglinlin
 */
public class BinaryDeleteCommand implements  Command {
    private static final Logger logger = LoggerFactory.getLogger(BinaryDeleteCommand.class);

    @Override
    public void execute(Connection conn) throws IOException {
        logger.info("execute delete command");
        int keylen = conn.getBinaryRequestHeader().getKeylen();
        ByteBuffer keyBuf = readkey(conn);
        if(keyBuf==null || keyBuf.capacity()==0){
            writeResponse(conn, ProtocolCommand.PROTOCOL_BINARY_CMD_DELETE.getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(),0l);
        }
        String key = new String(cs.decode(keyBuf).array());
        long addr =  JcacheContext.getItemsAccessManager().item_get(key,keylen,conn);
        if(addr>0){
        	long cas = readCAS(conn);
        	if(cas==0||cas==ItemUtil.ITEM_get_cas(addr)){
        		JcacheContext.getItemsAccessManager().item_unlink(addr);
        		writeResponse(conn,ProtocolCommand.PROTOCOL_BINARY_CMD_DELETE.getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(),cas);
        	}else{
        		writeResponse(conn, ProtocolCommand.PROTOCOL_BINARY_CMD_DELETE.getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS.getStatus(),0l);
        	}
        }else{
        	writeResponse(conn, ProtocolCommand.PROTOCOL_BINARY_CMD_DELETE.getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(),0l);
        }
    }
}
