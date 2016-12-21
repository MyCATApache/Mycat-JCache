package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;


/**
 * 
	Request server statistics. 
	Without a key specified the server will respond with a "default" set of statistics information. 
	Each piece of statistical information is returned in its own packet (key contains the name of 
	the statistical item and the body contains the value in ASCII format). The sequence of return 
	packets is terminated with a packet that contains no key and no value.
	Request:
	
	MUST NOT have extras.
	MAY have key.
	MUST NOT have value.
	Response:
	
	MUST NOT have extras.
	MAY have key.
	MAY have value. 
 * @author liyanjun
 *
 */
public class BinaryStatCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryStatCommand.class);
		
	@Override
	public void execute(Connection conn) throws IOException {
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (extlen == 0) {
			ByteBuffer key = readkey(conn);
			if(key!=null){
				String keystr = new String(cs.decode(key).array());
				logger.info("execute command stat key {}",keystr);				
			}

			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_GAT,null,null,null,1l);
			writeResponse(conn,header,null,null,null);
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_GAT, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
