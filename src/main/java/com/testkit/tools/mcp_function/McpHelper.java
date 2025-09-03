package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.testkit.TestkitHelper;
import com.testkit.util.ExceptionUtil;
import com.testkit.view.TestkitToolWindow;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class McpHelper {

    private static final String MCP_DIR = ".spring-testkit/mcp";

    private static final Map<String,McpServerDefinition> servers = new HashMap<>();

    private static final Set<TestkitToolWindow> subscribeWindows = new HashSet<>();

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
        return servers;
    }

    public static String callTool(String serverKey, String toolName, JSONObject args) {
        McpServerDefinition serverDefinition = servers.get(serverKey);
        if (serverDefinition == null) {
            throw new IllegalArgumentException("serverKey:" + serverKey + " not found");
        }
        McpSyncClient mcpSyncClient = null;
        try {
            mcpSyncClient = McpAdapter.initMcpServer(serverDefinition.getConfig());

            McpSchema.CallToolResult callToolResult = mcpSyncClient.callTool(new McpSchema.CallToolRequest(toolName, args));
            if (callToolResult == null) {
                return "null";
            }
            //fail
            if (callToolResult.isError() != null && callToolResult.isError()) {
                List<McpSchema.Content> content = callToolResult.content();
                if (content != null && content.size() == 1 && content.get(0) instanceof McpSchema.TextContent) {
                    return ((McpSchema.TextContent) content.get(0)).text();
                }
                return callToolResult.toString();
            }
            List<McpSchema.Content> content = Optional.ofNullable(callToolResult.content()).orElse(new ArrayList<>());
            if (content.isEmpty()) {
                return "null";
            }

            //判断是否是仅一个text
            if (content.size() == 1 && content.get(0) instanceof McpSchema.TextContent) {
                return ((McpSchema.TextContent) content.get(0)).text();
            }

            return content.stream().map(new Function<McpSchema.Content, String>() {
                @Override
                public String apply(McpSchema.Content content) {
                    if (content instanceof McpSchema.TextContent) {
                        return ((McpSchema.TextContent) content).text();
                    }else{
                        return content.toString();
                    }
                }
            }).collect(Collectors.joining("\n\n"));
        } catch (Throwable e) {
            e.printStackTrace();
            return ExceptionUtil.fetchStackTrace(e);
        }finally {
            if(mcpSyncClient!=null){
                System.out.println("mcp-server is close,"+mcpSyncClient.getServerInfo().name());
                mcpSyncClient.close();
            }
        }


    }

    public static void subscribe(TestkitToolWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window can not be null");
        }
        subscribeWindows.add(window);
    }


    public static void refreshServers(Map<String,McpServerDefinition> newServers) {
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
