
echo check JAVA_HOME & java
if [ "$JAVA_HOME" == "" ]; then
	echo ---------------------------------------------------
	echo WARN: JAVA_HOME environment variable is not set. 
	echo ---------------------------------------------------
else
	JAVA_CMD=$JAVA_HOME/bin/java
	if [ ! -x "$JAVA_CMD"/bin/java ]; then
		echo set HOME_DIR
		cd ..
		CURR_DIR= .
		JCACHE_HOME= .
		$JAVA_CMD -server -XX:+AggressiveOpts -DJCACHE_HOME=$JCACHE_HOME -XX:MaxDirectMemorySize=4G -cp "../config/*;../lib/*" io.mycat.jcache.net.JcacheMain 
	fi
fi