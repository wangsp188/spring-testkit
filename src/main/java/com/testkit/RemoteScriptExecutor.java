package com.testkit;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Remote Script Groovy 脚本执行器
 * 
 * 用户脚本需要实现以下三个函数：
 * 1. loadInfra() - 返回 Map<appName, List<partition>>
 * 2. loadInstances(appName, partition) - 返回实例列表
 * 3. sendRequest(appName, ip, port, request) - 发送请求
 *    request 包含: method, params, trace, prepare, interceptor 等字段
 */
public class RemoteScriptExecutor {

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private final String scriptPath;
    private Script compiledScript;
    private long lastModified = 0;

    public RemoteScriptExecutor(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    /**
     * 检查脚本是否有效（文件存在且路径不为空）
     */
    public boolean isValid() {
        if (StringUtils.isBlank(scriptPath)) {
            return false;
        }
        File file = new File(scriptPath);
        return file.exists() && file.isFile();
    }

    /**
     * 加载并编译脚本（支持热更新）
     */
    private synchronized Script getScript() throws IOException {
        if (!isValid()) {
            throw new IOException("Invalid script path: " + scriptPath);
        }
        
        File file = new File(scriptPath);
        long currentModified = file.lastModified();
        
        // 如果文件被修改过，重新编译
        if (compiledScript == null || currentModified != lastModified) {
            String scriptContent = Files.readString(file.toPath());
            GroovyShell shell = new GroovyShell(new Binding());
            compiledScript = shell.parse(scriptContent);
            lastModified = currentModified;
            System.out.println("[RemoteScriptExecutor] Script loaded/reloaded: " + scriptPath);
        }
        
        return compiledScript;
    }

    /**
     * 获取基础设施信息
     * @param timeout 超时时间（秒）
     * @return Map<appName, List<partition>>
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> loadInfra(int timeout) {
        long startTime = System.currentTimeMillis();
        
        Future<Map<String, List<String>>> future = executor.submit(() -> {
            Script script = getScript();
            Object result = script.invokeMethod("loadInfra", new Object[]{});
            
            if (result instanceof Map) {
                Map<String, List<String>> infraMap = new LinkedHashMap<>();
                Map<?, ?> rawMap = (Map<?, ?>) result;
                
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String appName = String.valueOf(entry.getKey());
                    List<String> partitions = new ArrayList<>();
                    
                    Object value = entry.getValue();
                    if (value instanceof List) {
                        for (Object item : (List<?>) value) {
                            partitions.add(String.valueOf(item));
                        }
                    }
                    infraMap.put(appName, partitions);
                }
                return infraMap;
            }
            
            return Collections.emptyMap();
        });

        try {
            Map<String, List<String>> result = future.get(timeout, TimeUnit.SECONDS);
            log("loadInfra", System.currentTimeMillis() - startTime, "OK", "result=" + result);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("loadInfra", System.currentTimeMillis() - startTime, "TIMEOUT", "timeout=" + timeout + "s");
            throw new RuntimeException("loadInfra() timeout after " + timeout + " seconds", e);
        } catch (Exception e) {
            log("loadInfra", System.currentTimeMillis() - startTime, "ERROR", "error=" + e.getMessage());
            throw new RuntimeException("Failed to execute loadInfra(): " + e.getMessage(), e);
        }
    }

    /**
     * 获取实例列表
     * @param appName 应用名
     * @param partition 分区名
     * @param timeout 超时时间（秒）
     * @return 实例信息列表
     */
    @SuppressWarnings("unchecked")
    public List<InstanceInfo> loadInstances(String appName, String partition, int timeout) {
        long startTime = System.currentTimeMillis();
        
        Future<List<InstanceInfo>> future = executor.submit(() -> {
            Script script = getScript();
            Object result = script.invokeMethod("loadInstances", new Object[]{appName, partition});
            
            List<InstanceInfo> instances = new ArrayList<>();
            
            if (result instanceof List) {
                for (Object item : (List<?>) result) {
                    if (item instanceof Map) {
                        Map<?, ?> dataMap = (Map<?, ?>) item;
                        InstanceInfo info = new InstanceInfo();
                        info.setAppName(appName);       // 直接用入参
                        info.setPartition(partition);   // 直接用入参
                        info.setIp(getStringValue(dataMap, "ip", ""));
                        info.setPort(getIntValue(dataMap, "port", 0));
                        info.setSuccess(getBooleanValue(dataMap, "success", false));
                        info.setErrorMessage(getStringValue(dataMap, "errorMessage", ""));
                        info.setEnableTrace(getBooleanValue(dataMap, "enableTrace", false));
                        info.setEnv(getStringValue(dataMap, "env", null));
                        // expireTime: 脚本返回的 UTC 时间戳，如果没有则默认 24 小时后过期
                        long defaultExpireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
                        info.setExpireTime(getLongValue(dataMap, "expireTime", defaultExpireTime));
                        instances.add(info);
                    }
                }
            }
            
            return instances;
        });

        try {
            List<InstanceInfo> result = future.get(timeout, TimeUnit.SECONDS);
            log("loadInstances", System.currentTimeMillis() - startTime, "OK", 
                    "params={appName=" + appName + ", partition=" + partition + "}, result=" + result);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("loadInstances", System.currentTimeMillis() - startTime, "TIMEOUT", 
                    "params={appName=" + appName + ", partition=" + partition + "}, timeout=" + timeout + "s");
            throw new RuntimeException("loadInstances() timeout after " + timeout + " seconds", e);
        } catch (Exception e) {
            log("loadInstances", System.currentTimeMillis() - startTime, "ERROR", 
                    "params={appName=" + appName + ", partition=" + partition + "}, error=" + e.getMessage());
            throw new RuntimeException("Failed to execute loadInstances(): " + e.getMessage(), e);
        }
    }

    /**
     * 向指定实例发送请求（带超时）
     * @param appName 应用名
     * @param partition 分区
     * @param ip 实例 IP
     * @param port 端口
     * @param request 完整请求对象，包含 method, params, trace, prepare, interceptor 等字段
     * @param timeout 超时时间（秒）
     * @return 响应结果
     */
    public Object sendRequest(String appName, String partition, String ip, int port, Map<String, Object> request, int timeout) {
        long startTime = System.currentTimeMillis();
        String target = appName + ":[" + partition + "]" + ip + ":" + port;
        
        Future<Object> future = executor.submit(() -> {
            Script script = getScript();
            return script.invokeMethod("sendRequest", new Object[]{appName, partition, ip, port, request});
        });

        try {
            Object result = future.get(timeout, TimeUnit.SECONDS);
            log("sendRequest", System.currentTimeMillis() - startTime, "OK", 
                    "target=" + target + ", request=" + request + ", result=" + result);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("sendRequest", System.currentTimeMillis() - startTime, "TIMEOUT", 
                    "target=" + target + ", request=" + request + ", timeout=" + timeout + "s");
            throw new RuntimeException("sendRequest() timeout after " + timeout + " seconds", e);
        } catch (Exception e) {
            log("sendRequest", System.currentTimeMillis() - startTime, "ERROR", 
                    "target=" + target + ", request=" + request + ", error=" + e.getMessage());
            throw new RuntimeException("Failed to execute sendRequest(): " + e.getMessage(), e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 记录日志（每次调用一行）
     */
    private void log(String method, long durationMs, String status, String detail) {
        System.out.println("[RemoteScript] " + method + " " + status + " (" + durationMs + "ms)" + 
                (detail != null ? " - " + detail : ""));
    }

    private String getStringValue(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int getIntValue(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean getBooleanValue(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private long getLongValue(Map<?, ?> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== Inner Classes ====================

    /**
     * 实例信息
     */
    public static class InstanceInfo {
        private String appName;
        private String partition;
        private String ip;  // 实例标识，会存储为 [partition]ip 格式
        private int port;
        private boolean success;
        private String errorMessage;
        private boolean enableTrace;  // 是否支持 trace
        private String env;  // 环境标识
        private long expireTime;  // 过期时间（UTC 时间戳），过期后会从列表中剔除

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getPartition() {
            return partition;
        }

        public void setPartition(String partition) {
            this.partition = partition;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public boolean isEnableTrace() {
            return enableTrace;
        }

        public void setEnableTrace(boolean enableTrace) {
            this.enableTrace = enableTrace;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        /**
         * 检查实例是否已过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        /**
         * 获取 Testkit 连接字符串格式：appName:[partition]ip:port
         */
        public String toConnectionString() {
            return appName + ":[" + partition + "]" + ip + ":" + port;
        }

        @Override
        public String toString() {
            return "InstanceInfo{" +
                    "appName='" + appName + '\'' +
                    ", partition='" + partition + '\'' +
                    ", ip='" + ip + '\'' +
                    ", port=" + port +
                    ", success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", enableTrace=" + enableTrace +
                    ", env='" + env + '\'' +
                    ", expireTime=" + expireTime +
                    '}';
        }
    }
}

