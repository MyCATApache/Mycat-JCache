package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.util.ItemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;


/**
 * Touch is used to set a new expiration time for an existing item. 
 * GAT (Get and touch) and GATQ will return the value 
 * for the object if it is present in the cache.
 * 
 * Request:

	MUST have extras.
	MUST have key.
	MUST NOT have value.

	Extra data for set/add/replace:

     Byte/     0       |       1       |       2       |       3       |
        /              |               |               |               |
       |0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|
       +---------------+---------------+---------------+---------------+
      0| Expiration                                                    |
       +---------------+---------------+---------------+---------------+
       Total 4 bytes
       
 * @author liyanjun
 *
 */
public class BinaryTouchCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryTouchCommand.class);
	
	private int expir;  //timeout
	
	@Override
	public void execute(Connection conn) throws IOException {
		logger.info("execute touche command");
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();

		if (extlen == 0 && bodylen == keylen && keylen > 0) {
			try {
				ByteBuffer key = readkey(conn);
				String keystr = new String(cs.decode(key).array());
				logger.info("execute command get key {}",keystr);
				long addr = JcacheContext.getItemsAccessManager().item_get(keystr, conn);
				System.out.println("  addr  "+addr);
//				byte[] value = ItemUtil.getValue(addr);
				ItemUtil.setExpTime(addr,System.currentTimeMillis()/1000);
				BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_TOUCH,null,null,null,1l);
				writeResponse(conn,header,null,null,null);
		} catch (Exception e) {
			logger.error(" execute command touch error ", e);
			throw e;
		}
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_GAT, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
