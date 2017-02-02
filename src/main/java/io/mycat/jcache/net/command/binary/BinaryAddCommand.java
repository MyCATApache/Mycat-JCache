package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;

/**
 * Created by qd on 2016/11/29.
 * @author  yanglinlin
 */
public class BinaryAddCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(BinaryAddCommand.class);

    @Override
    public void execute(Connection conn) throws IOException {
        if(logger.isDebugEnabled()){
			logger.debug("add command");
		}
        process_bin_update(conn);
    }
}
