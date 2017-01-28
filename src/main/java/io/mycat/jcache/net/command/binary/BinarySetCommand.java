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
import io.mycat.jcache.setting.Settings;
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
		
		byte[] keybyte = keystr.getBytes(JcacheGlobalConfig.defaultCahrset);
		byte[] valuebyte = new byte[value.limit()];
		value.get(valuebyte);
		
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		ByteBuffer extras = readExtras(conn);
		
		int flags = extras.getInt();
		int exptime = extras.getInt(4);
	
		try {
			
			if(Settings.detailEnabled){
//				stats_prefix_record_set(key,keylen);//TODO
			}
			
			long addr = JcacheContext.getItemsAccessManager().item_alloc(keystr,keylen, flags, exptime, readValueLength(conn)+2);
			if(addr==0){
				if(!JcacheContext.getItemsAccessManager().item_size_ok(readKeyLength(conn), flags, readValueLength(conn)+2)){
					writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),0l);
				}else{
					writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(),0l);
				}
				/* Avoid stale data persisting in cache because we failed alloc.
		         * Unacceptable for SET. Anywhere else too? */
				addr = JcacheContext.getItemsAccessManager().item_get(keystr,keylen, conn);
				
				if(addr>0){
					JcacheContext.getItemsAccessManager().item_unlink(addr);
					JcacheContext.getItemsAccessManager().item_remove(addr);
				}
				return;
			}
			
			ItemUtil.ITEM_set_cas(addr, readCAS(conn));
			complete_update_bin(addr,conn);
						
			if(logger.isDebugEnabled()){
				logger.debug(" item data = " +ItemUtil.ItemToString(addr));
			}
			
			writeResponse(conn,BinaryProtocol.OPCODE_SET,ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(),1l);
			
		} catch (Exception e) {
			logger.error("set command error ", e);
			throw e;
		}
	}
}
