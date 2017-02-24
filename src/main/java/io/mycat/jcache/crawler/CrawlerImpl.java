package io.mycat.jcache.crawler;
/**
 * item爬虫实现类
 * @author Tommy
 *
 */
public class CrawlerImpl implements Crawler {

	@Override
	public int start_item_crawler_thread() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int stop_item_crawler_thread() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int init_lru_crawler() {
		// TODO Auto-generated method stub
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
