package com.fling.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class UrlParamsHandler extends CurlHandlerChain {
 
    @Override
    public void handle(CurlEntity entity, String curl) {
        String url = extractUrl(curl);
        Map<String, String> urlParams = parseUrlParams(url);
        entity.setUrlParams(urlParams);
 
        this.log(urlParams);
        super.nextHandle(entity, curl);
    }
 
    private String extractUrl(String curl) {
        Matcher matcher = CurlPatternConstants.URL_PARAMS_PATTERN.matcher(curl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
 
    private Map<String, String> parseUrlParams(String url) {
        if (StringUtils.isBlank(url)) {
            return Collections.emptyMap();
        }
 
        Map<String, String> urlParams = new HashMap<>();
        // 提取URL的查询参数部分
        String[] urlParts = url.split("\\?");
        if (urlParts.length > 1) {
            // 只处理存在查询参数的情况
            String query = urlParts[1];
            // 解析查询参数到Map
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1 && idx < pair.length() - 1) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    urlParams.put(key, value);
                } else {
                    // 存在无值的参数时
                    urlParams.put(pair, null);
                }
            }
        }
        return urlParams;
    }
 
    @Override
    public void log(Object... logParams) {
        if (logParams==null) {
            return;
        }
        System.err.println("UrlParamsHandler execute: url="+StringUtils.join(logParams, ','));
    }
}