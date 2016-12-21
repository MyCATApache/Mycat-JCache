package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;



/**
	Request:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Response:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Used as a keep alive.
	
	Field        (offset) (value)
   Magic        (0)    : 0x80
   Opcode       (1)    : 0x0a
   Key length   (2,3)  : 0x0000
   Extra length (4)    : 0x00
   Data type    (5)    : 0x00
   VBucket      (6,7)  : 0x0000
   Total body   (8-11) : 0x00000000
   Opaque       (12-15): 0x00000000
   CAS          (16-23): 0x0000000000000000
   Extras              : None
   Key                 : None
   Value               : None
 * @author liyanjun
 *
 */
public class BinaryNoopCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryNoopCommand.class);
	
	@Override
	public void execute(Connection conn) throws IOException {
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (extlen == 0 && bodylen == keylen && keylen == 0) {
			logger.info("execute command noop key");
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_NOOP,null,null,null,0l);
			writeResponse(conn,header,null,null,null);
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_NOOP, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
