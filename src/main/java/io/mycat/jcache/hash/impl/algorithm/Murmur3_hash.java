package io.mycat.jcache.hash.impl.algorithm;

import io.mycat.jcache.hash.Hash;

public interface Murmur3_hash extends Hash {

	static final int DEFAULT_SEED = 0;

	default long fmix64(long h) {
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= (h >>> 33);
		return h;
	}
}
