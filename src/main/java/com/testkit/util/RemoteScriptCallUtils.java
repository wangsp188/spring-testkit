package com.testkit.util;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.project.Project;
import com.testkit.remote_script.RemoteScriptExecutor;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote Script 调用工具类
 * 
 * 当连接是 [ 开头时（通过 Groovy 脚本管理的远程实例），
 * 使用此工具类将请求路由到脚本的 sendRequest 函数执行
 */
public class RemoteScriptCallUtils {

    /**
     * 判断是否是远程脚本类型的连接
     */
    public static boolean isRemoteScriptConnection(RuntimeHelper.VisibleApp app) {
        return app != null && app.isRemoteInstance();
    }

    /**
     * 发送单次请求到远程实例（不做两步封装，由调用方控制流程）
     * 
     * @param project 当前项目
     * @param app 目标应用（包含 appName、[partition]ip、port）
     * @param request 完整请求对象（包含 method、params、trace、prepare、interceptor 等）
     * @param timeout 超时时间（秒）
     * @return 响应结果（格式与本地 HTTP 调用一致）
     */
    public static JSONObject sendRequest(Project project, RuntimeHelper.VisibleApp app, JSONObject request, int timeout) {
        String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
        
        if (StringUtils.isBlank(scriptPath)) {
            return errorResponse("Remote script not configured. Please configure script path first.");
        }

        try {
            RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
            
            if (!executor.isValid()) {
                return errorResponse("Remote script not found: " + scriptPath);
            }

            String appName = app.getAppName();
            String partition = app.getRemotePartition();  // 从 [partition]ip 格式提取 partition
            String ip = app.getRemoteIp();  // 从 [partition]ip 格式提取 ip
            int port = app.getTestkitPort();

            // 将 JSONObject 转为 Map 传给脚本
            Map<String, Object> requestMap = new HashMap<>(request);
            
            // 调用脚本的 sendRequest（详细日志由 RemoteScriptExecutor 记录）
            Object result = executor.sendRequest(appName, partition, ip, port, requestMap, timeout);
            
            return convertToResponse(result);
            
        } catch (Exception e) {
            System.err.println("[RemoteScriptCallUtils] Error: " + e.getMessage());
            e.printStackTrace();
            return errorResponse("Script execution failed: " + e.getMessage());
        }
    }

    /**
     * 将脚本返回结果转换为 JSONObject 响应格式
     */
    private static JSONObject convertToResponse(Object result) {
        JSONObject response = new JSONObject();
        
        if (result instanceof Map) {
            Map<?, ?> resultMap = (Map<?, ?>) result;
            
            // 检查是否已经是标准格式
            if (resultMap.containsKey("success")) {
                response.put("success", resultMap.get("success"));
                response.put("data", resultMap.get("data"));
                response.put("message", resultMap.get("message"));
                response.put("error", resultMap.get("error"));
            } else {
                // 假设成功，直接返回结果
                response.put("success", true);
                response.put("data", result);
            }
        } else if (result != null) {
            response.put("success", true);
            response.put("data", result);
        } else {
            response.put("success", true);
            response.put("data", null);
        }
        
        return response;
    }

    /**
     * 创建错误响应
     */
    private static JSONObject errorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }
}

