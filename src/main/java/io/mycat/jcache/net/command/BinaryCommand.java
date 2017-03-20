package io.mycat.jcache.net.command;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.DELTA_RESULT_TYPE;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.enums.protocol.binary.BinaryProtocol;
import io.mycat.jcache.enums.protocol.binary.ProtocolBinaryCommand;
import io.mycat.jcache.enums.protocol.binary.ProtocolDatatypes;
import io.mycat.jcache.enums.protocol.binary.ProtocolMagic;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryRequestHeader;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

/**
 * 二进制命令接口
 * @author liyanjun
 *
 */
@SuppressWarnings("restriction")
public interface BinaryCommand extends Command{


	/**
	 * 执行命令
	 */
	void execute(Connection conn) throws IOException;
	
	/**
	 * 获取key
	 * @param conn
	 * @param //buffer
	 * @return
	 * @throws IOException
	 */
	public default ByteBuffer readkey(Connection conn) throws IOException{
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		if(header.getKeylen()>0){
			ByteBuffer buffer = conn.getReadDataBuffer();
			int keystart  = BinaryProtocol.memcache_packetHeaderSize+ header.getExtlen();
			return getBytes(buffer,conn.getLastMessagePos(),keystart, header.getKeylen());
		}else{
			return null;
		}
	}
	
	public default int readKeyLength(Connection conn) throws IOException{
		return conn.getBinaryRequestHeader().getKeylen();
	}
	
	public default long readCAS(Connection conn){
		return conn.getBinaryRequestHeader().getCas();
	}
	
	/**
	 * 获取value
	 * @param conn
	 * @param //buffer
	 * @return
	 * @throws IOException
	 */
	public default ByteBuffer readValue(Connection conn) throws IOException{
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		ByteBuffer buffer = conn.getReadDataBuffer();
		int keystart  = BinaryProtocol.memcache_packetHeaderSize+ header.getExtlen() ;
		int valuestart = keystart + header.getKeylen();
		int totalBodylength = header.getBodylen();
		int valuelength = totalBodylength - header.getExtlen() - header.getKeylen() ;
		return getBytes(buffer,conn.getLastMessagePos(),valuestart, valuelength);
	}
	
	/**
	 * 获取 value length
	 * @param conn
	 * @return
	 * @throws IOException
	 */
	public default int readValueLength(Connection conn) throws IOException{
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		int totalBodylength = header.getBodylen();
		int valuelength = totalBodylength - header.getExtlen() - header.getKeylen() ;
		return valuelength;
	}
	
	/**
	 * 读取 extras 
	 * @param conn
	 * @return
	 * @throws IOException
	 */
	public default ByteBuffer readExtras(Connection conn) throws IOException{
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		ByteBuffer buffer = conn.getReadDataBuffer();
		return getBytes(buffer,conn.getLastMessagePos(),BinaryProtocol.memcache_packetHeaderSize,header.getExtlen());
	}
	
	/**
	 * 获取指定长度 的bytebuffer
	 * @param mapBuf
	 * @param index
	 * @param length
	 * @return
	 * @throws IOException
	 */
	public default ByteBuffer getBytes(ByteBuffer mapBuf,int start,int index,int length) throws IOException {
		int oldPos=mapBuf.position();
		mapBuf.position(index+ start);
		ByteBuffer copyBuf=mapBuf.slice();
		copyBuf.limit(length);
		mapBuf.position(oldPos);
		return copyBuf;
	}
	
	/**
	 * build respose header
	 * @param binaryHeader
	 * @param opcode
	 * @param key
	 * @param value
	 * @param extras
	 * @param cas
	 * @return
	 */
	public default BinaryResponseHeader buildHeader(BinaryRequestHeader binaryHeader,byte opcode,
													byte[] key,byte[] value,byte[] extras,long cas){
		BinaryResponseHeader header = new BinaryResponseHeader();
		
		int keylen  = key!=null?key.length:0;
		int extraslen = extras!=null?extras.length:0;
		int valuelen  = value !=null?value.length:0;
		int bodylen = valuelen+extraslen+keylen;
		
		header.setMagic(ProtocolMagic.PROTOCOL_BINARY_RES.getByte());
		header.setOpcode(opcode);
		header.setKeylen((byte)keylen);
		header.setExtlen((byte)extraslen);
		header.setDatatype(ProtocolDatatypes.PROTOCOL_BINARY_RAW_BYTES.getByte());
		header.setStatus(ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus());
		header.setBodylen(bodylen);
		header.setOpaque(binaryHeader.getOpaque());
		header.setCas(cas);
		return header;
	}
	
