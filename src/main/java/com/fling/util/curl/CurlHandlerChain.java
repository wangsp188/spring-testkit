package com.fling.util.curl;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;

import static com.fling.util.curl.CurlPatternConstants.CURL_BASIC_STRUCTURE_PATTERN;

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

                // 替换掉可能存在的转译(字符串中的空白字符，包括空格、换行符和制表符...)
                curl = curl.replace("\\", "")
                        .replace("\n", "")
                        .replace("\t", "");

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