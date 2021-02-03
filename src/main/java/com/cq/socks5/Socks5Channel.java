package com.cq.socks5;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * @author chenqi
 * @date 2021/2/2 15:33
 */
public class Socks5Channel {
    public static final byte[] CERTIFICATION_METHOD=new byte[]{0x00,0x02};

    private static final int SOCKS_PROTOCOL_4 = 0X04;
    private static final int SOCKS_PROTOCOL_5 = 0X05;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final byte TYPE_IPV4 = 0x01;
    private static final byte TYPE_IPV6 = 0X02;
    private static final byte TYPE_HOST = 0X03;
    private static final byte ALLOW_PROXY = 0X5A;
    private static final byte DENY_PROXY = 0X5B;

    /**
     * 客户端
     */
    public SocketChannel client;

    /**
     * 远端
     */
    public Object remote;

    /**
     * 当前状态
     */
    public int currentState;
    /**
     * 远端连接地址
     */
    private InetSocketAddress dstRemoteAddress;

    /**
     * 代理协议类型
     */
    private int proxyType=-1;



    public Socks5Channel() {
        this.currentState=0;
    }

    public Socks5Channel read(ByteBuffer buf, SelectionKey key, Selector selector) throws IOException {

        switch (this.currentState){
            case Socks5Status.HANDSHAKE:
//                协议格式长度校验
                init(buf, key);
                break;
            case Socks5Status.IDENTITY_AUTHENTICATION:

                break;
            case Socks5Status.EXECUTE_THE_ORDER:
                execCmd(buf, key,selector);
                break;
            case Socks5Status.PROXY_REQUEST:
                proxyRequest(buf,key);
                break;



        }


        return this;
    }

    private void proxyRequest(ByteBuffer buf, SelectionKey key) {
        SocketChannel channel= (SocketChannel) key.channel();
        if (instanceofFunc(remote,SocketChannel.class)){
            SocketChannel remoteSocketChannel= (SocketChannel) remote;
            if(remoteSocketChannel.isConnected()){
//                判断是来自远端的连接
                if( channel.socket().getRemoteSocketAddress().equals(remoteSocketChannel.socket().getRemoteSocketAddress()) ){
                    boolean isNotContect=exchangData(channel,client,buf);
                    if(isNotContect){
                        System.out.println("remote:"+channel.socket().getRemoteSocketAddress()+"断开连接");
                        key.cancel();
                    }
                }
//                判断是来自近端的连接
                if(channel.socket().getRemoteSocketAddress().equals(client.socket().getRemoteSocketAddress())){
                    boolean isNotContect=exchangData(channel,remoteSocketChannel,buf);
                    if(isNotContect){
                        System.out.println("client:"+channel.socket().getRemoteSocketAddress()+"断开连接");
                        key.cancel();
                    }
                }


            }
        }else if(instanceofFunc(remote,ServerSocket.class)){
            ServerSocket remoteServerSocket= (ServerSocket) remote;
            System.out.println(client.socket().getRemoteSocketAddress()+"当前不支持该协议:ServerSocket");
            key.cancel();
        }else if(instanceofFunc(remote,DatagramSocket.class)){
            DatagramSocket remoteDatagramSocket= (DatagramSocket) remote;
            System.out.println(client.socket().getRemoteSocketAddress()+"当前不支持该协议:DatagramSocket");
            key.cancel();
        }
    }

    /**
     * 转发数据
     * @param a
     * @param b
     * @param buf
     * @return 返回a客户端是否断开连接 true：断开，false:保持连接
     */
    private boolean exchangData(SocketChannel a,SocketChannel b,ByteBuffer buf)  {
        boolean result=false;
        buf.clear();
        int total=0;
        int len=0;
        try {
            while ( (len=a.read(buf))>0 ){
                buf.flip();
                total+=len;
                b.write(buf);
                buf.clear();
            }
            System.out.println("转发："+a.getRemoteAddress()+" ------>>>>>------ remote: "+a.getRemoteAddress()+" 共 "+total+" byte");
            if(len==-1)result=true;
        } catch (IOException e) {
            e.printStackTrace();
            result=true;
        }

        return result;
    }


    private void init(ByteBuffer buf,SelectionKey key) throws IOException {
            byte[] data=readBuffer(buf,key);
            if(isSocket5(data)){
                int mlen=data[1];
                int method=0xFF;
                byte[] methods=getByte(data,2,mlen);
                for(int i=0;i<methods.length;i++){
                    for(int j=0;j<CERTIFICATION_METHOD.length;j++){
                        if( methods[i]==CERTIFICATION_METHOD[j] ){
                            method=methods[i];
                            break;
                        }

                    }
                }

                byte[] send=new byte[]{SOCKS_PROTOCOL_5, (byte) method};
                buf.clear();
                buf.put(send);
                buf.flip();
                SocketChannel socketChannel = (SocketChannel)key.channel();
                socketChannel.write(buf);
                if(method==0x00){
                    currentState=2;
                }else {
                    currentState=1;
                }

            }



    }


