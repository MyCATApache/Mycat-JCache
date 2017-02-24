package io.mycat.jcache.crawler;

/**
 * item爬虫线程状态
 * @author Tommy
 *
 */
public class CrawlerstatsT {
	public int histo[] = new int[61] ;
	public int ttl_hourplus;
	public int noexp;
	public int reclaimed;
	public int seen;
	public long start_time;
	public long end_time;
	public boolean run_complete;
}