	/**
	 *  response no body
	 * @param conn
	 * @param opcode
	 * @param status
	 * @param cas
	 */
	public default  void writeResponse(Connection conn,byte opcode,short status,long cas){
		int totallen = BinaryProtocol.memcache_packetHeaderSize;
		ByteBuffer write = ByteBuffer.allocate(totallen);
		write.put(ProtocolMagic.PROTOCOL_BINARY_RES.getByte());
		write.put(opcode);
		write.putShort((short)0x0000);
		write.put((byte)0x00);
		write.put(ProtocolDatatypes.PROTOCOL_BINARY_RAW_BYTES.getByte());
		write.putShort(status);
		write.putInt(0x00);
		write.putInt(0);
		write.putLong(cas);
		conn.addWriteQueue(write);
		conn.enableWrite(true);
	}
	
	/**
	 * 待重构
	 * @param conn
	 * @param opcode
	 * @param status
	 */
	public static void writeResponseError(Connection conn,byte opcode,short status){
		int totallen = BinaryProtocol.memcache_packetHeaderSize;
		ByteBuffer write = ByteBuffer.allocate(totallen);
		write.put(ProtocolMagic.PROTOCOL_BINARY_RES.getByte());
		write.put(opcode);
		write.putShort((short)0x0000);
		write.put((byte)0x00);
		write.put(ProtocolDatatypes.PROTOCOL_BINARY_RAW_BYTES.getByte());
		write.putShort(status);
		write.putInt(0x00);
		write.putInt(0);
		write.putLong(0);
		conn.addWriteQueue(write);
		conn.enableWrite(true);
	}
	
	/**
	 * 待优化  TODO 这里每次都clear writeBuffer.可以优化为剩余空间不足时，再做优化。
	 *  response no body
	 * @param conn
	 */
	public default void writeResponse(Connection conn,BinaryResponseHeader header,byte[] extras,byte[] key,byte[] value){
		int totallen = BinaryProtocol.memcache_packetHeaderSize + header.getBodylen();
		ByteBuffer write = ByteBuffer.allocate(totallen);
		write.clear();  
		write.put(header.getMagic());
		write.put(header.getOpcode());
		write.putShort(header.getKeylen());
		write.put(header.getExtlen());
		write.put(header.getDatatype());
		write.putShort(header.getStatus());
		write.putInt(header.getBodylen());
		write.putInt(header.getOpaque());
		write.putLong(header.getCas());
		if(extras!=null){
			write.put(extras);
		}
		if(key!=null){
			write.put(key);
		}
		if(value!=null){
			write.put(value);
		}
		conn.addWriteQueue(write);
		conn.enableWrite(true);
	}
	
	/*
	 * set add replace append prepend
	 */
	public default void process_bin_update(Connection conn) throws IOException{
		ByteBuffer key = readkey(conn);

		String keystr = new String(cs.decode(key).array());
		
		ByteBuffer value = readValue(conn);
		if(value.remaining()> JcacheGlobalConfig.VALUE_MAX_LENGTH){
			writeResponse(conn,ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_SET.getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),1l);
			return;
		}
		
		byte[] valuebyte = new byte[value.limit()];
		value.get(valuebyte);
		int keylen = conn.getBinaryRequestHeader().getKeylen();
		ByteBuffer extras = readExtras(conn);
		
		int flags = extras.getInt();
		long exptime = extras.getInt(4);
		exptime = exptime==0?0:(exptime*1000L + System.currentTimeMillis());
			
		if(Settings.detailEnabled){
//				stats_prefix_record_set(key,keylen);//TODO
		}
		
