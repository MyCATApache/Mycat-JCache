package io.mycat.jcache.net.conn;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.Protocol;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.CommandType;
import io.mycat.jcache.net.conn.handler.AsciiIOHanlder;
import io.mycat.jcache.net.conn.handler.BinaryIOHandler;
import io.mycat.jcache.net.conn.handler.BinaryProtocol;
import io.mycat.jcache.net.conn.handler.BinaryRequestHeader;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.net.conn.handler.IOHandler;


/**
 * 
 * @author liyanjun
 *
 */
public class Connection implements Closeable,Runnable{
	
    public static Logger logger = LoggerFactory.getLogger(Connection.class);
    
    private SelectionKey selectionKey;
	protected final SocketChannel channel;
	private ByteBuffer writeBuffer;  //读缓冲区   //TODO 将 读写缓冲区优化为一个缓冲区
	protected ByteBuffer readBuffer; //写缓冲区
	private LinkedList<ByteBuffer> writeQueue = new LinkedList<ByteBuffer>();
	private AtomicBoolean writingFlag = new AtomicBoolean(false);
	private long id;
	private boolean isClosed;    
    private IOHandler ioHandler;  //io 协议处理类
    private Protocol protocol;  //协议类型
    private CommandType curCommand;
    
    private Map<Protocol,IOHandler> protMap = new HashMap<>();  //动态解析时，可以缓存当前iohandler 减少重复创建
    
    /**
     * 二进制请求头
     */
    private BinaryRequestHeader binaryHeader = new BinaryRequestHeader();  //当前连接的多个请求 使用同一个 header 对象， 减少对象创建
    

    
    public Connection(SocketChannel channel){
    	
        this.channel = channel;
    }
    
    public long getId(){
    	return this.id;
    }
    
    public void setId(long id){
    	this.id = id;
    }
    
    public void setProtocol(Protocol protocol){
    	this.protocol = protocol;
    }
    
    public Protocol getProtocol(){
    	return this.protocol;
    }
    
    public void setIOHanlder(IOHandler handler){
    	this.ioHandler = handler;
    }
    
    public void setBinaryRequestHeader(BinaryRequestHeader binaryHeader){
    	this.binaryHeader = binaryHeader;
    }
    
    public BinaryRequestHeader getBinaryRequestHeader(){
    	return this.binaryHeader;
    }
    
	@Override
	public void close() throws IOException {
		closeSocket();
	}
		
	public void register(Selector selector)  throws IOException {
		selectionKey = channel.register(selector, SelectionKey.OP_READ);
        readBuffer = ByteBuffer.allocate(1024); // 这里可以修改成从内存模块获取
//        writeBuffer=ByteBuffer.allocate(1024);
//        ioHandler = new IOHandler();
		// 绑定会话
		selectionKey.attach(this);  //会在 reactor 中被调用
		if(ioHandler!=null){
			ioHandler.onConnected(this);
		}
	}
	
	@Override
	public void run() {
		try {
			if (selectionKey.isValid()) {
				if (selectionKey.isReadable()) {
					logger.debug("select-key read");
					asynRead();
				}
				if (selectionKey.isWritable()) {
					logger.debug("select-key writ");
					asynWrite();
				}
			} else {
				logger.debug("select-key cancelled");
				selectionKey.cancel();
			}
		} catch (final Throwable e) {
			if (e instanceof CancelledKeyException) {
				if (logger.isDebugEnabled()) {
					logger.debug(this + " socket key canceled");
				}
			} else {
				logger.warn(this + " " + e);
			}
			close("program err:" + e.toString());
			
		}
	}
	
	/**
	 * 异步读取,该方法在 reactor 中 被调用
	 * @throws IOException
	 */
	public void asynRead() throws IOException{
//		final ConDataBuffer buffer = readBuffer;
//		final int got =  buffer.transferFrom(channel);
		final int got = channel.read(readBuffer);
		switch (got) {
	        case 0: {
	            // 如果空间不够了，继续分配空间读取
//	            if (readBuffer.isFull()) {
//	                //TODO extends
//	            }
	        	if(readBuffer.limit()<readBuffer.capacity()
	        			&&readBuffer.position()==readBuffer.limit()){
	        		readBuffer.limit(readBuffer.capacity());
	        	}
	        	logger.info(" readBuffer pos {}, limit {}, capacity {} ",readBuffer.position(),readBuffer.limit(),readBuffer.capacity());
//	        	close("client closed");
	            break;
	        }
	        case -1: {
	        	close("client closed");
	            break;
	        }
	        default: {
	        	logger.info(" read bytes {}.",got);
	        	// 处理指令
	        	readBuffer.flip();
	        	
	        	if(Protocol.negotiating.equals(JcacheGlobalConfig.prot)){
	            	byte magic = readBuffer.get(0);
	            	if((magic & 0xff)==(BinaryProtocol.MAGIC_REQ & 0xff)){
	            		setProtocol(Protocol.binary);
	            	}else{
	            		setProtocol(Protocol.ascii);
	            	}
	            	ioHandler = getIOHandler(getProtocol());
	            	ioHandler.doReadHandler(this);
	            }
	        }
	 	}
	}
	
	private IOHandler getIOHandler(Protocol prot){
		IOHandler handler = protMap.get(prot);
		if(handler==null){
			if(Protocol.binary.equals(prot)){
				handler = new BinaryIOHandler();
    		}else{
    			handler = new AsciiIOHanlder();
    		}
		}
		return handler;
	}
	
	/**
	 * 异步写
	 * @throws IOException
	 */
	public void asynWrite() throws IOException{
		
		while (!writingFlag.compareAndSet(false, true)) {
			// wait until release
		}
		try {
			ByteBuffer theWriteBuf = writeBuffer;
			if (theWriteBuf==null && writeQueue.isEmpty()) {
				disableWrite();
			} else if(theWriteBuf!=null){
				writeQueue.add(writeBuffer);
				writeToChannel(theWriteBuf);
			}else if(!writeQueue.isEmpty()){
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
				writeBuffer=curBuffer;
			}
		} else {
			System.out.println(" block write finished ");
			writeBuffer=null;
			if (writeQueue.isEmpty()) {
				System.out.println(" .... write finished  ,no more data ");
				selectionKey.interestOps((selectionKey.interestOps() & ~SelectionKey.OP_WRITE)|SelectionKey.OP_READ);
				
			} else {
				ByteBuffer buf = writeQueue.removeFirst();
				buf.flip();
				writeToChannel(buf);  //TODO 可以优化成非递归方式
			}
		}
	}
	
    public void enableWrite(boolean wakeup) {
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
            logger.warn("disable write con " + this);
        } catch (Exception e) {
            logger.warn("can't disable write " + e + " con " + this);
        }
    }
	
	public void disableRead() {
        try {
            SelectionKey key = this.selectionKey;
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            logger.warn("disable write con " + this);
        } catch (Exception e) {
            logger.warn("can't disable write " + e + " con " + this);
        }
    }
	
    public void close(String reason) {
        if (!isClosed) {  
            closeSocket();
            this.cleanup();
            isClosed = true;
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
        if(readBuffer!=null)
        {
//        	readBuffer.recycle();
        	readBuffer=null;
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
	public void addWriteQueue(ByteBuffer buffer){
		writeQueue.add(buffer);
	}

	public CommandType getCurCommand() {
		return curCommand;
	}

	public void setCurCommand(CommandType curCommand) {
		this.curCommand = curCommand;
	}
}
