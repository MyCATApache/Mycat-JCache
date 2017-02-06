package io.mycat.jcache.net.conn.handler;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;


public class AsciiIOHanlder implements IOHandler {
	
	private static ByteBuffer badformat = ByteBuffer.wrap("CLIENT_ERROR bad command line format \r\n>".getBytes());
	
	private static ByteBuffer tooLarge = ByteBuffer.wrap("SERVER_ERROR object too large for cache \r\n>".getBytes());
	
	private static ByteBuffer outofmemory = ByteBuffer.wrap("SERVER_ERROR out of memory storing object \r\n>".getBytes());
	
	private static ByteBuffer STORED = ByteBuffer.wrap("STORED\r\n>".getBytes());
	
	private static ByteBuffer EXISTS = ByteBuffer.wrap("EXISTS\r\n>".getBytes());
	
	private static ByteBuffer NOT_FOUND = ByteBuffer.wrap("NOT_FOUND\r\n>".getBytes());
	
	private static ByteBuffer NOT_STORED = ByteBuffer.wrap("NOT_STORED\r\n>".getBytes());
	
	private static ByteBuffer UnhandledType = ByteBuffer.wrap("SERVER_ERROR Unhandled storage type.\r\n>".getBytes());
	
	private static ByteBuffer baddatachunk = ByteBuffer.wrap("CLIENT_ERROR bad data chunk.\r\n>".getBytes());

	
	/**
	 * 文本协议处理
	 * TODO 编码/解码部分接口化，公用化  处理
	 * @param conn
	 * @param buffer
	 * @return boolean  是否需要继续读取命令, true 继续读取命令,不清空当前缓冲区,false 设置lastMessagePos 执行下一个状态
	 * @throws IOException
	 */
	@Override
	public boolean doReadHandler(Connection conn) throws IOException {
		ByteBuffer readBuffer = conn.getReadDataBuffer();
		int readEndPos = conn.getLastMessagePos();
		int limit = readBuffer.position();
		String readedLine = null;
		for (int i = readEndPos; i < limit; i++) {
			// System.out.println(readBuffer.get(i));
			if (readBuffer.get(i) == 13) {// a line finished
				byte[] lineBytes = new byte[i - readEndPos];
				readBuffer.position(readEndPos);
				readBuffer.get(lineBytes);
				readEndPos = i;
				readedLine = new String(lineBytes);
				System.out.println("received line ,lenth:" + readedLine.length() + " value " + readedLine);
				break;
			}
		}
		
		if (readedLine != null) {
			// 取消读事件关注，因为要应答数据
			conn.disableRead();
//			String response = "read ascii command :"+ readedLine;
			
//			ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes());
//			conn.addWriteQueue(writeBuffer);
//			conn.enableWrite(true);
			if(CONN_STATES.conn_nread.equals(conn.getState())){
				doReadValue(conn);
			}else{
				process_command(conn,readedLine);
				conn.setLastMessagePos(limit);
			}
			return true;
		}
		return false;
	}
	
	public void doReadValue(Connection conn) throws IOException{
		ByteBuffer readBuffer = conn.getReadDataBuffer();
		Store_item_type ret;
		if(readBuffer.array()[readBuffer.position()] == 13){
			if(conn.getRlbytes()!= (readBuffer.position()-conn.getLastMessagePos())){
				out_string(conn, baddatachunk);
			}else{
				byte[] value = new byte[readBuffer.position()];
				readBuffer.get(value, 0, readBuffer.position());
				ItemUtil.setValue(conn.getItem(), value);
				ret = JcacheContext.getItemsAccessManager().store_item(conn.getItem(), conn);
				
				switch(ret){
				case STORED:
					out_string(conn, STORED);
					break;
				case EXISTS:
					out_string(conn, EXISTS);
					break;
				case NOT_FOUND:
					out_string(conn, NOT_FOUND);
					break;
				case NOT_STORED:
					out_string(conn, NOT_STORED);
					break;
				default:
					out_string(conn, UnhandledType);
				}
			}
			JcacheContext.getItemsAccessManager().item_remove(conn.getItem());
			conn.setItem(0);
		}
	}
	
	/**
	 * 命令的解析处理
	 * TODO 增加  链式处理设计框架
	 * @param conn
	 * @param readedLine
	 */
	private void process_command(Connection conn,String readedLine){
		String[] params = readedLine.split(" ");
		int len = params.length;
		int comm = 0;
		if(len>=3&&(params[0].equals("get")
					||params[0].equals("bget"))){
			process_get_command(conn,params,false);
		}else if((len==5||len==6)){
			if(params[0].equals("add")) comm = Command.NREAD_ADD;
			if(params[0].equals("set")) comm = Command.NREAD_SET;
			if(params[0].equals("replace")) comm = Command.NREAD_REPLACE;
			if(params[0].equals("prepend")) comm = Command.NREAD_PREPEND;
			if(params[0].equals("append")) comm = Command.NREAD_APPEND;
			if(comm>0){
				process_update_command(conn,params,comm,false);
			}
		}
	}
	
	private void process_get_command(Connection conn,String[] params,boolean return_cas){
		
	}
	
	private void process_update_command(Connection conn,String[] params,int comm,boolean handle_cas){
		long exptime;
		long req_cas_id = 0;
		long it;
		String key = params[1];
		int nkey = params[1].length();
		if(nkey > JcacheGlobalConfig.KEY_MAX_LENGTH){
			out_string(conn,badformat);
			return;
		}
		
		int flags = Integer.parseInt(params[2]);
		exptime = Long.parseLong(params[3]);
		int vlen = Integer.parseInt(params[4]);
		
		if(exptime<0){
			exptime = ItemUtil.REALTIME_MAXDELTA +1000;
		}
		
		if(handle_cas){
			req_cas_id = Long.parseLong(params[5]);
		}
		
		if(vlen < 0 || vlen > JcacheGlobalConfig.VALUE_MAX_LENGTH){
			out_string(conn,badformat);
			return;
		}
		conn.setRlbytes(vlen);
		vlen +=2;
		
		it = JcacheContext.getItemsAccessManager().item_alloc(key, nkey, flags, exptime, vlen);
		if(it == 0){
			
			if(!JcacheContext.getItemsAccessManager().item_size_ok(nkey, flags, vlen)){
				out_string(conn,tooLarge);
			}else {
				out_string(conn,outofmemory);
			}
			/* swallow the data line */
			conn.setWrite_and_go(CONN_STATES.conn_swallow);
			conn.setRlbytes(vlen);
			 
			
			/* Avoid stale data persisting in cache because we failed alloc.
	         * Unacceptable for SET. Anywhere else too? */
			if(comm == Command.NREAD_SET){
				it = JcacheContext.getItemsAccessManager().item_get(key, nkey, conn);
				if(it > 0){
					JcacheContext.getItemsAccessManager().item_unlink(it);
					JcacheContext.getItemsAccessManager().item_remove(it);
				}
			}
			return;
		}
		
		ItemUtil.ITEM_set_cas(it, req_cas_id);
		conn.setItem(it);
		conn.setSubCmd(comm);
		conn.setWrite_and_go(CONN_STATES.conn_nread);
	}
	
	private void out_string(Connection conn,ByteBuffer badformat){
		ByteBuffer formet = badformat.slice();
		formet.position(formet.limit());
		conn.addWriteQueue(formet);
		conn.enableWrite(true);
	}
}
