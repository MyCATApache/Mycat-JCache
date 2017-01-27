package io.mycat.jcache.hash.impl.algorithm;

public class Murmur3_64hash implements Murmur3_hash{
	
	private static final long C1 = 0x87c37b91114253d5L;
	private static final long C2 = 0x4cf5ad432745937fL;
	private static final int R1 = 31;
	private static final int R2 = 27;
	private static final int R3 = 33;
	private static final int M = 5;
	private static final int N1 = 0x52dce729;
	private static final int N2 = 0x38495ab5;
	
	/**
	 * Murmur3 64-bit variant. This is essentially MSB 8 bytes of Murmur3
	 * 128-bit variant.
	 *
	 * @param data
	 *            - input byte array
	 * @return - hashcode
	 */
	public long hash64(byte[] data) {
		return hash64(data, data.length, DEFAULT_SEED);
	}

	/**
	 * Murmur3 64-bit variant. This is essentially MSB 8 bytes of Murmur3
	 * 128-bit variant.
	 *
	 * @param data
	 *            - input byte array
	 * @param length
	 *            - length of array
	 * @param seed
	 *            - seed. (default is 0)
	 * @return - hashcode
	 */
	public long hash64(byte[] data, int length, int seed) {
		long hash = seed;
		final int nblocks = length >> 3;

		// body
		for (int i = 0; i < nblocks; i++) {
			final int i8 = i << 3;
			long k = ((long) data[i8] & 0xff) | (((long) data[i8 + 1] & 0xff) << 8)
					| (((long) data[i8 + 2] & 0xff) << 16) | (((long) data[i8 + 3] & 0xff) << 24)
					| (((long) data[i8 + 4] & 0xff) << 32) | (((long) data[i8 + 5] & 0xff) << 40)
					| (((long) data[i8 + 6] & 0xff) << 48) | (((long) data[i8 + 7] & 0xff) << 56);

			// mix functions
			k *= C1;
			k = Long.rotateLeft(k, R1);
			k *= C2;
			hash ^= k;
			hash = Long.rotateLeft(hash, R2) * M + N1;
		}

		// tail
		long k1 = 0;
		int tailStart = nblocks << 3;
		switch (length - tailStart) {
		case 7:
			k1 ^= ((long) data[tailStart + 6] & 0xff) << 48;
		case 6:
			k1 ^= ((long) data[tailStart + 5] & 0xff) << 40;
		case 5:
			k1 ^= ((long) data[tailStart + 4] & 0xff) << 32;
		case 4:
			k1 ^= ((long) data[tailStart + 3] & 0xff) << 24;
		case 3:
			k1 ^= ((long) data[tailStart + 2] & 0xff) << 16;
		case 2:
			k1 ^= ((long) data[tailStart + 1] & 0xff) << 8;
		case 1:
			k1 ^= ((long) data[tailStart] & 0xff);
			k1 *= C1;
			k1 = Long.rotateLeft(k1, R1);
			k1 *= C2;
			hash ^= k1;
		}

		// finalization
		hash ^= length;
		hash = fmix64(hash);

		return hash;
	}

	@Override
	public long hash(String key, long... length) {
		byte[] input = key.getBytes();
		return hash64(input);
	}

}
