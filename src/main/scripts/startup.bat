@echo off

rem check JAVA_HOME & java
set "JAVA_CMD=%JAVA_HOME%/bin/java"
if "%JAVA_HOME%" == "" goto noJavaHome
if exist "%JAVA_HOME%\bin\java.exe" goto mainEntry
:noJavaHome
rem ---------------------------------------------------
rem WARN: JAVA_HOME environment variable is not set. 
rem ---------------------------------------------------
set "JAVA_CMD=java"
:mainEntry
REM set HOME_DIR
set "CURR_DIR=%cd%"
cd ..
set "JCACHE_HOME=%cd%"
cd %CURR_DIR%

rem Get remaining unshifted command line arguments and save them in the

set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

"%JAVA_CMD%" -server -XX:+AggressiveOpts -DJCACHE_HOME=%JCACHE_HOME% -XX:MaxDirectMemorySize=4G -cp "..\config\*;..\lib\*" io.mycat.jcache.net.JcacheMain %CMD_LINE_ARGS%