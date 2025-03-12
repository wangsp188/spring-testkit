package com.testkit.tools;

public enum PluginToolEnum {
    FUNCTION_CALL("function-call"),
    FLEXIBLE_TEST("flexible-test"),
    MAPPER_SQL("mapper-sql")
    ;

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
