package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;

/**
Request:

MUST NOT have extras.
MUST have key.
MUST have value.
Response:

MUST NOT have extras.
MUST NOT have key.
MUST NOT have value.
MUST have CAS
These commands will either append or prepend the specified value to the requested key.

 * @author liyanjun
 *
 */
public class BinaryAppendCommand  implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryAppendCommand.class);

	@Override
	public void execute(Connection conn) throws IOException {
        if(logger.isDebugEnabled()){
			logger.debug("append command");
		}
        process_bin_append_prepend(conn);
	}

}
