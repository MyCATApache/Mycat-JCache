package io.mycat.jcache.memory;

import java.nio.ByteBuffer;

/**
 * 
 * @author tangww
 * @author liyanjun
 * @version com.jcache
 * @since 2016年11月29日
 *
 */
@Deprecated
public class Slab {
	final int chunkSize;
	final int itemCount;
	// LinkedList<Item> items;

	public Slab(ByteBuffer buf, int chunkSize, int itemCount) {
		this.chunkSize = chunkSize;
		this.itemCount = itemCount;
//		this.buf = buf;
//		startAddress = ((sun.nio.ch.DirectBuffer) buf).address();
	}

	/**
	 * 设置 关联的slabClass
	 * @param slabc
	 */
	public void setSlabClass(SlabClass slabc) {
	}
	


	@Override
	public String toString() {
		return "Slab [chunkSize=" + chunkSize + ", itemCount=" + itemCount + "]";
	}
}
