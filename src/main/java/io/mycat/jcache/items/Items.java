package io.mycat.jcache.items;

import io.mycat.jcache.crawler.CrawlerExpiredData;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.setting.Settings;

public interface Items {
	
	static final int  LRU_PULL_EVICT = 1;
	static final int LRU_PULL_CRAWL_BLOCKS = 2;
	static final int LARGEST_ID = Settings.POWER_LARGEST;
	
	
	default int CLEAR_LRU(int id){
		return id & ~(3<<6);
	}
	
	long get_cas_id();
	
	long do_item_alloc(String key,int nkey,int flags,long exptime,int nbytes);
	
	void item_free(long it);
	
	boolean item_size_ok(int nkey,int flags,int nbytes);
	
	boolean do_item_link(long it,long hv);
	
	void do_item_unlink(long it,long hv);
	
	void do_item_unlink_nolock(long it,long hv);
	
	void do_item_remove(long it);
	
	void do_item_update(long it);
	
	void do_item_update_nolock(long it);
	
	boolean do_item_replace(long it,long new_it,long hv);
	
	boolean item_is_flushed(long it);
	
	void do_item_linktail_q(long it);
	
	void do_item_unlinktail_q(long it);
	
	long do_item_crawl_q(long it);
	
	long item_cachedump(int slabs_clsid, int limit, long bytes);
//	void item_stats(ADD_STAT add_stats, void *c);
	void do_item_stats_add_crawl(int i,long reclaimed,
			long unfetched, long checked);
//	void item_stats_totals(ADD_STAT add_stats, void *c);
	/*@null@*/
//	void item_stats_sizes(ADD_STAT add_stats, void *c);
	void item_stats_sizes_init();
//	void item_stats_sizes_enable(ADD_STAT add_stats, void *c);
//	void item_stats_sizes_disable(ADD_STAT add_stats, void *c);
	void item_stats_sizes_add(long it);
	void item_stats_sizes_remove(long it);
	boolean item_stats_sizes_status();
	
	long do_item_get(String key,int nkey,long hv,Connection conn);
	
	long do_item_touch(String key,int nkey,long exptime,long hv,Connection conn);
	
	void item_stats_reset();
	
	int start_lru_maintainer_thread();
	
	int stop_lru_maintainer_thread();
	
	int init_lru_maintainer();
	
	void lru_maintainer_pause();
	
	void lru_maintainer_resume();
	
	void lru_maintainer_crawler_check(CrawlerExpiredData cdata );
	
	int lru_maintainer_juggle(int slabs_clsid);
}