		long addr = JcacheContext.getItemsAccessManager().item_alloc(keystr,keylen, flags, exptime, readValueLength(conn)+2);
		if(addr==0){
			if(!JcacheContext.getItemsAccessManager().item_size_ok(readKeyLength(conn), flags, readValueLength(conn)+2)){
				writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),0l);
			}else{
				writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(),0l);
			}
			/* Avoid stale data persisting in cache because we failed alloc.
	         * Unacceptable for SET. Anywhere else too? */
			if(conn.getCurCommand().getByte().byteValue()==ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_SET.getByte()){
				addr = JcacheContext.getItemsAccessManager().item_get(keystr,keylen, conn);
				if(addr>0){
					JcacheContext.getItemsAccessManager().item_unlink(addr);
					JcacheContext.getItemsAccessManager().item_remove(addr);
				}
			}
			/* swallow the data line */
			conn.setWrite_and_go(CONN_STATES.conn_swallow);
			return;
		}
		ItemUtil.setValue(addr, valuebyte);
		ItemUtil.ITEM_set_cas(addr, readCAS(conn));
		
		switch(conn.getCurCommand()){
			case add:
				conn.setSubCmd(NREAD_ADD);
				break;
			case set:
				conn.setSubCmd(NREAD_SET);
				break;
			case replace:
				conn.setSubCmd(NREAD_REPLACE);
				break;
			default:
				break;
		}
		
		if(ItemUtil.ITEM_get_cas(addr)!=0){
			conn.setSubCmd(NREAD_CAS);
		}
		
		conn.setItem(addr);
		complete_update_bin(conn);
				
		
	}
	
	public default void complete_update_bin(Connection conn){
		long addr = conn.getItem();
		if(logger.isDebugEnabled()){
			logger.debug("item is {}",ItemUtil.ItemToString(addr));
		}
		ProtocolResponseStatus eno = ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL;
		Store_item_type ret = Store_item_type.NOT_STORED;		
		
		/* We don't actually receive the trailing two characters in the bin
	     * protocol, so we're going to just set them here */
		if((ItemUtil.getItflags(addr)&ItemFlags.ITEM_CHUNKED.getFlags())==0){
			UnSafeUtil.unsafe.putByte(ItemUtil.ITEM_data(addr)+ItemUtil.getNbytes(addr) -2, (byte)13);
			UnSafeUtil.unsafe.putByte(ItemUtil.ITEM_data(addr)+ItemUtil.getNbytes(addr) -1, (byte)10);
		}else{
//			assert(c->ritem);
//	        item_chunk *ch = (item_chunk *) c->ritem;
//	        if (ch->size == ch->used)
//	            ch = ch->next;
//	        if (ch->size - ch->used > 1) {
//	            ch->data[ch->used + 1] = '\r';
//	            ch->data[ch->used + 2] = '\n';
//	            ch->used += 2;
//	        } else {
//	            ch->data[ch->used + 1] = '\r';
//	            ch->next->data[0] = '\n';
//	            ch->used++;
//	            ch->next->used++;
//	            assert(ch->size == ch->used);
//	        }
		}
		
		
		
		ret = JcacheContext.getItemsAccessManager().store_item(addr,conn);
		switch(ret){
		case STORED:
			writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(),0L);
			break;
		case EXISTS:
			writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS.getStatus(),0L);
			break;
		case NOT_FOUND:
			writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(),0L);
			break;
		case NOT_STORED:
		case TOO_LARGE:
		case NO_MEMORY:
			if(conn.getSubCmd()==NREAD_ADD){
				eno = ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS;
			}else if (conn.getSubCmd()== NREAD_REPLACE){
				eno = ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT;
			}else {
				eno = ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_NOT_STORED;
			}
			writeResponse(conn,conn.getCurCommand().getByte(),eno.getStatus(),0L);
		default:
			break;
		}
		JcacheContext.getItemsAccessManager().item_remove(addr);  /* release the c->item reference */
		conn.setItem(0);
		if(logger.isDebugEnabled()){
			logger.debug("item is {}",ItemUtil.ItemToString(addr));
		}
	}
	
	/*
	 * c->cmd == PROTOCOL_BINARY_CMD_APPEND ||
                c->cmd == PROTOCOL_BINARY_CMD_PREPEND
	 */
	public default void process_bin_append_prepend(Connection conn) throws IOException{
		
		long it;
		
		ByteBuffer key = readkey(conn);

		String keystr = new String(cs.decode(key).array());
		int nkey = readKeyLength(conn);
		int vlen = readValueLength(conn);
		ByteBuffer value = readValue(conn);
		byte[] valuebyte = new byte[value.limit()];
		value.get(valuebyte);
		
		if(Settings.detailEnabled){
//			stats_prefix_record_set(key, nkey);
		}
		
		it = JcacheContext.getItemsAccessManager().item_alloc(keystr, nkey, 0, 0, vlen+2);
		
		if(it==0){
			if(!JcacheContext.getItemsAccessManager().item_size_ok(readKeyLength(conn), 0, readValueLength(conn)+2)){
				writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_E2BIG.getStatus(),0l);
			}else{
				writeResponse(conn,conn.getCurCommand().getByte(),ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(),0l);
			}
			conn.setWrite_and_go(CONN_STATES.conn_swallow);
			return;
		}
		
		ItemUtil.setValue(it, valuebyte);
		ItemUtil.ITEM_set_cas(it, readCAS(conn));
		
		switch(conn.getCurCommand()){
		case append:
			conn.setSubCmd(NREAD_APPEND);
			break;
		case prepend:
			conn.setSubCmd(NREAD_PREPEND);
			break;
		default:
			break;
		}
		
		conn.setItem(it);
		complete_update_bin(conn);
	}
	
	/*
	 * getq,get,getkq,getk
	 * touch,gat,gatq,gatk,gatkq
	 */
	public default void process_bin_get_or_touch(Connection conn) throws IOException{
		long it;
		ByteBuffer key = readkey(conn);

		String keystr = new String(cs.decode(key).array());
		int nkey = readKeyLength(conn);
		
		boolean should_touch = (CommandType.touch.equals(conn.getCurCommand())
								||CommandType.gat.equals(conn.getCurCommand())
								||CommandType.gatk.equals(conn.getCurCommand()));
		boolean should_return_key = (CommandType.getk.equals(conn.getCurCommand())
									||CommandType.getkq.equals(conn.getCurCommand())
									||CommandType.gatk.equals(conn.getCurCommand())
									||CommandType.gatkq.equals(conn.getCurCommand()));
		
		if(should_touch){
			long exptime = readExtras(conn).getInt()*1000L +System.currentTimeMillis();
			
			it = JcacheContext.getItemsAccessManager().item_touch(keystr, nkey, exptime, conn);
		}else{
			it = JcacheContext.getItemsAccessManager().item_get(keystr, nkey, conn);
		}
		
		if(it>0){
			
			byte[] value = ItemUtil.getValue(it);
			byte[] skey = null;
			JcacheContext.getItemsAccessManager().item_update(it);
//	        pthread_mutex_lock(&c->thread->stats.mutex);
//	        if (should_touch) {
//	            c->thread->stats.touch_cmds++;
//	            c->thread->stats.slab_stats[ITEM_clsid(it)].touch_hits++;
//	        } else {
//	            c->thread->stats.get_cmds++;
//	            c->thread->stats.slab_stats[ITEM_clsid(it)].get_hits++;
//	        }
//	        pthread_mutex_unlock(&c->thread->stats.mutex);
			
			if(CommandType.touch.equals(conn.getCurCommand())){
				value = new byte[0];
			}else if(should_return_key){
				skey = keystr.getBytes(JcacheGlobalConfig.defaultCahrset);
			}
			
			int flags = ItemUtil.ITEM_suffix_flags(it);
			byte[] extras = new byte[4];
			extras[0] = (byte) (flags <<24  &0xff);
			extras[1] = (byte) (flags <<16  &0xff);
			extras[2] = (byte) (flags <<8   &0xff);
			extras[3] = (byte) (flags       &0xff);
			
			BinaryResponseHeader rsp = buildHeader(conn.getBinaryRequestHeader(), conn.getCurCommand().getByte(), 
										skey,value, extras, ItemUtil.ITEM_get_cas(it));
			writeResponse(conn,rsp,extras,skey,value);
			conn.setItem(it);
			if(logger.isDebugEnabled()){
				logger.debug("item is {}",ItemUtil.ItemToString(it));
			}
		}else{
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(), 0L);
		}
	}
	
	/*
	 * bin_reading_stat
	 */
	public default void process_bin_stat(Connection conn){
		
	}
	
	/*
	 * bin_reading_del_header
	 */
	public default void process_bin_delete(Connection conn)throws IOException{
		long it;
		ByteBuffer key = readkey(conn);
		String keystr = new String(cs.decode(key).array());
		int nkey = readKeyLength(conn);
		
		it = JcacheContext.getItemsAccessManager().item_get(keystr, nkey, conn);
		if(it>0){
			long cas = readCAS(conn);
			if(cas==0||cas==ItemUtil.ITEM_get_cas(it)){
//	            pthread_mutex_lock(&c->thread->stats.mutex);
//	            c->thread->stats.slab_stats[ITEM_clsid(it)].delete_hits++;
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				JcacheContext.getItemsAccessManager().item_unlink(it);
				writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_SUCCESS.getStatus(), 0L);
			}else{
				writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS.getStatus(), 0L);
			}
			JcacheContext.getItemsAccessManager().item_remove(it);  /* release our reference */
		}else{
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(), 0L);
//	        pthread_mutex_lock(&c->thread->stats.mutex);
//	        c->thread->stats.delete_misses++;
//	        pthread_mutex_unlock(&c->thread->stats.mutex);
		}
	}
	
	/*
	 * incr,incrq,decr,decrq
	 * bin_read_flush_exptime
	 */
	public default void complete_incr_bin(Connection conn)throws IOException{
		long it;
		ByteBuffer key = readkey(conn);
		String keystr = new String(cs.decode(key).array());
		int nkey = readKeyLength(conn);
		long cas = readCAS(conn);
//		ByteBuffer tmpbuf = ByteBuffer.allocate(JcacheGlobalConfig.INCR_MAX_STORAGE_LEN);
		byte[] tmpbuf = new byte[8];
		
		ByteBuffer extras = readExtras(conn);
		long amount = extras.getLong();
		long initial = extras.getLong(8);
		int expiration = extras.getInt(16);
		
		JcacheContext.setLocal("cas", cas);
		DELTA_RESULT_TYPE result = JcacheContext.getItemsAccessManager().add_delta(conn, keystr, nkey,
														CommandType.increment.equals(conn.getCurCommand()),
														amount, 
														tmpbuf);
		cas = (long) JcacheContext.getLocal("cas");
		JcacheContext.setLocal("cas", 0);
		switch(result){
		case OK:
			BinaryResponseHeader rsp = buildHeader(conn.getBinaryRequestHeader(), conn.getCurCommand().getByte(), 
					null,tmpbuf, null, 0);
			writeResponse(conn,rsp,null,null,tmpbuf);
			break;
		case NON_NUMERIC:
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_DELTA_BADVAL.getStatus(), 0L);
			break;
		case EOM:
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(), 0L);
			break;
		case DELTA_ITEM_NOT_FOUND:
			if(expiration!=0xffffffff){
				it = JcacheContext.getItemsAccessManager().item_alloc(keystr, nkey, 0,
																	ItemUtil.realtime(expiration*1000+System.currentTimeMillis()),
																	4+2);
				if(it!=0){
					 String value = "\r\n"+initial;
					 ItemUtil.setValue(it, value.getBytes());
					 conn.setSubCmd(NREAD_ADD);
					 Store_item_type storetype = JcacheContext.getItemsAccessManager().store_item(it, conn);
					 if(Store_item_type.STORED.equals(storetype)){
						 conn.setCas(ItemUtil.ITEM_get_cas(it));
						 
						 rsp = buildHeader(conn.getBinaryRequestHeader(), conn.getCurCommand().getByte(), 
									null,value.getBytes(), null, 0);
							writeResponse(conn,rsp,null,null,value.getBytes());
					 }else{
						 writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_NOT_STORED.getStatus(), 0L);
					 }
					 JcacheContext.getItemsAccessManager().item_remove(it); /* release our reference */
				}else{
					writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_ENOMEM.getStatus(), 0L);
				}
			}else{
//				pthread_mutex_lock(&c->thread->stats.mutex);
//	            if (c->cmd == PROTOCOL_BINARY_CMD_INCREMENT) {
//	                c->thread->stats.incr_misses++;
//	            } else {
//	                c->thread->stats.decr_misses++;
//	            }
//	            pthread_mutex_unlock(&c->thread->stats.mutex);
				writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_ENOENT.getStatus(), 0L);
			}
			break;
		case DELTA_ITEM_CAS_MISMATCH:
			writeResponse(conn, conn.getCurCommand().getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS.getStatus(), 0L);
			break;
		default:
			break;
		}
	}
	
	/*
	 * bin_reading_sasl_auth
	 */
	public default void process_bin_sasl_auth(Connection conn){
		
	}
	
	/*
	 * bin_reading_sasl_auth_data
	 */
	public default void process_bin_complete_sasl_auth(Connection conn){
		
	}

}
