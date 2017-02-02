package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;



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
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(), 0L);
			conn.setWrite_and_go(CONN_STATES.conn_closing);
		} else {
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
