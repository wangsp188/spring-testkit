package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.testkit.TestkitHelper;
import com.testkit.util.ExceptionUtil;
import com.testkit.view.TestkitToolWindow;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class McpHelper {

    private static final String MCP_DIR = ".spring-testkit/mcp";

    private static final Map<String,McpServerDefinition> servers = new HashMap<>();

    private static final Set<TestkitToolWindow> subscribeWindows = new HashSet<>();

    private static final Object LOCK = new Object();

    static {
        JSONObject mcpJson = fetchMcpJson();
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.err.println("start Initialize MCP-server");
                McpAdapter.McpInitRet ret = McpAdapter.parseMcpServers(mcpJson);
                //从.spring-testkit/.mcp.json内容初始化
                System.err.println("Initialize MCP-server," + ret);
                if (MapUtils.isNotEmpty(ret.servers())) {
                    String servers = ret.servers().keySet().stream().collect(Collectors.joining(","));
                    TestkitHelper.notify(null, NotificationType.INFORMATION, "Initialize MCP-server successful<br>" + servers);
                }
                if (MapUtils.isNotEmpty(ret.errors())) {
                    TestkitHelper.notify(null, NotificationType.ERROR, "Initialize MCP-server occur some error<br>" + JSON.toJSONString(ret.errors()));
                }
                refreshServers(ret.servers());
                System.err.println("end Initialize MCP-server");
            }
        }).start();
    }


    public static JSONObject fetchMcpJson() {
        File configFile = getStoreFile("mcp.json");
        if (!configFile.exists()) {
            JSONObject object = new JSONObject();
            object.put("mcpServers", new HashMap<>());
            return object;
        }
        try (FileReader reader = new FileReader(configFile)) {
            String content = new String(Files.readAllBytes(configFile.toPath()));
            return JSON.parseObject(content);
        } catch (Throwable e) {
            e.printStackTrace();
            JSONObject object = new JSONObject();
            object.put("mcpServers", new HashMap<>());
            return object;
        }
    }

    public static void saveMcpJson(JSONObject mcpJson) {
        File configFile = getStoreFile("mcp.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            JSON.writeJSONString(writer, mcpJson, SerializerFeature.WriteMapNullValue);
        } catch (IOException e) {
            throw new RuntimeException("save mcp.json fail," + e.getMessage());
        }
    }

    private static File getStoreFile(String name) {
        String projectPath = System.getProperty("user.home");
        if (StringUtils.isEmpty(projectPath)) {
            throw new IllegalArgumentException("Project base path is not set.");
        }
        Path configDirPath = Paths.get(projectPath, MCP_DIR);
        File configDir = configDirPath.toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, name);
    }


    public static List<McpServerDefinition.McpFunctionDefinition> fetchDefinitions() {
        Map<String,McpServerDefinition> clients = getServers();
        if (MapUtils.isEmpty(clients)) {
            return new ArrayList<>();
        }
        List<McpServerDefinition.McpFunctionDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, McpServerDefinition> clientEntry : clients.entrySet()) {
            McpServerDefinition serverDefinition = clientEntry.getValue();
            if (serverDefinition==null) {
                continue;
            }
            List<McpServerDefinition.McpFunctionDefinition> mcpFunctionDefinitions = serverDefinition.getDefinitions();
            if (mcpFunctionDefinitions==null) {
                continue;
            }
            definitions.addAll(mcpFunctionDefinitions);
        }
        return definitions;
    }

    public static Map<String,McpServerDefinition> getServers() {
        synchronized (LOCK) {
            return new HashMap<>(servers);
        }
    }

    public static ToolExecutionResult callTool(String serverKey, String toolName, JSONObject args) {
        McpServerDefinition serverDefinition;
        synchronized (LOCK) {
            serverDefinition = servers.get(serverKey);
        }
        if (serverDefinition == null) {
            throw new IllegalArgumentException("serverKey:" + serverKey + " not found");
        }
        McpClient mcpClient = null;
        try {
            mcpClient = McpAdapter.initMcpServer(serverDefinition.getConfig());
            Constructor<?> constructor = ToolExecutionRequest.Builder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ToolExecutionRequest.Builder reqBuilder = (ToolExecutionRequest.Builder) constructor.newInstance();
            ToolExecutionRequest request = reqBuilder.name(toolName).arguments(args.toJSONString()).build();
            return mcpClient.executeTool(request);
        } catch (Throwable e) {
            e.printStackTrace();
            return ToolExecutionResult.builder().isError(true).resultText(ExceptionUtil.fetchStackTrace(e)).build();
        }finally {
            if(mcpClient!=null){
                System.out.println("mcp-server is close,"+serverKey);
                try {
                    mcpClient.close();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }


    }

    public static void subscribe(TestkitToolWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window can not be null");
        }
        synchronized (LOCK) {
            subscribeWindows.add(window);
            // 如果 MCP servers 已经初始化完成，立即触发一次刷新
            if (!servers.isEmpty()) {
                window.refreshMcpFunctions();
            }
        }
    }


    public static void refreshServers(Map<String,McpServerDefinition> newServers) {
        synchronized (LOCK) {
            servers.clear();
            servers.putAll(newServers);
            Iterator<TestkitToolWindow> iterator = subscribeWindows.iterator();
            while (iterator.hasNext()) {
                TestkitToolWindow window = iterator.next();
                Project project = window.getProject();
                if (project.isDisposed()) {
                    iterator.remove();
                    continue;
                }
                window.refreshMcpFunctions();
            }
        }
    }

    /**
     * 从 Cursor 配置文件中读取 MCP 配置
     * @return Cursor 的 mcp.json 配置对象
     * @throws Exception 如果文件不存在或解析失败
     */
    public static JSONObject readCursorMcpConfig() throws Exception {
        String userHome = System.getProperty("user.home");
        if (StringUtils.isEmpty(userHome)) {
            throw new IllegalArgumentException("User home directory is not set");
        }
        
        Path cursorConfigPath = Paths.get(userHome, ".cursor", "mcp.json");
        File cursorConfigFile = cursorConfigPath.toFile();
        
        if (!cursorConfigFile.exists()) {
            throw new IOException("Cursor configuration file not found: " + cursorConfigPath);
        }
        
        try (FileReader reader = new FileReader(cursorConfigFile)) {
            String content = new String(Files.readAllBytes(cursorConfigFile.toPath()));
            return JSON.parseObject(content);
        } catch (Exception e) {
            throw new Exception("Failed to parse Cursor configuration file: " + e.getMessage(), e);
        }
    }


}
