package com.testkit.util;

import com.alibaba.fastjson.JSON;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpUtil {

    /**
     * 发送POST请求并获取响应内容
     *
     * @param url          请求URL
     * @param request      请求对象
     * @param responseType 响应对象类型
     * @param <T>          请求对象类型
     * @param <R>          响应对象类型
     * @return 响应对象
     * @throws Exception 请求或解析时发生错误
     */
    public static <T, R> R sendPost(String url, T request, Class<R> responseType,Integer connectSecond,Integer timeoutSecond) throws Exception {
        HttpURLConnection connection = null;
        try {
            // 打开连接  
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            // 设置超时时间（关键修改）
            connection.setConnectTimeout(connectSecond==null?0:(int) TimeUnit.SECONDS.toMillis(connectSecond)); // 连接超时 5 秒
            connection.setReadTimeout(connectSecond==null?0:(int) TimeUnit.SECONDS.toMillis(timeoutSecond));
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            // 发送 JSON 请求  
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = JSON.toJSONBytes(request);
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                // 解析响应 JSON
                return JSON.parseObject(response.toString(), responseType);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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