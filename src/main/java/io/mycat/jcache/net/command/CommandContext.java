package io.mycat.jcache.net.command;

import java.util.HashMap;
import java.util.Map;

import io.mycat.jcache.net.command.binary.BinaryAddCommand;
import io.mycat.jcache.net.command.binary.BinaryAppendCommand;
import io.mycat.jcache.net.command.binary.BinaryDecrCommand;
import io.mycat.jcache.net.command.binary.BinaryDecrQCommand;
import io.mycat.jcache.net.command.binary.BinaryDeleteCommand;
import io.mycat.jcache.net.command.binary.BinaryFlushCommand;
import io.mycat.jcache.net.command.binary.BinaryGatCommand;
import io.mycat.jcache.net.command.binary.BinaryGatKCommand;
import io.mycat.jcache.net.command.binary.BinaryGatKQCommand;
import io.mycat.jcache.net.command.binary.BinaryGatQCommand;
import io.mycat.jcache.net.command.binary.BinaryGetCommand;
import io.mycat.jcache.net.command.binary.BinaryGetKCommand;
import io.mycat.jcache.net.command.binary.BinaryGetKQCommand;
import io.mycat.jcache.net.command.binary.BinaryGetQCommand;
import io.mycat.jcache.net.command.binary.BinaryIncrCommand;
import io.mycat.jcache.net.command.binary.BinaryIncrQCommand;
import io.mycat.jcache.net.command.binary.BinaryNoopCommand;
import io.mycat.jcache.net.command.binary.BinaryPrependCommand;
import io.mycat.jcache.net.command.binary.BinaryQuitCommand;
import io.mycat.jcache.net.command.binary.BinaryReplaceCommand;
import io.mycat.jcache.net.command.binary.BinarySetCommand;
import io.mycat.jcache.net.command.binary.BinaryTouchCommand;
import io.mycat.jcache.net.command.binary.BinaryVersionCommand;




/**
 * 命令上下文
 * 后期优化 ioc 
 * @author lyj
 *
 */
public class CommandContext {	
	
	private static Map<Object,BinaryCommand> commandMap = new HashMap<>();
	
	//TODO 注册过程待优化.暂时先这样写
	static {
		commandMap.put(CommandType.get, new BinaryGetCommand());
		commandMap.put(CommandType.getq, new BinaryGetQCommand());
		
		commandMap.put(CommandType.getk, new BinaryGetKCommand());
		commandMap.put(CommandType.getkq, new BinaryGetKQCommand());
		
		commandMap.put(CommandType.gat, new BinaryGatCommand());
		commandMap.put(CommandType.gatq, new BinaryGatQCommand());
		
		commandMap.put(CommandType.gatk, new BinaryGatKCommand());
		commandMap.put(CommandType.gatkq, new BinaryGatKQCommand());
		
		commandMap.put(CommandType.set, new BinarySetCommand());
		commandMap.put(CommandType.setq, new BinarySetCommand());
		
		commandMap.put(CommandType.add, new BinaryAddCommand());
		commandMap.put(CommandType.addq, new BinaryAddCommand());
		
		commandMap.put(CommandType.replace, new BinaryReplaceCommand());
		commandMap.put(CommandType.replaceq, new BinaryReplaceCommand());
		
		commandMap.put(CommandType.delete, new BinaryDeleteCommand());
		commandMap.put(CommandType.deleteq, new BinaryDeleteCommand());
		
		commandMap.put(CommandType.increment, new BinaryIncrCommand());
		commandMap.put(CommandType.incrementq, new BinaryIncrQCommand());
		
		commandMap.put(CommandType.decrement, new BinaryDecrCommand());
		commandMap.put(CommandType.decrementq, new BinaryDecrQCommand());
		
		commandMap.put(CommandType.quit, new BinaryQuitCommand());
		commandMap.put(CommandType.quitq, new BinaryQuitCommand());
		
		commandMap.put(CommandType.flush, new BinaryFlushCommand());
		commandMap.put(CommandType.flushq, new BinaryFlushCommand());
		
		commandMap.put(CommandType.append, new BinaryAppendCommand());
		commandMap.put(CommandType.appendq, new BinaryAppendCommand());
		
		commandMap.put(CommandType.prepend, new BinaryPrependCommand());
		commandMap.put(CommandType.prependq, new BinaryPrependCommand());
		
		commandMap.put(CommandType.noop, new BinaryNoopCommand());
		commandMap.put(CommandType.version, new BinaryVersionCommand());
		commandMap.put(CommandType.stat, new BinaryVersionCommand());
		commandMap.put(CommandType.touch, new BinaryTouchCommand());
	}
	
	private CommandContext(){}
	
	public static BinaryCommand getCommand(Byte key){
		CommandType type = CommandType.getType(key);
		if(type==null){
			return null;
		}
		return commandMap.get(type);
	}
	
	public static BinaryCommand getCommand(String key){
		CommandType type = CommandType.valueOf(key);
		if(type==null){
			return null;
		}
		return commandMap.get(type);
	}
}
