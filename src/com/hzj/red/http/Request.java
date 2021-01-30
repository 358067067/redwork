package com.hzj.red.http;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.hzj.red.server.Connector;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Request extends BaseRequest {

    public String getRealPath(String path) {
        return getServletContext().getRealPath(path);
    }

    private String requestString;
    private String method;
    private String uri;
    private SelectionKey key;
    private Connector connector;

    public Connector getConnector() {
        return connector;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    private int status;

    private void parseMethod() {
        method = StrUtil.subBefore(requestString, " ", false);
    }

    private String queryString;
    private Map<String, String[]> parameterMap;

    private Map<String, String> headerMap;
    private Cookie[] cookies;
    private HttpSession session;

    @Override
    public HttpSession getSession() {
        return session;
    }

    public void setSession(HttpSession session) {
        this.session = session;
    }

    @Override
    public String getMethod() {
        return method;
    }

    public Request(SelectionKey key, String requestHeader, Connector connector) throws IOException {
        this.connector = connector;
        this.key = key;
        this.parameterMap = new HashMap();
        this.headerMap = new HashMap<>();
        this.requestString = requestHeader;
//        parseHttpRequest();
        if (StrUtil.isEmpty(requestString))
            return;
        parseMethod();
        parseUri();
        parseParameters();
        parseHeaders();
        parseCookies();
    }

    /**
     * 解析http请求字符串
     * @throws IOException
     */
//    private void parseHttpRequest() throws IOException {
//        InputStream is = ((SocketChannel) this.key.channel()).socket().getInputStream();
//        byte[] bytes = readBytes(is, false);
//        requestString = new String(bytes, "utf-8");
//    }

    /**
     * 解析uri
     */
    private void parseUri() {
        String temp;
        temp = StrUtil.subBetween(requestString, " ", " ");
        if (!StrUtil.contains(temp, '?')) {
            uri = temp;
            return;
        }
        temp = StrUtil.subBefore(temp, '?', false);
        uri = temp;
    }

    private void parseParameters() {
        if ("GET".equals(this.getMethod())) {
            String url = StrUtil.subBetween(requestString, " ", " ");
            if (StrUtil.contains(url, '?')) {
                queryString = StrUtil.subAfter(url, '?', false);
            }
        }
        if ("POST".equals(this.getMethod())) {
            queryString = StrUtil.subAfter(requestString, "\r\n\r\n", false);
        }
        if (null == queryString)
            return;
        queryString = URLUtil.decode(queryString);

        String[] parameterValues = queryString.split("&");
        if (null != parameterValues) {
            for (String parameterValue : parameterValues) {
                String[] nameValues = parameterValue.split("=");
                String name = nameValues[0];
                String value = nameValues[1];

                String values[] = parameterMap.get(name);
                if (null == values) {
                    values = new String[]{value};
                    parameterMap.put(name, values);
                } else {
                    values = ArrayUtil.append(values, value);
                    parameterMap.put(name, values);
                }
            }
        }
    }

    public void parseHeaders() {
        StringReader stringReader = new StringReader(requestString);
        List<String> lines = new ArrayList<>();
        IoUtil.readLines(stringReader, lines);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (0 == line.length())
                break;
            String[] segs = line.split(":");
            String headerName = segs[0].toLowerCase();
            String headerValue = segs[1];

            headerMap.put(headerName, headerValue);
        }
    }

    public void parseCookies() {
        List<Cookie> cookieList = new ArrayList<>();
        String cookies = headerMap.get("cookie");
        if (null != cookies) {
            String[] pairs = cookies.split(",");
            for (String pair : pairs) {
                if (StrUtil.isEmpty(pair))
                    continue;
                String[] segs = StrUtil.split(pair, "=");
                String name = segs[0].trim();
                String value = segs[1].trim();
                Cookie cookie = new Cookie(name, value);
                cookieList.add(cookie);
            }
        }
        this.cookies = ArrayUtil.toArray(cookieList, Cookie.class);
    }

    public String getUri() {
        return uri;
    }

    public String getRequestString() {
        return requestString;
    }

    public String getParameter(String name) {
        String values[] = parameterMap.get(name);
        if (null != values && 0 != values.length)
            return values[0];
        return null;
    }

    public Map getParameterMap() {
        return parameterMap;
    }

    public Enumeration getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }

    public String getHeader(String name) {
        if (null == name)
            return null;
        name = name.toLowerCase();
        return headerMap.get(name);
    }

    public Enumeration getHeaderNames() {
        return Collections.enumeration(headerMap.keySet());
    }

    public int getIntHeader(String name) {
        String value = headerMap.get(name);
        return Convert.toInt(value);
    }

    @Override
    public String getLocalAddr() {
        return ((SocketChannel) key.channel()).socket().getLocalAddress().getHostAddress();
    }

    @Override
    public String getLocalName() {
        return ((SocketChannel) key.channel()).socket().getLocalAddress().getHostName();
    }

    public int getLocalPort() {
        return ((SocketChannel) key.channel()).socket().getLocalPort();
    }

    public String getProtocol() {
        return "HTTP:/1.1";
    }

    public String getRemoteAddr() {
        InetSocketAddress isa = (InetSocketAddress) ((SocketChannel) key.channel()).socket().getRemoteSocketAddress();
        String temp = isa.getAddress().toString();

        return StrUtil.subAfter(temp, "/", false);

    }

    public String getRemoteHost() {
        InetSocketAddress isa = (InetSocketAddress) ((SocketChannel) key.channel()).socket().getRemoteSocketAddress();
        return isa.getHostName();

    }

    public int getRemotePort() {
        return ((SocketChannel) key.channel()).socket().getPort();
    }

    public String getScheme() {
        return "http";
    }

    public String getServerName() {
        return getHeader("host").trim();
    }

    public int getServerPort() {
        return getLocalPort();
    }

    public String getRequestURI() {
        return uri;
    }

    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0) {
            port = 80; // Work around java.net.URL bug
        }
        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80)) || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return url;
    }


    public Cookie[] getCookies() {
        return cookies;
    }

    public String getJSessionIdFromCookie() {
        if (null == cookies)
            return null;
        for (Cookie cookie : cookies) {
            if ("JSESSIONID".equals(cookie.getName()))
                return cookie.getValue();
        }
        return null;
    }

    public static byte[] readBytes(InputStream is, boolean fully) throws IOException {
        int buffer_size = 1024;
        byte buffer[] = new byte[buffer_size];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int length = is.read(buffer);
            if (-1 == length)
                break;
            baos.write(buffer, 0, length);
            if (!fully && length != buffer_size)
                break;
        }
        byte[] result = baos.toByteArray();
        return result;
    }
}
