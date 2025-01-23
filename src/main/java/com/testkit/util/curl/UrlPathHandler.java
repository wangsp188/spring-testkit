package com.testkit.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;

public class UrlPathHandler extends CurlHandlerChain {

    @Override
    public void handle(CurlEntity entity, String curl) {
        String url = parseUrlPath(curl);
        entity.setUrl(url);

        this.log(url);
        super.nextHandle(entity, curl);
    }

    /**
     * 该方法用于解析URL路径。
     *
     * @param curl 需要解析的URL，以字符串形式给出
     * @return URL中的路径部分。如果找不到，将返回null
     */
    private String parseUrlPath(String curl) {
        Matcher matcher = CurlPatternConstants.URL_PATH_PATTERN.matcher(curl);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
        }
        return null;
    }

    @Override
    public void log(Object... logParams) {
        if (logParams == null) {
            return;
        }
        System.err.println("UrlPathHandler execute: url=" + StringUtils.join(logParams, ','));
    }
}