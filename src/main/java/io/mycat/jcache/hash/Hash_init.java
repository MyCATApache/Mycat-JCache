/*
 *  文件创建时间： 2016年12月11日
 *  文件创建者: PigBrother(LZY/LZS)二师兄
 *  所属工程: JCache
 *  CopyRights
 *
 *  备注:
 */
package io.mycat.jcache.hash;

/**
 * Created by PigBrother(LZS/LZY) on 2016/12/12 7:22.
 */
/**
 *
 * 类功能描述：Hash初始化接口（采用装饰者模式，仅完成一个hash重构方法）
 * @author <a href="mailto:2393647162@qq.com">PigBrother</a>
 * @version 0.0.1
 * @since 2016年12月11日
 *
 */
public interface Hash_init extends Hash {
    int hash_init(Hash_func_type type);
}
