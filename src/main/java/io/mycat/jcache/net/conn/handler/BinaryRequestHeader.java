package io.mycat.jcache.net.conn.handler;

/**
 * 二进制协议头
 * @author liyanjun
 *
 */
public class BinaryRequestHeader {
	byte magic;
    byte opcode;
    short keylen;
    byte extlen;
    byte datatype;
    short reserved;
    int bodylen;
    int opaque;
    long cas;
    
	public byte getMagic() {
		return magic;
	}
	public void setMagic(byte magic) {
		this.magic = magic;
	}
	public byte getOpcode() {
		return opcode;
	}
	public void setOpcode(byte opcode) {
		this.opcode = opcode;
	}
	public short getKeylen() {
		return keylen;
	}
	public void setKeylen(short keylen) {
		this.keylen = keylen;
	}
	public byte getExtlen() {
		return extlen;
	}
	public void setExtlen(byte extlen) {
		this.extlen = extlen;
	}
	public byte getDatatype() {
		return datatype;
	}
	public void setDatatype(byte datatype) {
		this.datatype = datatype;
	}
	public short getReserved() {
		return reserved;
	}
	public void setReserved(short reserved) {
		this.reserved = reserved;
	}
	public int getBodylen() {
		return bodylen;
	}
	public void setBodylen(int bodylen) {
		this.bodylen = bodylen;
	}
	public int getOpaque() {
		return opaque;
	}
	public void setOpaque(int opaque) {
		this.opaque = opaque;
	}
	public long getCas() {
		return cas;
	}
	public void setCas(long cas) {
		this.cas = cas;
	}
}
