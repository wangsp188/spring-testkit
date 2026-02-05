package com.testkit.remote_script;

import com.testkit.RuntimeHelper;
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


    // Remote Script 超时配置
    public static final int REMOTE_SUBMIT_TIMEOUT = 60;     // 提交请求超时 60 秒
    public static final int REMOTE_RESULT_TIMEOUT = 300;    // 获取结果超时 300 秒
    public static final int REMOTE_CANCEL_TIMEOUT = 60;     // 取消请求超时 60 秒
    public static final int REMOTE_ARTHAS_TIMEOUT = 300;

    // Remote Script API Reference
    public static final String REMOTE_SCRIPT_INFO = """
================================================================================
                        Remote Script API Reference
================================================================================

The plugin calls functions in the Groovy script. Implement these functions:

────────────────────────────────────────────────────────────────────────────────
1. isArthasSupported()                                              [Optional]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Determine whether Arthas features are available in this environment
   
   When returns true, the IDE will show Arthas-related features:
     - TimeTunnel: Record and replay method invocations
     - View Remote Code: Decompile classes from running JVM
     - Trace: Monitor method execution paths
   
   When returns false (or not implemented), these features are hidden.
   
   Parameters: None
   Returns: boolean
   
   Example:
   def isArthasSupported() {
       return true  // Enable Arthas features in IDE
   }

────────────────────────────────────────────────────────────────────────────────
2. loadInfra()                                                      [Required]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Get available Apps and Partitions
   Parameters: None
   Returns: Map<String, List<String>>
         Key   = appName (Spring Boot main class name, e.g., "WebApplication")
         Value = partition list (e.g., ["us01", "qa01", "dev01"])
   
   Example:
   [
       "WebApplication"    : ["us01", "qa01"],
       "ApiWebApplication" : ["us01"]
   ]

────────────────────────────────────────────────────────────────────────────────
3. loadInstances(String appName, String partition)                  [Required]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Get instance list for specified App + Partition
   Parameters: appName   - Application name
               partition - Partition name
   Returns: List<Map> - Instance information list
   
   Required fields:
     - ip           : String  Instance identifier (stored as [partition]ip)
     - port         : int     Testkit port
     - env          : String  Environment identifier (e.g., "prod", "qa")
     - success      : boolean Availability status
     - errorMessage : String  Error message (when success=false)
   
   Optional fields:
     - enableTrace  : boolean Trace support (default: false)
     - expireTime   : long    Expiration time UTC timestamp (default: 24h later)
     - arthasPort   : Integer Arthas HTTP port (default: null, non-null enables Arthas)
   
   Example:
   [
       [ip: "pod-001", port: 18080, success: true, errorMessage: "", enableTrace: true, env: "prod", arthasPort: 8563],
       [ip: "pod-002", port: 18080, success: false, errorMessage: "Connection refused"]
   ]

────────────────────────────────────────────────────────────────────────────────
4. sendRequest(String appName, String partition, String ip, int port, Map request)
                                                                    [Required]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Send request to specified instance (forward to Testkit Server)
   Parameters: appName   - Application name
               partition - Partition name
               ip        - Instance identifier (from loadInstances)
               port      - Testkit port
               request   - Request object Map
   
   Request structure:
     - method      : String             Request method
     - params      : Map<String,String> Request parameters
     - trace       : boolean            Enable tracing (optional)
     - prepare     : boolean            Enable preprocessing (optional)
     - interceptor : String             Interceptor config (optional)
   
   Common method values:
     - "hi"           : Health check
     - "function-call": Submit [function-call] task
     - "flexible-test": Submit [flexible-test] task
     - "view-value"   : View variable value
     - "get_task_ret" : Get task result
     - "stop_task"    : Stop task
   
   Response: Directly forward Testkit Server response (keep structure consistent)
   {
       "success" : boolean,                       // true=success, false=failure
       "cost"    : int,                           // execution time in milliseconds
       "message" : String,                        // error message (when success=false)
       "data"    : Object,                        // response data (varies by method)
       "profile" : [{"link":..., "cost":...}]     // trace profiler (when trace=true)
   }
   
   Response data by method:
     - "hi"           : {enableTrace, app, env, testPass, ip, loadByCli, port}
     - "function-call": String (reqId) - use get_task_ret to poll result
     - "flexible-test": String (reqId) - use get_task_ret to poll result
     - "view-value"   : String (reqId) - use get_task_ret to poll result
     - "get_task_ret" : Final execution result (same Ret structure)
     - "stop_task"    : boolean (true=cancelled, false=not found)

────────────────────────────────────────────────────────────────────────────────
5. sendArthasRequest(String appName, String partition, String ip, int port, Map params)
                                                                    [Optional]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Send Arthas command to specified instance
   Parameters: appName   - Application name
               partition - Partition name
               ip        - Instance identifier
               port      - Arthas HTTP port (from arthasPort in loadInstances)
               params    - Request parameters Map (must contain "command" key)
   
   Params structure:
     - command : String (required) - Arthas command string
   
   Common commands:
     - "jad com.example.MyClass"              : Decompile class
     - "tt -t com.example.Controller method"  : Start TimeTunnel recording
     - "tt -l"                                 : List TimeTunnel records
     - "tt -i 1000"                            : View TimeTunnel record detail
     - "tt -w 'target.method(params)'"        : Watch expression on record
     - "tt -p -i 1000"                         : Replay method invocation
     - "trace com.example.Service method -n 5": Trace method calls
   
   Response structure:
   {
       "success" : boolean,    // true=success, false=failure
       "message" : String,     // error message (when success=false)
       "data"    : Object      // command result (structure varies by command)
   }
   
   TimeTunnel (tt) command response data:
     - "tt -l"         : data = timeFragmentList (List of recorded invocations)
                         [{ "index": 1000, "timestamp": ..., "cost": ..., 
                            "isReturn": true, "isException": false, ... }]
     - "tt -i <index>" : data = timeFragment (Single invocation detail)
                         { "index": 1000, "className": ..., "methodName": ...,
                           "params": [...], "returnObj": ..., ... }
     - "tt -w <expr>"  : data = watchValue (Expression evaluation result)
     - "tt -p -i <idx>": data = replayResult (Replay execution result)
   
   Other commands: data = raw command output (String or parsed JSON)

────────────────────────────────────────────────────────────────────────────────
6. sendFileRequest(String appName, String partition, String ip, Map options)
                                                                    [Optional]
────────────────────────────────────────────────────────────────────────────────
   Purpose: Upload or download files to/from specified instance
   
   When to implement: Required when Arthas is supported (isArthasSupported=true)
   Used by:
     - Profiler: Download profiler result files (e.g., flame graph HTML)
     - Hot Deploy: Upload compiled class files for hot reloading
   
   Parameters: appName   - Application name
               partition - Partition name
               ip        - Instance identifier
               options   - File operation options Map
   
   Options structure:
     - action     : String (required) - "download" or "upload"
     - remotePath : String (required) - File path on the remote pod
     - localPath  : String (optional) - Local file path
     - content    : String (optional) - Content to upload (for upload action)
     - timeout    : int    (optional) - Timeout in seconds (default: 300)
   
   Response structure:
   {
       "success" : boolean,    // true=success, false=failure
       "message" : String,     // error message (when success=false)
       "data"    : Object      // file content for download, or upload result
   }
   
   Example usage:
     Download: [action: "download", remotePath: "/app/config/app.properties"]
     Upload:   [action: "upload", remotePath: "/tmp/test.txt", content: "Hello"]

================================================================================
                              Demo Script
================================================================================

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ==================== 1. isArthasSupported (Optional) ====================
// Return true to enable Arthas features (TimeTunnel, View Remote Code) in the IDE
def isArthasSupported() {
    return true
}

// ==================== 2. loadInfra ====================
def loadInfra() {
    // Return your service cluster architecture definition
    return [
        "WebApplication": ["dev01", "qa01", "us01"]
    ]
}

// ==================== 3. loadInstances ====================
// Note: Testkit Server must be started on target machine via testkit-cli attach
//       success=true  means Testkit Server is running and connectable
//       success=false means startup failed or unreachable, fill errorMessage
def loadInstances(String appName, String partition) {
    def pods = getPodList(appName, partition)  // Get pod list (implement yourself)
    
    return pods.collect { pod ->
        def result = [
            ip   : pod.name,
            port : 18080,
            env  : partition
        ]
        try {
            // 1) Start testkit-cli on target machine via SSH/kubectl (if not started)
            def resp = startTestkitCli(pod.ip, appName)
            result.success      = resp.success == true
            result.errorMessage = result.success ? "" : (resp.message ?: "Unknown error")
            result.enableTrace  = resp.data?.enableTrace ?: false
            result.arthasPort   = 8563  // Optional: Arthas HTTP port (if Arthas is available)
        } catch (Exception e) {
            result.success      = false
            result.errorMessage = e.message ?: "Connection failed"
            result.enableTrace  = false
        }
        return result
    }
}

// ==================== 4. sendRequest ====================
// Forward request to Testkit Server, return response as-is
def sendRequest(String appName, String partition, String ip, int port, Map request) {
    def address = getAddress(ip, partition)  // Get actual address from ip (implement yourself)
    return httpPost("http://${address}:${port}/", request)
}

// ==================== 5. sendArthasRequest (Optional) ====================
// Send Arthas command via HTTP API (only if arthasPort is provided in loadInstances)
def sendArthasRequest(String appName, String partition, String ip, int port, Map params) {
    def address = getAddress(ip, partition)  // Get actual address from ip
    def url = "http://${address}:${port}/api"
    
    // Extract command from params
    def command = params.get("command")
    if (!command) {
        return [success: false, message: "Missing 'command' in params"]
    }
    
    // Construct POST form data (Arthas HTTP API format)
    def formData = "action=exec&command=" + URLEncoder.encode(command.toString(), "UTF-8")
    
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(30000)
    
    conn.outputStream.write(formData.getBytes("UTF-8"))
    def responseText = conn.inputStream.text
    
    // Parse Arthas JSON response and extract data
    def json = new JsonSlurper().parseText(responseText)
    def results = json.body?.results ?: []
    
    // Handle TimeTunnel (tt) commands specially
    if (command.toString().startsWith("tt ")) {
        for (result in results) {
            if (result?.type == 'tt') {
                if (result.timeFragmentList != null) {
                    return [success: true, data: result.timeFragmentList, message: null]
                }
                if (result.timeFragment != null) {
                    return [success: true, data: result.timeFragment, message: null]
                }
                if (result.watchValue != null) {
                    return [success: true, data: result.watchValue, message: null]
                }
                if (result.replayResult != null) {
                    return [success: true, data: result.replayResult, message: null]
                }
            }
        }
    }
    
    // Default: return raw response
    return [success: true, data: responseText, message: null]
}

// ==================== Helper Functions ====================
def httpPost(String url, Map data) {
    def conn = new URL(url).openConnection()
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(600000)
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.withWriter { it << JsonOutput.toJson(data) }
    return new JsonSlurper().parseText(conn.inputStream.text)
}

// TODO: Implement these functions based on your infrastructure
// def getPodList(appName, partition) { ... }
// def getAddress(ip, partition) { ... }
// def startTestkitCli(podIp, appName) { ... }

================================================================================
""";

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

            // Auto-refresh Arthas support flag when script is reloaded
            refreshArthasSupportAsync();
        }

        return compiledScript;
    }

    /**
     * Refresh Arthas support flag asynchronously (called when script is reloaded)
     */
    private void refreshArthasSupportAsync() {
        executor.submit(() -> {
            try {
                Object result = compiledScript.invokeMethod("isArthasSupported", new Object[]{});
                boolean supported = false;
                if (result instanceof Boolean) {
                    supported = (Boolean) result;
                } else if (result instanceof String) {
                    supported = Boolean.parseBoolean((String) result);
                }
                RuntimeHelper.setArthasSupported(supported);
                System.out.println("[RemoteScriptExecutor] Arthas support refreshed: " + supported);
            } catch (Throwable e) {
                // Function doesn't exist or failed, disable Arthas
                RuntimeHelper.setArthasSupported(false);
                System.out.println("[RemoteScriptExecutor] Arthas support disabled (function not found or error)");
            }
        });
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
                        // arthasPort: Arthas 端口，非空表示支持 Arthas
                        Integer arthasPort = getIntegerValue(dataMap, "arthasPort", null);
                        info.setArthasPort(arthasPort);
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

    /**
     * 向指定实例发送 Arthas 命令（带超时）
     * @param appName 应用名
     * @param partition 分区
     * @param ip 实例 IP
     * @param port Arthas 端口
     * @param params 请求参数（Map），至少包含 "command" key，可包含其他参数
     * @param timeout 超时时间（秒）
     * @return 命令执行结果
     */
    public Object sendArthasRequest(String appName, String partition, String ip, int port, Map<String, Object> params, int timeout) {
        long startTime = System.currentTimeMillis();
        String target = appName + ":[" + partition + "]" + ip + ":" + port;

        Future<Object> future = executor.submit(() -> {
            Script script = getScript();
            return script.invokeMethod("sendArthasRequest", new Object[]{appName, partition, ip, port, params});
        });

        try {
            Object result = future.get(timeout, TimeUnit.SECONDS);
            log("sendArthasRequest", System.currentTimeMillis() - startTime, "OK",
                    "target=" + target + ", params=" + params + ", result=" + result);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("sendArthasRequest", System.currentTimeMillis() - startTime, "TIMEOUT",
                    "target=" + target + ", params=" + params + ", timeout=" + timeout + "s");
            throw new RuntimeException("sendArthasRequest() timeout after " + timeout + " seconds", e);
        } catch (Exception e) {
            log("sendArthasRequest", System.currentTimeMillis() - startTime, "ERROR",
                    "target=" + target + ", params=" + params + ", error=" + e.getMessage());
            throw new RuntimeException("Failed to execute sendArthasRequest(): " + e.getMessage(), e);
        }
    }

    /**
     * 向指定实例发送文件操作请求（上传或下载）
     * @param appName 应用名
     * @param partition 分区
     * @param ip 实例 IP（实际是 pod name）
     * @param options 操作参数 Map，包含：
     *                - action: "download" 或 "upload"
     *                - remotePath: Pod 上的文件路径
     *                - localPath: 本地文件路径
     *                - content: 要上传的内容（upload 时可选）
     *                - timeout: 超时时间（可选）
     * @return 响应结果
     */
    public Object sendFileRequest(String appName, String partition, String ip, Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        String target = appName + ":[" + partition + "]" + ip;
        int timeout = options.containsKey("timeout") ? ((Number) options.get("timeout")).intValue() : 300;

        Future<Object> future = executor.submit(() -> {
            Script script = getScript();
            return script.invokeMethod("sendFileRequest", new Object[]{appName, partition, ip, options});
        });

        try {
            Object result = future.get(timeout, TimeUnit.SECONDS);
            log("sendFileRequest", System.currentTimeMillis() - startTime, "OK",
                    "target=" + target + ", options=" + options + ", result=" + result);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("sendFileRequest", System.currentTimeMillis() - startTime, "TIMEOUT",
                    "target=" + target + ", options=" + options + ", timeout=" + timeout + "s");
            throw new RuntimeException("sendFileRequest() timeout after " + timeout + " seconds", e);
        } catch (Exception e) {
            log("sendFileRequest", System.currentTimeMillis() - startTime, "ERROR",
                    "target=" + target + ", options=" + options + ", error=" + e.getMessage());
            throw new RuntimeException("Failed to execute sendFileRequest(): " + e.getMessage(), e);
        }
    }

    /**
     * Check if Arthas is supported by the remote script
     * Calls the script's isArthasSupported() function
     * @return true if supported, false otherwise
     */
    public boolean isArthasSupported() {
        long startTime = System.currentTimeMillis();

        Future<Boolean> future = executor.submit(() -> {
            try {
                Script script = getScript();
                Object result = script.invokeMethod("isArthasSupported", new Object[]{});

                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
                if (result instanceof String) {
                    return Boolean.parseBoolean((String) result);
                }
                // If function doesn't exist or returns null, assume not supported
                return false;
            } catch (Exception e) {
                // Function doesn't exist or failed, assume not supported
                System.err.println("[RemoteScriptExecutor] isArthasSupported check failed: " + e.getMessage());
                return false;
            }
        });

        try {
            Boolean result = future.get(5, TimeUnit.SECONDS);
            log("isArthasSupported", System.currentTimeMillis() - startTime, "OK", "result=" + result);
            return result != null && result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log("isArthasSupported", System.currentTimeMillis() - startTime, "TIMEOUT", "timeout=" + 5 + "s");
            return false;
        } catch (Exception e) {
            log("isArthasSupported", System.currentTimeMillis() - startTime, "ERROR", "error=" + e.getMessage());
            return false;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Log each call in one line
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

    private Integer getIntegerValue(Map<?, ?> map, String key, Integer defaultValue) {
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
        private Integer arthasPort;  // Arthas 端口，非空表示支持 Arthas

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

        public Integer getArthasPort() {
            return arthasPort;
        }

        public void setArthasPort(Integer arthasPort) {
            this.arthasPort = arthasPort;
        }

        /**
         * 检查实例是否已过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        /**
         * 检查是否支持 Arthas
         */
        public boolean isArthasEnabled() {
            return arthasPort != null && arthasPort > 0;
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
                    ", arthasPort=" + arthasPort +
                    '}';
        }
    }
}

