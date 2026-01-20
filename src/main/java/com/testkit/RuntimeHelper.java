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

    // 连接元数据：connectionStr -> ConnectionMeta（包含 local 和 remote 连接）
    private static final Map<String, ConnectionMeta> connectionMetaMap = new HashMap<>();

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


    // ==================== Connection Meta 相关方法 ====================

    /**
     * 更新连接的元数据
     */
    public static void updateConnectionMeta(String connectionStr, boolean enableTrace, String env, long expireTime) {
        if (connectionStr == null) {
            return;
        }
        ConnectionMeta meta = connectionMetaMap.computeIfAbsent(connectionStr, k -> new ConnectionMeta());
        meta.setEnableTrace(enableTrace);
        meta.setEnv(env);
        meta.setExpireTime(expireTime);
    }

    /**
     * 获取连接的元数据
     */
    public static ConnectionMeta getConnectionMeta(String connectionStr) {
        if (connectionStr == null) {
            return null;
        }
        return connectionMetaMap.get(connectionStr);
    }

    /**
     * 移除连接的元数据
     */
    public static void removeConnectionMeta(String connectionStr) {
        if (connectionStr != null) {
            connectionMetaMap.remove(connectionStr);
        }
    }

    /**
     * 批量更新 trace 状态
     */
    public static void updateTraces(Map<String, Boolean> traces) {
        if (traces == null) {
            return;
        }
        for (Map.Entry<String, Boolean> entry : traces.entrySet()) {
            String connectionStr = entry.getKey();
            ConnectionMeta meta = connectionMetaMap.computeIfAbsent(connectionStr, k -> new ConnectionMeta());
            meta.setEnableTrace(entry.getValue() != null && entry.getValue());
        }
    }

    public static boolean isEnableTrace(String connectionStr) {
        if (connectionStr == null) {
            return false;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null && meta.isEnableTrace();
    }

    public static String getEnv(String connectionStr) {
        if (connectionStr == null) {
            return null;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null ? meta.getEnv() : null;
    }

    public static long getExpireTime(String connectionStr) {
        if (connectionStr == null) {
            return 0;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null ? meta.getExpireTime() : 0;
    }

    /**
     * 检查连接是否已过期（仅对 remote 连接有意义）
     */
    public static boolean isConnectionExpired(String connectionStr) {
        if (connectionStr == null) {
            return true;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        if (meta == null) {
            return true; // 没有元数据视为已过期
        }
        return System.currentTimeMillis() > meta.getExpireTime();
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

        /**
         * 判断是否是通过 Groovy 脚本连接的远程机器
         * IP 格式为 [partition]ip
         */
        public boolean isRemoteScript() {
            return ip != null && ip.startsWith("[");
        }

        /**
         * 获取远程实例标识（从 [partition]ip 中提取 ip）
         */
        public String getRemoteIp() {
            if (isRemoteScript() && ip.contains("]")) {
                return ip.substring(ip.indexOf("]") + 1);
            }
            return null;
        }

        /**
         * 获取远程实例的 partition（从 [partition]ip 中提取 partition）
         */
        public String getRemotePartition() {
            if (isRemoteScript() && ip.contains("]")) {
                return ip.substring(1, ip.indexOf("]"));
            }
            return null;
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

        public String toConnectionString() {
            return appName + ":" + ip + ":" + testkitPort;
        }
    }

    /**
     * 连接元数据（包含 local 和 remote 连接的元信息）
     */
    public static class ConnectionMeta {
        private boolean enableTrace;
        private String env;
        private long expireTime;  // 过期时间（UTC 时间戳）

        public boolean isEnableTrace() {
            return enableTrace;
        }

        public void setEnableTrace(boolean enableTrace) {
            this.enableTrace = enableTrace;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        @Override
        public String toString() {
            return "ConnectionMeta{" +
                    "enableTrace=" + enableTrace +
                    ", env='" + env + '\'' +
                    ", expireTime=" + expireTime +
                    '}';
        }
    }
}
