package com.nb.view;

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
public class LocalStorage {

    private static final String CONFIG_DIR = ".no-bug/config";


    public static Config getConfig(Project project) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig==null) {
            Config config = new Config();
            config.setFlexibleTestPackage(".flexibletest");
            config.setScript("");
            return config;
        }
        Config config = new Config();
        config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage());
        config.setScript(projectConfig.getScript());
        return config;
    }


    public static Config getAppConfig(Project project, String app) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig==null) {
            Config config = new Config();
            config.setFlexibleTestPackage(".");
            config.setScript(null);
            return config;
        }
        if (app==null || projectConfig.getAppConfigs()==null || !projectConfig.getAppConfigs().containsKey(app)) {
            Config config = new Config();
            config.setFlexibleTestPackage(projectConfig.getFlexibleTestPackage());
            config.setScript(projectConfig.getScript());
            return config;
        }
        return projectConfig.getAppConfigs().get(app);
    }



    public static void setFlexibleTestPackage(Project project, String flexibleTestPackage) {
        ProjectConfig projectConfig = loadProjectConfig(project);
        if (projectConfig == null) {
            projectConfig = new ProjectConfig();
        }
        projectConfig.setFlexibleTestPackage(flexibleTestPackage);
        saveProjectConfig(project, projectConfig);
    }

    public static void setAppFlexibleTestPackage(Project project, String app, String flexibleTestPackage) {
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
        }).setFlexibleTestPackage(flexibleTestPackage);
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

    private static ProjectConfig loadProjectConfig(Project project) {
        File configFile = getConfigFile(project);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                return JSON.parseObject(content, new TypeReference<ProjectConfig>() {});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void saveProjectConfig(Project project, ProjectConfig projectConfig) {
        File configFile = getConfigFile(project);
        try (FileWriter writer = new FileWriter(configFile)) {
            JSON.writeJSONString(writer, projectConfig);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getConfigFile(Project project) {
        String projectPath = project.getBasePath();
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