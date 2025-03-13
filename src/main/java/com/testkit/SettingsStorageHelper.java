package com.testkit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.view.SettingsDialog;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 这是一个本地文件存储器
 * 本质上根据project的名称来存储到用户的.testkit/config的目录下的一个文件
 * 每个project一个文件
 * 每个文件的内容是个json，结构是ProjectConfig这样的
 */
public class SettingsStorageHelper {

    private static final String CONFIG_DIR = ".spring-testkit/config";

    public static final String defFlexibleTestPackage = "flexibletest";

    public static final List<String> defBeanAnnotations = Arrays.asList("org.apache.ibatis.annotations.Mapper", "org.springframework.cloud.openfeign.FeignClient");

    public static final String defProperties = "logging.level.com.testkit=INFO";
    public static final String datasourceTemplateProperties = "#local_test datasource\n" +
            "datasource.local.url=jdbc:mysql:///test\n" +
            "datasource.local.username=xx\n" +
            "datasource.local.password=xx";

    public static final String defInterceptor =
            "import org.springframework.beans.factory.annotation.Autowired;\n" +
                    "import org.springframework.context.ApplicationContext;\n" +
                    "\n" +
                    "import java.util.Map;\n" +
                    "\n" +
                    "\n" +
                    "/**\n" +
                    " * tool执行的拦截类\n" +
                    " * 一定要有一个无参构造\n" +
                    " * tool执行前后分别会执行invokeBefore和invokeAfter函数\n" +
                    " * 可执行业务逻辑\n" +
                    " * 可引用项目中的类及函数\n" +
                    " * trace写入\n" +
                    " * 登录态模拟\n" +
                    " * 支持使用@Autowired, @Resource, @Qualifier注入项目中存在的bean\n" +
                    " */\n" +
                    "class MyInterceptor {\n" +
                    "\n" +
                    "    @Autowired\n" +
                    "    private ApplicationContext ctx;\n" +
                    "\n" +
                    "    public MyInterceptor() {\n" +
                    "    }\n" +
                    "\n" +
                    "\n" +
                    "    /**\n" +
                    "     * tool执行前\n" +
                    "     *\n" +
                    "     * @param env    环境，可能为空\n" +
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @throws Throwable 抛出异常则阻断tool调用\n" +
                    "     */\n" +
                    "    public void invokeBefore(String env, String tool, Map<String, String> params)  throws Throwable{\n" +
                    "\n" +
                    "    }\n" +
                    "\n" +
                    "    /**\n" +
                    "     * tool执行后\n" +
                    "     *\n" +
                    "     * @param env    环境，可能为空\n" +
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @param cost   耗时ms 不包含脚本耗时 可能为空\n" +
                    "     * @param ret    返回内容 可能为空\n" +
                    "     * @param e      抛出的异常 脚本异常也会记录 可能为空\n" +
                    "     */\n" +
                    "    public void invokeAfter(String env, String tool, Map<String, String> params, Integer cost, Object ret, Throwable e) {\n" +
                    "    }\n" +
                    "\n" +
                    "}";

    public static final HttpCommand DEF_CONTROLLER_COMMAND = new HttpCommand();

    public static final HttpCommand DEF_FEIGN_COMMAND = new HttpCommand();


