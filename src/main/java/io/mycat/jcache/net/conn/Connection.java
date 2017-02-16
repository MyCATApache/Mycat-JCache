package io.mycat.jcache.net.conn;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.conn.BIN_SUBSTATES;
import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.enums.conn.TRY_READ_RESULT;
import io.mycat.jcache.enums.protocol.Protocol;
import io.mycat.jcache.enums.protocol.binary.ProtocolMagic;
import io.mycat.jcache.net.command.CommandType;
import io.mycat.jcache.net.conn.handler.BinaryRequestHeader;
import io.mycat.jcache.net.conn.handler.IOHandler;
import io.mycat.jcache.net.conn.handler.IOHandlerFactory;
import io.mycat.jcache.setting.Settings;


/**
 * @author liyanjun
 * @author dragonwu
 */
@SuppressWarnings("restriction")
public class Connection implements Closeable, Runnable {

    public static Logger logger = LoggerFactory.getLogger(Connection.class);
    
    private static final int DATA_BUFFER_SIZE = 2048;

    private SelectionKey selectionKey;
    protected final SocketChannel channel;
    private ByteBuffer writeBuffer;  //写缓冲区 
    protected ByteBuffer readBuffer; /* 读缓冲区  默认 2048 会扩容    */
    private int lastMessagePos; // readBuffer 最后读取位置
    
    private LinkedList<ByteBuffer> writeQueue = new LinkedList<ByteBuffer>();
    private AtomicBoolean writingFlag = new AtomicBoolean(false);
    private long id;
    private boolean isClosed;
    private IOHandler ioHandler;  //io 协议处理类
    private Protocol protocol;  //协议类型
    
    private CommandType curCommand; /* current command beging processed */
    private int subCmd; /* 用于add set replace cas  */
    private long cas; /* th cas to return */
    private CONN_STATES state;
    private CONN_STATES write_and_go;  /** which state to go into after finishing current write */
    private BIN_SUBSTATES substate;
    private boolean noreply; /* True if the reply should not be sent. */
    private long item;
    private int rlbytes;/* how many bytes to swallow */  
    
    /**
     * 二进制请求头
     */
    private BinaryRequestHeader binaryHeader = new BinaryRequestHeader();  //当前连接的多个请求 使用同一个 header 对象， 减少对象创建

    public Connection(SocketChannel channel) {

        this.channel = channel;
    }

    public void register(Selector selector) throws IOException {
        
    	state = CONN_STATES.conn_read;
    	
    	readBuffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
    	writeBuffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
        selectionKey = channel.register(selector, SelectionKey.OP_READ);  //注册读事件监听
        // 绑定会话
        selectionKey.attach(this);  //会在 reactor 中被调用
        if (ioHandler != null) {
            ioHandler.onConnected(this);
        }
//        writeBuffer.put("Welcome Mycat-JCache ...\r\nJCache>".getBytes());
//        enableWrite(true);
    }

    @Override
    public void run() {
       boolean stop = false;
       TRY_READ_RESULT res;
       try {
    	   while(!stop){
        	   switch(state){
    	    	   case conn_listening:   // 无效状态
    	    		   stop = true;
    	    		   break;
    	    	   case conn_waiting:
    	    		   selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
    	    		   state = CONN_STATES.conn_read;
    	    		   stop = true;
    	    		   break;
    	    	   case conn_nread:  //  文本命令 telnet 会进入到该状态
    	    		   selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
    	    		   res = try_read_network();
    	    		   switch(res){
	    	    		   case READ_NO_DATA_RECEIVED:
	    	    			   stop = true;
	    	    			   break;
	    	    		   case READ_DATA_RECEIVED:   /* 数据读取完成,开始 处理value 部分 */
	    	    			   ioHandler.doReadHandler(this);
	    	    			   break;
	    	    		   case READ_ERROR:
	    	    			   state = CONN_STATES.conn_closing;
	    	    			   break;
	    	    		   case READ_MEMORY_ERROR: /* Failed to allocate more memory */
	    	    			   /* State already set by try_read_network */
	    	    			   break;
    	    		   }
    	    		   break;
    	    	   case conn_read:
    	    		   res = try_read_network();
    	    		   switch(res){
	    	    		   case READ_NO_DATA_RECEIVED:
	    	    			   state = CONN_STATES.conn_waiting;
	    	    			   break;
	    	    		   case READ_DATA_RECEIVED:   /* 数据读取完成,开始解析命令 */
	    	    			   state = CONN_STATES.conn_parse_cmd;
	    	    			   break;
	    	    		   case READ_ERROR:
	    	    			   state = CONN_STATES.conn_closing;
	    	    			   break;
	    	    		   case READ_MEMORY_ERROR: /* Failed to allocate more memory */
	    	    			   /* State already set by try_read_network */
	    	    			   break;
    	    		   }
    	    		   break;
    	    	   case conn_parse_cmd:
    	    		   if(!try_read_command()){
    	    			   state = CONN_STATES.conn_waiting;
    	    		   }
    	    		   break;
    	    	   case conn_new_cmd:
    	    		   break;
    	    	   case conn_swallow:
    	    		   break;
    	    	   case conn_write:
    	    		   setLastMessagePos(0);
    	    		   readBuffer.clear();
    	    		   asynWrite();
    	    		   state = CONN_STATES.conn_read;
    	    		   stop = true;
    	    		   break;
    	    	   case conn_mwrite:
    	    		   break;
    	    	   case conn_closing:
    	    		   close(" close connection!");
    	    		   stop = true;
    	    		   break;
    	    	   case conn_closed:
    	    		   stop = true;
    	    		   break;
    	    	   case conn_watch:
    	    		   stop = true;
    	    		   break;
    	    	   case conn_max_state:
    	    		   break;
        	   }
        	   if(getWrite_and_go()!=null){
        		   state = getWrite_and_go();
        		   stop = false;
        		   setWrite_and_go(null);
        	   }
           }
		} catch (Exception e) {
			if (e instanceof CancelledKeyException) {
                if (logger.isDebugEnabled()) {
                    logger.debug(this + " socket key canceled");
                }
            } else {
                logger.error("connection id "+id ,e);
            }
            close("program err:" + e.toString());
		}
    }
    
