
JCache 内部存储涉及到的概念:
-----
1.Item  jcache 存储的最小单元.

2.slab  相当于page 的概念,一个slab由多个item组成.

3.slabclass 一个 slabclass 由多个slab组成.

4.pool  全局唯一的一个容器.负责 slabclass,slab,item 的生命周期.

