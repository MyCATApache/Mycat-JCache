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
	Close the connection to the server.
 * @author liyanjun
 *
 */
public class BinaryQuitCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryQuitCommand.class);
	
	@Override
	public void execute(Connection conn) throws IOException {
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (keylen == 0 && extlen == 0 && bodylen == 0) {
			logger.info("execute command quit ");
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_QUIT,null,null,null,0l);
//			writeResponse(conn,header,null,null,null);
			conn.closeSocket();  //TODO 待优化 状态机
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_QUIT, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