    static {
        DEF_CONTROLLER_COMMAND.setScript("import groovy.json.JsonOutput\n" +
                "import groovy.json.JsonSlurper\n" +
                "\n" +
                "import java.io.FileNotFoundException;\n" +
                "import java.io.IOException;\n" +
                "import java.net.HttpURLConnection;\n" +
                "import java.net.URLEncoder;\n" +
                "import java.util.Map;\n" +
                "\n" +
                "\n" +
                "/**\n" +
                " * groovy脚本，在idea-plugin容器执行\n" +
                " * controller command构建函数，返回结果会被copy到剪切板，建议采用curl格式\n" +
                " * <p>\n" +
                " * 不可使用项目中类，不可使用项目中类，不可使用项目中类\n" +
                " * <p>\n" +
                " * 运行代码如下\n" +
                " * <p>\n" +
                " * GroovyShell groovyShell = new GroovyShell();\n" +
                " * Script script = groovyShell.parse(this-code);\n" +
                " * Object build = InvokerHelper.invokeMethod(script, \"generate\", new Object[]{env,  selectedAppPort, httpMethod, path, params, jsonBody});\n" +
                " * return build == null ? \"\" : String.valueOf(build);\n" +
                " * <p>\n" +
                " * 提供以下工具函数\n" +
                " * buildCurl：构建curl函数\n" +
                " * http：发起http请求函数\n" +
                " *\n" +
                " * @param env        环境，可能为空\n" +
                " * @param httpMethod GET/POST 等等http方法，非空\n" +
                " * @param path       /uri 非空\n" +
                " * @param params     传递的参数，k和v都是string，非空\n" +
                " * @param requesterHeaders  Map<String,String> 形式请求头，非空\n" +
                " * @param jsonBody   json形式的请求体，字符串类型，非空代表Content-Type: application/json，可能为空\n" +
                " * @return 返回结果会被copy到剪切板\n" +
                " */\n" +
                "def generate(String env, Integer runtimePort, String httpMethod, String path, Map<String, String> params, Map<String, String> requesterHeaders, String jsonBody) {\n" +
                "    String domain = \"http://localhost:8080\"\n" +
                "    if (\"local\" == env && runtimePort != null) {\n" +
                "        domain = \"http://localhost:\" + runtimePort\n" +
                "    }\n" +
                "    String token = getToken(env)\n" +
                "    requesterHeaders['Authorization'] = \"Bearer ${token}\"\n" +
                "    buildCurl(domain, httpMethod, path, params,requesterHeaders,jsonBody)\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def getToken(env) {\n" +
                "    // your project logic\n" +
                "    return \"your_token\"\n" +
                "}\n" +
                "\n" +
                "\n" +
                "class HttpRes {\n" +
                "    Integer status\n" +
                "    String body\n" +
                "    Map<String, List<String>> headers\n" +
                "\n" +
                "    String toJson() {\n" +
                "        def responseMap = [\n" +
                "                status:\n" +
                "                        status,\n" +
                "                body:parseBodyAsJson(),\n" +
                "                headers:filterNullKeys(headers)\n" +
                "        ]\n" +
                "        return JsonOutput.toJson(responseMap)\n" +
                "    }\n" +
                "\n" +
                "    private Object parseBodyAsJson() {\n" +
                "        try {\n" +
                "            return new JsonSlurper().parseText(body)\n" +
                "        } catch (Exception e) {\n" +
                "            return body\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    private Map<String, List<String>> filterNullKeys(Map<String, List<String>> map) {\n" +
                "        return map.findAll {\n" +
                "            k, v -> k != null\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "HttpRes http(String httpMethod, String uri, Map<String, String> headers, Map<String, String> params, String jsonBody) {\n" +
                "    if (params) {\n" +
                "        def queryString = params.collect {\n" +
                "            k, v -> \"${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v==null?\" \":v, 'UTF-8')}\"\n" +
                "        }.join('&')\n" +
                "        uri += \"?${queryString}\"\n" +
                "    }\n" +
                "    HttpURLConnection connection = null\n" +
                "    try {\n" +
                "        connection = new URL(uri).openConnection()\n" +
                "        connection.setRequestMethod(httpMethod.toUpperCase())\n" +
                "\n" +
                "        if (headers) {\n" +
                "            headers.each {\n" +
                "                key, value ->\n" +
                "                    connection.setRequestProperty(key, value)\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        if (jsonBody) {\n" +
                "            connection.setRequestProperty(\"Content-Type\", \"application/json\")\n" +
                "            connection.doOutput = true\n" +
                "            connection.outputStream.withWriter(\"UTF-8\") {\n" +
                "                writer ->\n" +
                "                    writer << jsonBody\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        def responseCode = connection.responseCode\n" +
                "        def responseHeaders = connection.headerFields\n" +
                "\n" +
                "        def responseBody\n" +
                "        try {\n" +
                "            responseBody = connection.inputStream.withReader(\"UTF-8\") {\n" +
                "                reader ->\n" +
                "                    reader.text\n" +
                "            }\n" +
                "        } catch (Throwable e) {\n" +
                "            responseBody = connection.errorStream.withReader(\"UTF-8\") {\n" +
                "                reader ->\n" +
                "                    reader.text\n" +
                "            }\n" +
                "        }\n" +
                "        return new HttpRes(status:responseCode, body:responseBody, headers:responseHeaders)\n" +
                "    } catch (ConnectException e) {\n" +
                "        return new HttpRes(status:503, body:\"Service unavailable\", headers: [:])\n" +
                "    } catch (FileNotFoundException e) {\n" +
                "        return new HttpRes(status:404, body:\"Resource not found: ${e.message}\", headers: [:])\n" +
                "    } catch (IOException e) {\n" +
                "        return new HttpRes(status:500, body:\"IO Exception: ${e.message}\", headers: [:])\n" +
                "    } finally {\n" +
                "        if (connection != null) {\n" +
                "            connection.disconnect()\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def buildCurl(domain, httpMethod, path, params, headers, jsonBody) {\n" +
                "    StringBuilder ret = new StringBuilder()\n" +
                "    domain = domain.endsWith('/') ? domain.substring(0, domain.length() - 1) : domain\n" +
                "    path = path.startsWith('/') ? path : (\"/\"+path)\n" +
                "    ret.append(\"curl -i -X ${httpMethod.toUpperCase()} '${domain}${path}\")\n" +
                "\n" +
                "    if (params != null && params.size() > 0) {\n" +
                "        ret.append(\"?\");\n" +
                "        params.forEach((key, value) -> {\n" +
                "            ret.append(URLEncoder.encode(key, \"UTF-8\"))\n" +
                "                    .append(\"=\")\n" +
                "                    .append(URLEncoder.encode(value, \"UTF-8\"))\n" +
                "                    .append(\"&\");\n" +
                "        });\n" +
                "        // Remove the trailing '&'  \n" +
                "        ret.setLength(ret.length() - 1);\n" +
                "    }\n" +
                "    ret.append(\"' \")\n" +
                "\n" +
                "    if (headers != null && headers.size() > 0) {\n" +
                "        headers.forEach((key, value) -> {\n" +
                "            if (\"Content-Type\" == key) {\n" +
                "                return\n" +
                "            }\n" +
                "            ret.append(\" \\\\\\n  -H '${key}: ${value}'\")\n" +
                "        });\n" +
                "    }\n" +
                "    if (jsonBody != null) {\n" +
                "        ret.append(\" \\\\\\n  -H 'Content-Type: application/json' \\\\\\n  -d '${jsonBody}'\")\n" +
                "    }\n" +
                "    return ret\n" +
                "}\n" +
                "\n");
    }


    static {
        DEF_FEIGN_COMMAND.setScript("import groovy.json.JsonOutput\n" +
                "import groovy.json.JsonSlurper\n" +
                "\n" +
                "import java.io.FileNotFoundException;\n" +
                "import java.io.IOException;\n" +
                "import java.net.HttpURLConnection;\n" +
                "import java.net.URLEncoder;\n" +
                "import java.util.Map;\n" +
                "\n" +
                "\n" +
                "/**\n" +
                " * groovy脚本，在idea-plugin容器执行\n" +
                " * FeignClient command构建函数，返回结果会被copy到剪切板，建议采用curl格式\n" +
                " * <p>\n" +
                " * 不可使用项目中类，不可使用项目中类，不可使用项目中类\n" +
                " * <p>\n" +
                " * 运行代码如下\n" +
                " * <p>\n" +
                " * GroovyShell groovyShell = new GroovyShell();\n" +
                " * Script script = groovyShell.parse(this-code);\n" +
                " * Object build = InvokerHelper.invokeMethod(script, \"generate\", new Object[]{env,  feignName, feignUrl, httpMethod, path, params, jsonBody});\n" +
                " * return build == null ? \"\" : String.valueOf(build);\n" +
                " * <p>\n" +
                " * 提供以下工具函数\n" +
                " * buildCurl：构建curl函数\n" +
                " * http：发起http请求函数\n" +
                " *\n" +
                " * @param env        环境，可能为空\n" +
                " * @param feignName    FeignClient.name  非空\n" +
                " * @param feignUrl        FeignClient.url  可能为空\n" +
                " * @param httpMethod GET/POST 等等http方法，非空\n" +
                " * @param path       /uri 非空\n" +
                " * @param params     传递的参数，k和v都是string，非空\n" +
                " * @param requesterHeaders  Map<String,String> 形式请求头，非空\n" +
                " * @param jsonBody   json形式的请求体，字符串类型，非空代表Content-Type: application/json，可能为空\n" +
                " * @return 返回结果会被copy到剪切板\n" +
                " */\n" +
                "def generate(String env, String feignName, String feignUrl, String httpMethod, String path, Map<String, String> params, Map<String, String> requesterHeaders, String jsonBody) {\n" +
                "    String domain = getDomain(feignName,feignUrl)\n" +
                "    String token = getToken(env)\n" +
                "    requesterHeaders['Authorization'] = \"Bearer ${token}\"\n" +
                "    buildCurl(domain, httpMethod, path, params,requesterHeaders,jsonBody)\n" +
                "}\n" +
                "\n" +
                "def getDomain(feignName, feignUrl){\n" +
                "   if(feignUrl){\n" +
                "       return feignUrl\n" +
                "   }\n" +
                "   return \"http://\"+feignName\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def getToken(env) {\n" +
                "    // your project logic\n" +
                "    return \"your_token\"\n" +
                "}\n" +
                "\n" +
                "\n" +
                "class HttpRes {\n" +
                "    Integer status\n" +
                "    String body\n" +
                "    Map<String, List<String>> headers\n" +
                "\n" +
                "    String toJson() {\n" +
                "        def responseMap = [\n" +
                "                status:\n" +
                "                        status,\n" +
                "                body:parseBodyAsJson(),\n" +
                "                headers:filterNullKeys(headers)\n" +
                "        ]\n" +
                "        return JsonOutput.toJson(responseMap)\n" +
                "    }\n" +
                "\n" +
                "    private Object parseBodyAsJson() {\n" +
                "        try {\n" +
                "            return new JsonSlurper().parseText(body)\n" +
                "        } catch (Exception e) {\n" +
                "            return body\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    private Map<String, List<String>> filterNullKeys(Map<String, List<String>> map) {\n" +
                "        return map.findAll {\n" +
                "            k, v -> k != null\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "HttpRes http(String httpMethod, String uri, Map<String, String> headers, Map<String, String> params, String jsonBody) {\n" +
                "    if (params) {\n" +
                "        def queryString = params.collect {\n" +
                "            k, v -> \"${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v==null?\" \":v, 'UTF-8')}\"\n" +
                "        }.join('&')\n" +
                "        uri += \"?${queryString}\"\n" +
                "    }\n" +
                "    HttpURLConnection connection = null\n" +
                "    try {\n" +
                "        connection = new URL(uri).openConnection()\n" +
                "        connection.setRequestMethod(httpMethod.toUpperCase())\n" +
                "\n" +
                "        if (headers) {\n" +
                "            headers.each {\n" +
                "                key, value ->\n" +
                "                    connection.setRequestProperty(key, value)\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        if (jsonBody) {\n" +
                "            connection.setRequestProperty(\"Content-Type\", \"application/json\")\n" +
                "            connection.doOutput = true\n" +
                "            connection.outputStream.withWriter(\"UTF-8\") {\n" +
                "                writer ->\n" +
                "                    writer << jsonBody\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        def responseCode = connection.responseCode\n" +
                "        def responseHeaders = connection.headerFields\n" +
                "\n" +
                "        def responseBody\n" +
                "        try {\n" +
                "            responseBody = connection.inputStream.withReader(\"UTF-8\") {\n" +
                "                reader ->\n" +
                "                    reader.text\n" +
                "            }\n" +
                "        } catch (Throwable e) {\n" +
                "            responseBody = connection.errorStream.withReader(\"UTF-8\") {\n" +
                "                reader ->\n" +
                "                    reader.text\n" +
                "            }\n" +
                "        }\n" +
                "        return new HttpRes(status:responseCode, body:responseBody, headers:responseHeaders)\n" +
                "    } catch (ConnectException e) {\n" +
                "        return new HttpRes(status:503, body:\"Service unavailable\", headers: [:])\n" +
                "    } catch (FileNotFoundException e) {\n" +
                "        return new HttpRes(status:404, body:\"Resource not found: ${e.message}\", headers: [:])\n" +
                "    } catch (IOException e) {\n" +
                "        return new HttpRes(status:500, body:\"IO Exception: ${e.message}\", headers: [:])\n" +
                "    } finally {\n" +
                "        if (connection != null) {\n" +
                "            connection.disconnect()\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def buildCurl(domain, httpMethod, path, params, headers, jsonBody) {\n" +
                "    StringBuilder ret = new StringBuilder()\n" +
                "    domain = domain.endsWith('/') ? domain.substring(0, domain.length() - 1) : domain\n" +
                "    path = path.startsWith('/') ? path : (\"/\"+path)\n" +
                "    ret.append(\"curl -i -X ${httpMethod.toUpperCase()} '${domain}${path}\")\n" +
                "\n" +
                "    if (params != null && params.size() > 0) {\n" +
                "        ret.append(\"?\");\n" +
                "        params.forEach((key, value) -> {\n" +
                "            ret.append(URLEncoder.encode(key, \"UTF-8\"))\n" +
                "                    .append(\"=\")\n" +
                "                    .append(URLEncoder.encode(value, \"UTF-8\"))\n" +
                "                    .append(\"&\");\n" +
                "        });\n" +
                "        // Remove the trailing '&'  \n" +
                "        ret.setLength(ret.length() - 1);\n" +
                "    }\n" +
                "    ret.append(\"' \")\n" +
                "\n" +
                "    if (headers != null && headers.size() > 0) {\n" +
                "        headers.forEach((key, value) -> {\n" +
                "            if (\"Content-Type\" == key) {\n" +
                "                return\n" +
                "            }\n" +
                "            ret.append(\" \\\\\\n  -H '${key}: ${value}'\")\n" +
                "        });\n" +
                "    }\n" +
                "    if (jsonBody != null) {\n" +
                "        ret.append(\" \\\\\\n  -H 'Content-Type: application/json' \\\\\\n  -d '${jsonBody}'\")\n" +
                "    }\n" +
                "    return ret\n" +
                "}\n" +
                "\n");
    }


    public static final TraceConfig DEF_TRACE_CONFIG = new TraceConfig();

    public static final Map<String, SettingsStorageHelper.ProjectConfig> preSettings;

    static {
        DEF_TRACE_CONFIG.setEnable(false);
        DEF_TRACE_CONFIG.setTraceWeb(true);
        DEF_TRACE_CONFIG.setTraceMybatis(true);
        DEF_TRACE_CONFIG.setLogMybatis(true);
        DEF_TRACE_CONFIG.setPackages("app.three.package");
        DEF_TRACE_CONFIG.setClsSuffix("Controller,Service,Impl,Repository");
        DEF_TRACE_CONFIG.setBlacks("");
        DEF_TRACE_CONFIG.setWhites("");
        preSettings = parsePreSettings();
//        DEF_TRACE_CONFIG.setSingleClsDepth(2);
    }

    public static Map<String, SettingsStorageHelper.ProjectConfig> getPreSettings() {
        return new HashMap<>(preSettings);
//        return parsePreSettings();
    }

    private static Map<String, SettingsStorageHelper.ProjectConfig> parsePreSettings() {
        HashMap<String, SettingsStorageHelper.ProjectConfig> map = new HashMap<>();
        final String dirPath = "/settings-templates";
        // 获取资源目录URL（兼容JAR包内和文件系统）
        URL resourceUrl = SettingsStorageHelper.class.getResource(dirPath);
        if (resourceUrl == null) {
            return map;
        }
        try {
            String jarPath = resourceUrl.getPath().split("!")[0].replace("file:", "");
            jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name());
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    // 匹配目标目录下的.json文件
                    if (entryName.startsWith("settings-templates/") && entryName.endsWith(".json") &&
                            !entry.isDirectory()) {

                        try (InputStream stream = jar.getInputStream(entry)) {
                            String jsonContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            SettingsStorageHelper.ProjectConfig config = JSON.parseObject(jsonContent, SettingsStorageHelper.ProjectConfig.class);
                            // 提取纯文件名（处理路径分隔符）
                            String baseName = entryName.substring(entryName.lastIndexOf('/') + 1)
                                    .replaceFirst("\\.json$", "");

                            map.put(baseName, config);
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            return map;
        } catch (Throwable e) {
            e.printStackTrace();
            return map;
        }
    }

    public static boolean hasAnySettings() {
        String projectPath = System.getProperty("user.home");
        if (StringUtils.isEmpty(projectPath)) {
            throw new IllegalArgumentException("Project base path is not set.");
        }
        Path configDirPath = Paths.get(projectPath, CONFIG_DIR);
        File configDir = configDirPath.toFile();
        return configDir.exists();
    }

    public static boolean isEnableSideServer(Project project) {
        return getConfig(project).isEnableSideServer();
    }


    public static void setEnableSideServer(Project project, boolean enable) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setEnableSideServer(enable);
        saveProjectConfig(project, projectConfig);
    }


    public static SqlConfig getSqlConfig(Project project) {
        return getConfig(project).getSqlConfig();
    }


    public static void setSqlConfig(Project project, SqlConfig config) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setSqlConfig(config);
        saveProjectConfig(project, projectConfig);
    }

    public static String getFlexibleTestPackage(Project project) {
        return getConfig(project).getFlexibleTestPackage();
    }

    public static List<String> getBeanAnnotations(Project project) {
        return getConfig(project).getBeanAnnotations();
    }

    public static TraceConfig getTraceConfig(Project project) {
        return getConfig(project).getTraceConfig();
    }


    public static String getAppScript(Project project, String app) {
        return getAppConfig(project, app).getScript();
    }


    public static HttpCommand getAppControllerCommand(Project project, String app) {
        return getAppConfig(project, app).getControllerCommand();
    }

    public static HttpCommand getAppFeignCommand(Project project, String app) {
        return getAppConfig(project, app).getFeignCommand();
    }


    public static String getAppProperties(Project project, String app) {
        return getAppConfig(project, app).getProperties();
    }


    public static void setFlexibleTestPackage(Project project, String flexibleTestPackage) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setFlexibleTestPackage(flexibleTestPackage);
        saveProjectConfig(project, projectConfig);
    }

