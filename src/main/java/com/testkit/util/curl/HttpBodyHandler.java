package com.testkit.util.curl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

public class HttpBodyHandler extends CurlHandlerChain {
    @Override
    public void handle(CurlEntity entity, String curl) {
        JSONObject body = parseBody(curl);
        entity.setBody(body);

        this.log(body);
        super.nextHandle(entity, curl);
    }

    private JSONObject parseBody(String curl) {
        Matcher formMatcher = CurlPatternConstants.HTTP_FROM_BODY_PATTERN.matcher(curl);
        if (formMatcher.find()) {
            return parseFormBody(formMatcher);
        }

        Matcher urlencodeMatcher = CurlPatternConstants.HTTP_URLENCODE_BODY_PATTERN.matcher(curl);
        if (urlencodeMatcher.find()) {
            return parseUrlEncodeBody(urlencodeMatcher);
        }

        Matcher rawMatcher = CurlPatternConstants.HTTP_ROW_BODY_PATTERN.matcher(curl);
        if (rawMatcher.find()) {
            return parseRowBody(rawMatcher);
        }

        Matcher defaultMatcher = CurlPatternConstants.DEFAULT_HTTP_BODY_PATTERN.matcher(curl);
        if (defaultMatcher.find()) {
            return parseDefaultBody(defaultMatcher);
        }

        return new JSONObject();
    }

    private JSONObject parseDefaultBody(Matcher defaultMatcher) {
        String bodyStr = "";
        if (defaultMatcher.group(1) != null) {
            // 单引号包裹的数据
            bodyStr = defaultMatcher.group(1);
        } else if (defaultMatcher.group(2) != null) {
            // 双引号包裹的数据
            bodyStr = defaultMatcher.group(2);
        } else {
            // 无引号的数据
            bodyStr = defaultMatcher.group(3);
        }

        // 判断是否是json结构
        if (isJSON(bodyStr)) {
            return JSONObject.parseObject(bodyStr);
        }

        // 特殊Case: username=test&password=secret
        Matcher kvMatcher = CurlPatternConstants.DEFAULT_HTTP_BODY_PATTERN_KV.matcher(bodyStr);
        return kvMatcher.matches() ? parseKVBody(bodyStr) : new JSONObject();
    }

    private JSONObject parseKVBody(String kvBodyStr) {
        JSONObject json = new JSONObject();
        String[] pairs = kvBodyStr.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            json.put(key, value);
        }
        return json;
    }

    private JSONObject parseFormBody(Matcher formMatcher) {
        JSONObject formData = new JSONObject();

        // 重置指针匹配的位置
        formMatcher.reset();
        while (formMatcher.find()) {
            // 提取表单项
            String formItem = formMatcher.group(1) != null ? formMatcher.group(1) : formMatcher.group(2);

            // 分割键和值
            String[] keyValue = formItem.split("=", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];

                // 检测文件字段标记
                // PS: 理论上文件标记字段不需要支持
                if (value.startsWith("@")) {
                    // 只提取文件名，不读取文件内容
                    formData.put(key, value.substring(1));
                } else {
                    // 放入表单数据
                    formData.put(key, value);
                }
            }
        }

        return formData;
    }

    private JSONObject parseUrlEncodeBody(Matcher urlencodeMatcher) {
        JSONObject urlEncodeData = new JSONObject();

        // 重置指针匹配的位置
        urlencodeMatcher.reset();
        while (urlencodeMatcher.find()) {
            // 提取键值对字符串：兼容'..' / ".." / 无引号
            String keyValueEncoded = urlencodeMatcher.group(1) != null
                    ? urlencodeMatcher.group(1)
                    : (urlencodeMatcher.group(2) != null ? urlencodeMatcher.group(2) : urlencodeMatcher.group(3));

            // 分隔键和值
            String[] keyValue = keyValueEncoded.split("=", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];

                // 对值进行URL解码
                String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);

                // 存入数据到JSON对象
                urlEncodeData.put(key, decodedValue);
            }
        }

        return urlEncodeData;
    }

    private JSONObject parseRowBody(Matcher rowMatcher) {
        // 兼容'..' / ".." / 无引号三种捕获
        String rawData = rowMatcher.group(1) != null
                ? rowMatcher.group(1)
                : (rowMatcher.group(2) != null ? rowMatcher.group(2) : rowMatcher.group(3));

        if (isXML(rawData)) {
            // throw new IllegalArgumentException("Curl --data-raw content cant' be XML");
            return xml2json(rawData);
        }

        try {
            return JSON.parseObject(rawData);
        } catch (Exception e) {
            try {
                return JSON.parseObject(rawData.replace("\"{E}\"","\"").replace("\"{/E}\"","\""));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Curl --data-raw content is not a valid JSON",ex);
            }
        }
    }

    private boolean isJSON(String jsonStr) {
        try {
            JSONObject.parseObject(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isXML(String xmlStr) {
        return false;
//        try {
//            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//            factory.setFeature(SecurityConstants.DDD, true);
//            factory.setFeature(SecurityConstants.EGE, false);
//            factory.setFeature(SecurityConstants.EPE, false);
//
//            DocumentBuilder builder = factory.newDocumentBuilder();
//            InputSource is = new InputSource(new StringReader(xmlStr));
//            builder.parse(is);
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
    }

    private JSONObject xml2json(String xmlStr) {
//        try {
//            org.json.JSONObject orgJsonObj = XML.toJSONObject(xmlStr);
//            String jsonString = orgJsonObj.toString();
//            return JSON.parseObject(jsonString);
//        } catch (JSONException e) {
//            throw new LinkConsoleException("Curl --data-raw content xml2json error", e);
//        }
        return null;
    }

    @Override
    public void log(Object... logParams) {
        if (logParams==null) {
            return;
        }
        System.err.println("HttpBodyHandler execute: body="+ StringUtils.join(logParams, ','));
    }
}