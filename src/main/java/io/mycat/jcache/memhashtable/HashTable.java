/*
 *  文件创建时间： 2016年12月15日
 *  文件创建者: PigBrother(LZY/LZS)二师兄
 *  所属工程: JCache
 *  CopyRights
 *
 *  备注:
 */
package io.mycat.jcache.memhashtable;

import io.mycat.jcache.hash.Hash;
import io.mycat.jcache.hash.Hash_func_type;
import io.mycat.jcache.hash.impl.HashImpl;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;

import java.nio.ByteBuffer;

/**
 * Created by PigBrother(LZS/LZY) on 2016/12/15 7:17.
 */

/**
 * hashtable 作为底层的 提供 find put delect 既可满足需求
 */
public class HashTable {
    private static ByteBuffer cached = ByteBuffer.allocateDirect(Settings.hashsize);
    static Hash hash = new HashImpl(Hash_func_type.PIG_HASH);
/*    static {
        for (int i = 0; i < 0xfffffff; i++) {
            cached.putLong(-1);
        }
    }*/
    
    public static long hash(String key , long ... length){
    	return hash.hash(key,length);
    }

    //艳军提示逻辑地址 0xC0000000  开头 ，根据此原则 改变判断方法
    public static long find(String key) {
        long index = cached.getLong((int) (hash.hash(key) & 0xfffffff));
        if(index != 0)
            do {
                if (ItemUtil.getKey(index).equals(key)) {
                    return index;
                }
            } while ((index = ItemUtil.getHNext(index)) != -1);
        return -1; //调用者需做处理  -1 等于 Store_item_type.NOT_FOUND
    }

    public static long put(String key, long item){
        int index = (int) (hash.hash(key) & 0xfffffff);
        long pre_index = 0;
        long pre_index2 = 0;
        while ((pre_index=cached.get(index))!= 0){
            if(ItemUtil.getKey(pre_index).equals(key))
                return pre_index;
            pre_index2=pre_index;
        }
        if(pre_index2!=0)
            ItemUtil.setHNext(pre_index2,item);
        else
            cached.putLong(index,item);
        return item;
    }

    //20161217艳军发现逻辑bug，fixed
    public static long delect(String key){
        long index = cached.getLong((int) (hash.hash(key) & 0xfffffff));
        long pre_index;
        if(index!=0)
            do {
                pre_index = index;
                if (ItemUtil.getKey(index).equals(key)) {
                    ItemUtil.setHNext(pre_index,ItemUtil.getHNext(index));
                    return index;  //return 需要被进一步处理的item的adrr ，此时 这个item已经不在hashtable里了  ，调用者可以释放 item了
                }
            } while ((index = ItemUtil.getHNext(index)) != -1);
        return -1; //调用者需做处理  -1 等于 Store_item_type.NOT_FOUND
    }
}
