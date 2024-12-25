package com.fling;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 这是一个本地文件存储器
 * 本质上根据project的名称来存储到用户的.fling/config的目录下的一个文件
 * 每个project一个文件
 * 每个文件的内容是个json，结构是ProjectConfig这样的
 */
public class LocalStorageHelper {

    private static final String CONFIG_DIR = ".spring-fling/config";

    public static final String defFlexibleTestPackage = "flexibletest";

    public static final String defProperties = "#Here properties takes precedence over spring.properties\n"
            + "#You can write some configurations for local startup use, like log level\n"
            + "logging.level.com.fling=INFO";

    public static final String defScript =
            "import org.springframework.beans.factory.annotation.Autowired;\n" +
                    "import org.springframework.context.ApplicationContext;\n" +
                    "\n" +
                    "import java.util.Map;\n" +
                    "\n" +
                    "\n" +
                    "/**\n" +
                    " * 拦截tool执行的脚本类\n" +
                    " * 一定要有一个无参构造\n" +
                    " * tool执行前后分别会执行invokeBefore和invokeAfter函数\n" +
                    " * 可执行业务逻辑\n" +
                    " * 可引用项目中的类及函数\n" +
                    " * trace写入\n" +
                    " * 登录态模拟\n" +
                    " * 支持使用@Autowired注入项目中存在的bean\n" +
                    " */\n" +
                    "class MyScript {\n" +
                    "\n" +
                    "    @Autowired\n" +
                    "    private ApplicationContext ctx;\n" +
                    "\n" +
                    "    public MyScript() {\n" +
                    "    }\n" +
                    "\n" +
                    "\n" +
                    "    /**\n" +
                    "     * tool执行前\n" +
                    "     *\n" +
                    "     * @param appName 哪个应用名 非空\n" +
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @throws Throwable 抛出异常则阻断tool调用\n" +
                    "     */\n" +
                    "    public void invokeBefore(String appName, String tool, Map<String, String> params)  throws Throwable{\n" +
                    "\n" +
                    "    }\n" +
                    "\n" +
                    "    /**\n" +
                    "     * tool执行后\n" +
                    "     *\n" +
                    "     * @param appName 哪个应用名 非空\n" +
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @param cost   耗时ms 不包含脚本耗时 可能为空\n" +
                    "     * @param ret    返回内容 可能为空\n" +
                    "     * @param e      抛出的异常 脚本异常也会记录 可能为空\n" +
                    "     */\n" +
                    "    public void invokeAfter(String appName, String tool, Map<String, String> params, Integer cost, Object ret, Throwable e) {\n" +
                    "    }\n" +
                    "\n" +
                    "}";

    public static final ControllerAdapter defControllerAdapter = new ControllerAdapter();

