package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class McpServerDefinition {
    private String serverName;
    private JSONObject config;
    private List<McpFunctionDefinition> definitions;

    public JSONObject getConfig() {
        return config;
    }

    public void setConfig(JSONObject config) {
        this.config = config;
    }

    public List<McpFunctionDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<McpFunctionDefinition> definitions) {
        this.definitions = definitions;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public static enum FunctionType {
        tool
    }

    public static enum ArgType {
        integer("integer"){
            @Override
            public Object init() {
                return 0;
            }
        },
        string("string"){
            @Override
            public Object init() {
                return "";
            }
        },
        number("number"){
            @Override
            public Object init() {
                return 0.0;
            }
        },
        array("array"){
            @Override
            public Object init() {
                return new ArrayList<>();
            }
        },
        object("object"){
            @Override
            public Object init() {
                return new HashMap<>();
            }
        },
        Boolean("boolean"){
            @Override
            public Object init() {
                return false;
            }
        };

        private String code;

        ArgType(String code) {
            this.code = code;
        }
        public abstract Object init();

        public static ArgType getByCode(String code) {
            for (ArgType value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            return null;
        }

    }

    public static record ArgSchema(String name,
                                   String type,
                                   String description,
                                   boolean required) {
    }

    public static class McpFunctionDefinition {
        private FunctionType type;
        private String serverKey;
        private String name;
        private String description;
        private List<ArgSchema> argSchemas;



        public JSONObject buildParamTemplate(){
            if (CollectionUtils.isEmpty(argSchemas)) {
                return new JSONObject();
            }
            JSONObject ret = new JSONObject();
            for (ArgSchema argSchema : argSchemas) {
                String argName = argSchema.name();
                String argType = argSchema.type();
                ArgType byCode = ArgType.getByCode(argType);
                ret.put(argName, byCode == null ? null : byCode.init());
            }
            return ret;
        }

        public String buildDescription(){
            String ret = "";
            if(StringUtils.isNotBlank(description)){
                ret = description + "<br>";
            }
            if (CollectionUtils.isEmpty(argSchemas)) {
                return ret.replace("\n", "<br>");
            }
            ret += "Args:<br>";
            for (ArgSchema argSchema : argSchemas) {
                ret += "  ";
                if(argSchema.required()){
                    ret +="*";
                }
                ret += argSchema.name() + "("+argSchema.type()+"): ";
                if (StringUtils.isNotBlank(argSchema.description())) {
                    ret+=(argSchema.description() + "<br>");
                }else{
                    ret+="<br>";
                }
            }
            return ret.replace("\n", "<br>");
        }

        public String buildDisplayName() {
            return name + "(" + serverKey + "-" + type + ")";
        }

        public FunctionType getType() {
            return type;
        }

        public void setType(FunctionType type) {
            this.type = type;
        }

        public String getServerKey() {
            return serverKey;
        }

        public void setServerKey(String serverKey) {
            this.serverKey = serverKey;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<ArgSchema> getArgSchemas() {
            return argSchemas;
        }

        public void setArgSchemas(List<ArgSchema> argSchemas) {
            this.argSchemas = argSchemas;
        }

        @Override
        public String toString() {
            return "McpFunctionDefinition{" +
                    "type=" + type +
                    ", serverName='" + serverKey + '\'' +
                    ", name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", argSchemas=" + argSchemas +
                    '}';
        }


    }
}
