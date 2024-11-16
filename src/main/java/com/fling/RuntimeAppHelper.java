package com.fling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RuntimeAppHelper {

    private static ObjectMapper MAPPER = new ObjectMapper();


    private static final String APPS_DIR = ".spring-fling/apps";


    public static void removeApp(String project, String appName, Integer port, Integer sidePort) {
        if (project == null || project.isEmpty()) {
            return;
        }
        doRemoveApp(project, appName, port, sidePort);
    }

    private static void doRemoveApp(String project, String appName, Integer port, Integer sidePort) {
        List<String> runtimes = loadProjectRuntimes(project);
        if (runtimes == null) {
            return;
        }
        if (!runtimes.contains(appName + ":" + port + ":" + sidePort)) {
            return;
        }
        ;
        runtimes.remove(appName + ":" + port + ":" + sidePort);
        storeProjectRuntimes(project, runtimes);
    }

    public static List<String> loadProjectRuntimes(String project) {
        if (project == null || project.isEmpty()) {
            return null;
        }

        List<String> ret = null;
        File configFile = getConfigFile(project);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                ret = MAPPER.readValue(content, new TypeReference<List<String>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ret;
    }

    private static void storeProjectRuntimes(String project, List<String> runtimes) {
        if (project == null || project.isEmpty()) {
            return;
        }

        File configFile = getConfigFile(project);
        try (FileWriter writer = new FileWriter(configFile)) {
            if (runtimes == null) {
                runtimes = new ArrayList<>();
            }
            MAPPER.writeValue(writer, runtimes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getConfigFile(String project) {
        String projectPath = System.getProperty("user.home");
        if (projectPath == null || projectPath.isEmpty()) {
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