    /*
     * read from network as much as we can, handle buffer overflow and connection
     * close.
     * before reading, move the remaining incomplete fragment of a command
     * (if any) to the beginning of the buffer.
     *
     * To protect us from someone flooding a connection with bogus data causing
     * the connection to eat up all available memory, break out and start looking
     * at the data I've got after a number of reallocs...
     *
     * @return enum try_read_result
     */
    private TRY_READ_RESULT try_read_network() throws IOException{
    	TRY_READ_RESULT godata = TRY_READ_RESULT.READ_NO_DATA_RECEIVED;
    	boolean hasdata = true;
    	while(hasdata){
    		final int got = channel.read(readBuffer);
        	switch (got) {
            case 0: {	
            	if (readBuffer.limit() == readBuffer.capacity()&&readBuffer.position()==readBuffer.limit()) {
            		ByteBuffer newReadBuffer;
            		int newcap = readBuffer.capacity()*2;
            		if(newcap >=Runtime.getRuntime().freeMemory()){
            			setWrite_and_go(CONN_STATES.conn_closing);
            			return TRY_READ_RESULT.READ_MEMORY_ERROR;
            		}
                	newReadBuffer = ByteBuffer.allocate(readBuffer.capacity()*2);
                	newReadBuffer.put(readBuffer.array());
                	newReadBuffer.position(readBuffer.position());
                	readBuffer = newReadBuffer;
                	newReadBuffer = null;
                	setLastMessagePos(0);  //扩容后,重置最后一次读取位置
            	}else if (readBuffer.limit() < readBuffer.capacity()
                        && readBuffer.position() == readBuffer.limit()) {
                    readBuffer.limit(readBuffer.capacity());
                }else{
                	hasdata = false;
                	godata = TRY_READ_RESULT.READ_NO_DATA_RECEIVED;
                }
                break;
            }
            case -1: {
            	hasdata = false;
            	godata = TRY_READ_RESULT.READ_ERROR;
                break;
            }
            default: {
            	godata = TRY_READ_RESULT.READ_DATA_RECEIVED;
            	
            	if(readBuffer.position() < readBuffer.limit()){
            		hasdata = false;
            	}else{
            		continue;
            	}

	            }
	    	}
	    }
    	return godata;
    }
    
    private boolean try_read_command() throws IOException{
        // 处理指令
//      readBuffer.flip();
      if(Objects.equals(Settings.binding_protocol,Protocol.negotiating)){
          byte magic = readBuffer.array()[0];
          dynamicProtocol(magic);
      }
      return ioHandler.doReadHandler(this);
    }

    /**
     * 商定协议
     */
    private void dynamicProtocol(byte magic) {
        if ((magic & 0xff) == (ProtocolMagic.PROTOCOL_BINARY_REQ.getByte() & 0xff)) {
            setProtocol(Protocol.binary);
        } else {
            setProtocol(Protocol.ascii);
        }
        ioHandler = IOHandlerFactory.getHandler(getProtocol());
    }

    /**
     * 异步写
     *
     * @throws IOException
     */
    public void asynWrite() throws IOException {

        while (!writingFlag.compareAndSet(false, true)) {
            // wait until release
        }
        try {
            ByteBuffer theWriteBuf = writeBuffer;
            if (theWriteBuf == null && writeQueue.isEmpty()) {
                disableWrite();
            } else if (theWriteBuf != null&&writeBuffer.position()!=0) {
                writeQueue.add(writeBuffer);
                writeToChannel(theWriteBuf);
            } else if (!writeQueue.isEmpty()) {
                theWriteBuf = writeQueue.removeFirst();
                theWriteBuf.flip();
                writeToChannel(theWriteBuf);
            }
        } finally {
            // release
            writingFlag.lazySet(false);
        }
    }

