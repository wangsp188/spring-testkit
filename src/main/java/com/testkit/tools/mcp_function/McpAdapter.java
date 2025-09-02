package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testkit.TestkitHelper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
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
                                    Map<String, McpSyncClient> clients) implements AutoCloseable {

        @Override
        public String toString() {
            ArrayList<String> list = new ArrayList<>();
            if (MapUtils.isNotEmpty(errors)) {
                errors.forEach((key, value) -> list.add("MCP-server: "+key + " initialize fail\nerrorMsg: " + value));
            }
            if (MapUtils.isNotEmpty(clients)) {
                for (Map.Entry<String, McpSyncClient> clientEntry : clients.entrySet()) {
                    McpSyncClient client = clientEntry.getValue();
                    List<McpFunctionDefinition> mcpFunctionDefinitions = fetchToolDefinition(clientEntry.getKey(), client);
                    String tools = mcpFunctionDefinitions.stream().map(new Function<McpFunctionDefinition, String>() {
                        @Override
                        public String apply(McpFunctionDefinition mcpFunctionDefinition) {
                            return mcpFunctionDefinition.getName();
                        }

                    }).collect(Collectors.joining(","));
                    list.add("MCP-server: "+clientEntry.getKey() + " initialize success\ntools: " + tools);
                }
            }
            return StringUtils.join(list,"\n\n");
        }

        @Override
        public void close() {
            if (clients == null) {
                return;
            }
            for (Map.Entry<String, McpSyncClient> clientEntry : clients.entrySet()) {
                System.err.println("close MCP-server, "+clientEntry.getKey());
                clientEntry.getValue().close();
            }
        }
    }

    public static McpInitRet parseAndBuildMcpClients(JSONObject mcpJson) {
        Map<String, String> errors = new HashMap<>();
        Map<String, McpSyncClient> clients = new HashMap<>();
        if (mcpJson == null) {
            return new McpInitRet(errors, clients);
        }

        JSONObject mcpServers = mcpJson.getJSONObject("mcpServers");
        if (MapUtils.isEmpty(mcpServers)) {
            return new McpInitRet(errors, clients);
        }
        for (String key : mcpServers.keySet()) {
            try {
                JSONObject value = mcpServers.getJSONObject(key);
                if (value == null) {
                    errors.put(key, "config is empty");
                    continue;
                }
                //stdio
                if (value.containsKey("command")) {
                    String command = value.getString("command");
                    JSONArray argsArray = value.getJSONArray("args");
                    List<String> args = argsArray != null ? argsArray.toJavaList(String.class) : new ArrayList<>();
                    JSONObject envObject = value.getJSONObject("env");
                    Map<String, String> env = envObject != null ? envObject.toJavaObject(new TypeReference<Map<String, String>>() {
                    }) : new HashMap<>();
                    McpSyncClient stdioMcp = buildStdioMcp(command, args, env);
                    clients.put(key, stdioMcp);
                    continue;
                }
                //sse
                if (value.containsKey("url")) {
                    String url = value.getString("url");
                    McpSyncClient sseMcp = buildSseMcp(url, null);
                    clients.put(key, sseMcp);
                }
            } catch (Throwable e) {
                errors.put(key, "errorType:" + e.getClass().getSimpleName() + ", errorMsg:" + e.getMessage());
            }
        }
        return new McpInitRet(errors, clients);
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

    public static McpSyncClient buildSseMcp(String baseUrl, String endPoint) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(TestkitHelper.getPluginName(), VERSION);
        String sseEndpoint = endPoint != null ? endPoint : "/sse";
        HttpClientSseClientTransport sseTransport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .clientBuilder(HttpClient.newBuilder())
                .objectMapper(new ObjectMapper())
                .build();

        McpClient.SyncSpec spec2 = McpClient.sync(sseTransport)
                .clientInfo(clientInfo)
                .requestTimeout(Duration.ofSeconds(300));
        McpSyncClient client2 = spec2.build();

        client2.initialize();
        return client2;
    }


    public static List<McpFunctionDefinition> fetchToolDefinition(String name, McpSyncClient client) {
        McpSchema.ListToolsResult listToolsResult = client.listTools();
        if (listToolsResult == null) {
            return new ArrayList<>();
        }

        List<McpSchema.Tool> tools = listToolsResult.tools();
        if (CollectionUtils.isEmpty(tools)) {
            return new ArrayList<>();
        }
        ArrayList<McpFunctionDefinition> objects = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            McpSchema.JsonSchema jsonSchema = tool.inputSchema();
            McpFunctionDefinition exposureDefinition = new McpFunctionDefinition();
            exposureDefinition.setType(McpFunctionDefinition.FunctionType.tool);
            exposureDefinition.setServerKey(name);
            exposureDefinition.setName(tool.name());
            exposureDefinition.setDescription(tool.description());

            ArrayList<McpFunctionDefinition.ArgSchema> argSchemas = new ArrayList<>();
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
                McpFunctionDefinition.ArgSchema argSchema = new McpFunctionDefinition.ArgSchema(argName, string, argJson.getString("description"), required != null && required.contains(argName));
                argSchemas.add(argSchema);
            }
            exposureDefinition.setArgSchemas(argSchemas);
            objects.add(exposureDefinition);
        }
        return objects;
    }

}
