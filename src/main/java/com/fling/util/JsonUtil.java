package com.fling.util;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.text.SimpleDateFormat;

public class JsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        // 在静态块中初始化
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setDateFormat(dateFormat);
        // 设置包含 null 值
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public static String formatObj(Object obj) {
        if (obj == null) {
            return "";
        }

        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return JSONObject.toJSONString(obj, SerializerFeature.WriteNonStringKeyAsString, SerializerFeature.WriteDateUseDateFormat);
        }
    }


}
