package com.testkit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.module.Module;
import org.apache.commons.collections.CollectionUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

public class RuntimeHelper {

    private static ObjectMapper MAPPER = new ObjectMapper();


    private static final String APPS_DIR = ".spring-testkit/apps";

    private static final Map<String, Boolean> traceMap = new HashMap<>();

    private static final Map<String, VisibleApp> selectedApps = new HashMap<>();

    private static final Map<String, List<AppMeta>> appMetas = new HashMap<>();

    private static final Map<String, List<VisibleApp>> visibleApps = new HashMap<>();

    private static final Map<String, List<SettingsStorageHelper.DatasourceConfig>> validDatasources = new HashMap<>();
    private static final Map<String, List<String>> ddlDatasources = new HashMap<>();
    private static final Map<String, List<String>> writeDatasources = new HashMap<>();
    private static final Map<String, List<String>> tempApps = new HashMap<>();

    private static boolean enableMapperSql = false;


    public static boolean isEnableMapperSql() {
        return enableMapperSql;
    }

    public static void setEnableMapperSql(boolean enableMapperSql) {
        RuntimeHelper.enableMapperSql = enableMapperSql;
    }

    public static VisibleApp getSelectedApp(String project) {
        if (project == null) {
            return null;
        }
        return selectedApps.get(project);
    }

    public static void updateSelectedApp(String project, VisibleApp app) {
        if (project == null) {
            return;
        }
        if (app == null) {
            selectedApps.remove(project);
        } else {
            selectedApps.put(project, app);
        }
    }


    public static void updateTraces(Map<String, Boolean> traces) {
        if (traces == null) {
            return;
        }
        traceMap.putAll(traces);
    }

    public static boolean isEnableTrace(String app) {
        if (app == null) {
            return false;
        }
        Boolean trace = traceMap.get(app);
        return trace != null && trace;
    }

    public static void updateValidDatasources(String project, List<SettingsStorageHelper.DatasourceConfig> datasources, List<String> ddls, List<String> writes) {
        if (project == null) {
            return;
        }
        if (CollectionUtils.isEmpty(datasources)) {
            validDatasources.remove(project);
            ddlDatasources.remove(project);
            writeDatasources.remove(project);
            return;
        }
        validDatasources.put(project, datasources);
        ddlDatasources.put(project, ddls);
        writeDatasources.put(project, writes);
    }

    public static List<SettingsStorageHelper.DatasourceConfig> getValidDatasources(String project) {
        if (project == null) {
            return new ArrayList<>();
        }
        List<SettingsStorageHelper.DatasourceConfig> visibleApps1 = validDatasources.get(project);
        return visibleApps1 == null ? new ArrayList<>() : visibleApps1;
    }

    public static SettingsStorageHelper.DatasourceConfig getDatasource(String project, String datasource) {
        if (datasource == null) {
            return null;
        }
        List<SettingsStorageHelper.DatasourceConfig> datasources = getValidDatasources(project);
        Optional<SettingsStorageHelper.DatasourceConfig> first = datasources.stream().filter(new Predicate<SettingsStorageHelper.DatasourceConfig>() {
            @Override
            public boolean test(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                return datasource.equals(datasourceConfig.getName());
            }
        }).findFirst();
        return first.orElse(null);
    }

    public static List<String> getDDLDatasources(String project) {
        if (project == null) {
            return new ArrayList<>();
        }
        List<String> visibleApps1 = ddlDatasources.get(project);
        return visibleApps1 == null ? new ArrayList<>() : visibleApps1;
    }

    public static List<String> getWriteDatasources(String project) {
        if (project == null) {
            return new ArrayList<>();
        }
        List<String> visibleApps1 = writeDatasources.get(project);
        return visibleApps1 == null ? new ArrayList<>() : visibleApps1;
    }

    public static void updateVisibleApps(String project, List<VisibleApp> apps) {
        if (project == null) {
            return;
        }
        if (CollectionUtils.isEmpty(apps)) {
            visibleApps.remove(project);
            return;
        }
        visibleApps.put(project, apps);
    }