    static {
        defControllerAdapter.setScript("import groovy.json.JsonOutput\n" +
                "import groovy.json.JsonSlurper\n" +
                "\n" +
                "\n" +
                "/**\n" +
                " * groovy脚本，在idea-plugin环境执行\n" +
                " * 构建函数，返回结果会被copy到剪切板，建议采用curl格式\n" +
                " * \n" +
                " * 不可使用项目中类，不可使用项目中类，不可使用项目中类\n" +
                " * \n" +
                " * 运行代码如下\n" +
                " * \n" +
                " GroovyShell groovyShell = new GroovyShell();\n" +
                " Script script = groovyShell.parse(this-code);\n" +
                " Object build = InvokerHelper.invokeMethod(script, \"generate\", new Object[]{env,  selectedAppPort, httpMethod, path, params, jsonBody});\n" +
                " return build == null ? \"\" : String.valueOf(build);\n" +
                " * \n" +
                " * 工具函数\n" +
                " * buildCurl：构建curl函数\n" +
                " * http：发起http请求函数\n" +
                " * \n" +
                " * @param env 环境，可能为空\n" +
                " * @param httpMethod GET/POST 等等http方法，非空\n" +
                " * @param path /uri 非空\n" +
                " * @param params 传递的参数，k和v都是string，非空\n" +
                " * @param jsonBody json形式的请求体，字符串类型，非空代表Content-Type: application/json，可能为空\n" +
                " * @return 返回结果会被copy到剪切板\n" +
                " */\n" +
                "def generate(String env, Integer runtimePort,String httpMethod,String path,Map<String,String> params,String jsonBody) {\n" +
                "    String domain = \"http://localhost:8080\"\n" +
                "    if(\"local\" == env && runtimePort!=null){\n" +
                "        domain = \"http://localhost:\"+runtimePort\n" +
                "    }\n" +
                "    String token = getToken(env)\n" +
                "    buildCurl(domain,httpMethod,path,params,[\"Authorization\":\"Bearer ${token}\"],jsonBody)\n" +
                "}\n" +
                "\n" +
                "\n" +
                "\n" +
                "def getToken(env){\n" +
                "    // your project logic\n" +
                "    return \"your_token\"\n" +
                "}\n" +
                "\n" +
                "\n" +
                "class HttpResponse {\n" +
                "    String body\n" +
                "    Map<String, List<String>> headers\n" +
                "\n" +
                "    String toJson() {\n" +
                "        def responseMap = [\n" +
                "                body: parseBodyAsJson(),\n" +
                "                headers: filterNullKeys(headers)\n" +
                "        ]\n" +
                "        return JsonOutput.prettyPrint(JsonOutput.toJson(responseMap))\n" +
                "    }\n" +
                "\n" +
                "    private Object parseBodyAsJson() {\n" +
                "        try {\n" +
                "            return new JsonSlurper().parseText(body)\n" +
                "        } catch (Exception e) {\n" +
                "            return body // 如果不是JSON格式，返回原始body  \n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    private Map<String, List<String>> filterNullKeys(Map<String, List<String>> map) {\n" +
                "        return map.findAll { k, v -> k != null }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "HttpResponse http(String httpMethod,String uri, Map<String, String> headers, Map<String, String> params, String jsonBody) {\n" +
                "    if (params) {\n" +
                "        def queryString = params.collect { k, v -> \"${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v==null?\"\":v, 'UTF-8')}\" }.join('&')\n" +
                "        uri += \"?${queryString}\"\n" +
                "    }\n" +
                "    HttpURLConnection connection = new URL(uri).openConnection()\n" +
                "    connection.setRequestMethod(httpMethod.toUpperCase())\n" +
                "   \n" +
                "    if(headers){\n" +
                "        headers.each { key, value ->\n" +
                "            connection.setRequestProperty(key, value)\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    if (jsonBody) {\n" +
                "        connection.setRequestProperty(\"Content-Type\", \"application/json\")\n" +
                "        connection.doOutput = true\n" +
                "        connection.outputStream.withWriter(\"UTF-8\") { writer ->\n" +
                "            writer << jsonBody\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    def responseHeaders = connection.headerFields\n" +
                "    def responseBody = connection.inputStream.withReader(\"UTF-8\") { reader ->\n" +
                "        reader.text\n" +
                "    }\n" +
                "\n" +
                "    return new HttpResponse(body: responseBody, headers: responseHeaders)\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def buildCurl(domain, httpMethod, path, params, headers, jsonBody){\n" +
                "    StringBuilder ret = new StringBuilder()\n" +
                "    ret.append(\"curl -X ${httpMethod.toUpperCase()} '${domain}${path}\")\n" +
                "\n" +
                "    if(params!=null && params.size()>0){\n" +
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
                "    if(headers!=null && headers.size()>0){\n" +
                "        headers.forEach((key, value) -> {\n" +
                "            if(\"Content-Type\" == key){\n" +
                "                return\n" +
                "            }\n" +
                "            ret.append(\" \\\\\\n  -H '${key}: ${value}'\")\n" +
                "        });\n" +
                "    }\n" +
                "    if(jsonBody!=null){\n" +
                "        ret.append(\" \\\\\\n  -H 'Content-Type: application/json' \\\\\\n  -d '${jsonBody}'\")\n" +
                "    }\n" +
                "    return ret\n" +
                "}\n" +
                "\n");
    }

    public static final MonitorConfig defMonitorConfig = new MonitorConfig();

    static {
        defMonitorConfig.setEnable(false);
        defMonitorConfig.setMonitorPrivate(false);
        defMonitorConfig.setLogMybatis(true);
        defMonitorConfig.setPackages("app.three.package");
        defMonitorConfig.setClsSuffix("Controller,Service,Impl,Repository");
        defMonitorConfig.setBlacks("");
        defMonitorConfig.setWhites("");
    }


    public static String getFlexibleTestPackage(Project project) {
        return getConfig(project).getFlexibleTestPackage();
    }

    public static MonitorConfig getMonitorConfig(Project project) {
        return getConfig(project).getMonitorConfig();
    }


    public static String getAppScript(Project project, String app) {
        return getAppConfig(project, app).getScript();
    }

    public static ControllerAdapter getAppControllerAdapter(Project project, String app) {
        return getAppConfig(project, app).getControllerAdapter();
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

    public static void setMonitorConfig(Project project, MonitorConfig monitorConfig) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        if (monitorConfig != null) {
            if (StringUtils.isBlank(monitorConfig.getPackages())) {
                monitorConfig.setPackages("app.three.package");
            }

            if (StringUtils.isBlank(monitorConfig.getClsSuffix())) {
                monitorConfig.setClsSuffix("Controller,Service,Impl,Repository");
            }

            if (StringUtils.isBlank(monitorConfig.getWhites())) {
                monitorConfig.setWhites("");
            }

            if (StringUtils.isBlank(monitorConfig.getBlacks())) {
                monitorConfig.setBlacks("");
            }
        }
        projectConfig.setMonitorConfig(monitorConfig);
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

    public static void setAppControllerAdapter(Project project, String app, ControllerAdapter adapter) {
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
        }).setControllerAdapter(adapter);
        saveProjectConfig(project, projectConfig);
    }


