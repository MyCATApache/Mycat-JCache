package io.mycat.jcache.enums.protocol.binary;


/**
 * Definition of the data types in the packet
 * See section 3.4 Data Types
 * @author  yangll
 */
public enum ProtocolDatatypes {
    PROTOCOL_BINARY_RAW_BYTES((byte)0);

    ProtocolDatatypes(byte type){
        this.type =  type;
    }

    private byte type;
    
    public byte getByte(){
    	return type;
    }
}
