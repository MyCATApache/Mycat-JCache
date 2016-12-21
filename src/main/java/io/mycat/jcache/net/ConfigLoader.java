package io.mycat.jcache.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置加载器
 * @author lyj
 *
 */
public class ConfigLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
	
	/**
	 * 默认配置文件路径
	 */
	private static final String filepath = "config.properties";
	// 缓存所有的属性配置
	private static Properties properties = new Properties();
		
	private ConfigLoader(){}
	
	public static void loadProperties(String path) throws IOException{
		if(path==null&&path==""){
			 path = filepath;
		}

		if(logger.isInfoEnabled()){
			logger.info("Loading properties file from " + path);
		}
		
		String root = Thread.currentThread().getContextClassLoader().getResource("").getPath();
		System.out.println(root);
		try (InputStream is = new FileInputStream(root+ "/" + path);){
				properties.load(is);
		} catch (IOException e) {
			if(logger.isErrorEnabled()){
				logger.error("Could not load properties from "+path+":"+e.getMessage());
			}else{
				throw e;
			}
		}
	}
	
	public static String getProperty(String property){
		return properties.getProperty(property);
	}
	
	public static String getStringProperty(String key,String def){
		String value = properties.getProperty(key);
		if(value==null||"".equals(value)){
			return def;
		}
		return value;
	}
	
	public static int getIntProperty(String key,int def){
		String value = properties.getProperty(key);
		if(value==null||"".equals(value)){
			return def;
		}
		return Integer.parseInt(value);
	}
	
	public static long getLongProperty(String key,long def){
		String value = properties.getProperty(key);
		if(value==null||"".equals(value)){
			return def;
		}
		return Long.parseLong(value);
	}
	
	public static void forEach(){
		properties.forEach((k,v)->System.out.println("propertie key : " + k + " value : " + v));
	}
	

	
}
