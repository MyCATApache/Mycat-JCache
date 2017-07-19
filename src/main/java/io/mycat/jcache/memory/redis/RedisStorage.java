package io.mycat.jcache.memory.redis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 暂时先用现成的数据结构存储数据，后续改造
 * @author yangll
 * @create 2017-07-18 21:49
 */

public final class RedisStorage {

    /**
     * String 数据存储结构
     */
    private final static ConcurrentMap<String,Object> stringStorage = new ConcurrentHashMap<>();

    public static ConcurrentMap<String, Object> getStringStorage() {
        return stringStorage;
    }
}
