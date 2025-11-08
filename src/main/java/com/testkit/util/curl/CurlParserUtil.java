package com.testkit.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class CurlParserUtil {
    /**
     * 该方法是用来解析CURL的入口。
     *
     * @param curl 输入的CURL文本字符串
     * @return 返回解析后生成的CURL实体对象
     */
    public static CurlEntity parse(String curl) {
        if (StringUtils.isBlank(curl)) {
            return null;
        }
        curl = curl.trim();
        if (curl.startsWith("http")) {
//            localhost://
            curl = "curl '" + curl + "'";
        }else if(curl.startsWith("/")) {
            curl = "curl 'http://unknowndomain" + curl + "'";
        }


        CurlEntity entity = new CurlEntity();
        ICurlHandler<CurlEntity, String> handlerChain = CurlHandlerChain.init();

        // 如需扩展其他解析器，继续往链表中add即可
        handlerChain.next(new UrlPathHandler())
                .next(new UrlParamsHandler())
                .next(new HttpMethodHandler())
                .next(new HeaderHandler())
                .next(new HttpBodyHandler());

        handlerChain.handle(entity, curl);
        return entity;
    }


    /**
     * Execute curl command by parsing it and making HTTP request
     */
    public static String executeCurlCommand(String curl) {
        // Parse curl command
        CurlEntity curlEntity = null;
        try {
            curlEntity = CurlParserUtil.parse(curl);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to parse curl command: Curl:" + curl+"\n\nerrorType:"+e.getClass().getSimpleName()+", errorMsg:"+e.getMessage());
        }
        if (curlEntity == null) {
            throw new RuntimeException("Failed to parse curl command: " + curl);
        }
        try {
            // CurlEntity.url may already contain query parameters, so we need to extract base URL first
            String url = curlEntity.getUrl();
            String baseUrl = url;
            if (url != null && url.contains("?")) {
                baseUrl = url.substring(0, url.indexOf("?"));
            }

            // Add query parameters from urlParams
            if (curlEntity.getUrlParams() != null && !curlEntity.getUrlParams().isEmpty()) {
                StringBuilder urlBuilder = new StringBuilder(baseUrl);
                urlBuilder.append("?");
                for (Map.Entry<String, String> entry : curlEntity.getUrlParams().entrySet()) {
                    urlBuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue() != null ? entry.getValue() : "", "UTF-8"))
                            .append("&");
                }
                // Remove trailing &
                if (urlBuilder.length() > 0 && urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
                    url = urlBuilder.substring(0, urlBuilder.length() - 1);
                } else {
                    url = urlBuilder.toString();
                }
            } else {
                url = baseUrl;
            }

            // Execute HTTP request
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(curlEntity.getMethod().name());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            // Set headers
            if (curlEntity.getHeaders() != null) {
                for (Map.Entry<String, String> header : curlEntity.getHeaders().entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            // Set body if present
            if (curlEntity.getBody() != null && !curlEntity.getBody().isEmpty()) {
                connection.setDoOutput(true);
                String bodyStr = curlEntity.getBody().toJSONString();
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bodyStr.getBytes("UTF-8"));
                }
            }

            // Read response
            int statusCode = connection.getResponseCode();
            InputStream inputStream = (statusCode >= 200 && statusCode < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder response = new StringBuilder();
            response.append("HTTP Status: ").append(statusCode).append("\n\n");

            // Read response headers
            response.append("Response Headers:\n");
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                if (entry.getKey() != null) {
                    response.append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
                }
            }
            response.append("\n");

            // Read response body
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                }
            }

            connection.disconnect();
            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing curl command: " + e.getMessage() + "\nCurl: " + curl;
        }
    }
}