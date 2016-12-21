package io.mycat.jcache.net.command.binary;

/**
 * Definition of the different command opcodes.
 * See section 3.3 Command Opcodes
 * @author  yangll
 */
public enum ProtocolCommand {
    PROTOCOL_BINARY_CMD_GET((byte)0),
    PROTOCOL_BINARY_CMD_SET((byte)1),
    PROTOCOL_BINARY_CMD_ADD((byte)2),
    PROTOCOL_BINARY_CMD_REPLACE((byte)3),
    PROTOCOL_BINARY_CMD_DELETE((byte)4),
    PROTOCOL_BINARY_CMD_INCREMENT((byte)5),
    PROTOCOL_BINARY_CMD_DECREMENT((byte)6),
    PROTOCOL_BINARY_CMD_QUIT((byte)7),
    PROTOCOL_BINARY_CMD_FLUSH((byte)8),
    PROTOCOL_BINARY_CMD_GETQ((byte)9),
    PROTOCOL_BINARY_CMD_NOOP((byte)10),
    PROTOCOL_BINARY_CMD_VERSION((byte)11),
    PROTOCOL_BINARY_CMD_GETK((byte)12),
    PROTOCOL_BINARY_CMD_GETKQ((byte)13),
    PROTOCOL_BINARY_CMD_APPEND((byte)14),
    PROTOCOL_BINARY_CMD_PREPEND((byte)15),
    PROTOCOL_BINARY_CMD_STAT((byte)16),
    PROTOCOL_BINARY_CMD_SETQ((byte)17),
    PROTOCOL_BINARY_CMD_ADDQ((byte)18),
    PROTOCOL_BINARY_CMD_REPLACEQ((byte)19),
    PROTOCOL_BINARY_CMD_DELETEQ((byte)20),
    PROTOCOL_BINARY_CMD_INCREMENTQ((byte)21),
    PROTOCOL_BINARY_CMD_DECREMENTQ((byte)22),
    PROTOCOL_BINARY_CMD_QUITQ((byte)23),
    PROTOCOL_BINARY_CMD_FLUSHQ((byte)24),
    PROTOCOL_BINARY_CMD_APPENDQ((byte)25),
    PROTOCOL_BINARY_CMD_PREPENDQ((byte)26),
    PROTOCOL_BINARY_CMD_TOUCH((byte)27),
    PROTOCOL_BINARY_CMD_GAT((byte)28),
    PROTOCOL_BINARY_CMD_GATQ((byte)29),
    PROTOCOL_BINARY_CMD_GATK((byte)30),
    PROTOCOL_BINARY_CMD_GATKQ((byte)31),

    PROTOCOL_BINARY_CMD_SASL_LIST_MECHS((byte)32),
    PROTOCOL_BINARY_CMD_SASL_AUTH((byte)33),
    PROTOCOL_BINARY_CMD_SASL_STEP((byte)34),

        /* These commands are used for range operations and exist within
         * this header for use in other projects.  Range operations are
         * not expected to be implemented in the memcached server itself.
         */
    PROTOCOL_BINARY_CMD_RGET     ((byte)35),
    PROTOCOL_BINARY_CMD_RSET     ((byte)36),
    PROTOCOL_BINARY_CMD_RSETQ    ((byte)37),
    PROTOCOL_BINARY_CMD_RAPPEND  ((byte)38),
    PROTOCOL_BINARY_CMD_RAPPENDQ ((byte)39),
    PROTOCOL_BINARY_CMD_RPREPEND ((byte)40),
    PROTOCOL_BINARY_CMD_RPREPENDQ((byte)41),
    PROTOCOL_BINARY_CMD_RDELETE  ((byte)42),
    PROTOCOL_BINARY_CMD_RDELETEQ ((byte)43),
    PROTOCOL_BINARY_CMD_RINCR    ((byte)44),
    PROTOCOL_BINARY_CMD_RINCRQ   ((byte)45),
    PROTOCOL_BINARY_CMD_RDECR    ((byte)46),
    PROTOCOL_BINARY_CMD_RDECRQ   ((byte)47);
        /* End Range operations */

    ProtocolCommand(byte command) {
        this.command = command;
    }

    private byte command;
    
    public byte getByte(){
    	return this.command;
    }
    
}