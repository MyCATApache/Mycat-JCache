#!/bin/sh

if [ -z "$JAVA_HOME" ]; then
   echo -------------------------------------------------------------
   echo WARN: JAVA_HOME environment variable is not set. 
   echo -------------------------------------------------------------
else
   JAVA_CMD=java
   JCACHE_HOME=$(cd `dirname $0`/..; pwd)
   echo set JCACHE_HOME = $JCACHE_HOME
   echo $JAVA_CMD -server -XX:+AggressiveOpts -DJCACHE_HOME=$JCACHE_HOME -XX:MaxDirectMemorySize=4G -cp ".:../config/*:../lib/*" io.mycat.jcache.net.JcacheMain
   $JAVA_CMD -server -XX:+AggressiveOpts -DJCACHE_HOME=$JCACHE_HOME -XX:MaxDirectMemorySize=4G -cp ".:../config/*:../lib/*" io.mycat.jcache.net.JcacheMain
fi
