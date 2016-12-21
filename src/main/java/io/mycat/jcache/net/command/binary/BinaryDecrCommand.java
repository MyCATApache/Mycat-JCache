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
	These commands will either add or remove the specified amount to the requested counter. 
	If you want to set the value of the counter with add/set/replace, 
	the objects data must be the ascii representation of the value and not the byte values of a 64 bit integer.
	
	If the counter does not exist, one of two things may happen:
	
	If the expiration value is all one-bits (0xffffffff), the operation will fail with NOT_FOUND.
	For all other expiration values, the operation will succeed by seeding the value for this key 
	with the provided initial value to expire with the provided expiration time. The flags will be set to zero.
	Decrementing a counter will never result in a "negative value" (or cause the counter to "wrap"). 
	instead the counter is set to 0. Incrementing the counter may cause the counter to wrap.
 * @author liyanjun
 *
 */
public class BinaryDecrCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryDecrCommand.class);
	
	private long amount;
	private long init;
	private int expir;
		
	@Override
	public void execute(Connection conn) throws IOException {
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (keylen > 0 && extlen == 20 && bodylen == (keylen + extlen)) {
			//TODO bin_reading_incr_header
			ByteBuffer key = readkey(conn);
			String keystr = new String(cs.decode(key).array());
			logger.info("execute command gat key {}",keystr);
			byte[] value = "This is a test String".getBytes("UTF-8");
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_DECREMENT,null,value,null,1l);
			writeResponse(conn,header,null,null,value);
        } else {
        	writeResponse(conn, BinaryProtocol.OPCODE_DECREMENT, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
        }
	}
}
