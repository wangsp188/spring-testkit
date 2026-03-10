package com.testkit.server;

import java.util.Map;

class Req {

    private String method;

    private boolean prepare;

    private String interceptor;

    private boolean trace = true;

    private Map<String, String> params;

    private String operator = "testkit";

    private String source = "plugin";

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public String getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(String interceptor) {
        this.interceptor = interceptor;
    }

    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public boolean isPrepare() {
        return prepare;
    }

    public void setPrepare(boolean prepare) {
        this.prepare = prepare;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
