JCache NIO模型涉及到的概念:

1.JcacheMain 是jcache的启动类，在该类调用tcp协议监听类

2.TCPNIOAcceptor 是TCP监听逻辑的主要实现，创建专门的线程来处理所有的 IO 事件，并负责分发

3.NIOReactor 是reactor的实现，专门负责维护连接请求队列，包括存放连接请求进入队列、注册selector的读写事件

4.NIOReactorPool 是rector线程池处理类  也是acceptor线程分配 连接给 具体某一个rector 线程的策略上下文