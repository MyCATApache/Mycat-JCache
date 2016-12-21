package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.TCPNIOAcceptor;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.util.ItemUtil;


/**
 * set 命令 
 * @author liyanjun
 * @author  yanglinlin
 *
 */
public class BinarySetCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinarySetCommand.class);

	@Override
	public void execute(Connection conn) throws IOException {
		ByteBuffer key = readkey(conn);

		String keystr = new String(cs.decode(key).array());
		ByteBuffer value = readValue(conn);
		
		if(value.remaining()> JcacheGlobalConfig.VALUE_MAX_LENGTH){
			writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),1l);
		}
				
		ByteBuffer extras = readExtras(conn);
		
		int flags = extras.getInt();
		int exptime = extras.getInt(4);
	
		try {
			long addr = JcacheContext.getItemsAccessManager().item_alloc(keystr, flags, exptime, readValueLength(conn)+2);
			System.out.println("  addr  "+addr);
			if(addr==0){
				if(!JcacheContext.getItemsAccessManager().item_size_ok(readKeyLength(conn), flags, readValueLength(conn)+2)){
					writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),0l);
				}else{
					writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(),0l);
				}
				addr = JcacheContext.getItemsAccessManager().item_get(keystr, conn);
				
				if(addr>0){
					JcacheContext.getItemsAccessManager().item_unlink(addr);
					JcacheContext.getItemsAccessManager().item_remove(addr);
				}
				return;
			}
			// prev,next,hnext,flushTime,expTime,nbytes,refCount,slabsClisd,it_flags,nsuffix,nskey
//			ItemUtil.set
//			ItemUtil.setKey(keystr.getBytes(JcacheGlobalConfig.defaultCahrset), addr);
			byte[] valuebyte = new byte[value.limit()];
			value.get(valuebyte);
			ItemUtil.setValue(addr, valuebyte);
			
			ItemUtil.ITEM_set_cas(addr, readCAS(conn));
			
			complete_update_bin(addr,conn);
			
			if(logger.isInfoEnabled()){
				logger.info("execute command set key {} , value {} ",new String(cs.decode (key).array()),new String(cs.decode (value).array()));
			}
			
			writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(),1l);
			
		} catch (Exception e) {
			logger.error("set command error ", e);
			throw e;
		}
	}
}
