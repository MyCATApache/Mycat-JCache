package io.mycat.jcache.crawler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import io.mycat.jcache.context.JcacheContext;
import io.mycat.jcache.enums.DELTA_RESULT_TYPE;
import io.mycat.jcache.enums.ItemFlags;
import io.mycat.jcache.enums.Store_item_type;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.BytesUtil;
import io.mycat.jcache.util.ItemChunkUtil;
import io.mycat.jcache.util.ItemUtil;
import io.mycat.jcache.util.UnSafeUtil;

/**
 * 
 * 
 * @author liyanjun
 *
 */
@SuppressWarnings("restriction")
public class CrawlersAccessManager {
			
	private Crawler crawlers;
	
	public CrawlersAccessManager(){
		this.crawlers = new CrawlerImpl();
	}
	
	public void init_lru_crawler(){
		crawlers.init_lru_crawler();
	}
	
}
