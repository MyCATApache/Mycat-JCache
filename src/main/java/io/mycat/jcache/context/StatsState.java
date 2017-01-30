package io.mycat.jcache.context;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global "state" stats. Reflects state that shouldn't be wiped ever.
 * Ordered for some cache line locality for commonly updated counters.
 */
public class StatsState {
    public volatile static AtomicLong curr_items = new AtomicLong(0);
    public volatile static AtomicLong curr_bytes = new AtomicLong(0);
    public volatile static AtomicLong curr_conns = new AtomicLong(0);
    public volatile static AtomicLong hash_bytes = new AtomicLong(0);       /* size used for hash tables */
    public volatile static AtomicLong conn_structs = new AtomicLong(0);
    public volatile static AtomicLong reserved_fds = new AtomicLong(0);
    public volatile static AtomicLong hash_power_level = new AtomicLong(0); /* Better hope it's not over 9000 */
    public volatile static AtomicBoolean hash_is_expanding = new AtomicBoolean(false); /* If the hash table is being expanded */
    public volatile static AtomicBoolean accepting_conns = new AtomicBoolean(false);  /* whether we are currently accepting */
    public volatile static AtomicBoolean slab_reassign_running = new AtomicBoolean(false); /* slab reassign in progress */
    public volatile static AtomicBoolean lru_crawler_running = new AtomicBoolean(false); /* crawl in progress */
}
