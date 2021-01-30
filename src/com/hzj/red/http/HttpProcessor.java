package com.hzj.red.http;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.log.LogFactory;
import com.hzj.red.server.Connector;
import com.hzj.red.utils.Constant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class HttpProcessor implements Runnable {
    private SelectionKey key;
    private Request request;
    private Response response;

    public HttpProcessor(SelectionKey key, Request request, Response response) {
        this.key = key;
        this.request = request;
        this.response = response;
    }

    public void execute() {
        SocketChannel sc = (SocketChannel) key.channel();
        String uri = request.getUri();
        try {
            if (null == uri)
                return;
            if (Constant.CODE_200 == response.getStatus()) {
                handle200(sc, request, response);
                return;
            }
            if (Constant.CODE_404 == response.getStatus()) {
                handle404(sc, uri);
                return;
            }
        } catch (Exception e) {
            LogFactory.get().error(e);
            handle500(sc, e);
        }
    }

    private void handle200(SocketChannel sc, Request request, Response response)
            throws IOException {
        String contentType = response.getContentType();

        byte[] body = response.getBody();
        String cookiesHeader = response.getCookiesHeader();

        boolean gzip = isGzip(request, body, contentType);

        String headText;
        if (gzip)
            headText = Constant.response_head_200_gzip;
        else
            headText = Constant.response_head_200;

        headText = StrUtil.format(headText, contentType, cookiesHeader);

        if (gzip)
            body = ZipUtil.gzip(body);

        byte[] head = headText.getBytes();
        byte[] responseBytes = new byte[head.length + body.length];
        ArrayUtil.copy(head, 0, responseBytes, 0, head.length);
        ArrayUtil.copy(body, 0, responseBytes, head.length, body.length);

        sc.write(ByteBuffer.wrap(responseBytes));

    }

    protected void handle404(SocketChannel sc, String uri) throws IOException {
        String responseText = StrUtil.format(Constant.textFormat_404, uri, uri);
        responseText = Constant.response_head_404 + responseText;
        byte[] responseBytes = responseText.getBytes("utf-8");
        sc.write(ByteBuffer.wrap(responseBytes));
    }

    protected void handle500(SocketChannel sc, Exception e) {
        try {
            StackTraceElement stes[] = e.getStackTrace();
            StringBuffer sb = new StringBuffer();
            sb.append(e.toString());
            sb.append("\r\n");
            for (StackTraceElement ste : stes) {
                sb.append("\t");
                sb.append(ste.toString());
                sb.append("\r\n");
            }

            String msg = e.getMessage();

            if (null != msg && msg.length() > 20)
                msg = msg.substring(0, 19);

            String text = StrUtil.format(Constant.textFormat_500, msg, e.toString(), sb.toString());
            text = Constant.response_head_500 + text;
            byte[] responseBytes = text.getBytes("utf-8");
            sc.write(ByteBuffer.wrap(responseBytes));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private boolean isGzip(Request request, byte[] body, String mimeType) {
        String acceptEncodings = request.getHeader("Accept-Encoding");
        if (!StrUtil.containsAny(acceptEncodings, "gzip"))
            return false;


        Connector connector = request.getConnector();

        if (mimeType.contains(";"))
            mimeType = StrUtil.subBefore(mimeType, ";", false);

        if (!"on".equals(connector.getCompression()))
            return false;

        if (body.length < connector.getCompressionMinSize())
            return false;

        String userAgents = connector.getNoCompressionUserAgents();
        String[] eachUserAgents = userAgents.split(",");
        for (String eachUserAgent : eachUserAgents) {
            eachUserAgent = eachUserAgent.trim();
            String userAgent = request.getHeader("User-Agent");
            if (StrUtil.containsAny(userAgent, eachUserAgent))
                return false;
        }

        String mimeTypes = connector.getCompressAbleMimeType();
        String[] eachMimeTypes = mimeTypes.split(",");
        for (String eachMimeType : eachMimeTypes) {
            if (mimeType.equals(eachMimeType))
                return true;
        }

        return false;
    }

    @Override
    public void run() {
        execute();
    }
}
