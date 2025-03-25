package com.testkit.server;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class RuntimeAppHelper {

    public static final String LOCAL = "local";


    private static final String APPS_DIR = ".spring-testkit/apps";

    public static boolean needRegister(String env){
        return Objects.equals(LOCAL, env);
    }


    public static void addApp(String project,String appName,Integer sidePort){
        if (project==null || project.isEmpty()) {
            return;
        }
        List<String> runtimes = loadProjectRuntimes(project);
        if (runtimes==null) {
            runtimes = new ArrayList<>();
        }
        if(runtimes.contains(appName+":"+LOCAL+":"+sidePort)){
            return;
        }
        runtimes.add(appName+":"+LOCAL+":"+sidePort);

        storeProjectRuntimes(project, runtimes);
    }

    public static void removeApp(String project,String appName,Integer sidePort){
        if (project==null || project.isEmpty()) {
            return;
        }
        List<String> runtimes = loadProjectRuntimes(project);
        if (runtimes==null) {
            return;
        }
        if(!runtimes.contains(appName+":local:"+sidePort)){
            return;
        };
        runtimes.remove(appName+":local:"+sidePort);
        storeProjectRuntimes(project, runtimes);
    }

    private static List<String> loadProjectRuntimes(String project) {
        if (project==null || project.isEmpty()) {
            return null;
        }

        File configFile = getConfigFile(project);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                return ReflexUtils.SIMPLE_MAPPER.readValue(content, new TypeReference<List<String>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void storeProjectRuntimes(String project,List<String> runtimes) {
        if (project==null || project.isEmpty()) {
            return ;
        }

        File configFile = getConfigFile(project);
        try (FileWriter writer = new FileWriter(configFile)) {
            if (runtimes==null) {
                runtimes = new ArrayList<>();
            }
            ReflexUtils.SIMPLE_MAPPER.writeValue(writer, runtimes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getConfigFile(String project) {
        String projectPath = System.getProperty("user.home");
        if (projectPath==null || projectPath.isEmpty()) {
            throw new IllegalArgumentException("Project base path is not set.");
        }
        Path configDirPath = Paths.get(projectPath, APPS_DIR);
        File configDir = configDirPath.toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, project + ".json");
    }

}
