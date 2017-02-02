package io.mycat.jcache.enums.protocol.binary;

/**
 * Definition of the different command opcodes.
 * See section 3.3 Command Opcodes
 * @author  yangll
 */
public enum ProtocolBinaryCommand {
    PROTOCOL_BINARY_CMD_GET((byte)0x00),
    PROTOCOL_BINARY_CMD_SET((byte)0x01),
    PROTOCOL_BINARY_CMD_ADD((byte)0x02),
    PROTOCOL_BINARY_CMD_REPLACE((byte)0x03),
    PROTOCOL_BINARY_CMD_DELETE((byte)0x04),
    PROTOCOL_BINARY_CMD_INCREMENT((byte)0x05),
    PROTOCOL_BINARY_CMD_DECREMENT((byte)0x06),
    PROTOCOL_BINARY_CMD_QUIT((byte)0x07),
    PROTOCOL_BINARY_CMD_FLUSH((byte)0x08),
    PROTOCOL_BINARY_CMD_GETQ((byte)0x09),
    PROTOCOL_BINARY_CMD_NOOP((byte)0x0a),
    PROTOCOL_BINARY_CMD_VERSION((byte)0x0b),
    PROTOCOL_BINARY_CMD_GETK((byte)0x0c),
    PROTOCOL_BINARY_CMD_GETKQ((byte)0x0d),
    PROTOCOL_BINARY_CMD_APPEND((byte)0x0e),
    PROTOCOL_BINARY_CMD_PREPEND((byte)0x0f),
    PROTOCOL_BINARY_CMD_STAT((byte)0x10),
    PROTOCOL_BINARY_CMD_SETQ((byte)0x11),
    PROTOCOL_BINARY_CMD_ADDQ((byte)0x12),
    PROTOCOL_BINARY_CMD_REPLACEQ((byte)0x13),
    PROTOCOL_BINARY_CMD_DELETEQ((byte)0x14),
    PROTOCOL_BINARY_CMD_INCREMENTQ((byte)0x15),
    PROTOCOL_BINARY_CMD_DECREMENTQ((byte)0x16),
    PROTOCOL_BINARY_CMD_QUITQ((byte)0x17),
    PROTOCOL_BINARY_CMD_FLUSHQ((byte)0x18),
    PROTOCOL_BINARY_CMD_APPENDQ((byte)0x19),
    PROTOCOL_BINARY_CMD_PREPENDQ((byte)0x1a),
    PROTOCOL_BINARY_CMD_TOUCH((byte)0x1c),
    PROTOCOL_BINARY_CMD_GAT((byte)0x1d),
    PROTOCOL_BINARY_CMD_GATQ((byte)0x1e),
    PROTOCOL_BINARY_CMD_GATK((byte)0x23),
    PROTOCOL_BINARY_CMD_GATKQ((byte)0x24),

    PROTOCOL_BINARY_CMD_SASL_LIST_MECHS((byte)0x20),
    PROTOCOL_BINARY_CMD_SASL_AUTH((byte)0x21),
    PROTOCOL_BINARY_CMD_SASL_STEP((byte)0x22),

        /* These commands are used for range operations and exist within
         * this header for use in other projects.  Range operations are
         * not expected to be implemented in the memcached server itself.
         */
    PROTOCOL_BINARY_CMD_RGET     ((byte)0x30),
    PROTOCOL_BINARY_CMD_RSET     ((byte)0x31),
    PROTOCOL_BINARY_CMD_RSETQ    ((byte)0x32),
    PROTOCOL_BINARY_CMD_RAPPEND  ((byte)0x33),
    PROTOCOL_BINARY_CMD_RAPPENDQ ((byte)0x34),
    PROTOCOL_BINARY_CMD_RPREPEND ((byte)0x35),
    PROTOCOL_BINARY_CMD_RPREPENDQ((byte)0x36),
    PROTOCOL_BINARY_CMD_RDELETE  ((byte)0x37),
    PROTOCOL_BINARY_CMD_RDELETEQ ((byte)0x38),
    PROTOCOL_BINARY_CMD_RINCR    ((byte)0x39),
    PROTOCOL_BINARY_CMD_RINCRQ   ((byte)0x3a),
    PROTOCOL_BINARY_CMD_RDECR    ((byte)0x3b),
    PROTOCOL_BINARY_CMD_RDECRQ   ((byte)0x3c);
        /* End Range operations */

    ProtocolBinaryCommand(byte command) {
        this.command = command;
    }

    private Byte command;
    
    public byte getByte(){
    	return this.command;
    }
	
	public static ProtocolBinaryCommand getType(Byte type){
		for(ProtocolBinaryCommand tp:ProtocolBinaryCommand.values()){
			if(tp.command.equals(type)){return tp;}
		}
		return null;
	}
    
}