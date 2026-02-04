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
    
    // Global Arthas support flag (determined by remote script's isArthasSupported function)
    private static volatile boolean arthasSupported = false;


    public static boolean isEnableMapperSql() {
        return enableMapperSql;
    }

    public static void setEnableMapperSql(boolean enableMapperSql) {
        RuntimeHelper.enableMapperSql = enableMapperSql;
    }

    /**
     * Check if Arthas is supported (determined by remote script)
     */
    public static boolean isArthasSupported() {
        return arthasSupported;
    }

    /**
     * Set Arthas support flag
     */
    public static void setArthasSupported(boolean supported) {
        RuntimeHelper.arthasSupported = supported;
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
    public static void updateConnectionMeta(String connectionStr, boolean enableTrace, String env, long expireTime, Integer arthasPort) {
        if (connectionStr == null) {
            return;
        }
        ConnectionMeta meta = connectionMetaMap.computeIfAbsent(connectionStr, k -> new ConnectionMeta());
        meta.setEnableTrace(enableTrace);
        meta.setEnv(env);
        meta.setExpireTime(expireTime);
        meta.setArthasPort(arthasPort);
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

    public static Integer getArthasPort(String connectionStr) {
        if (connectionStr == null) {
            return null;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null ? meta.getArthasPort() : null;
    }

    public static boolean isArthasEnabled(String connectionStr) {
        if (connectionStr == null) {
            return false;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null && meta.isArthasEnabled();
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

    /**
     * 获取 TimeTunnel 状态
     * @param connectionStr 连接字符串
     * @param methodKey className#methodName
     * @return 状态名称 (READY/RECORDING)，如果不存在返回 null
     */
    public static String getTtState(String connectionStr, String methodKey) {
        if (connectionStr == null || methodKey == null) {
            return null;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        return meta != null ? meta.getTtState(methodKey) : null;
    }

    /**
     * 设置 TimeTunnel 状态
     * @param connectionStr 连接字符串
     * @param methodKey className#methodName
     * @param state 状态名称 (READY/RECORDING)，传 null 表示删除
     */
    public static void setTtState(String connectionStr, String methodKey, String state) {
        if (connectionStr == null || methodKey == null) {
            return;
        }
        ConnectionMeta meta = connectionMetaMap.get(connectionStr);
        if (meta == null) {
            // 如果 meta 不存在且要设置状态，创建一个新的
            if (state != null) {
                meta = new ConnectionMeta();
                connectionMetaMap.put(connectionStr, meta);
            } else {
                return;
            }
        }
        meta.setTtState(methodKey, state);
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

    /**
     * 添加单个 VisibleApp 到 visibleApps（避免重复）
     * 用于 remote script 加载的 instance 直接写入
     */
    public static void addVisibleApp(String project, VisibleApp app) {
        if (project == null || app == null) {
            return;
        }
        List<VisibleApp> apps = visibleApps.computeIfAbsent(project, k -> new ArrayList<>());
        // 检查是否已存在（按 connectionString 判断）
        String connStr = app.toConnectionString();
        boolean exists = apps.stream().anyMatch(a -> connStr.equals(a.toConnectionString()));
        if (!exists) {
            apps.add(app);
        }
    }

    /**
     * 从 visibleApps 中移除指定的 app
     */
    public static void removeVisibleApp(String project, VisibleApp app) {
        if (project == null || app == null) {
            return;
        }
        List<VisibleApp> apps = visibleApps.get(project);
        if (apps != null) {
            String connStr = app.toConnectionString();
            apps.removeIf(a -> connStr.equals(a.toConnectionString()));
        }
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
        public boolean isRemoteInstance() {
            return ip != null && ip.startsWith("[");
        }

        /**
         * 获取远程实例标识（从 [partition]ip 中提取 ip）
         */
        public String getRemoteIp() {
            if (isRemoteInstance() && ip.contains("]")) {
                return ip.substring(ip.indexOf("]") + 1);
            }
            return null;
        }

        /**
         * 获取远程实例的 partition（从 [partition]ip 中提取 partition）
         */
        public String getRemotePartition() {
            if (isRemoteInstance() && ip.contains("]")) {
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
        private Integer arthasPort;  // Arthas 端口，非空表示支持 Arthas
        // TimeTunnel 状态缓存，key: className#methodName, value: 状态名称 (READY/RECORDING)
        private Map<String, String> ttStates = new HashMap<>();

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

        public Integer getArthasPort() {
            return arthasPort;
        }

        public void setArthasPort(Integer arthasPort) {
            this.arthasPort = arthasPort;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        public boolean isArthasEnabled() {
            return arthasPort != null && arthasPort > 0;
        }

        public String getTtState(String methodKey) {
            return ttStates.get(methodKey);
        }

        public void setTtState(String methodKey, String state) {
            if (state == null) {
                ttStates.remove(methodKey);
            } else {
                ttStates.put(methodKey, state);
            }
        }

        @Override
        public String toString() {
            return "ConnectionMeta{" +
                    "enableTrace=" + enableTrace +
                    ", env='" + env + '\'' +
                    ", expireTime=" + expireTime +
                    ", arthasPort=" + arthasPort +
                    ", ttStates=" + ttStates +
                    '}';
        }
    }
}