    public static void setBeanAnnotations(Project project, List<String> beanAnnotations) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setBeanAnnotations(beanAnnotations);
        saveProjectConfig(project, projectConfig);
    }

    public static void setMonitorConfig(Project project, TraceConfig traceConfig) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (traceConfig != null) {
            if (StringUtils.isBlank(traceConfig.getPackages())) {
                traceConfig.setPackages("app.three.package");
            }

            if (StringUtils.isBlank(traceConfig.getClsSuffix())) {
                traceConfig.setClsSuffix("Controller,Service,Impl,Repository");
            }

            if (StringUtils.isBlank(traceConfig.getWhites())) {
                traceConfig.setWhites("");
            }

            if (StringUtils.isBlank(traceConfig.getBlacks())) {
                traceConfig.setBlacks("");
            }
        }
        projectConfig.setTraceConfig(traceConfig);
        saveProjectConfig(project, projectConfig);
    }


    public static void setAppProperties(Project project, String app, String properties) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (projectConfig.getAppConfigs() == null) {
            projectConfig.setAppConfigs(new HashMap<>());
        }


        projectConfig.getAppConfigs().computeIfAbsent(app, new Function<String, Config>() {
            @Override
            public Config apply(String s) {
                return new Config();
            }
        }).setProperties(properties);
        saveProjectConfig(project, projectConfig);
    }


    public static void setAppScript(Project project, String app, String script) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (projectConfig.getAppConfigs() == null) {
            projectConfig.setAppConfigs(new HashMap<>());
        }


        projectConfig.getAppConfigs().computeIfAbsent(app, new Function<String, Config>() {
            @Override
            public Config apply(String s) {
                return new Config();
            }
        }).setScript(script);
        saveProjectConfig(project, projectConfig);
    }


    public static void setAppControllerCommand(Project project, String app, HttpCommand adapter) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (projectConfig.getAppConfigs() == null) {
            projectConfig.setAppConfigs(new HashMap<>());
        }


        projectConfig.getAppConfigs().computeIfAbsent(app, new Function<String, Config>() {
            @Override
            public Config apply(String s) {
                return new Config();
            }
        }).setControllerCommand(adapter);
        saveProjectConfig(project, projectConfig);
    }

    public static void setAppFeignCommand(Project project, String app, HttpCommand adapter) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (projectConfig.getAppConfigs() == null) {
            projectConfig.setAppConfigs(new HashMap<>());
        }


        projectConfig.getAppConfigs().computeIfAbsent(app, new Function<String, Config>() {
            @Override
            public Config apply(String s) {
                return new Config();
            }
        }).setFeignCommand(adapter);
        saveProjectConfig(project, projectConfig);
    }


    private static Config getConfig(Project project) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
            config.setFlexibleTestPackage(defFlexibleTestPackage);
            config.setBeanAnnotations(defBeanAnnotations);
            config.setTraceConfig(copyDefMonitorConfig());
            config.setSqlConfig(copyDefSqlConfig());
            config.setEnableSideServer(true);
            return config;
        }
        Config config = new Config();
        config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
        config.setBeanAnnotations(projectConfig.getBeanAnnotations() == null ? defBeanAnnotations : projectConfig.getBeanAnnotations());
        config.setTraceConfig(projectConfig.getTraceConfig() == null ? copyDefMonitorConfig() : projectConfig.getTraceConfig());
        config.setSqlConfig(projectConfig.getSqlConfig() == null ? copyDefSqlConfig() : projectConfig.getSqlConfig());
        config.setEnableSideServer(projectConfig.isEnableSideServer());
        return config;
    }

    private static TraceConfig copyDefMonitorConfig() {
        TraceConfig copyConfig = new TraceConfig();
//        copyConfig.setSingleClsDepth(DEF_TRACE_CONFIG.getSingleClsDepth());
        copyConfig.setPackages(DEF_TRACE_CONFIG.getPackages());
        copyConfig.setClsSuffix(DEF_TRACE_CONFIG.getClsSuffix());
        copyConfig.setWhites(DEF_TRACE_CONFIG.getWhites());
        copyConfig.setBlacks(DEF_TRACE_CONFIG.getBlacks());
        copyConfig.setEnable(DEF_TRACE_CONFIG.isEnable());
        copyConfig.setTraceWeb(DEF_TRACE_CONFIG.isTraceWeb());
        copyConfig.setTraceMybatis(DEF_TRACE_CONFIG.isTraceMybatis());
        copyConfig.setLogMybatis(DEF_TRACE_CONFIG.isLogMybatis());
        return copyConfig;
    }

    private static SqlConfig copyDefSqlConfig() {
        SqlConfig sqlConfig = new SqlConfig();
        return sqlConfig;
    }


    private static Config getAppConfig(Project project, String app) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
