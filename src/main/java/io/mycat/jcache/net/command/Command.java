package io.mycat.jcache.net.command;

import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.JcacheGlobalConfig;

/**
 * 命令接口
 * @author liyanjun
 *
 */

public interface Command {
	
	Charset cs = Charset.forName (JcacheGlobalConfig.defaultCahrset);
	
	static final Logger logger = LoggerFactory.getLogger(Command.class);
	
	public final int NREAD_ADD = 1;
	
	public final int NREAD_SET = 2;
	
	public final int NREAD_REPLACE = 3;
	
	public final int NREAD_APPEND = 4;
	
	public final int NREAD_PREPEND = 5;
	
	public final int NREAD_CAS = 6;

}
