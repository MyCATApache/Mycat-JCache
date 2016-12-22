package io.mycat.jcache.net.command;

import io.mycat.jcache.net.conn.handler.BinaryProtocol;

/**
 * 命令类型  同时支持 二进制 /文本协议查找
 * @author lyj
 *
 */
public enum CommandType {
	
	get(BinaryProtocol.OPCODE_GET),
	getq(BinaryProtocol.OPCODE_GETQ),
	getk(BinaryProtocol.OPCODE_GETK),
	getkq(BinaryProtocol.OPCODE_GETKQ),
	set(BinaryProtocol.OPCODE_SET),
	add(BinaryProtocol.OPCODE_ADD),
	replace(BinaryProtocol.OPCODE_REPLACE),
	delete(BinaryProtocol.OPCODE_DELETE),
	increment(BinaryProtocol.OPCODE_INCREMENT),
	decrement(BinaryProtocol.OPCODE_DECREMENT),
	incrementq(BinaryProtocol.OPCODE_INCREMENTQ),
	decrementq(BinaryProtocol.OPCODE_DECREMENTQ),
	quit(BinaryProtocol.OPCODE_QUIT),
	quitq(BinaryProtocol.OPCODE_QUITQ),
	flush(BinaryProtocol.OPCODE_FLUSH),
	flushq(BinaryProtocol.OPCODE_FLUSHQ),
	noop(BinaryProtocol.OPCODE_NOOP),
	version(BinaryProtocol.OPCODE_VERSION),
	append(BinaryProtocol.OPCODE_APPEND),
	prepend(BinaryProtocol.OPCODE_PREPEND),
	stat(BinaryProtocol.OPCODE_STAT),
	auth_list(BinaryProtocol.OPCODE_AUTH_LIST),
	start_auth(BinaryProtocol.OPCODE_START_AUTH),
	auth_steps(BinaryProtocol.OPCODE_AUTH_STEPS),
	touch(BinaryProtocol.OPCODE_TOUCH),
	gat(BinaryProtocol.OPCODE_GAT),
	gatq(BinaryProtocol.OPCODE_GATQ),
	gatk(BinaryProtocol.OPCODE_GATK),
	gatkq(BinaryProtocol.OPCODE_GATKQ);
	
	CommandType(byte type){
		this.type = type;
	}
	
	private Byte type;
	
	public Byte getByte(){
		return this.type;
	}
	
	public static CommandType getType(Byte type){
		for(CommandType tp:CommandType.values()){
			if(tp.type.equals(type)){return tp;}
		}
		return null;
	}
	
	public static void main(String[] args) {
		System.out.println(1 << 1);
	}
}
