package io.mycat.jcache.crawler;

import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.jcache.setting.Settings;

/**
 * item爬虫常量定义
 * @author Tommy
 *
 */
public class CrawlerExpiredData {
	public static AtomicBoolean lock = new AtomicBoolean(false);
	public CrawlerstatsT crawlerstats[] = new CrawlerstatsT[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
    /* redundant with crawlerstats_t so we can get overall start/stop/done */
	public long start_time;
	public long end_time;
	public boolean crawl_complete;
	public boolean is_external; /* whether this was an alloc local or remote to the module. */
}
