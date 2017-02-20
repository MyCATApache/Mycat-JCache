package io.mycat.jcache.enums;

/**
 * LRU类型备注
 * @author Tommy
 *
 */
public enum LRU_TYPE_MAP {
	
	HOT_LRU(0), WARM_LRU(64), COLD_LRU(128), NOEXP_LRU(192);

	private int value;
	
	LRU_TYPE_MAP(int value){
		this.value = value;
	}
	
	public int getValue(){
		return value;
	}
}
