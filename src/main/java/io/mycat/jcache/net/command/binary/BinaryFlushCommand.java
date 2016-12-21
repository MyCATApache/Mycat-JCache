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
	
	MAY have extras.
	MUST NOT have key.
	MUST NOT have value.
	
	Extra data for flush:
	
	     Byte/     0       |       1       |       2       |       3       |
	        /              |               |               |               |
	       |0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|
	       +---------------+---------------+---------------+---------------+
	      0| Expiration                                                    |
	       +---------------+---------------+---------------+---------------+
	     Total 4 bytes
	Response:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Flush the items in the cache now or some time in the future as specified by the expiration field.
	 See the documentation of the textual protocol for the full description on how to specify the expiration time.
 * @author liyanjun
 *
 */
public class BinaryFlushCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryFlushCommand.class);
	
	private int expir;
	
	@Override
	public void execute(Connection conn) throws IOException {
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (keylen == 0 && bodylen == extlen && (extlen == 0 || extlen == 4)) {
			logger.info("execute command flush ");
			//TODO bin_read_flush_exptime
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_FLUSH,null,null,null,0l);
			writeResponse(conn,header,null,null,null);
        } else {
        	writeResponse(conn, BinaryProtocol.OPCODE_FLUSH, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
        }
	}
}
