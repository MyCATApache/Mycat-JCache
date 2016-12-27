
REM check JAVA_HOME & java
set "JAVA_CMD=%JAVA_HOME%/bin/java"
if "%JAVA_HOME%" == "" goto noJavaHome
if exist "%JAVA_HOME%\bin\java.exe" goto mainEntry
:noJavaHome
echo ---------------------------------------------------
echo WARN: JAVA_HOME environment variable is not set. 
echo ---------------------------------------------------
set "JAVA_CMD=java"
:mainEntry
REM set HOME_DIR
set "CURR_DIR=%cd%"
cd ..
set "JCACHE_HOME=%cd%"
cd %CURR_DIR%
"%JAVA_CMD%" -server -XX:+AggressiveOpts -DJCACHE_HOME=%JCACHE_HOME% -XX:MaxDirectMemorySize=4G -cp "..\config\*;..\lib\*" io.mycat.jcache.net.JcacheMain