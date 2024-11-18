package com.fling.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class HeaderHandler extends CurlHandlerChain {

    @Override
    public void handle(CurlEntity entity, String curl) {
        Map<String, String> headers = parseHeaders(curl);
        entity.setHeaders(headers);

        this.log(headers);
        super.nextHandle(entity, curl);
    }

    private Map<String, String> parseHeaders(String curl) {
        if (StringUtils.isBlank(curl)) {
            return Collections.emptyMap();
        }

        Matcher matcher = CurlPatternConstants.CURL_HEADERS_PATTERN.matcher(curl);
        Map<String, String> headers = new HashMap<>();
        while (matcher.find()) {
            String header = matcher.group(1);
            String[] headerKeyValue = header.split(":", 2);
            if (headerKeyValue.length == 2) {
                // 去除键和值的首尾空白字符
                headers.put(headerKeyValue[0].trim(), headerKeyValue[1].trim());
            }
        }

        return headers;
    }

    @Override
    public void log(Object... logParams) {
        if (logParams==null) {
            return;
        }
        System.err.println("HeaderHandler execute: headers="+StringUtils.join(logParams, ','));
    }
}