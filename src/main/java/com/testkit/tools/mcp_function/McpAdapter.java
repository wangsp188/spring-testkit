package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testkit.TestkitHelper;
import com.testkit.util.ExceptionUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class McpAdapter {

    public static final String VERSION = "1.0";


    public static record McpInitRet(Map<String, String> errors,
                                    Map<String, McpServerDefinition> servers) {

        @Override
        public String toString() {
            ArrayList<String> list = new ArrayList<>();
            if (MapUtils.isNotEmpty(errors)) {
                errors.forEach((key, value) -> list.add("MCP-server: " + key + " initialize fail\nerrorMsg: " + value));
            }
            if (MapUtils.isNotEmpty(servers)) {
                for (Map.Entry<String, McpServerDefinition> clientEntry : servers.entrySet()) {
                    McpServerDefinition serverDefinition = clientEntry.getValue();
                    List<McpServerDefinition.McpFunctionDefinition> mcpFunctionDefinitions = serverDefinition.getDefinitions();
                    String tools = mcpFunctionDefinitions.stream().map(new Function<McpServerDefinition.McpFunctionDefinition, String>() {
                        @Override
                        public String apply(McpServerDefinition.McpFunctionDefinition mcpFunctionDefinition) {
                            return mcpFunctionDefinition.getName();
                        }

                    }).collect(Collectors.joining(","));
                    list.add("MCP-server: " + clientEntry.getKey() + " initialize success\ntools: " + tools);
                }
            }
            return StringUtils.join(list, "\n\n");
        }
    }

    public static McpInitRet parseMcpServers(JSONObject mcpJson) {
        Map<String, String> errors = new HashMap<>();
        Map<String, McpServerDefinition> servers = new HashMap<>();
        if (mcpJson == null) {
            return new McpInitRet(errors, servers);
        }

        JSONObject mcpServers = mcpJson.getJSONObject("mcpServers");
        if (MapUtils.isEmpty(mcpServers)) {
            return new McpInitRet(errors, servers);
        }
        for (String key : mcpServers.keySet()) {
            McpSyncClient syncClient = null;
            try {
                JSONObject value = mcpServers.getJSONObject(key);
                if (value == null) {
                    errors.put(key, "config is empty");
                    continue;
                }
                syncClient = initMcpServer(value);
                McpServerDefinition serverDefinition = new McpServerDefinition();
                serverDefinition.setServerName(syncClient.getServerInfo().name());
                serverDefinition.setConfig(value);
                serverDefinition.setDefinitions(fetchToolDefinition(key, syncClient));
                servers.put(key, serverDefinition);
            } catch (Throwable e) {
                System.err.println("init mcp-server fail, key:" + key);
                e.printStackTrace();
                errors.put(key, ExceptionUtil.fetchStackTrace(e));
            }finally {
                if (syncClient != null) {
                    System.out.println("mcp-server is close,"+syncClient.getServerInfo().name());
                    syncClient.close();
                }
            }
        }
        return new McpInitRet(errors, servers);
    }


    public static McpSyncClient initMcpServer(JSONObject config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP-server config can not be null");
        }
        //stdio
        if (config.containsKey("command")) {
            String command = config.getString("command");
            JSONArray argsArray = config.getJSONArray("args");
            List<String> args = argsArray != null ? argsArray.toJavaList(String.class) : new ArrayList<>();
            JSONObject envObject = config.getJSONObject("env");
            Map<String, String> env = envObject != null ? envObject.toJavaObject(new TypeReference<Map<String, String>>() {
            }) : new HashMap<>();
            return buildStdioMcp(command, args, env);
        }
        //sse
        if ("sse".equals(config.getString("type"))) {
            String url = config.getString("url");
            return buildSseMcp(url);
        }
        if ("streamable-http".equals(config.getString("type"))) {
            String url = config.getString("url");
            return buildStreamMcp(url);
        }
        throw new IllegalArgumentException("unknown mcp protocol," + config.toJSONString());
    }


    public static McpSyncClient buildStdioMcp(String commond, List<String> args, Map<String, String> env) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(TestkitHelper.getPluginName(), VERSION);
        ServerParameters serverParameters = ServerParameters.builder(commond).args(args).env(env).build();
