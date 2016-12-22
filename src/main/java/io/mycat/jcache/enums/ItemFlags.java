package io.mycat.jcache.enums;

public enum ItemFlags {
	ITEM_LINKED((byte)1),
	ITEM_CAS((byte)2),
	/* temp */
	ITEM_SLABBED((byte)4),
	/* Item was fetched at least once in its lifetime */
	ITEM_FETCHED((byte)8),
	/* Appended on fetch, removed on LRU shuffling */
	ITEM_ACTIVE((byte)16),
	/* If an item's storage are chained chunks. */
	ITEM_CHUNKED((byte)32), 
	ITEM_CHUNK((byte)64);
	
	private byte flags;
	
	private ItemFlags(byte flags) {
		this.flags = flags;
	}

	public byte getFlags() {
		return flags;
	} 
}
