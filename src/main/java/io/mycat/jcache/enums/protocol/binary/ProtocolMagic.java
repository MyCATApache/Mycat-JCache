package io.mycat.jcache.enums.protocol.binary;

/**
 * ProtocolMagic
 * @author  yangll
 */
public enum ProtocolMagic {
    PROTOCOL_BINARY_REQ((byte)0x80),
    PROTOCOL_BINARY_RES((byte)0x81);

    ProtocolMagic(byte type) {
        this.type = type;
    }

    private byte type;
    
    public byte getByte(){
    	return type;
    }
}
