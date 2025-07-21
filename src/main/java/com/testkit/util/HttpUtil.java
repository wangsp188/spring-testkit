package com.testkit.util;

import com.alibaba.fastjson.JSON;
import org.apache.http.HttpException;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpUtil {


    /**
     * 本机ip
     */
    private static String localIp = "127.0.0.1";

    private static final String LOCALHOST_NAME = "localhost";


    static {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            if (allNetInterfaces != null) {
                while (allNetInterfaces.hasMoreElements()) {
                    NetworkInterface netInterface = allNetInterfaces.nextElement();
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress inetAddress = addresses.nextElement();
                        if (inetAddress instanceof Inet4Address) {
                            if (inetAddress.getHostAddress() != null && !inetAddress.getHostAddress().isEmpty() && !inetAddress.getHostAddress().startsWith("127")) {
                                localIp = inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * 增强版POST请求（支持本机地址重试）
     */
    public static <T, R> R sendPost(String url, T request, Class<R> responseType,
                                    Integer connectSecond, Integer timeoutSecond) throws Exception {
        try {
            return sendPostOnce(url, request, responseType, connectSecond, timeoutSecond);
        } catch (Throwable firstEx) {
            String altUrl = switchLocalHost(url);
            System.err.println("Testkit switch_url,  url: " + url+", to_url: " + altUrl);
            firstEx.printStackTrace();
            if (altUrl == null) {
                throw firstEx;
            }
            try {
                return sendPostOnce(altUrl, request, responseType, connectSecond, timeoutSecond);
            } catch (Throwable secondEx) {
                secondEx.printStackTrace();
                throw firstEx;
            }

        }
    }

    /**
     * 切换本机地址（localhost ↔ IP）
     */
    private static String switchLocalHost(String originalUrl) {
        try {
            URL url = new URL(originalUrl);
            String host = url.getHost();
            if (host.equals(LOCALHOST_NAME)) {
                return new URL(url.getProtocol(), localIp, url.getPort(), url.getFile()).toString();
            } else if (host.equals(localIp)) {
                return new URL(url.getProtocol(), LOCALHOST_NAME, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 单次请求执行逻辑
     */
    private static <T, R> R sendPostOnce(String url, T request, Class<R> responseType,
                                         Integer connectSecond, Integer timeoutSecond) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setConnectTimeout(connectSecond != null ? (int) TimeUnit.SECONDS.toMillis(connectSecond) : 5000);
            connection.setReadTimeout(timeoutSecond != null ? (int) TimeUnit.SECONDS.toMillis(timeoutSecond) : 10000);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Java/HttpURLConnection");
            connection.setRequestProperty("Connection", "close");

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = JSON.toJSONBytes(request);
                os.write(input, 0, input.length);
            }

            int status = connection.getResponseCode();
            InputStream is = (status == HttpURLConnection.HTTP_OK) ?
                    connection.getInputStream() : connection.getErrorStream();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP Error " + status + ": " + response);
                }
                if(response.isEmpty()){
                    throw new IOException("HTTP Error " + status + ": [response is empty]");
                }
                return JSON.parseObject(response.toString(), responseType);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }


    // 编码：Map → URL参数字符串
    public static String encode(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            String key1 = entry.getKey();
            Object value1 = entry.getValue();
            if (key1 == null || value1 == null) {
                continue;
            }

            String key = encodeComponent(key1);
            String value = encodeComponent(value1.toString());
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    // URL组件编码（兼容空格处理）
    private static String encodeComponent(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
                    .replace("+", "%20"); // 将+替换为%20
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }

}