    private static Config getConfig(Project project) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
            config.setFlexibleTestPackage(defFlexibleTestPackage);
            config.setMonitorConfig(defMonitorConfig);
            return config;
        }
        Config config = new Config();
        config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
        config.setMonitorConfig(projectConfig.getMonitorConfig() == null ? defMonitorConfig : projectConfig.getMonitorConfig());
        return config;
    }


    private static Config getAppConfig(Project project, String app) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
            config.setFlexibleTestPackage(defFlexibleTestPackage);
//            config.setScript(defScript);
            config.setControllerAdapter(defControllerAdapter);
            config.setProperties(defProperties);
            return config;
        }
        if (app == null || projectConfig.getAppConfigs() == null || !projectConfig.getAppConfigs().containsKey(app)) {
            Config config = new Config();
            config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
//            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
            config.setControllerAdapter(projectConfig.getControllerAdapter() == null ? defControllerAdapter : projectConfig.getControllerAdapter());
            config.setProperties(defProperties);
            return config;
        }
        Config config = projectConfig.getAppConfigs().get(app);
        if (config.getFlexibleTestPackage() == null) {
            config.setFlexibleTestPackage(defFlexibleTestPackage);
        }
//        if (config.getScript() == null) {
//            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
//        }
        if (config.getControllerAdapter() == null) {
            config.setControllerAdapter(projectConfig.getControllerAdapter() == null ? defControllerAdapter : projectConfig.getControllerAdapter());
        }
        if (config.getProperties() == null) {
            config.setProperties(defProperties);
        }
        return config;
    }


    private static ProjectConfig loadProjectConfig(Project project) {
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

    private synchronized static void saveProjectConfig(Project project, ProjectConfig projectConfig) {
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

        private String flexibleTestPackage;
        private String script;
        private ControllerAdapter controllerAdapter;
        private Map<String, Config> appConfigs;
        private MonitorConfig monitorConfig;

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

        public MonitorConfig getMonitorConfig() {
            return monitorConfig;
        }

        public void setMonitorConfig(MonitorConfig monitorConfig) {
            this.monitorConfig = monitorConfig;
        }

        public ControllerAdapter getControllerAdapter() {
            return controllerAdapter;
        }

        public void setControllerAdapter(ControllerAdapter controllerAdapter) {
            this.controllerAdapter = controllerAdapter;
        }
    }

    public static class Config {

        private String flexibleTestPackage;
        private String script;
        private ControllerAdapter controllerAdapter;
        private String properties;
        private MonitorConfig monitorConfig;

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

        public MonitorConfig getMonitorConfig() {
            return monitorConfig;
        }

        public void setMonitorConfig(MonitorConfig monitorConfig) {
            this.monitorConfig = monitorConfig;
        }

        public ControllerAdapter getControllerAdapter() {
            return controllerAdapter;
        }

        public void setControllerAdapter(ControllerAdapter controllerAdapter) {
            this.controllerAdapter = controllerAdapter;
        }
    }


    public static class ControllerAdapter{
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


    public static class MonitorConfig {

        private boolean enable;
        private boolean monitorPrivate;
        private boolean monitorWeb = true;
        private boolean monitorMybatis = true;
        private boolean logMybatis = false;
        private String packages;

        private String clsSuffix;

        private String whites;

        private String blacks;

        public boolean judgeIsAppSthreePackage() {
            return "app.three.package".equals(packages);
        }

        public boolean isMonitorPrivate() {
            return monitorPrivate;
        }

        public void setMonitorPrivate(boolean monitorPrivate) {
            this.monitorPrivate = monitorPrivate;
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

        public boolean isMonitorWeb() {
            return monitorWeb;
        }

        public void setMonitorWeb(boolean monitorWeb) {
            this.monitorWeb = monitorWeb;
        }

        public boolean isMonitorMybatis() {
            return monitorMybatis;
        }

        public void setMonitorMybatis(boolean monitorMybatis) {
            this.monitorMybatis = monitorMybatis;
        }

        public boolean isLogMybatis() {
            return logMybatis;
        }

        public void setLogMybatis(boolean logMybatis) {
            this.logMybatis = logMybatis;
        }
    }


}