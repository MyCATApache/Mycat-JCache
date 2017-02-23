package io.mycat.jcache.crawler;
/**
 * item爬虫状态
 * @author Tommy
 *
 */
public enum CrawlerResultType {
    CRAWLER_OK(0), CRAWLER_RUNNING(1), CRAWLER_BADCLASS(2), CRAWLER_NOTSTARTED(3), CRAWLER_ERROR(4),CRAWLER_EXPIRED(5);
    
	private int value;
	
	CrawlerResultType(int value){
		this.value = value;
	}
	
	public int getValue(){
		return value;
	}
}
