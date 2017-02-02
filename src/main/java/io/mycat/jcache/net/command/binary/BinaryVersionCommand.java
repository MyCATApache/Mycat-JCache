package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.protocol.binary.BinaryProtocol;
import io.mycat.jcache.enums.protocol.binary.ProtocolBinaryCommand;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;


/**
	version
	
	Request:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Response:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST have value.
	Request the server version.
	
	The server responds with a packet containing the version string in the body with the following format: "x.y.z"
 * @author liyanjun
 *
 */
public class BinaryVersionCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryVersionCommand.class);
	
	@Override
	public void execute(Connection conn) throws IOException {
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (keylen == 0 && extlen == 0 && bodylen == 0) {
			logger.info("execute command quit ");
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_VERSION.getByte(),null,JcacheGlobalConfig.version.getBytes(),null,0l);
			writeResponse(conn,header,null,null,null);
		} else {
			writeResponse(conn, ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_VERSION.getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
