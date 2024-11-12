package com.nb.util;

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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 这是一个本地文件存储器
 * 本质上根据project的名称来存储到用户的.no-bug/config的目录下的一个文件
 * 每个project一个文件
 * 每个文件的内容是个json，结构是ProjectConfig这样的
 */
public class LocalStorageHelper {

    private static final String CONFIG_DIR = ".no-bug/config";

    public static final String defFlexibleTestPackage = "flexibletest";

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
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @throws Throwable 抛出异常则阻断tool调用\n" +
                    "     */\n" +
                    "    public void invokeBefore(String tool, Map<String, String> params)  throws Throwable{\n" +
                    "\n" +
                    "    }\n" +
                    "\n" +
                    "    /**\n" +
                    "     * tool执行后\n" +
                    "     *\n" +
                    "     * @param tool   将要执行的tool 非空\n" +
                    "     * @param params 入参 非空\n" +
                    "     * @param cost   耗时ms 不包含脚本耗时 可能为空\n" +
                    "     * @param ret    返回内容 可能为空\n" +
                    "     * @param e      抛出的异常 脚本异常也会记录 可能为空\n" +
                    "     */\n" +
                    "    public void invokeAfter(String tool, Map<String, String> params, Integer cost, Object ret, Throwable e) {\n" +
                    "        System.err.println(\"No-Bug\\n\"\n" +
                    "                + \"tool:\" + tool + \"\\n\"\n" +
                    "                + \"params:\" + params + \"\\n\"\n" +
                    "                + \"cost:\" + cost + \"\\n\"\n" +
                    "                + \"ret:\" + ret + \"\\n\"\n" +
                    "                + \"e:\" + e);\n" +
                    "    }\n" +
                    "\n" +
                    "}";


    public static String getFlexibleTestPackage(Project project) {
        return getConfig(project).getFlexibleTestPackage();
    }


    public static String getScript(Project project) {
        return getConfig(project).getScript();
    }

    public static String getAppScript(Project project,String app) {
        return getAppConfig(project,app).getScript();
    }



    public static void setFlexibleTestPackage(Project project, String flexibleTestPackage) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setFlexibleTestPackage(flexibleTestPackage);
        saveProjectConfig(project, projectConfig);
    }


    public static void setScript(Project project, String script) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setScript(script);
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


    private static Config getConfig(Project project) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
            config.setFlexibleTestPackage(defFlexibleTestPackage);
            config.setScript(defScript);
            return config;
        }
        Config config = new Config();
        config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
        config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
        return config;
    }


    private static Config getAppConfig(Project project, String app) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            Config config = new Config();
            config.setFlexibleTestPackage(defFlexibleTestPackage);
            config.setScript(defScript);
            return config;
        }
        if (app == null || projectConfig.getAppConfigs() == null || !projectConfig.getAppConfigs().containsKey(app)) {
            Config config = new Config();
            config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage() == null ? defFlexibleTestPackage : projectConfig.getFlexibleTestPackage());
            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
            return config;
        }
        Config config = projectConfig.getAppConfigs().get(app);
        if (config.getFlexibleTestPackage() == null) {
            config.setFlexibleTestPackage(defFlexibleTestPackage);
        }
        if (config.getScript() == null) {
            config.setScript(projectConfig.getScript() == null ? defScript : projectConfig.getScript());
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
        private Map<String, Config> appConfigs;

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
    }

    public static class Config {

        private String flexibleTestPackage;
        private String script;

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
    }


}