    public static List<VisibleApp> getVisibleApps(String project) {
        if (project == null) {
            return new ArrayList<>();
        }
        List<VisibleApp> visibleApps1 = visibleApps.get(project);
        return visibleApps1 == null ? new ArrayList<>() : visibleApps1;
    }


    public static boolean hasAppMeta(String project) {
        if (project == null) {
            return false;
        }
        return appMetas.containsKey(project);
    }


    public static List<AppMeta> getAppMetas(String project) {
        if (project == null) {
            return new ArrayList<>();
        }
        List<AppMeta> appMetas1 = appMetas.get(project);
        return appMetas1 == null ? new ArrayList<>() : appMetas1;
    }

    public static void updateAppMetas(String project, List<AppMeta> metas) {
        if (project == null) {
            return;
        }
        if (CollectionUtils.isEmpty(metas)) {
            appMetas.remove(project);
            return;
        }
        appMetas.put(project, metas);
    }

    public static List<String> getTempApps(String project) {
        if (project == null) {
            return new  ArrayList<>();
        }
        List<String> tempApp = tempApps.get(project);
        return tempApp == null ? new ArrayList<>() : new ArrayList<>(tempApp);
    }

    public static void setTempApps(String project,List<String> apps) {
        if (project == null) {
            return ;
        }
        if (CollectionUtils.isEmpty(apps)) {
            tempApps.remove(project);
        }else{
            tempApps.put(project, apps);
        }
    }



    public static void removeApp(String project, VisibleApp app) {
        if (project == null || project.isEmpty() || app == null) {
            return;
        }
        doRemoveApp(project, app.getAppName(), app.getIp(), app.getTestkitPort());
    }

    private static void doRemoveApp(String project, String appName, String ip, Integer sidePort) {
        List<String> runtimes = loadProjectRuntimes(project);
        if (runtimes == null) {
            return;
        }
        if (!runtimes.contains(appName + ":" + ip + ":" + sidePort)) {
            return;
        }
        ;
        runtimes.remove(appName + ":" + ip + ":" + sidePort);
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


    public static RuntimeHelper.VisibleApp parseApp(String selectedItem) {
        if (selectedItem == null || selectedItem.isEmpty()) {
            return null;
        }
        String[] split = selectedItem.split(":");
        if (split.length != 3) {
            throw new IllegalArgumentException("un support app item");
        }
        RuntimeHelper.VisibleApp visibleApp = new RuntimeHelper.VisibleApp();
        visibleApp.setAppName(split[0]);
        visibleApp.setIp(split[1]);
        visibleApp.setTestkitPort(Integer.parseInt(split[2]));
        return visibleApp;
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

    public static class AppMeta {
        private String app;
        private String fullName;
        private com.intellij.openapi.module.Module module;

        public String getApp() {
            return app;
        }

        public void setApp(String app) {
            this.app = app;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public com.intellij.openapi.module.Module getModule() {
            return module;
        }

        public void setModule(Module module) {
            this.module = module;
        }

        @Override
        public String toString() {
            return "AppMeta{" +
                    "app='" + app + '\'' +
                    ", fullName='" + fullName + '\'' +
                    ", module='" + module + '\'' +
                    '}';
        }
    }

    public static class VisibleApp {

        private String appName;

        private String ip;

        private int testkitPort;

        public boolean judgeIsLocal() {
            return "local".equals(ip);
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public int buildWebPort() {
            return testkitPort > 10000 ? testkitPort - 10000 : 8080;
        }

        public int getTestkitPort() {
            return testkitPort;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public void setTestkitPort(int testkitPort) {
            this.testkitPort = testkitPort;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()){
                return false;
            }
            VisibleApp that = (VisibleApp) o;
            return testkitPort == that.testkitPort && Objects.equals(appName, that.appName) && Objects.equals(ip, that.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(appName, ip, testkitPort);
        }
    }
}
