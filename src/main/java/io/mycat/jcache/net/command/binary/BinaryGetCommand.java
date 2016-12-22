package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.util.ItemUtil;


/**
 * get 命令 
   Field        (offset) (value)
   Magic        (0)    : 0x81
   Opcode       (1)    : 0x00
   Key length   (2,3)  : 0x0000
   Extra length (4)    : 0x04
   Data type    (5)    : 0x00
   Status       (6,7)  : 0x0000
   Total body   (8-11) : 0x00000009
   Opaque       (12-15): 0x00000000
   CAS          (16-23): 0x0000000000000001
   Extras              :
     Flags      (24-27): 0xdeadbeef
   Key                 : None
   Value        (28-32): The textual string "World"
 * @author liyanjun
 *
 */
public class BinaryGetCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryGetCommand.class);
	
	@Override
	public void execute(Connection conn) throws IOException {
		
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
				byte[] value = ItemUtil.getValue(addr);
				int flags = ItemUtil.ITEM_suffix_flags(addr);
//				int flags = BinaryProtocol.MARKER_STRING;  //TODO 
				byte[] extras = new byte[4];
				extras[0] = (byte) (flags <<24  &0xff);
				extras[1] = (byte) (flags <<16  &0xff);
				extras[2] = (byte) (flags <<8   &0xff);
				extras[3] = (byte) (flags       &0xff);
				BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_GET,null,value,extras,1l);
				writeResponse(conn,header,extras,null,value);				
			} catch (Exception e) {
				logger.error(" execute command get error ", e);
				throw e;
			}
		} else {
			writeResponse(conn, BinaryProtocol.OPCODE_GET, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus(), 0L);
		}
	}
}
