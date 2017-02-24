package io.mycat.jcache.crawler;

/**
 * item爬虫接口类
 * @author Tommy
 *
 */
public interface Crawler {
	int start_item_crawler_thread();
	int stop_item_crawler_thread();
	int init_lru_crawler();
	
	CrawlerResultType lru_crawler_crawl(char slabs, CrawlerResultType crawler_run_type, int sfd);
	
	int lru_crawler_start(int[] ids, int remaining, CrawlerResultType crawler_run_type,CrawlerExpiredData cdata, long c,int sfd);
	void lru_crawler_pause();
	void lru_crawler_resume();
}