//            config.setFlexibleTestPackage(defFlexibleTestPackage);
//            config.setScript(defScript);
            config.setControllerCommand(DEF_CONTROLLER_COMMAND);
            config.setFeignCommand(DEF_FEIGN_COMMAND);
            config.setProperties(defProperties);
            return config;
        }
        if (app == null || projectConfig.getAppConfigs() == null || !projectConfig.getAppConfigs().containsKey(app)) {
            Config config = new Config();
//            config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
//            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
            config.setControllerCommand(projectConfig.getControllerCommand() == null ? DEF_CONTROLLER_COMMAND : projectConfig.getControllerCommand());
            config.setFeignCommand(projectConfig.getFeignCommand() == null ? DEF_FEIGN_COMMAND : projectConfig.getFeignCommand());
            config.setProperties(defProperties);
            return config;
        }
        Config config = projectConfig.getAppConfigs().get(app);
//        if (config.getFlexibleTestPackage() == null) {
//            config.setFlexibleTestPackage(defFlexibleTestPackage);
//        }
//        if (config.getScript() == null) {
//            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
//        }
        if (config.getControllerCommand() == null) {
            config.setControllerCommand(projectConfig.getControllerCommand() == null ? DEF_CONTROLLER_COMMAND : projectConfig.getControllerCommand());
        }

        if (config.getFeignCommand() == null) {
            config.setFeignCommand(projectConfig.getFeignCommand() == null ? DEF_FEIGN_COMMAND : projectConfig.getFeignCommand());
        }
        if (config.getProperties() == null) {
            config.setProperties(defProperties);
        }
        return config;
    }


    public static ProjectConfig loadProjectConfig(Project project) {
        File configFile = getConfigFile(project);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                return JSON.parseObject(content, new TypeReference<ProjectConfig>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public synchronized static void saveProjectConfig(Project project, ProjectConfig projectConfig) {
        File configFile = getConfigFile(project);
        try (FileWriter writer = new FileWriter(configFile)) {
            JSON.writeJSONString(writer, projectConfig);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getConfigFile(Project project) {
        String projectPath = System.getProperty("user.home");
        if (StringUtils.isEmpty(projectPath)) {
            throw new IllegalArgumentException("Project base path is not set.");
        }
        Path configDirPath = Paths.get(projectPath, CONFIG_DIR);
        File configDir = configDirPath.toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, project.getName() + ".json");
    }

    public static class ProjectConfig {

        private boolean enableSideServer = true;
        private String flexibleTestPackage;
        private List<String> beanAnnotations;
        private String script;
        private HttpCommand controllerCommand;
        private HttpCommand feignCommand;

        private Map<String, Config> appConfigs;
        private TraceConfig traceConfig;
        private SqlConfig sqlConfig;

        public String getFlexibleTestPackage() {
            return flexibleTestPackage;
        }

        public void setFlexibleTestPackage(String flexibleTestPackage) {
            this.flexibleTestPackage = flexibleTestPackage;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public Map<String, Config> getAppConfigs() {
            return appConfigs;
        }

        public void setAppConfigs(Map<String, Config> appConfigs) {
            this.appConfigs = appConfigs;
        }

        public TraceConfig getTraceConfig() {
            return traceConfig;
        }

        public void setTraceConfig(TraceConfig traceConfig) {
            this.traceConfig = traceConfig;
        }

        public HttpCommand getControllerCommand() {
            return controllerCommand;
        }

        public void setControllerCommand(HttpCommand controllerCommand) {
            this.controllerCommand = controllerCommand;
        }

        public HttpCommand getFeignCommand() {
            return feignCommand;
        }

        public void setFeignCommand(HttpCommand feignCommand) {
            this.feignCommand = feignCommand;
        }

        public boolean isEnableSideServer() {
            return enableSideServer;
        }

        public void setEnableSideServer(boolean enableSideServer) {
            this.enableSideServer = enableSideServer;
        }

        public SqlConfig getSqlConfig() {
            return sqlConfig;
        }

        public void setSqlConfig(SqlConfig sqlConfig) {
            this.sqlConfig = sqlConfig;
        }

        public List<String> getBeanAnnotations() {
            return beanAnnotations;
        }

        public void setBeanAnnotations(List<String> beanAnnotations) {
            this.beanAnnotations = beanAnnotations;
        }
    }

    public static class Config {

        private boolean enableSideServer = true;
        private String flexibleTestPackage;
        private List<String> beanAnnotations;
        private String script;
        private HttpCommand controllerCommand;
        private HttpCommand feignCommand;
        private String properties;
        private TraceConfig traceConfig;
        private SqlConfig sqlConfig;

        public String getFlexibleTestPackage() {
            return flexibleTestPackage;
        }

        public void setFlexibleTestPackage(String flexibleTestPackage) {
            this.flexibleTestPackage = flexibleTestPackage;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public String getProperties() {
            return properties;
        }

        public void setProperties(String properties) {
            this.properties = properties;
        }

        public TraceConfig getTraceConfig() {
            return traceConfig;
        }

        public void setTraceConfig(TraceConfig traceConfig) {
            this.traceConfig = traceConfig;
        }

        public HttpCommand getControllerCommand() {
            return controllerCommand;
        }

        public void setControllerCommand(HttpCommand controllerCommand) {
            this.controllerCommand = controllerCommand;
        }

        public HttpCommand getFeignCommand() {
            return feignCommand;
        }

        public void setFeignCommand(HttpCommand feignCommand) {
            this.feignCommand = feignCommand;
        }

        public boolean isEnableSideServer() {
            return enableSideServer;
        }

        public void setEnableSideServer(boolean enableSideServer) {
            this.enableSideServer = enableSideServer;
        }

        public SqlConfig getSqlConfig() {
            return sqlConfig;
        }

        public void setSqlConfig(SqlConfig sqlConfig) {
            this.sqlConfig = sqlConfig;
        }

        public List<String> getBeanAnnotations() {
            return beanAnnotations;
        }

        public void setBeanAnnotations(List<String> beanAnnotations) {
            this.beanAnnotations = beanAnnotations;
        }
    }


    public static class HttpCommand {
        //        controllerInstruction
        private String script;
        private List<String> envs;

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public List<String> getEnvs() {
            return envs;
        }

        public void setEnvs(List<String> envs) {
            this.envs = envs;
        }

    }


    public static class TraceConfig {

        private boolean enable;
        private boolean traceWeb = true;
        private boolean traceMybatis = true;
        private boolean logMybatis = false;
        private String packages;

        private String clsSuffix;

        private String whites;

        private String blacks;

//        private int singleClsDepth = 2;

        public boolean judgeIsAppThreePackage() {
            return "app.three.package".equals(packages);
        }


        public String getPackages() {
            return packages;
        }

        public void setPackages(String packages) {
            this.packages = packages;
        }

        public String getClsSuffix() {
            return clsSuffix;
        }

        public void setClsSuffix(String clsSuffix) {
            this.clsSuffix = clsSuffix;
        }

        public String getWhites() {
            return whites;
        }

        public void setWhites(String whites) {
            this.whites = whites;
        }

        public String getBlacks() {
            return blacks;
        }

        public void setBlacks(String blacks) {
            this.blacks = blacks;
        }

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public boolean isTraceWeb() {
            return traceWeb;
        }

        public void setTraceWeb(boolean traceWeb) {
            this.traceWeb = traceWeb;
        }

        public boolean isTraceMybatis() {
            return traceMybatis;
        }

        public void setTraceMybatis(boolean traceMybatis) {
            this.traceMybatis = traceMybatis;
        }

        public boolean isLogMybatis() {
            return logMybatis;
        }

        public void setLogMybatis(boolean logMybatis) {
            this.logMybatis = logMybatis;
        }

//        public int getSingleClsDepth() {
//            return singleClsDepth;
//        }
//
//        public void setSingleClsDepth(int singleClsDepth) {
//            this.singleClsDepth = singleClsDepth;
//        }
    }


    public static class DatasourceConfig {

        private String name;
        private String url;
        private String username;
        private String password;

        public Connection newConnection() {
            return MysqlUtil.getDatabaseConnection(this);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }


    public static class SqlConfig {

        private String properties;


        public static List<DatasourceConfig> parseDatasources(String propertiesStr) {
            if (StringUtils.isBlank(propertiesStr)) {
                return new ArrayList<>();
            }
            return SettingsDialog.parseDatasourceConfigs(propertiesStr);
        }


        public String getProperties() {
            return properties;
        }

        public void setProperties(String properties) {
            this.properties = properties;
        }
    }


}