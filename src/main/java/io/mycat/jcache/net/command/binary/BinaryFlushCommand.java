package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;

/**
	Request:
	
	MAY have extras.
	MUST NOT have key.
	MUST NOT have value.
	
	Extra data for flush:
	
	     Byte/     0       |       1       |       2       |       3       |
	        /              |               |               |               |
	       |0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|
	       +---------------+---------------+---------------+---------------+
	      0| Expiration                                                    |
	       +---------------+---------------+---------------+---------------+
	     Total 4 bytes
	Response:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Flush the items in the cache now or some time in the future as specified by the expiration field.
	 See the documentation of the textual protocol for the full description on how to specify the expiration time.
 * @author liyanjun
 *
 */
public class BinaryFlushCommand implements Command{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryFlushCommand.class);
		
	@Override
	public void execute(Connection conn) throws IOException {

		long new_oldest = 0;
		
		if(!Settings.flushEnabled){
			writeResponse(conn, BinaryProtocol.OPCODE_FLUSH, ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_AUTH_ERROR.getStatus(), 0L);
			return;
		}
		
		ByteBuffer extras = readExtras(conn);
		
		long exptime = extras.capacity()>0?extras.getInt(4):0;
		
		exptime = exptime * 1000 + System.currentTimeMillis();
		
		if(exptime > 0){
			new_oldest = ItemUtil.realtime(exptime);
		}else{
			new_oldest = System.currentTimeMillis();
		}
		
		if(Settings.useCas){
			Settings.oldestLive = new_oldest - 1000;
			if(Settings.oldestLive < System.currentTimeMillis()){
				Settings.oldestCas = ItemUtil.get_cas_id();
			}
		}else{
			Settings.oldestLive = new_oldest;
		}
		//TODO STATS
//	    pthread_mutex_lock(&c->thread->stats.mutex);
//	    c->thread->stats.flush_cmds++;
//	    pthread_mutex_unlock(&c->thread->stats.mutex);
		BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),BinaryProtocol.OPCODE_FLUSH,null,null,null,0l);
		writeResponse(conn,header,null,null,null);

	}
}
