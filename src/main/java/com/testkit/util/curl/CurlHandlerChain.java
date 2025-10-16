package com.testkit.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;

import static com.testkit.util.curl.CurlPatternConstants.CURL_BASIC_STRUCTURE_PATTERN;

public abstract class CurlHandlerChain implements ICurlHandler<CurlEntity, String> {

    ICurlHandler<CurlEntity, String> next;

    @Override
    public ICurlHandler<CurlEntity, String> next(ICurlHandler<CurlEntity, String> handler) {
        this.next = handler;
        return this.next;
    }

    @Override
    public abstract void handle(CurlEntity entity, String curl);

    /**
     * for subclass call
     */
    protected void nextHandle(CurlEntity curlEntity, String curl) {
        if (next != null) {
            next.handle(curlEntity, curl);
        }
    }

    protected void validate(String curl) {
        if (StringUtils.isBlank(curl)) {
            throw new IllegalArgumentException("Curl script is empty");
        }

        Matcher matcher = CURL_BASIC_STRUCTURE_PATTERN.matcher(curl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Curl script is invalid");
        }
    }

    public static CurlHandlerChain init() {
        return new CurlHandlerChain() {
            @Override
            public void handle(CurlEntity entity, String curl) {
                this.validate(curl);

                // 仅折叠续行：将行尾反斜杠 + 换行 + 后续缩进折叠为单个空格，保留字符串内部转义
                // 1) 归一化换行
                curl = curl.replace("\r\n", "\n");
                // 2) 折叠续行：\\\n 及其后空白 -> 单个空格
                curl = curl.replaceAll("\\\\\n\\s*", " ");

                if (next != null) {
                    next.handle(entity, curl);
                }
            }
        };
    }

    public void log(Object... logParams) {
        // Write log for subclass extensions
    }
}