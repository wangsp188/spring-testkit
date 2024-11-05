package com.halo.plugin.tools;

public enum PluginToolEnum {
    SPRING_CACHE("spring-cache"), CALL_METHOD("call-method"), FLEXIBLE_TEST("flexible-test");

    private String code;

    PluginToolEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PluginToolEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (PluginToolEnum pluginToolEnum : PluginToolEnum.values()) {
            if (pluginToolEnum.code.equals(code)) {
                return pluginToolEnum;
            }
        }
        return null;
    }
}
