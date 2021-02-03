package com.cq.socket5;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * @author chenqi
 * @date 2021/2/2 15:16
 */
public class Socket5Server {

    private static final int DEFAULT_PORT=1080;
    private static final int DEFAULT_BUFFER_SIZE=1024;

    private ServerSocketChannel server;
    private Selector selector;

    private ByteBuffer buf;
    private int bufSize;

    private int port;



    public Socket5Server(int bufSize, int port) {
        this.bufSize = bufSize;
        this.port = port;
        buf=ByteBuffer.allocate(bufSize);
        try {
            server=ServerSocketChannel.open();
            server.socket().bind(new InetSocketAddress(this.port));
            server.configureBlocking(false);
            selector=Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            this.run();
        } catch (IOException e) {
            System.out.println("nio服务open失败!端口号："+port+"缓冲池大小:"+bufSize);
        }
    }

    public Socket5Server() {
        this(DEFAULT_PORT,DEFAULT_BUFFER_SIZE);
    }


    private void run() throws IOException {
        while (selector.select()>0){
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while(it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if(!key.isValid()){
                    key.cancel();
                    key.channel().close();
                }
                if(key.isAcceptable()) {
                    accept(key);
                }
                if(key.isConnectable()) {
                    SocketChannel sc= (SocketChannel) key.channel();
                    sc.finishConnect();
                    Socket5Channel channel= (Socket5Channel) key.attachment();
                    sc.register(selector,SelectionKey.OP_READ,channel);
                }

                if(key.isReadable()){
                    try{
                        Socket5Channel channel= (Socket5Channel) key.attachment();
                        channel.read(buf,key,selector);
                    }catch (Exception e){
                        e.printStackTrace();
                        key.cancel();
                        key.channel().close();
                    }

                }
            }
        }
    }



    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        Socket5Channel channel;
        SocketChannel clientChannel = ssc.accept();
        clientChannel.socket().setSoTimeout(3000);
        clientChannel.configureBlocking(false);

        channel=new Socket5Channel();
        clientChannel.register(selector, SelectionKey.OP_READ,channel);
        System.out.println("a new client connected "+clientChannel.getRemoteAddress());


    }

    public static void main(String[] args) {
        Socket5Server socket5Server=new Socket5Server(1024,1888);
    }
}
