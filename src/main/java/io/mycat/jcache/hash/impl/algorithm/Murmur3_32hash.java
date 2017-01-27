package io.mycat.jcache.hash.impl.algorithm;

public class Murmur3_32hash implements Murmur3_hash{
	
	// Constants for 32 bit variant
	private static final int C1_32 = 0xcc9e2d51;
	private static final int C2_32 = 0x1b873593;
	private static final int R1_32 = 15;
	private static final int R2_32 = 13;
	private static final int M_32 = 5;
	private static final int N_32 = 0xe6546b64;

	/**
	 * Murmur3 32-bit variant.
	 *
	 * @param data
	 *            - input byte array
	 * @return - hashcode
	 */
	public int hash32(byte[] data) {
		return hash32(data, data.length, DEFAULT_SEED);
	}

	/**
	 * Murmur3 32-bit variant.
	 *
	 * @param data
	 *            - input byte array
	 * @param length
	 *            - length of array
	 * @param seed
	 *            - seed. (default 0)
	 * @return - hashcode
	 */
	public int hash32(byte[] data, int length, int seed) {
		int hash = seed;
		final int nblocks = length >> 2;

		// body
		for (int i = 0; i < nblocks; i++) {
			int i_4 = i << 2;
			int k = (data[i_4] & 0xff) | ((data[i_4 + 1] & 0xff) << 8) | ((data[i_4 + 2] & 0xff) << 16)
					| ((data[i_4 + 3] & 0xff) << 24);

			// mix functions
			k *= C1_32;
			k = Integer.rotateLeft(k, R1_32);
			k *= C2_32;
			hash ^= k;
			hash = Integer.rotateLeft(hash, R2_32) * M_32 + N_32;
		}

		// tail
		int idx = nblocks << 2;
		int k1 = 0;
		switch (length - idx) {
		case 3:
			k1 ^= data[idx + 2] << 16;
		case 2:
			k1 ^= data[idx + 1] << 8;
		case 1:
			k1 ^= data[idx];

			// mix functions
			k1 *= C1_32;
			k1 = Integer.rotateLeft(k1, R1_32);
			k1 *= C2_32;
			hash ^= k1;
		}

		// finalization
		hash ^= length;
		hash ^= (hash >>> 16);
		hash *= 0x85ebca6b;
		hash ^= (hash >>> 13);
		hash *= 0xc2b2ae35;
		hash ^= (hash >>> 16);

		return hash;
	}

	@Override
	public long hash(String key, long... length) {
		byte[] input = key.getBytes();
		return hash32(input);
	}
	
}
