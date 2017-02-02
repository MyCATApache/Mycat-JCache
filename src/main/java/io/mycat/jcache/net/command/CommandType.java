package io.mycat.jcache.net.command;

import io.mycat.jcache.enums.protocol.binary.ProtocolBinaryCommand;

/**
 * 命令类型  同时支持 二进制 /文本协议查找
 * @author lyj
 *
 */
public enum CommandType {
	
	get(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GET.getByte()),
	getq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GETQ.getByte()),
	
	getk(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GETK.getByte()),
	getkq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GETKQ.getByte()),
	
	gat(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GAT.getByte()),
	gatq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GETQ.getByte()),
	
	gatk(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GATK.getByte()),
	gatkq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_GATKQ.getByte()),
	
	set(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_SET.getByte()),
	setq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_SETQ.getByte()),
	
	add(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_ADD.getByte()),
	addq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_ADDQ.getByte()),
	
	replace(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_REPLACE.getByte()),
	replaceq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_REPLACEQ.getByte()),
	
	delete(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_DELETE.getByte()),
	deleteq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_RDELETEQ.getByte()),
	
	increment(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_INCREMENT.getByte()),
	incrementq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_INCREMENTQ.getByte()),
	
	
	decrement(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_DECREMENT.getByte()),
	decrementq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_DECREMENTQ.getByte()),
	
	quit(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_QUIT.getByte()),
	quitq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_QUITQ.getByte()),
	
	flush(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_FLUSH.getByte()),
	flushq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_FLUSHQ.getByte()),
	
	append(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_APPEND.getByte()),
	appendq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_APPENDQ.getByte()),
	
	prepend(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_PREPEND.getByte()),
	prependq(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_PREPENDQ.getByte()),
	
	noop(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_NOOP.getByte()),
	version(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_VERSION.getByte()),
	
	
	stat(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_STAT.getByte()),
//	auth_list(BinaryProtocol.OPCODE_AUTH_LIST),
//	start_auth(BinaryProtocol.OPCODE_START_AUTH),
//	auth_steps(BinaryProtocol.OPCODE_AUTH_STEPS),
	touch(ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_TOUCH.getByte());
	
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
