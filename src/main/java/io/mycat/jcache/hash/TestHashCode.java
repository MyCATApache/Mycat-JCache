/*
 *  文件创建时间： 2016年12月11日
 *  文件创建者: PigBrother(LZY/LZS)二师兄
 *  所属工程: JCache
 *  CopyRights
 *
 *  备注:
 */
package io.mycat.jcache.hash;

import io.mycat.jcache.hash.impl.HashImpl;

import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;


/**
 * Created by PigBrother(LZS/LZY) on 2016/12/12 17:16.
 */

/**
 * 类功能描述：test类  demo
 * <p>
 * 方法调用：
 * Hash hash = new HashImpl(Hash_func_type.PIG_HASH);
 * hash.hash(key); //此处返回 自定义 hashcode
 * 新的hashcode 为 long 类型 冲突率 较 原生的hashcode 小一些
 * 数据如下 若条件允许 可以 生成几乎不冲突的hashcode 条件是接受long[]类型的hashcode数组
 *                                  数据量     :随机字符串不相同的个数:java自带hashcode的不相同个数:优化后的hashcode的不相同个数
 * Tue Dec 13 08:27:41 CST 2016     :1          :1                     :1                           :1
 * Tue Dec 13 08:27:41 CST 2016     :10         :10                    :10                          :10
 * Tue Dec 13 08:27:41 CST 2016     :100        :100                   :100                         :100
 * Tue Dec 13 08:27:42 CST 2016     :1000       :1000                  :1000                        :1000
 * Tue Dec 13 08:27:42 CST 2016     :10000      :10000                 :10000                       :10000
 * Tue Dec 13 08:27:42 CST 2016     :100000     :100000                :99995                       :100000
 * Tue Dec 13 08:27:47 CST 2016     :1000000    :1000000               :999892                      :1000000
 * Tue Dec 13 08:29:55 CST 2016     :10000000   :10000000              :9988403                     :10000000
 * <p>
 * <p>
 * <p>
 * <p>
 * <p> 版权所有：
 * <p> 未经许可，不得以任何方式复制或使用本程序任何部分 <p>
 *
 * @author <a href="mailto:2393647162@qq.com">PigBrother</a>
 * @version 0.0.1
 * @since 2016年12月11日
 */
public class TestHashCode {
    public static void main(String[] args) {
        Hash hash = new HashImpl(Hash_func_type.PIG_HASH);
        Random r = new Random();
        String key;
        int tmp = 1;
        HashSet<Long> hashcode_pig = new HashSet<>();
        HashSet<Integer> hashcode = new HashSet<>();
        HashSet<String> keySet = new HashSet<>();
        System.out.println("数据量:随机字符串不相同的个数:java自带hashcode的不相同个数:优化后的hashcode的不相同个数");
        for (int ii = 0; ii < 8; ii++) {
            hashcode.clear();
            hashcode_pig.clear();
            keySet.clear();
            for (int i = 0; i < tmp * Math.pow(10, ii); i++) {
                key = UUID.randomUUID().toString();
                keySet.add(key);
                hashcode.add(key.hashCode());
                hashcode_pig.add(hash.hash(key));
            }
            System.out.println(new Date().toString() +":"+ tmp * (long) Math.pow(10, ii) + ":" + keySet.size() + ":" + hashcode.size() + ":" + hashcode_pig.size());
        }
    }

}
