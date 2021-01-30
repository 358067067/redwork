package com.hzj.red.server;

import cn.hutool.log.LogFactory;
import com.hzj.red.http.HttpProcessor;
import com.hzj.red.http.Request;
import com.hzj.red.http.Response;
import com.hzj.red.utils.ThreadPoolUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.Time;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Connector implements Runnable {

    int port = 8080;
    private String compression = "on";
    private int compressionMinSize = 20;
    private String noCompressionUserAgents = "gozilla, traviata";
    private String compressAbleMimeType = "text/html,text/xml,text/javascript,application/javascript,text/css,text/plain,text/json";

    public String getCompression() {
        return compression;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public String getCompressAbleMimeType() {
        return compressAbleMimeType;
    }

    //考虑并发 ConcurrentHashMap
    public static volatile Map<String, SocketChannel> keys = Collections.synchronizedMap(new ConcurrentHashMap<>());

    @Override
    public void run() {
        ServerSocketChannel ssc = null;
        try {
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(port));
            ssc.configureBlocking(false);
            Selector selector = Selector.open();
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                if (selector.select() > 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        try {
                            if (key.isAcceptable()) {
                                SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
                                if (sc != null) {
                                    LogFactory.get().info("收到来自{}的请求", sc.getRemoteAddress().toString());
                                    sc.configureBlocking(false);
                                    sc.register(selector, SelectionKey.OP_READ);
                                }
                            }
                            //读事件
                            if (key.isReadable()) {
                                SocketChannel sc = (SocketChannel) key.channel();
                                String reqHeader = "";
                                try {
                                    reqHeader = receive(sc);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return;
                                }
                                if (reqHeader.contains("@@@@remote@@@@")) {
                                    String addr = reqHeader.substring(reqHeader.indexOf(":") + 1);
                                    keys.put(addr, sc);
                                    sc.write(ByteBuffer.wrap("已成功连接服务".getBytes("UTF-8")));
                                } else if (reqHeader.length() > 0) {
                                    Request request = new Request(key, reqHeader, this);
                                    Response response = new Response();
//                                    ThreadPoolUtil.run(new HttpProcessor(key, request, response));
                                    HttpProcessor processor = new HttpProcessor(key, request, response);
                                    processor.execute();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        iterator.remove();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        LogFactory.get().info("Starting ProtocolHandler [http-bio-{}]", port);
        new Thread(this).start();
    }

    private String receive(SocketChannel socketChannel) {
        //声明一个1024大小的缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        byte[] bytes = null;
        int size = 0;
        //定义一个字节数组输出流
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //将socketChannel中的数据写入到buffer中，此时的buffer为写模式，size为写了多少个字节
        try {
            while ((size = socketChannel.read(buffer)) > 0) {
                //将写模式改为读模式
                //The limit is set to the current position and then the position is set to zero.
                //将limit设置为之前的position，而将position置为0，更多java nio的知识会写成博客的
                buffer.flip();
                bytes = new byte[size];
                //将Buffer写入到字节数组中
                buffer.get(bytes);
                //将字节数组写入到字节缓冲流中
                baos.write(bytes);
                //清空缓冲区
                buffer.clear();
            }
        } catch (IOException e) {
            try {
                keys.remove(socketChannel.getRemoteAddress().toString(), socketChannel);
                socketChannel.close();
            } catch (IOException e1) {
                e.printStackTrace();
            }
        }
        //将流转回字节数组
        bytes = baos.toByteArray();
        return new String(bytes);
    }
}
