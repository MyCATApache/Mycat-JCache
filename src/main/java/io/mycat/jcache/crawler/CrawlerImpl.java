package io.mycat.jcache.crawler;

import java.util.concurrent.atomic.AtomicBoolean;

import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemStatsUtil;

/**
 * item爬虫实现类
 * @author Tommy
 *
 */
public class CrawlerImpl implements Crawler {
	
	static volatile int do_run_lru_crawler_thread = 0;
	private static AtomicBoolean lru_crawler_lock = new AtomicBoolean(false);
	static int lru_crawler_initialized = 0;
	CrawlerModuleT active_crawler_mod;
	
	class ItemCrawlerThread implements Runnable {

		@Override
		public void run() {
			
		}
		
	}
	
	
	ItemCrawlerThread item_crawler_thread = new ItemCrawlerThread();
	
	Thread item_crawler_thread_warper;
	
	@Override
	public int start_item_crawler_thread() {
		int ret;

	    if (Settings.lru_crawler)
	        return -1;
	    
	    while(!lru_crawler_lock.compareAndSet(false, true)){}
	    try {
	    	item_crawler_thread_warper = new Thread(item_crawler_thread);
	    	do_run_lru_crawler_thread = 1;
		} finally {
			lru_crawler_lock.lazySet(false);
		}

	    return 0;
	}

	@Override
	public int stop_item_crawler_thread() {
		int ret;
		while(!lru_crawler_lock.compareAndSet(false, true)){}
		try {
		    do_run_lru_crawler_thread = 0;
		    item_crawler_thread_warper.stop();
		} finally {
			lru_crawler_lock.lazySet(false);
		}
	    
		Settings.lru_crawler = false;
	    
	    return 0;
	}

	@Override
	public int init_lru_crawler() {
		if (lru_crawler_initialized == 0) {
//	       if (pthread_cond_init(&lru_crawler_cond, NULL) != 0) {
//	            fprintf(stderr, "Can't initialize lru crawler condition\n");
//	            return -1;
//	        }
//	        pthread_mutex_init(&lru_crawler_lock, NULL);
//	        active_crawler_mod.c.c = null;
//	        active_crawler_mod.mod = null;
//	        active_crawler_mod.data = null;
	        lru_crawler_initialized = 1;
	    }
	    return 0;
	}

	@Override
	public CrawlerResultType lru_crawler_crawl(char slabs,
			CrawlerResultType crawler_run_type, int sfd) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int lru_crawler_start(int[] ids, int remaining,
			 CrawlerResultType crawler_run_type,CrawlerExpiredData cdata,long c,int sfd) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void lru_crawler_pause() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void lru_crawler_resume() {
		// TODO Auto-generated method stub

	}

}
