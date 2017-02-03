#jcache 安装与使用

当前已完成大部分二进制协议.不包括 stats 命令。
可以使用二进制协议连接测试.

##运行环境
jcache 是用java 开发,需要有 java8 以上运行环境。

##下载:
二进制包可以有两种方式获取:
*  下载release 版本 二进制包。当前 JCache Bate 版本 二进制包可用。
*  编译工程.自动生成二进制包.项目采用maven 构建。

##运行:
 默认端口11211. <br>
 如果没有配置内存参数 则使用 -XX:MaxDirectMemorySize 设置. <br>
 如果都没有配置,默认64m <br>
 运行具体参数含义及配置格式 和 memcached 保持一致. <br>

###linux:
./startup.sh -m 1024 
###windows:
Mycat-jcache-0.5\bin>startup.bat -m 1024
