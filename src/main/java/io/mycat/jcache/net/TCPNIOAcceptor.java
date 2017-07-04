package io.mycat.jcache.net;




import io.mycat.jcache.enums.protocol.Protocol;
import io.mycat.jcache.net.conn.ConnectIdGenerator;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.IOHandlerFactory;
import io.mycat.jcache.setting.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;


/**
 * @author liyanjun
 */
public final class TCPNIOAcceptor extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(TCPNIOAcceptor.class);
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final NIOReactorPool reactorPool;


    public TCPNIOAcceptor(String bindIp, int port, NIOReactorPool reactorPool, int backlog, AcceptModel aModel)
            throws IOException {
        super.setName("nioacceptor");
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        /** 设置TCP属性 */
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024 * 16 * 2);
        serverChannel.bind(new InetSocketAddress(bindIp, port), backlog);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.reactorPool = reactorPool;

        if(AcceptModel.MEMCACHE == aModel){
            Settings.binding_protocol=Protocol.negotiating;
        }else if(AcceptModel.REDIS == aModel){
            Settings.binding_protocol=Protocol.resp;
        }

    }

    @Override
    public void run() {
        final Selector selector = this.selector;
        for (; ; ) {
            try {
                selector.select(500L);
                Set<SelectionKey> keys = selector.selectedKeys();
                try {
                    keys.forEach(this::handleKey);
                } finally {
                    keys.clear();
                }
            } catch (Throwable e) {
                logger.warn(getName(), e);
            }
        }
    }

    private void handleKey(SelectionKey key) {
        if (key.isValid() && key.isAcceptable()) {
            accept();
        } else {
            key.cancel();
        }
    }

    /**
     * 接受新连接
     */
    private void accept() {
        SocketChannel channel = null;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("===accepted new client connection");
            }
            channel = serverChannel.accept();
            channel.configureBlocking(false);
            //构建Connection
            Connection conn = buildConnection(channel);
            //将新连接派发至reactor进行异步处理
            registerNewClient(conn);
        } catch (Throwable e) {
            closeChannel(channel);
            logger.warn(getName(), e);
        }
    }

    private Connection buildConnection(SocketChannel channel) {
        return new Connection(channel)
                .setId(ConnectIdGenerator.getINSTNCE().getId())
                .setProtocol(Settings.binding_protocol)
                .setIOHanlder(IOHandlerFactory.getHandler(Settings.binding_protocol));
    }

    private void registerNewClient(Connection conn) {
        NIOReactor reactor = reactorPool.getNextReactor();
        reactor.registerNewClient(conn);
    }

    private static void closeChannel(SocketChannel channel) {
        if (channel == null) {
            return;
        }
        Socket socket = channel.socket();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
        }
    }

    enum AcceptModel{
        MEMCACHE("memcache"),
        REDIS("redis"),;

       AcceptModel(String model){
           this.model = model;
       }

        private String model;

        public String getModel() {
            return model;
        }
    }

}