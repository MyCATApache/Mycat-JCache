package io.mycat.jcache.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置加载器
 * @author lyj
 *
 */
public class ConfigLoader {
	
	private static final String SYS_HOME = "JCACHE_HOME";
	
	private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
	
	/**
	 * 默认配置文件路径
	 */
	private static final String filepath = "config.properties";
	// 缓存所有的属性配置
	private static Properties properties = new Properties();
		
	private ConfigLoader(){}
	
	public static void loadProperties(String path) throws IOException{
		if(path==null||"".equals(path)){
			 path = filepath;
		}

		if(logger.isInfoEnabled()){
			logger.info("Loading properties file from " + path);
		}
		
		File prop = getProperties(path);

        try (InputStream ins = new FileInputStream(prop)){
				properties.load(ins);
		} catch (IOException e) {
			if(logger.isErrorEnabled()){
				logger.error("Could not load properties from "+path+":"+e.getMessage());
			}else{
				throw e;
			}
		}
	}
	
	
	
	private static File getProperties(String path){
		String jcachehome = getJcacheHome();
		System.out.println();
		if(jcachehome!=null){
			File home = new File(jcachehome);
	        File conf = new File(home, "config");
	        File prop = new File(conf, path);
	        return prop;
		}else{
			String floder = ConfigLoader.class.getClassLoader().getResource("").getPath();
			return new File(floder+path);
		}
	}
	
	private static String getJcacheHome(){
		return System.getProperty(SYS_HOME);
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
