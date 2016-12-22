package io.mycat.jcache.net.command;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryRequestHeader;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;


/**
 * 命令接口
 * @author liyanjun
 *
 */
public interface Command {
	
	Charset cs = Charset.forName (JcacheGlobalConfig.defaultCahrset);

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
			return getBytes(buffer,keystart, header.getKeylen());
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
		return getBytes(buffer,valuestart, valuelength);
	}
	
	/**
	 * 获取 value length
	 * @param conn
	 * @return
	 * @throws IOException
	 */
	public default int readValueLength(Connection conn) throws IOException{
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		ByteBuffer buffer = conn.getReadDataBuffer();
		int keystart  = BinaryProtocol.memcache_packetHeaderSize+ header.getExtlen() ;
		int valuestart = keystart + header.getKeylen();
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
		return getBytes(buffer,BinaryProtocol.memcache_packetHeaderSize,header.getExtlen());
	}
	
	/**
	 * 获取指定长度 的bytebuffer
	 * @param mapBuf
	 * @param index
	 * @param length
	 * @return
	 * @throws IOException
	 */
	public default ByteBuffer getBytes(ByteBuffer mapBuf,int index,int length) throws IOException {
		int oldPos=mapBuf.position();
		mapBuf.position(index+ mapBuf.position());
		ByteBuffer copyBuf=mapBuf.slice();
		copyBuf.limit(length);
		mapBuf.position(oldPos);
		return copyBuf;
	}
	
	public default void complete_update_bin(long addr,Connection conn){
		Store_item_type ret = Store_item_type.NOT_STORED;
		byte flags = ItemUtil.getItflags(addr);
		/* We don't actually receive the trailing two characters in the bin
	     * protocol, so we're going to just set them here */
		if((flags&ItemFlags.ITEM_CHUNKED.getFlags())==0){
			long datastart = ItemUtil.ITEM_data(addr);
			long nbytes = ItemUtil.getNbytes(addr);
			UnSafeUtil.putByte(datastart+nbytes-2, (byte)13);
			UnSafeUtil.putByte(datastart+nbytes-1, (byte)10);
		}else{
			//TODO 
		}
		
		ret = JcacheContext.getItemsAccessManager().store_item(addr,conn);
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
		
		header.setMagic(BinaryProtocol.MAGIC_RESP);
		header.setOpcode(opcode);
		header.setKeylen((byte)keylen);
		header.setExtlen((byte)extraslen);
		header.setDatatype(BinaryProtocol.PROTOCOL_BINARY_RAW_BYTES);
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
	public default void writeResponse(Connection conn,byte opcode,short status,long cas){
		int totallen = BinaryProtocol.memcache_packetHeaderSize;
		ByteBuffer write = ByteBuffer.allocate(totallen);
		write.put(BinaryProtocol.MAGIC_RESP);
		write.put(opcode);
		write.putShort((short)0x0000);
		write.put((byte)0x00);
		write.put(BinaryProtocol.PROTOCOL_BINARY_RAW_BYTES);
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
		write.put(BinaryProtocol.MAGIC_RESP);
		write.put(opcode);
		write.putShort((short)0x0000);
		write.put((byte)0x00);
		write.put(BinaryProtocol.PROTOCOL_BINARY_RAW_BYTES);
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
}