    public static byte[] readBuffer(ByteBuffer buf,SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel)key.channel();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int len=0;
        buf.clear();
        while ((len=socketChannel.read(buf))>0){
            byte[] temp=new byte[len];
            buf.flip();
            buf.get(temp,0,len);
            buf.clear();
            out.write(temp);
        }
        if(len==-1){
            key.cancel();
            key.channel().close();
        }
        return out.toByteArray();

    }


    private void execCmd(ByteBuffer buf, SelectionKey key, Selector selector) throws IOException {
        SocketChannel socketChannel= (SocketChannel) key.channel();
        this.client=socketChannel;
        byte[] data=readBuffer(buf,key);
        if(isSocket5(data)){
            ByteBuffer rsv = ByteBuffer.allocate(10);
            rsv.put((byte) SOCKS_PROTOCOL_5);
            int cmd=data[1];
            int atyp=data[3];
            String host=null;
            int port=-1;
            host=getHost(data,atyp);
            port=getPort(data);
            System.out.println("client："+socketChannel.getRemoteAddress()+"------>>>>>------remote: "+host+" : "+port);
            this.dstRemoteAddress=new InetSocketAddress(host,port);
            this.proxyType=cmd;
            switch (cmd){
                case 0x01:
                    try {
                        SocketChannel remoteSocketChannel = SocketChannel.open();
                        this.remote=remoteSocketChannel;
                        remoteSocketChannel.configureBlocking(false);
                        remoteSocketChannel.connect(dstRemoteAddress);
                        remoteSocketChannel.register(selector,SelectionKey.OP_CONNECT,this);
                        rsv.put((byte) 0x00);
                    } catch (IOException e) {
                        e.printStackTrace();
                        rsv.put((byte) 0x05);
                    }
                    break;
                case 0x02:
                    try {
                        remote = new ServerSocket(port);
                    } catch (IOException e) {
                        e.printStackTrace();
                        rsv.put((byte) 0x05);
                    }
                    break;
                case 0x03:
                    try {
                        remote=new DatagramSocket();
                    } catch (SocketException e) {
                        e.printStackTrace();
                        rsv.put((byte) 0x05);
                    }
                    rsv.put((byte) 0x00);
                    break;
            }
            rsv.put((byte) 0x00);
            rsv.put((byte) 0x01);
            rsv.put(socketChannel.socket().getLocalAddress().getAddress());
            Short localPort = (short) ((socketChannel.socket().getLocalPort()) & 0xFFFF);
            rsv.putShort(localPort);
            rsv.flip();
            socketChannel.write(rsv);
            this.currentState=3;

        }
    }

    /**
     * 获取端口号
     * @param data
     * @return
     */
    private int getPort(byte[] data) {
        int port=-1;
        byte[] portBytes =getByte(data,data.length-2,2);
        port=ByteBuffer.wrap(portBytes).asShortBuffer().get() & 0xFFFF;
        return port;
    }

    /**
     * 获取远端连接地址
     * @param data
     * @param atyp
     * @return
     */
    private String getHost(byte[] data, int atyp) {
        String host="";
        byte[] tmp;
        switch (atyp){
            case TYPE_IPV4:
                tmp=getByte(data,4,4);
                try {
                    host = InetAddress.getByAddress(tmp).getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                break;
            case TYPE_IPV6:
                tmp=getByte(data,4,6);
                try {
                    host = InetAddress.getByAddress(tmp).getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                break;
            case TYPE_HOST:
                tmp=getByte(data,5,data[4]);
                host=new String(tmp);
                break;
        }


        return host;
    }

    /**
     * 截取字节数组
     * @param data
     * @param offset
     * @param length
     * @return
     */
    private byte[] getByte(byte[] data, int offset, int length) {
        byte[] tmp=new byte[length];
        for(int i=0;i<length;i++){
            tmp[i]=data[offset+i];
        }
        return tmp;
    }

    /**
     * 判断是否是socket5协议头
     * @param data
     * @return
     */
    public static boolean isSocket5(byte[] data){

        boolean result=false;
        if(data!=null&&data.length>0){
            result= data[0]==SOCKS_PROTOCOL_5;
        }
        return result;
    }


    /**
     * 获取远端连接类型
     * @param proxyType
     * @return
     */
    private Class<?> getRemoteClass(int proxyType){
        Class<?> clazz=null;
        switch (proxyType){
            case 0x01:
                clazz=SocketChannel.class;
                break;
            case 0x02:
                clazz=ServerSocket.class;
                break;
            case 0x03:
                clazz=DatagramSocket.class;
                break;
        }
        return clazz;

    }


    private <T> boolean instanceofFunc(Object obj,Class<T> clazz){
        boolean result;
        if (obj == null) {
            result = false;
        } else {
            try {
                T temp=clazz.cast(obj);
//                T temp= (T) obj; // checkcast
                result = true;
            } catch (ClassCastException e) {
                result = false;
            }
        }
        return result;

    }



}
