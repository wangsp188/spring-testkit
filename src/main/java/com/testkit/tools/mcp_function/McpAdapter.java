package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.testkit.TestkitHelper;
import com.testkit.util.ExceptionUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.request.json.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

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
            McpClient mcpClient = null;
            try {
                JSONObject value = mcpServers.getJSONObject(key);
                if (value == null) {
                    errors.put(key, "config is empty");
                    continue;
                }
                mcpClient = initMcpServer(value);
                McpServerDefinition serverDefinition = new McpServerDefinition();
                serverDefinition.setConfig(value);
                serverDefinition.setDefinitions(fetchToolDefinition(key, mcpClient));
                servers.put(key, serverDefinition);
            } catch (Throwable e) {
                System.err.println("init mcp-server fail, key:" + key);
                e.printStackTrace();
                if (e.getMessage() != null && e.getMessage().contains("java.io.IOException: Cannot run program") && e.getMessage().contains("error=2")) {
                    errors.put(key, "Command execution failed: the IDE plugin cannot access your terminal PATH.\n" +
                            "Please provide the full absolute path on the `command` key.\n" +
                            "On macOS, run `which uv` (or `which <command>`) in Terminal to find the path, then update the plugin configuration.");
                } else {
                    errors.put(key, ExceptionUtil.fetchStackTrace(e));
                }
            }finally {
                if (mcpClient != null) {
                    System.out.println("mcp-server is close,"+key);
                    try {
                        mcpClient.close();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return new McpInitRet(errors, servers);
    }


    public static McpClient initMcpServer(JSONObject config) {
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


    public static McpClient buildStdioMcp(String command, List<String> args, Map<String, String> env) {
        List<String> commands = new ArrayList<>();
        commands.add(command);
        if(CollectionUtils.isNotEmpty(args)){
            commands.addAll(args);
        }

        McpTransport transport = new StdioMcpTransport.Builder()
                .command(commands)
                .environment(env)
                .logEvents(true)
                .build();

        DefaultMcpClient mcpClient = new DefaultMcpClient.Builder()
                .clientName(TestkitHelper.getPluginName())
                .clientVersion(VERSION)
                .toolExecutionTimeout(Duration.ofSeconds(300))
                .initializationTimeout(Duration.ofSeconds(10))
                .autoHealthCheck(false)
                .transport(transport)
                .build();
        return mcpClient;
    }

    public static McpClient buildSseMcp(String baseUrl) {
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(baseUrl)
                .timeout(Duration.ofSeconds(10))
                .logRequests(true)
                .logResponses(true)
                .build();


        DefaultMcpClient mcpClient = new DefaultMcpClient.Builder()
                .clientName(TestkitHelper.getPluginName())
                .clientVersion(VERSION)
                .toolExecutionTimeout(Duration.ofSeconds(300))
                .initializationTimeout(Duration.ofSeconds(10))
                .autoHealthCheck(false)
                .transport(transport)
                .build();
        return mcpClient;
    }

    public static McpClient buildStreamMcp(String baseUrl) {
        McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url(baseUrl)
                .timeout(Duration.ofSeconds(10))
                .logRequests(true)
                .logResponses(true)
                .build();
        DefaultMcpClient mcpClient = new DefaultMcpClient.Builder()
                .clientName(TestkitHelper.getPluginName())
                .clientVersion(VERSION)
                .toolExecutionTimeout(Duration.ofSeconds(300))
                .initializationTimeout(Duration.ofSeconds(10))
                .autoHealthCheck(false)
                .transport(transport)
                .build();
        return mcpClient;
    }


    private static List<McpServerDefinition.McpFunctionDefinition> fetchToolDefinition(String name, McpClient client) {
        List<ToolSpecification> tools = client.listTools();
        if (CollectionUtils.isEmpty(tools)) {
            return new ArrayList<>();
        }
        ArrayList<McpServerDefinition.McpFunctionDefinition> objects = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            JsonObjectSchema jsonSchema = tool.parameters();
            McpServerDefinition.McpFunctionDefinition exposureDefinition = new McpServerDefinition.McpFunctionDefinition();
            exposureDefinition.setType(McpServerDefinition.FunctionType.tool);
            exposureDefinition.setServerKey(name);
            exposureDefinition.setName(tool.name());
            exposureDefinition.setDescription(tool.description());

            ArrayList<McpServerDefinition.ArgSchema> argSchemas = new ArrayList<>();
            Map<String, JsonSchemaElement> properties = jsonSchema.properties();
            if (MapUtils.isEmpty(properties)) {
                exposureDefinition.setArgSchemas(argSchemas);
                objects.add(exposureDefinition);
                continue;
            }
            List<String> required = jsonSchema.required();
            for (Map.Entry<String, JsonSchemaElement> stringObjectEntry : properties.entrySet()) {
                String argName = stringObjectEntry.getKey();
                JsonSchemaElement value = stringObjectEntry.getValue();
                String type = value.getClass().getSimpleName();
                Object typeExtension = null;
                if (value instanceof JsonAnyOfSchema) {
                    type = McpServerDefinition.ArgType.ANY_OF.getCode();
                }else if(value instanceof JsonObjectSchema){
                    type = McpServerDefinition.ArgType.OBJECT.getCode();
                }else if (value instanceof JsonArraySchema){
                    type = McpServerDefinition.ArgType.ARRAY.getCode();
                }else if(value instanceof JsonBooleanSchema){
                    type = McpServerDefinition.ArgType.BOOLEAN.getCode();
                }else if(value instanceof JsonNumberSchema){
                    type = McpServerDefinition.ArgType.NUMBER.getCode();
                }else if(value instanceof JsonIntegerSchema){
                    type = McpServerDefinition.ArgType.INTEGER.getCode();
                }else if (value instanceof JsonStringSchema){
                    type = McpServerDefinition.ArgType.STRING.getCode();
                }else if(value instanceof JsonNullSchema){
                    type = McpServerDefinition.ArgType.NULL.getCode();
                }else if (value instanceof JsonEnumSchema){
                    type = McpServerDefinition.ArgType.ENUM.getCode();
                    typeExtension = ((JsonEnumSchema) value).enumValues();
                }else if (value instanceof JsonRawSchema){
                    type = McpServerDefinition.ArgType.RAW.getCode();
                }else if (value instanceof JsonReferenceSchema){
                    type = McpServerDefinition.ArgType.REFERENCE.getCode();
                }
                McpServerDefinition.ArgSchema argSchema = new McpServerDefinition.ArgSchema(argName, type, typeExtension,value.description(), required != null && required.contains(argName));
                argSchemas.add(argSchema);
            }
            exposureDefinition.setArgSchemas(argSchemas);
            objects.add(exposureDefinition);
        }
        return objects;
    }

}
