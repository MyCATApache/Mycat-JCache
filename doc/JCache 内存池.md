
JCache 内部存储涉及到的概念:
-----
1.Item  jcache 存储的最小单元.

2.slab  相当于page 的概念,一个slab由多个item组成.

3.slabclass 一个 slabclass 由多个slab组成.

4.pool  全局唯一的一个容器.负责 slabclass,slab,item 的生命周期.

### [item 结构](https://github.com/MyCATApache/Mycat-JCache/blob/master/src/main/java/io/mycat/jcache/util/ItemUtil.java)
|#|字段|说明|
|---|----|-----


|1|prev:|指向上一个item 的内存首地址,没有就是0.<br>
|2|next:|指向下一个item 的内存首地址,没有就是0.<br>
|3|hnext:|hashtable 某一个桶内, 指向下一个item 的内存首地址,hash单向链表<br>
|4|flushtime:|当前item 最近一次访问时间. 每次访问都会刷新该字段.<br>
|5|exptime:|过期时间<br>
|6|nbytes:|value 的总大小, 包含最后两位\r\n 的长度. 客户端传递过来的数据并没有包含\r\n,存储的时候在最后两位附加上的.<br>
|7|refcount:|当前item 被 引用的次数,只在内部使用.<br><br>
|8|nsuffix:|flags 和 nbytes 的字符串长度.<br>
            格式为: '' "flags" '' "nbytes" \r \n  .  '' 代表  一个空的字符
            例如: 如果 flags=32 （32 代表字符串）, nbytes=15 （ 15 代表 value 的byte[] 长度.）.
                  那么 nsuffix = ''32''15\r\n. 
|9|it_flags:   
