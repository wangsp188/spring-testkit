package com.testkit.util;

import com.alibaba.fastjson.JSON;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginInstaller;
import com.intellij.ide.plugins.InstalledPluginsTableModel;
import com.intellij.ide.plugins.PluginEnabler;
import com.intellij.ide.plugins.PluginInstallCallbackData;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.testkit.TestkitHelper;

import javax.swing.JComponent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class UpdaterUtil {

    private static String pluginStorageBasePackage = System.getProperty("user.home")+File.separator+".spring-testkit"+File.separator+"plugins";

    private static String pluginLastVersionUrl = "https://raw.githubusercontent.com/wangsp188/product/refs/heads/main/spring-testkit/version.json";


    /**
     * 每当打开该插件就调用次api，如果发现最新版本则提醒用户安装
     * 下载完成后才移动到目标目录，不做 sha 校验
     */
    public static LastVersion fetchNeedUpdateLastVersion() {
        try {
            List<LastVersion> all = fetchRemoteVersions(pluginLastVersionUrl);
            if (all == null || all.isEmpty()) {
                System.err.println("未配置版本文件");
                return null;
            }

            String currentVersion = getCurrentPluginVersion();
            BuildNumber ideBuild = ApplicationInfo.getInstance().getBuild();
            Date now = new Date();

            List<LastVersion> candidates = new ArrayList<>();
            for (LastVersion v : all) {
                System.err.println("处理插件版本："+v);
                if (StringUtil.isEmpty(v.getVersion())){
                    System.err.println("版本为空");
                    continue;
                }
                if (!isPublishTimeArrived(v.getPublishTime(), now)){
                    System.err.println("时候未到");
                    continue;
                }
                if (!isBuildCompatible(ideBuild, v.getSinceBuild(), v.getUntilBuild())){
                    System.err.println("版本不支持,"+ideBuild);
                    continue;
                }
                if (compareVersions(v.getVersion(), currentVersion) <= 0){
                    System.err.println("版本老,"+currentVersion);
                    continue;
                }
                candidates.add(v);
            }

            if (candidates.isEmpty()) {
                System.err.println("未发现匹配版本,currentVersion:"+currentVersion);
                return null;
            }


            // 选择最大版本
            candidates.sort(new Comparator<LastVersion>() {
                @Override
                public int compare(LastVersion o1, LastVersion o2) {
                    return compareVersions(o2.getVersion(), o1.getVersion());
                }
            });
            LastVersion target = candidates.get(0);
            System.err.println("候选版本,currentVersion:"+currentVersion+",target:"+target.getVersion());

            // 确保 zip 已下载（下载完成后再原子移动到目标目录）
            ensureDownloaded(target);

            // 清理旧版本，只保留最新的2个
            cleanOldVersions(2);

            return target;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<LastVersion> fetchRemoteVersions(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
            conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = code == HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = is.readAllBytes();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("http status: " + code + ", body: " + new String(bytes, StandardCharsets.UTF_8));
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            List<LastVersion> list = JSON.parseArray(json, LastVersion.class);
            return list == null ? Collections.emptyList() : list;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void ensureDownloaded(LastVersion v) throws Exception {
        String finalPath = v.buildPluginPath();
        File finalFile = new File(finalPath);

        // 如果文件已存在，验证其完整性
        if (finalFile.exists() && finalFile.isFile() && finalFile.length() > 0) {
            if (ZipUtil.isValidZipFile(finalFile)) {
                return; // 文件有效，直接返回
            } else {
                // 文件损坏，删除并重新下载
                System.err.println("Existing plugin zip is corrupted, deleting and re-downloading: " + finalPath);
                finalFile.delete();
            }
        }

        File parent = finalFile.getParentFile();
        if (!parent.exists()){
            parent.mkdirs();
        }

        File tmp = File.createTempFile("plugin-", ".zip", parent);
        try {
            downloadToFile(v.getDownloadUrl(), tmp.toPath());

            // 验证下载的 zip 文件完整性
            if (!ZipUtil.isValidZipFile(tmp)) {
                throw new RuntimeException("Downloaded file is not a valid zip archive: " + tmp.getAbsolutePath());
            }

            Files.move(tmp.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (tmp.exists()){
                tmp.delete();
            }
        }
    }


    private static void downloadToFile(String url, Path target) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
            conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(60));
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("download http status: " + code);
            }
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(target.toFile())) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        } finally {
            if (conn != null){
                conn.disconnect();
            }
        }
    }

    /**
     * 清理旧版本插件 zip，只保留最新的 keepCount 个版本
     */
    private static void cleanOldVersions(int keepCount) {
        try {
            File dir = new File(pluginStorageBasePackage);
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }
            File[] zips = dir.listFiles((d, name) -> name.endsWith(".zip"));
            if (zips == null || zips.length <= keepCount) {
                return;
            }

            // 按版本号降序排序（文件名去掉 .zip 后缀即为版本号）
            Arrays.sort(zips, (a, b) -> {
                String va = a.getName().replace(".zip", "");
                String vb = b.getName().replace(".zip", "");
                return compareVersions(vb, va);
            });

            // 删除排名 keepCount 之后的旧版本
            for (int i = keepCount; i < zips.length; i++) {
                if (zips[i].delete()) {
                    System.err.println("Cleaned old plugin version: " + zips[i].getName());
                } else {
                    System.err.println("Failed to delete old plugin version: " + zips[i].getName());
                }
            }
        } catch (Throwable e) {
            // 清理失败不影响主流程
            System.err.println("Failed to clean old plugin versions: " + e.getMessage());
        }
    }

    private static boolean isPublishTimeArrived(String publishTime, Date now) {
        if (StringUtil.isEmpty(publishTime)) return true;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setLenient(false);
            Date pub = sdf.parse(publishTime);
            return !pub.after(now);
        } catch (ParseException e) {
            return true;
        }
    }

    private static boolean isBuildCompatible(BuildNumber build, String since, String until) {
        if (build == null) {
            return true;
        }
        try {
            BuildNumber s = StringUtil.isEmpty(since) ? null : BuildNumber.fromString(since);
            BuildNumber u = StringUtil.isEmpty(until) ? null : BuildNumber.fromString(until);
            if (s != null && build.compareTo(s) < 0){
                return false;
            }
            if (u != null && build.compareTo(u) > 0){
                return false;
            }
            return true;
        } catch (Throwable e) {
            return true;
        }
    }

    private static int compareVersions(String a, String b) {
        if (Objects.equals(a, b)){
            return 0;
        }
        String[] as = a.split("[^0-9]+");
        String[] bs = b.split("[^^0-9]+");
        // 修正：上面的正则写错了，重新按点分割并回退到数字比较
        if (as.length <= 1 && bs.length <= 1) {
            as = a.split("\\.");
            bs = b.split("\\.");
        }
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length && StringUtil.isNotEmpty(as[i]) ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length && StringUtil.isNotEmpty(bs[i]) ? parseIntSafe(bs[i]) : 0;
            if (ai != bi){
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }



    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static String getCurrentPluginVersion() {
        try {
            IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(TestkitHelper.getPluginId()));
            if (descriptor != null) {
                return StringUtil.notNullize(descriptor.getVersion(), "0");
            }
        } catch (Throwable ignored) {
            System.err.println("获取版本异常");
            ignored.printStackTrace();
        }
        return "0";
    }

    /**
     * 尝试通过 IDEA API 从本地 zip 安装插件；失败时返回 false
     * 支持多版本 IntelliJ IDEA API 兼容
     */
    public static boolean installFromZip(String zipPath, Project project) {
        File file = new File(zipPath);
        if (!file.exists() || !file.isFile()){
            return false;
        }

        // 尝试方案: 完整参数 API (InstalledPluginsTableModel, PluginEnabler, File, Project, JComponent, Consumer) - 2023-2024
        try {
            InstalledPluginsTableModel model = new InstalledPluginsTableModel(project);
            PluginEnabler enabler = PluginEnabler.HEADLESS;
            JComponent parent = null;
            Consumer<PluginInstallCallbackData> cb = data -> { };
            Method installFromDisk = PluginInstaller.class.getDeclaredMethod("installFromDisk", InstalledPluginsTableModel.class, PluginEnabler.class, File.class, Project.class, JComponent.class, Consumer.class);
            installFromDisk.setAccessible(true);
            Object result = installFromDisk.invoke(null, model, enabler, file, project, parent, cb);
            System.out.println("Plugin installed successfully using API signature: (InstalledPluginsTableModel, PluginEnabler, File, Project, JComponent, Consumer)");
            return result == null || (result instanceof Boolean && (Boolean) result);
        } catch (NoSuchMethodException e) {
            // 方案1失败，继续尝试方案4
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // 尝试方案: 完整参数 API (InstalledPluginsTableModel, PluginEnabler, Path, Project, JComponent, Consumer) - 2023-2024
        try {
            InstalledPluginsTableModel model = new InstalledPluginsTableModel(project);
            PluginEnabler enabler = PluginEnabler.HEADLESS;
            JComponent parent = null;
            Consumer<PluginInstallCallbackData> cb = data -> { };
            Method installFromDisk = PluginInstaller.class.getDeclaredMethod("installFromDisk", InstalledPluginsTableModel.class, PluginEnabler.class, Path.class, Project.class, JComponent.class, Consumer.class);
            installFromDisk.setAccessible(true);
            Object result = installFromDisk.invoke(null, model, enabler, file.toPath(), project, parent, cb);
            System.out.println("Plugin installed successfully using API signature: (InstalledPluginsTableModel, PluginEnabler, Path, Project, JComponent, Consumer)");
            return result == null || (result instanceof Boolean && (Boolean) result);
        } catch (NoSuchMethodException e) {
            // 方案2失败
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // 所有方案都失败，打印可用方法供调试
        System.err.println("All known installFromDisk signatures failed. Available methods in PluginInstaller:");
        Method[] methods = PluginInstaller.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().contains("install")) {
                System.err.println("  - " + method.getName() + ": " + java.util.Arrays.toString(method.getParameterTypes()));
            }
        }
        return false;
    }

    /**
     * 在系统文件管理器中显示文件或其所在目录
     */
    public static void revealFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()){
                f = f.getParentFile();
            }
            // 优先使用 IDEA 自带的 Reveal 动作
            try {
                com.intellij.ide.actions.RevealFileAction.openFile(f);
                return;
            } catch (Throwable ignore) {}

            // 回退到系统桌面
            if (f != null && f.exists()) {
                java.awt.Desktop.getDesktop().open(f.isDirectory() ? f : f.getParentFile());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static class LastVersion {

        private String version;
        private String sinceBuild = "";

        private String untilBuild;


        private String downloadUrl;

        private String releaseNotes;

        //yyyy-MM-dd HH:mm:ss
        private String publishTime;


        public String buildPluginPath(){
            return pluginStorageBasePackage+ File.separator + version+".zip";
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getSinceBuild() {
            return sinceBuild;
        }

        public void setSinceBuild(String sinceBuild) {
            this.sinceBuild = sinceBuild;
        }

        public String getUntilBuild() {
            return untilBuild;
        }

        public void setUntilBuild(String untilBuild) {
            this.untilBuild = untilBuild;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public String getReleaseNotes() {
            return releaseNotes;
        }

        public void setReleaseNotes(String releaseNotes) {
            this.releaseNotes = releaseNotes;
        }

        public String getPublishTime() {
            return publishTime;
        }

        public void setPublishTime(String publishTime) {
            this.publishTime = publishTime;
        }

        @Override
        public String toString() {
            return "LastVersion{" +
                    "version='" + version + '\'' +
                    ", sinceBuild='" + sinceBuild + '\'' +
                    ", untilBuild='" + untilBuild + '\'' +
                    ", downloadUrl='" + downloadUrl + '\'' +
                    ", releaseNotes='" + releaseNotes + '\'' +
                    ", publishTime='" + publishTime + '\'' +
                    '}';
        }
    }

}
