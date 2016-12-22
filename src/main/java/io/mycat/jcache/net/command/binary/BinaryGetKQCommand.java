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
 * getkq 命令 
   Field        (offset) (value)
   Magic        (0)    : 0x81
   Opcode       (1)    : 0x00
   Key length   (2,3)  : 0x0005
   Extra length (4)    : 0x04
   Data type    (5)    : 0x00
   Status       (6,7)  : 0x0000
   Total body   (8-11) : 0x00000009
   Opaque       (12-15): 0x00000000
   CAS          (16-23): 0x0000000000000001
   Extras              :
     Flags      (24-27): 0xdeadbeef
   Key          (28-32): The textual string: "Hello"
   Value        (33-37): The textual string: "World"
 * @author liyanjun
 *
 */
public class BinaryGetKQCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryGetKQCommand.class);
	
	@Override
	public void execute(Connection conn) throws IOException {
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		int bodylen = conn.getBinaryRequestHeader().getBodylen();
		int extlen  = conn.getBinaryRequestHeader().getExtlen();
		
		if (extlen == 0 && bodylen == keylen && keylen > 0) {
			ByteBuffer key = readkey(conn);
			String keystr = new String(cs.decode(key).array());
			logger.info("execute command getkq key {}",keystr);
			byte[] value = "This is a test String".getBytes("UTF-8");
			int flags = 0x00000020;
			byte[] extras = new byte[4];
			extras[0] = (byte) (flags <<24  &0xff);
			extras[1] = (byte) (flags <<16  &0xff);
			extras[2] = (byte) (flags <<8   &0xff);
			extras[3] = (byte) (flags       &0xff);
			BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_GETKQ,keystr.getBytes(),value,extras,1l);
			writeResponse(conn,header,extras,keystr.getBytes(),value);
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_GETQ, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
