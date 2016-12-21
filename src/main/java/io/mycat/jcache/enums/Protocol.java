package io.mycat.jcache.enums;

/**
 * 协议类型
 * @author liyanjun
 *
 */
public enum Protocol {
    binary( 0 ),
    negotiating( 0 ), /* Discovering the protocol */
    ascii( 3 ); /* arbitrary value. */
	
	private int value = 0;

	private Protocol(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public void setValue( int value ) {
		this.value = value;
	}
}