    private void writeToChannel(ByteBuffer curBuffer) throws IOException {
        int writed = channel.write(curBuffer);
        System.out.println("writed " + writed);
        if (curBuffer.hasRemaining()) {
            System.out.println("writed " + writed + " not write finished ,remains " + curBuffer.remaining());
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            if (curBuffer != this.writeBuffer) {
                writeBuffer = curBuffer;
            }
        } else {
            System.out.println(" block write finished ");
            writeBuffer = null;
            if (writeQueue.isEmpty()) {
                System.out.println(" .... write finished  ,no more data ");
                selectionKey.interestOps((selectionKey.interestOps() & ~SelectionKey.OP_WRITE) | SelectionKey.OP_READ);

            } else {
                ByteBuffer buf = writeQueue.removeFirst();
                buf.flip();
                writeToChannel(buf);  //TODO 可以优化成非递归方式
            }
        }
    }

    public void enableWrite(boolean wakeup) {
    	state = CONN_STATES.conn_write;
        boolean needWakeup = false;
        SelectionKey key = this.selectionKey;
        try {
            logger.info("enable write ");
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            needWakeup = true;
        } catch (Exception e) {
            logger.warn("can't enable write " + e);
        }
        if (needWakeup && wakeup) {
            key.selector().wakeup();
        }
    }

    private void disableWrite() {
        try {
            SelectionKey key = this.selectionKey;
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
//            logger.warn("disable write con " + this);
        } catch (Exception e) {
            logger.warn("can't disable write " + e + " con " + this);
        }
    }

    public void close(String reason) {
        if (!isClosed) {
            closeSocket();
            this.cleanup();
            isClosed = true;
            state = CONN_STATES.conn_closed;
            logger.info("close connection,reason:" + reason + " ," + this.getClass());
            if (ioHandler != null) {
                ioHandler.onClosed(this, reason);
            }
        }
    }

    /**
     * 清理资源
     */

    protected void cleanup() {
        // 清理资源占用
        if (readBuffer != null) {
//        	readBuffer.recycle();
            readBuffer = null;
        }
    }

    public void closeSocket() {

        if (channel != null) {
            boolean isSocketClosed = true;
            try {
                selectionKey.cancel();
                channel.close();
            } catch (Throwable e) {
            }
            boolean closed = isSocketClosed && (!channel.isOpen());
            if (!closed) {
                logger.warn("close socket of connnection failed " + this);
            }

        }
    }

    public ByteBuffer getReadDataBuffer() {
        return this.readBuffer;
    }

    /**
     * 将回写数据加入的写队列中
     */
    public void addWriteQueue(ByteBuffer buffer) {
        writeQueue.add(buffer);
    }

    public CommandType getCurCommand() {
        return curCommand;
    }

    public void setCurCommand(CommandType curCommand) {
        this.curCommand = curCommand;
    }

	public BIN_SUBSTATES getSubstate() {
		return substate;
	}

	public void setSubstate(BIN_SUBSTATES substate) {
		this.substate = substate;
	}
	
    public long getId() {
        return this.id;
    }

    public Connection setId(long id) {
        this.id = id;
        return this;
    }

    public Connection setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public Protocol getProtocol() {
        return this.protocol;
    }

    public Connection setIOHanlder(IOHandler handler) {
        this.ioHandler = handler;
        return this;
    }

    public Connection setBinaryRequestHeader(BinaryRequestHeader binaryHeader) {
        this.binaryHeader = binaryHeader;
        return this;
    }

    public BinaryRequestHeader getBinaryRequestHeader() {
        return this.binaryHeader;
    }

    @Override
    public void close() throws IOException {
        closeSocket();
    }

	public boolean isNoreply() {
		return noreply;
	}

	public void setNoreply(boolean noreply) {
		this.noreply = noreply;
	}

	public CONN_STATES getWrite_and_go() {
		return write_and_go;
	}

	public void setWrite_and_go(CONN_STATES write_and_go) {
		this.write_and_go = write_and_go;
	}

	public int getSubCmd() {
		return subCmd;
	}

	public void setSubCmd(int subCmd) {
		this.subCmd = subCmd;
	}

	public long getCas() {
		return cas;
	}

	public void setCas(long cas) {
		this.cas = cas;
	}

	public long getItem() {
		return item;
	}

	public void setItem(long item) {
		this.item = item;
	}

	public int getLastMessagePos() {
		return lastMessagePos;
	}

	public void setLastMessagePos(int lastMessagePos) {
		this.lastMessagePos = lastMessagePos;
	}

	public int getRlbytes() {
		return rlbytes;
	}

	public void setRlbytes(int rlbytes) {
		this.rlbytes = rlbytes;
	}

	public CONN_STATES getState() {
		return state;
	}

	public void setState(CONN_STATES state) {
		this.state = state;
	}
}
