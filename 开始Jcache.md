当前已完成大部分二进制协议.stats 命令没有完成.可以使用二进制协议连接.

jcache 是用java 开发,需要有 java8 以上运行环境。


二进制包可以有两种方式获取:

1. 下载release 版本 二进制包。
2. 编译工程.自动生成二进制包.项目采用maven 构建。

  windows 下 可以在命令窗口下运行 bin 目录下的startup.bat 文件。
  
  linux 下 需要运行 startup.sh 文件。

如果需要配置参数 可以以如下形式输入:

    windows 下 Mycat-jcache-0.5\bin>startup.bat -m 1024
    
    linux   下 ./startup.sh -m 1024
    
 具体参数含义及配置格式 和 memcached 保持一致.
 
 默认端口11211.
