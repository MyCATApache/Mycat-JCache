/*
 *  文件创建时间： 2016年12月11日
 *  文件创建者: PigBrother(LZY/LZS)二师兄
 *  所属工程: JCache
 *  CopyRights
 *
 *  备注:
 */
package io.mycat.jcache.hash.impl;

import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.hash.Hash;
import io.mycat.jcache.hash.Hash_init;
import io.mycat.jcache.hash.Hash_func_type;
import io.mycat.jcache.hash.impl.algorithm.Jenkins_hash;
import io.mycat.jcache.hash.impl.algorithm.Pig_SDBM_hash;

/**
 * Created by PigBrother(LZS/LZY) on 2016/12/11 18:12.
 */
/**
 *
 * 类功能描述：Hash初始化接口实现类（采用装饰者模式，仅完成一个hash重构方法）
 * @author <a href="mailto:2393647162@qq.com">PigBrother</a>
 * @version 0.0.1
 * @since 2016年12月11日
 *
 */
public class HashImpl implements Hash_init {

    private Hash hash;

    @Override
    public long hash(String key, long... length) {
        return hash.hash(key);
    }

    @Override
    public int hash_init(Hash_func_type type) {
        switch (type){
            case JENKINS_HASH:
                Settings.hash_algorithm="jenkins";
                hash = new Jenkins_hash();
                break;
            case MURMUR3_HASH:
                Settings.hash_algorithm="murmur3";
                break;
            case PIG_HASH:
                Settings.hash_algorithm="Pig_SDBM_hash";
                hash = new Pig_SDBM_hash();
                break;
        }
        return 0;
    }

    public HashImpl(Hash_func_type hashfunc_type){
        hash_init(hashfunc_type);
    }
}
