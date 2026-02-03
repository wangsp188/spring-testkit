package com.testkit.remote_script;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TimeTunnel 记录
 */
public class TtRecord {
    
    private String index;      // INDEX
    private String instance;   // 来源实例 IP
    private String timestamp;  // TIMESTAMP (用于排序)
    private String time;       // 显示时间
    private String cost;       // COST
    private boolean success;   // IS-RET
    private String exception;  // 异常信息
    private String methodInfo; // className.methodName
    private String returnObj;  // 返回值
    private Object params;     // 参数

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public String getMethodInfo() {
        return methodInfo;
    }

    public void setMethodInfo(String methodInfo) {
        this.methodInfo = methodInfo;
    }

    public String getReturnObj() {
        return returnObj;
    }

    public void setReturnObj(String returnObj) {
        this.returnObj = returnObj;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return "TtRecord{" +
                "index='" + index + '\'' +
                ", instance='" + instance + '\'' +
                ", time='" + time + '\'' +
                ", cost='" + cost + '\'' +
                ", success=" + success +
                ", methodInfo='" + methodInfo + '\'' +
                '}';
    }


    /**
     * 解析 tt -l 输出（JSON 格式）
     *
     * @param result Arthas 命令返回结果
     * @param instanceIp 来源实例 IP
     * @return TtRecord 列表
     */
    @SuppressWarnings("unchecked")
    public static List<TtRecord> parse(Object result, String instanceIp) {

        List<TtRecord> records = new ArrayList<>();
        if (result == null || !(result instanceof Map)) {
            System.out.println("[TtRecordParser] result is null or not Map");
            return records;
        }
        List data = new JSONObject((Map) result).getJSONArray("data");
        if (data == null || data.isEmpty()) {
            // No records found
            return records;
        }
        data.forEach(item -> {
            TtRecord record = parseItem((Map<String, Object>) item, instanceIp);
            if (record != null) {
                records.add(record);
            }
        });
        return records;
    }

    /**
     * 解析单个记录项
     */
    private static TtRecord parseItem(Map<String, Object> item, String instanceIp) {
        if (item == null) {
            return null;
        }

        try {
            TtRecord record = new TtRecord();

            // index
            Object index = item.get("index");
            record.setIndex(index != null ? String.valueOf(index) : "");

            // instance
            record.setInstance(instanceIp);

            // timestamp
            Object timestamp = item.get("timestamp");
            String timestampStr = timestamp != null ? String.valueOf(timestamp) : "";
            record.setTimestamp(timestampStr);
            record.setTime(formatTime(timestampStr));

            // cost (转换为 ms 格式)
            Object cost = item.get("cost");
            if (cost != null) {
                double costValue = 0;
                if (cost instanceof Number) {
                    costValue = ((Number) cost).doubleValue();
                } else {
                    try {
                        costValue = Double.parseDouble(String.valueOf(cost));
                    } catch (NumberFormatException ignored) {
                    }
                }
                record.setCost(String.format("%.3fms", costValue));
            } else {
                record.setCost("");
            }

            // success: return=true && throw=false
            Object isReturn = item.get("return");
            Object isThrow = item.get("throw");
            boolean returnValue = isReturn != null && Boolean.TRUE.equals(isReturn);
            boolean throwValue = isThrow != null && Boolean.TRUE.equals(isThrow);
            record.setSuccess(returnValue && !throwValue);

            // exception
            if (throwValue) {
                Object throwExp = item.get("throwExp");
                record.setException(throwExp != null ? String.valueOf(throwExp) : "Exception occurred");
            }

            // 额外信息：className.methodName
            Object className = item.get("className");
            Object methodName = item.get("methodName");
            if (className != null && methodName != null) {
                String simpleClassName = getSimpleClassName(String.valueOf(className));
                record.setMethodInfo(simpleClassName + "." + methodName);
            }

            // returnObj
            Object returnObj = item.get("returnObj");
            if (returnObj != null) {
                record.setReturnObj(String.valueOf(returnObj));
            }

            // params
            Object params = item.get("params");
            if (params != null) {
                record.setParams(params);
            }

            return record;
        } catch (Exception e) {
            System.err.println("Failed to parse tt record item: " + item + ", error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取简单类名（去掉包名）
     */
    private static String getSimpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return "";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * 格式化时间显示（只显示时分秒.毫秒）
     */
    private static String formatTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }

        // 如果包含空格，取时间部分
        if (timestamp.contains(" ")) {
            String[] parts = timestamp.split(" ");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        return timestamp;
    }
}
