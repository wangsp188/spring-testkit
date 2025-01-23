package com.testkit.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;

public class HttpMethodHandler extends CurlHandlerChain {

    @Override
    public void handle(CurlEntity entity, String curl) {
        CurlEntity.Method method = parseMethod(curl);
        entity.setMethod(method);

        this.log(method);
        super.nextHandle(entity, curl);
    }

    private CurlEntity.Method parseMethod(String curl) {
        Matcher matcher = CurlPatternConstants.HTTP_METHOD_PATTERN.matcher(curl);
        Matcher defaultMatcher = CurlPatternConstants.DEFAULT_HTTP_METHOD_PATTERN.matcher(curl);
        if (matcher.find()) {
            String method = matcher.group(1);
            return CurlEntity.Method.valueOf(method.toUpperCase());
        } else if (defaultMatcher.find()) {
            // 如果命令中包含 -d 或 --data，没有明确请求方法，默认为 POST
            return CurlEntity.Method.POST;
        } else {
            // 没有明确指定请求方法，默认为 GET
            return CurlEntity.Method.GET;
        }
    }

    @Override
    public void log(Object... logParams) {
        if (logParams == null) {
            return;
        }
        System.err.println("HttpMethodHandler execute: method=" + StringUtils.join(logParams, ','));
    }
}