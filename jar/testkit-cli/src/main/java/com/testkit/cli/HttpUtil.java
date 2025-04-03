package com.testkit.cli;

import com.alibaba.fastjson.JSON;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {  

    /**  
     * 发送POST请求并获取响应内容  
     *  
     * @param url       请求URL  
     * @param request   请求对象  
     * @param responseType 响应对象类型  
     * @param <T>       请求对象类型  
     * @param <R>       响应对象类型  
     * @return          响应对象  
     * @throws Exception 请求或解析时发生错误  
     */  
    public static <T, R> R sendPost(String url, T request, Class<R> responseType) throws Exception {  
        HttpURLConnection connection = null;  
        try {  
            // 打开连接  
            URL requestUrl = new URL(url);  
            connection = (HttpURLConnection) requestUrl.openConnection();  
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

    /**
     * 检查本地 TCP 端口是否可用（所有网络接口）
     */
    public static boolean isTcpPortAvailable(int port) {
        return isTcpPortAvailable(port, null);
    }

    /**
     * 检查指定地址的 TCP 端口是否可用
     */
    public static boolean isTcpPortAvailable(int port, String bindAddress) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            InetSocketAddress address = (bindAddress == null) ?
                    new InetSocketAddress(port) :
                    new InetSocketAddress(bindAddress, port);
            serverSocket.bind(address);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}