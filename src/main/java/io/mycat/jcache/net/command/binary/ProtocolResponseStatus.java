package io.mycat.jcache.net.command.binary;

/**
 * ProtocolResponseStatus
 * @author  yanglinlin 完善+增加注释
 */
public enum ProtocolResponseStatus {
    PROTOCOL_BINARY_RESPONSE_SUCCESS((short)0),//success
    PROTOCOL_BINARY_RESPONSE_KEY_ENOENT((short)1),//key not found
    PROTOCOL_BINARY_RESPONSE_KEY_EEXISTS((short)2),//key exists
    PROTOCOL_BINARY_RESPONSE_E2BIG((short)3),//value too large
    PROTOCOL_BINARY_RESPONSE_EINVAL((short)4),//IInvalid arguments
    PROTOCOL_BINARY_RESPONSE_NOT_STORED((short)5),//item not stored
    PROTOCOL_BINARY_RESPONSE_DELTA_BADVAL((short)6),//incr/decr on non-numric value
    //the vbucket beglongs to another server 7
    PROTOCOL_BINARY_RESPONSE_AUTH_ERROR((short)20),//authentication error
    PROTOCOL_BINARY_RESPONSE_AUTH_CONTINUE((short)21),//authentication continue
    PROTOCOL_BINARY_RESPONSE_UNKNOWN_COMMAND((short)81),//unknown command
    PROTOCOL_BINARY_RESPONSE_ENOMEM((short)82);//out of memory

    private short status;

    public short getStatus(){
        return status;
    }



     ProtocolResponseStatus(short status){
        this.status = status;
    }
}
