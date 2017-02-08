package io.mycat.jcache.net.conn.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.DELTA_RESULT_TYPE;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.command.CommandType;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.util.BytesUtil;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;


public class AsciiIOHanlder implements IOHandler {
	
	private static ByteBuffer badformat = ByteBuffer.wrap("CLIENT_ERROR bad command line format \r\n>".getBytes());
	
	private static ByteBuffer tooLarge = ByteBuffer.wrap("SERVER_ERROR object too large for cache \r\n>".getBytes());
	
	private static ByteBuffer outofmemory = ByteBuffer.wrap("SERVER_ERROR out of memory storing object \r\n>".getBytes());
	
	private static ByteBuffer STORED = ByteBuffer.wrap("STORED\r\n".getBytes());
	
	private static ByteBuffer EXISTS = ByteBuffer.wrap("EXISTS\r\n".getBytes());
	
	private static ByteBuffer NOT_FOUND = ByteBuffer.wrap("NOT_FOUND\r\n".getBytes());
	
	private static ByteBuffer ERROR = ByteBuffer.wrap("ERROR\r\n".getBytes());
	
	private static ByteBuffer NOT_STORED = ByteBuffer.wrap("NOT_STORED\r\n".getBytes());
	
	private static ByteBuffer UnhandledType = ByteBuffer.wrap("SERVER_ERROR Unhandled storage type.\r\n".getBytes());
	
	private static ByteBuffer baddatachunk = ByteBuffer.wrap("CLIENT_ERROR bad data chunk.\r\n".getBytes());
	
	private static ByteBuffer END = ByteBuffer.wrap("END\r\n".getBytes());
	
	private static ByteBuffer DELETED = ByteBuffer.wrap("DELETED\r\n".getBytes());
	
	private static ByteBuffer INVALID_NUM = ByteBuffer.wrap("CLIENT_ERROR invalid numeric delta argument\r\n".getBytes());

	private static ByteBuffer NON_NUM = ByteBuffer.wrap("CLIENT_ERROR cannot increment or decrement non-numeric value\r\n".getBytes());
	
	private static ByteBuffer outofmemory1 = ByteBuffer.wrap("SERVER_ERROR out of memory\r\n".getBytes());
	
	private static ByteBuffer INVALID_EXP = ByteBuffer.wrap("CLIENT_ERROR invalid exptime argument\r\n".getBytes());
	
	private static ByteBuffer TOUCHED = ByteBuffer.wrap("TOUCHED\r\n".getBytes());

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
		int lastPos = 0;
		boolean hasprocess = false;
		for (int i = readEndPos; i < limit&&conn.getLastMessagePos() < limit; i++) {
			// System.out.println(readBuffer.get(i));
			if (readBuffer.get(i) == 13) {// a line finished
				int readlimit = i - readEndPos - lastPos;
				byte[] lineBytes = new byte[readlimit];
				readBuffer.position(readEndPos+lastPos);
				readBuffer.get(lineBytes);
				readlimit += 2;
				lastPos = readlimit+conn.getLastMessagePos();
				readBuffer.position(lastPos);
				readedLine = new String(lineBytes);
				if(conn.getItem()>0){
					doReadValue(conn,readedLine);
				}else{
					process_command(conn,readedLine);
				}
				conn.setLastMessagePos(readlimit);
				hasprocess = true;
			}
		}
		
