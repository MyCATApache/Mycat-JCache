package io.mycat.jcache.net.conn.handler;

/**
 * 二进制相应头
 * @author liyanjun
 *
 */
public class BinaryResponseHeader {
	
	byte magic;
    byte opcode;
    short keylen;
    byte extlen;
    byte datatype;
    short status;
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
	public short getStatus() {
		return status;
	}
	public void setStatus(short status) {
		this.status = status;
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
