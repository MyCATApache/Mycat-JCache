package io.mycat.jcache.net;

import java.io.File;
import java.io.IOException;

import io.mycat.jcache.enums.Protocol;

/**
 * jcache 全局配置
 * @author 
 *
 */
public final class JcacheGlobalConfig {
	
	/**
	 * 默认reactor pool 大小
	 */
	public static int defaulePoolSize = Runtime.getRuntime().availableProcessors();
	
	/**
	 * 默认 reactor pool 绑定地址
	 */
	public static final String defaultPoolBindIp = "0.0.0.0";
	
	/**
	 * 默认 reactor 选择策略
	 */
	public static final String defaultReactorSelectStrategy = "ROUND_ROBIN";
	
	/**
	 * 
	 */
	public static final int defaultMaxAcceptNum = 10000;
	
	/**
	 * 默认字符编码
	 */
	public static final String defaultCahrset = "UTF-8";
	
	/** Maximum length of a key */
	public static final int KEY_MAX_LENGTH = 250;


	/** Maximum length of a value */
	public static final long VALUE_MAX_LENGTH=1024*1024;
	
	/** current version */
	public static final String version = "0.5.0";
	
	/** How long an object can reasonably be assumed to be locked before
    	harvesting it on a low memory condition. Default: disabled. */
	public static final int TAIL_REPAIR_TIME_DEFAULT = 0;
	
}
