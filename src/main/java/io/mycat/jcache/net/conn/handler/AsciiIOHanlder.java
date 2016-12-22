package io.mycat.jcache.net.conn.handler;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;


public class AsciiIOHanlder implements IOHandler {
	
	
    private int lastMessagePos;

    
	/**
	 * 文本协议处理
	 * @param conn
	 * @param buffer
	 * @throws IOException
	 */
	@Override
	public void doReadHandler(Connection conn) throws IOException {
		Command command = null;
		final ByteBuffer readBuffer = conn.getReadDataBuffer();
		int readEndPos = readBuffer.position();
		String readedLine = null;
		for (int i = lastMessagePos; i < readEndPos; i++) {
			// System.out.println(readBuffer.get(i));
			if (readBuffer.get(i) == 13) {// a line finished
				byte[] lineBytes = new byte[i - lastMessagePos];
				readBuffer.position(lastMessagePos);
				readBuffer.get(lineBytes);
				lastMessagePos = i;
				readedLine = new String(lineBytes);
				System.out.println("received line ,lenth:" + readedLine.length() + " value " + readedLine);
				break;
			}
		}
		
		if (readedLine != null) {
			// 取消读事件关注，因为要应答数据
			conn.disableRead();
			// 处理指令 TODO
//			processCommand(conn,readedLine);
		}	}
}