		if(!hasprocess){
			conn.setWrite_and_go(CONN_STATES.conn_nread);
		}
		return true;
	}
	
	public void doReadValue(Connection conn,String value) throws IOException{
		ByteBuffer readBuffer = conn.getReadDataBuffer();
		Store_item_type ret;
		if(conn.getRlbytes()!= (readBuffer.position()-conn.getLastMessagePos()-2)){
			out_string(conn, baddatachunk);
		}else{
			ItemUtil.setValue(conn.getItem(), value.getBytes(JcacheGlobalConfig.defaultCahrset));
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
		conn.setWrite_and_go(CONN_STATES.conn_write);
	}
	
	/**
	 * 命令的解析处理
	 * TODO 增加  链式处理设计框架
	 * @param conn
	 * @param readedLine
	 */
	private void process_command(Connection conn,String readedLine)throws IOException{
		String[] params = readedLine.split("\\s+");
		int len = params.length;
		int comm = 0;
		if(len>=2&&(params[0].equals("get")
					||params[0].equals("bget"))){
			process_get_command(conn,params,false);
		}else if(len>=2&&params[0].equals("gets")){
			process_get_command(conn,params,true);
		}else if((len==5||len==6)&&(
				(params[0].equals("add")&&(comm = Command.NREAD_ADD)>0)||
				(params[0].equals("set")&&(comm = Command.NREAD_SET)>0)||
				(params[0].equals("replace")&&(comm = Command.NREAD_REPLACE)>0)||
				(params[0].equals("prepend")&&(comm = Command.NREAD_PREPEND)>0)||
				(params[0].equals("append")&&(comm = Command.NREAD_APPEND)>0)
				)){
				process_update_command(conn,params,comm,false);
		}else if((len==6||len==7)&&params[0].equals("cas")&&(comm = Command.NREAD_CAS)>0){
				process_update_command(conn,params,comm,true);
		}else if(len>=2&&len<=4&&"delete".equals(params[0])){
			process_delete_command(conn,params);
		}else if((len==3||len==4)&&"incr".equals(params[0])){
			process_arithmetic_command(conn,params,true);
		}else if((len==3||len==4)&&"decr".equals(params[0])){
			process_arithmetic_command(conn,params,false);
		}else if((len==3||len==4)&&"touch".equals(params[0])){
			process_touch_command(conn,params);
		}else if(len == 1&&"version".equals(params[0])){
			out_string(conn,ByteBuffer.wrap(("VERSION "+JcacheGlobalConfig.version).getBytes()));
			conn.setWrite_and_go(CONN_STATES.conn_write);
		}else if(len ==1 &&"quit".equals(params[0])){
			conn.setWrite_and_go(CONN_STATES.conn_closing);
		}else{
			out_string(conn, ERROR);
			conn.setWrite_and_go(CONN_STATES.conn_write);
		}
	}
	
	private void process_touch_command(Connection conn,String[] params)throws IOException{
		String key = params[1];
		int nkey = key.length();
		long exptime = 0;
		long it;
		if(nkey > JcacheGlobalConfig.KEY_MAX_LENGTH){
			out_string(conn,badformat);
			return;
		}
		
		Pattern pattern = Pattern.compile("[0-9]*");
		Matcher isNum = pattern.matcher(params[2]);
		if(!isNum.matches()){
			out_string(conn, INVALID_EXP);
			return;
		}
		
		exptime = Long.parseLong(params[2])*1000L+System.currentTimeMillis();
		it = JcacheContext.getItemsAccessManager().item_touch(key, nkey, exptime, conn);
		if(it>0){
			JcacheContext.getItemsAccessManager().item_update(it);
//	        pthread_mutex_lock(&c->thread->stats.mutex);
//	        c->thread->stats.touch_cmds++;
//	        c->thread->stats.slab_stats[ITEM_clsid(it)].touch_hits++;
//	        pthread_mutex_unlock(&c->thread->stats.mutex);
			out_string(conn,TOUCHED);
			JcacheContext.getItemsAccessManager().item_remove(it);
		}else{
//	        pthread_mutex_lock(&c->thread->stats.mutex);
//	        c->thread->stats.touch_cmds++;
//	        c->thread->stats.touch_misses++;
//	        pthread_mutex_unlock(&c->thread->stats.mutex);
			out_string(conn,NOT_FOUND);
		}
		conn.setWrite_and_go(CONN_STATES.conn_write);
	}
	
	private void process_arithmetic_command(Connection conn,String[] params,boolean flag)throws IOException{
		String key = params[1];
		int nkey = key.length();
		byte[] tmpbuf = new byte[8];
		if(nkey > JcacheGlobalConfig.KEY_MAX_LENGTH){
			out_string(conn,badformat);
			return;
		}
		Pattern pattern = Pattern.compile("[0-9]*");
		Matcher isNum = pattern.matcher(params[2]);
		if(!isNum.matches()){
			out_string(conn, INVALID_NUM);
			return;
		}
		JcacheContext.setLocal("cas", 0L);
		DELTA_RESULT_TYPE result = JcacheContext.getItemsAccessManager().add_delta(conn, key, nkey,
														flag,
														Long.parseLong(params[2]), 
														tmpbuf);
		switch(result){
		case OK:
			out_string(conn,ByteBuffer.wrap((BytesUtil.BytesToLong(tmpbuf)+"\r\n").getBytes()));
			break;
		case NON_NUMERIC:
			out_string(conn,NON_NUM);
			break;
		case EOM:
			out_string(conn,outofmemory1);
			break;
		case DELTA_ITEM_NOT_FOUND:
//	        pthread_mutex_lock(&c->thread->stats.mutex);
//	        if (incr) {
//	            c->thread->stats.incr_misses++;
//	        } else {
//	            c->thread->stats.decr_misses++;
//	        }
//	        pthread_mutex_unlock(&c->thread->stats.mutex);
			out_string(conn,NOT_FOUND);
			break;
		case DELTA_ITEM_CAS_MISMATCH:
			/* Should never get here */
			break;
		default:
			break;
		}
		conn.setWrite_and_go(CONN_STATES.conn_write);
	}
	
	private void process_delete_command(Connection conn,String[] params){
		String key;
		int nkey;
		long it;
		key = params[1];
		nkey = key.length();
		if(nkey > JcacheGlobalConfig.KEY_MAX_LENGTH){
			out_string(conn,badformat);
			return;
		}
		
//	    if (settings.detail_enabled) {
//	        stats_prefix_record_delete(key, nkey);
//	    }
		
		it = JcacheContext.getItemsAccessManager().item_get(key, nkey, conn);
		
		if(it>0){
			JcacheContext.getItemsAccessManager().item_unlink(it);
			JcacheContext.getItemsAccessManager().item_remove(it);
			out_string(conn,DELETED);
		}else{
			out_string(conn,NOT_FOUND);
		}
		conn.setWrite_and_go(CONN_STATES.conn_write);
	}
	
	private void process_get_command(Connection conn,String[] params,boolean return_cas){
		String key;
		int nkey;
		long it;
		
		for(int i=1;i<params.length;i++){
			key = params[i];
			nkey = key.length();
			
			if(nkey > JcacheGlobalConfig.KEY_MAX_LENGTH){
				out_string(conn,badformat);
				return;
			}
			
			it = JcacheContext.getItemsAccessManager().item_get(key, nkey, conn);  //refcount ++;
//            if (settings.detail_enabled) {
//                stats_prefix_record_get(key, nkey, NULL != it);
//            }
			if(it>0){
				StringBuilder result = new StringBuilder();
				result.append("VALUE ")
				.append(ItemUtil.getKey(it))
				.append(new String(ItemUtil.getSuffix(it)));
				
				if(return_cas){
					result.append(" ").append(ItemUtil.getCAS(it));
				}
				result.append("\r\n")
				.append(new String(ItemUtil.getValue(it))).append("\r\n");
				addWriteQueue(conn,ByteBuffer.wrap(result.toString().getBytes()));
				JcacheContext.getItemsAccessManager().item_remove(it);  //refcount --;
				JcacheContext.getItemsAccessManager().item_update(it);  // 更新 最近访问时间
			}
		}
		
		addWriteQueue(conn,END);
		conn.setWrite_and_go(CONN_STATES.conn_write);
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
			exptime = ItemUtil.REALTIME_MAXDELTA +1000 + System.currentTimeMillis();
		}else{
			exptime = exptime>0?(exptime * 1000l + System.currentTimeMillis()):0;
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
	
	private void addWriteQueue(Connection conn,ByteBuffer badformat){
		ByteBuffer formet = badformat.slice();
		formet.position(formet.limit());
		conn.addWriteQueue(formet);
	}
	
	private void out_string(Connection conn,ByteBuffer badformat){
		ByteBuffer formet = badformat.slice();
		formet.position(formet.limit());
		conn.addWriteQueue(formet);
		conn.enableWrite(true);
	}
}
