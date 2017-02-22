package io.mycat.jcache.crawler;

import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.jcache.setting.Settings;

public class CrawlerExpiredData {
	private static AtomicBoolean lock = new AtomicBoolean(false);
	CrawlerstatsT crawlerstats[] = new CrawlerstatsT[Settings.MAX_NUMBER_OF_SLAB_CLASSES];
    /* redundant with crawlerstats_t so we can get overall start/stop/done */
    long start_time;
    long end_time;
    boolean crawl_complete;
    boolean is_external; /* whether this was an alloc local or remote to the module. */
}
