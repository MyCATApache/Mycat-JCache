/*
 *  文件创建时间： 2016年12月11日
 *  文件创建者: PigBrother(LZY/LZS)二师兄
 *  所属工程: JCache
 *  CopyRights
 *
 *  备注:
 */
package io.mycat.jcache.hash.impl.algorithm;

import io.mycat.jcache.hash.Hash;

/**
 * Created by PigBrother(LZS/LZY) on 2016/12/12 16:41.
 */

/**
 * 类功能描述：Hash算法实现类（采用装饰者模式，仅完成一个hash重构方法）
 * @author <a href="mailto:2393647162@qq.com">PigBrother</a>
 * @version 0.0.1
 * @since 2016年12月11日
 */
public class Pig_SDBM_hash implements Hash {
    @Override
    public long hash(String key, long... length) {
        long hash = 0;
        for (int i = 0; i < key.length(); i++) {
            hash = key.charAt(i) + (hash << 6) + (hash << 16) - hash;
        }
        return hash;
    }
}
