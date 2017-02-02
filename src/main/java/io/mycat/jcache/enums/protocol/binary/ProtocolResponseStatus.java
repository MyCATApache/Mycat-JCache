package io.mycat.jcache.enums.protocol.binary;

/**
 * ProtocolResponseStatus
 * @author  yanglinlin 完善+增加注释
 */
public enum ProtocolResponseStatus {
    PROTOCOL_BINARY_RESPONSE_SUCCESS((short)0x00),//success
    PROTOCOL_BINARY_RESPONSE_KEY_ENOENT((short)0x01),//key not found
    PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS((short)0x02),//key exists
    PROTOCOL_BINARY_RESPONSE_E2BIG((short)0x03),//value too large
    PROTOCOL_BINARY_RESPONSE_EINVAL((short)0x04),//IInvalid arguments
    PROTOCOL_BINARY_RESPONSE_NOT_STORED((short)0x05),//item not stored
    PROTOCOL_BINARY_RESPONSE_DELTA_BADVAL((short)0x06),//incr/decr on non-numric value
    //the vbucket beglongs to another server 7
    PROTOCOL_BINARY_RESPONSE_AUTH_ERROR((short)0x20),//authentication error
    PROTOCOL_BINARY_RESPONSE_AUTH_CONTINUE((short)0x21),//authentication continue
    PROTOCOL_BINARY_RESPONSE_UNKNOWN_COMMAND((short)0x81),//unknown command
    PROTOCOL_BINARY_RESPONSE_ENOMEM((short)0x82);//out of memory

    private short status;

    public short getStatus(){
        return status;
    }



     ProtocolResponseStatus(short status){
        this.status = status;
    }
}