//
        StdioClientTransport transport = new StdioClientTransport(serverParameters);
        McpClient.SyncSpec spec = McpClient.sync(transport)
                .clientInfo(clientInfo)
                .requestTimeout(Duration.ofSeconds(300));


        McpSyncClient client = spec.build();
        client.initialize();
        return client;
    }

    public static McpSyncClient buildSseMcp(String baseUrl) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(TestkitHelper.getPluginName(), VERSION);
        int i = baseUrl.lastIndexOf("/");
        if (i <= 0) {
            throw new RuntimeException("baseUrl is error");
        }
        String baseUri = baseUrl.substring(0, i);
        String endPoint = baseUrl.substring(i);
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL);

        HttpClientSseClientTransport sseTransport = HttpClientSseClientTransport.builder(baseUri)
                .sseEndpoint(endPoint)
                .clientBuilder(clientBuilder)
                .objectMapper(new ObjectMapper())
                .build();

        McpClient.SyncSpec spec2 = McpClient.sync(sseTransport)
                .clientInfo(clientInfo)
                .requestTimeout(Duration.ofSeconds(300));
        McpSyncClient client2 = spec2.build();

        client2.initialize();
        return client2;
    }

    public static McpSyncClient buildStreamMcp(String baseUrl) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(TestkitHelper.getPluginName(), VERSION);
        int i = baseUrl.lastIndexOf("/");
        if (i <= 0) {
            throw new RuntimeException("baseUrl is error");
        }
        String baseUri = baseUrl.substring(0, i);
        String endPoint = baseUrl.substring(i);
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL);

        HttpClientStreamableHttpTransport streamTransport = HttpClientStreamableHttpTransport.builder(baseUri)
                .endpoint(endPoint)
                .clientBuilder(clientBuilder)
                .objectMapper(new ObjectMapper())
                .build();

        McpClient.SyncSpec spec2 = McpClient.sync(streamTransport)
                .clientInfo(clientInfo)
                .requestTimeout(Duration.ofSeconds(300));
        McpSyncClient client2 = spec2.build();

        client2.initialize();
        return client2;
    }


    private static List<McpServerDefinition.McpFunctionDefinition> fetchToolDefinition(String name, McpSyncClient client) {
        McpSchema.ListToolsResult listToolsResult = client.listTools();
        if (listToolsResult == null) {
            return new ArrayList<>();
        }

        List<McpSchema.Tool> tools = listToolsResult.tools();
        if (CollectionUtils.isEmpty(tools)) {
            return new ArrayList<>();
        }
        ArrayList<McpServerDefinition.McpFunctionDefinition> objects = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            McpSchema.JsonSchema jsonSchema = tool.inputSchema();
            McpServerDefinition.McpFunctionDefinition exposureDefinition = new McpServerDefinition.McpFunctionDefinition();
            exposureDefinition.setType(McpServerDefinition.FunctionType.tool);
            exposureDefinition.setServerKey(name);
            exposureDefinition.setName(tool.name());
            exposureDefinition.setDescription(tool.description());

            ArrayList<McpServerDefinition.ArgSchema> argSchemas = new ArrayList<>();
            Map<String, Object> properties = jsonSchema.properties();
            if (MapUtils.isEmpty(properties)) {
                exposureDefinition.setArgSchemas(argSchemas);
                objects.add(exposureDefinition);
                continue;
            }
            List<String> required = jsonSchema.required();
            for (Map.Entry<String, Object> stringObjectEntry : properties.entrySet()) {
                String argName = stringObjectEntry.getKey();
                Object value = stringObjectEntry.getValue();
                JSONObject argJson = new JSONObject();
                if (value != null) {
                    argJson = JSON.parseObject(JSON.toJSONString(value));
                }
                String string = argJson.getString("type");
                McpServerDefinition.ArgSchema argSchema = new McpServerDefinition.ArgSchema(argName, string, argJson.getString("description"), required != null && required.contains(argName));
                argSchemas.add(argSchema);
            }
            exposureDefinition.setArgSchemas(argSchemas);
            objects.add(exposureDefinition);
        }
        return objects;
    }

}
