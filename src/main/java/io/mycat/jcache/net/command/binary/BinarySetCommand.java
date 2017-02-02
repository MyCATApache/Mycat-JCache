package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;


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
		if(logger.isDebugEnabled()){
			logger.debug("execute set command");
		}
		process_bin_update(conn);
		
	}
}
