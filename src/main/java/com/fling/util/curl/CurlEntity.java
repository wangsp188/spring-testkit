package com.fling.util.curl;

import com.alibaba.fastjson.JSONObject;
import groovy.transform.builder.Builder;

import java.util.Map;

public class CurlEntity {
    /**
     * URL路径
     */
    private String url;
 
    /**
     * 请求方法类型
     */
    private Method method;
 
    /**
     * URL参数
     */
    private Map<String, String> urlParams;
 
    /**
     * header参数
     */
    private Map<String, String> headers;
 
    /**
     * 请求体
     */
    private JSONObject body;
 
    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Map<String, String> getUrlParams() {
        return urlParams;
    }

    public void setUrlParams(Map<String, String> urlParams) {
        this.urlParams = urlParams;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public JSONObject getBody() {
        return body;
    }

    public void setBody(JSONObject body) {
        this.body = body;
    }
}