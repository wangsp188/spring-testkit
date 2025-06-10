package com.testkit;

import com.alibaba.fastjson.JSON;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class JavaProgramPatcher extends com.intellij.execution.runners.JavaProgramPatcher {

    @Override
    public void patchJavaParameters(Executor executor, RunProfile runProfile, JavaParameters javaParameters) {
        try {
            if (!(runProfile instanceof ApplicationConfiguration configurationBase)) {
                System.err.println("当前启动类非spring-boot");
                return;
            }

            Project project = configurationBase.getProject();
//            我想验证这个类是要带有@SpringBootApplication注解的才可以
            // Get the main class from the configuration
            String mainClassName = configurationBase.getRunClass();

            // Use the Java PSI (Program Structure Interface) to find the class in the project
            JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
            PsiClass psiClass = javaPsiFacade.findClass(mainClassName,
                    GlobalSearchScope.allScope(project));

            if (psiClass == null) {
                System.err.println("无法找到指定的主类: " + mainClassName);
                return;
            }

            // Check for the @SpringBootApplication annotation
            PsiAnnotation springBootAnnotation = psiClass.getAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication");
            if (springBootAnnotation == null) {
                System.err.println("主类不包含@SpringBootApplication注解");
                return;
            }


            // 设置系统属性
            ParametersList vmParametersList = javaParameters.getVMParametersList();

            // 获取插件安装目录
            String pluginPath = PathManager.getPluginsPath();

            boolean show = false;

            if (SettingsStorageHelper.isEnableSideServer(project)) {
                // 相对路径到你的 JAR 包
                String relativeJarPath = TestkitHelper.PLUGIN_ID + File.separator + "lib" + File.separator + "testkit-starter-1.0.jar";
                String springStarterJarPath = pluginPath + File.separator + relativeJarPath;
                // 添加 Jar 到 classpath
                javaParameters.getClassPath().add(springStarterJarPath);
                show = true;
            }


            String linkJarPath = TestkitHelper.PLUGIN_ID + File.separator + "lib" + File.separator + "testkit-trace-1.0.jar";
//            增加ajar到
            javaParameters.getVMParametersList().add("-Xbootclasspath/a:" + pluginPath + File.separator + linkJarPath);

            SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(project);
            if (traceConfig.isEnable()) {
                //            增加参数 -javaagent:/Users/dexwang/sourcecode/java/spring-fling_side_server/agent/target/agent-1.0-SNAPSHOT.jar
                String agentPath = TestkitHelper.PLUGIN_ID + File.separator + "lib" + File.separator + "testkit-agent-1.0.jar";

                traceConfig = JSON.parseObject(JSON.toJSONString(traceConfig), SettingsStorageHelper.TraceConfig.class);
                if (traceConfig.judgeIsAppThreePackage()) {
                    String runClass = configurationBase.getRunClass();
                    String[] parts = runClass.split("\\.");
                    if (parts.length > 3) {
                        String packagePrefix = String.join(".", Arrays.copyOfRange(parts, 0, 3));
                        traceConfig.setPackages(packagePrefix);
                    } else {
                        // 如果包名不足三段，可以根据需求选择适当的处理方式
                        traceConfig.setPackages(runClass.substring(0,runClass.lastIndexOf("."))); // 或者其他逻辑
                    }
                }
                String base64Json = Base64.getEncoder().encodeToString(traceConfig.toString().getBytes("UTF-8"));
                String encodedJson = URLEncoder.encode(base64Json, "UTF-8");
                javaParameters.getVMParametersList().add("-javaagent:" + pluginPath + File.separator + agentPath+"="+encodedJson);
                vmParametersList.addProperty("testkit.trace.enable", "true");
                show = true;
            }

            vmParametersList.addProperty("testkit.project.name", project.getName());
            vmParametersList.addProperty("testkit.app.name", runProfile.getName());
            vmParametersList.addProperty("testkit.app.env", "local");

            String appName = configurationBase.getName();
            String propertiesStr = SettingsStorageHelper.getAppProperties(project, appName);


            Properties properties = new Properties();
            properties.load(new StringReader(propertiesStr));
            // 添加系统指令：把 properties 转到 VM 参数
            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                vmParametersList.addProperty(propertyName, propertyValue);
                System.err.println("Testkit addProperty:" + propertyName + "=" + propertyValue);
                show = true;
            }

            if (SettingsStorageHelper.isAppStartupAnalyzer(project, appName)) {
                String userHome = System.getProperty("user.home");
                if(StringUtils.isBlank(userHome) || !new File(userHome).exists()) {
                    TestkitHelper.notify(project, NotificationType.ERROR, "Spring startup analyzer not support ,Because user.home is not exists\nPlease check");
                }else{
                    String startupzip = pluginPath + File.separator+TestkitHelper.PLUGIN_ID + File.separator + "lib" + File.separator + "spring-startup-analyzer.tar.gz";
                    String startupdic = userHome + File.separator + "spring-startup-analyzer";

                    Path targetDir = Paths.get(startupdic); // 指定目标目录
                    Path resultPath = extractTarGz(startupzip, targetDir);

                    String startupAgentPath = resultPath.toAbsolutePath()+File.separator+"lib"+File.separator+"spring-profiler-agent.jar";

                    javaParameters.getVMParametersList().add("-javaagent:" + startupAgentPath);
                    boolean containsHealthcheck = properties.containsKey("spring-startup-analyzer.app.health.check.endpoints");
                    if (containsHealthcheck) {
                        String startupPort = properties.getProperty("spring-startup-analyzer.admin.http.server.port");
                        TestkitHelper.notify(project, NotificationType.INFORMATION, "Spring startup analyzer will provide in port " + (StringUtils.isBlank(startupPort) ? "8065" : startupPort));
                    } else {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Spring startup analyzer need config spring-startup-analyzer.app.health.check.endpoints\nPlease check");
                    }
                    show = true;
                }
            }


            if(show){
                TestkitHelper.notify(project,NotificationType.INFORMATION,"佛祖保佑 永无BUG");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 解压 .tar.gz 文件到指定目录
     * @param inputFile 输入的 .tar.gz 文件路径
     * @param targetDir 目标目录（解压到此目录）
     * @return 解压后的目标目录路径（如果已存在则直接返回）
     * @throws IOException
     */
    public static Path extractTarGz(String inputFile, Path targetDir) throws IOException {
        // 检查目标目录是否存在且非空
        if (isDirectoryNonEmpty(targetDir)) {
            System.out.println("目标目录已存在且非空，跳过解压: " + targetDir);
            return targetDir;
        }

        // 创建目标目录
        Files.createDirectories(targetDir);

        // 解压过程
        try (InputStream fi = Files.newInputStream(Paths.get(inputFile));
             InputStream gzi = new GZIPInputStream(fi);
             TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = ti.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue; // 跳过目录（实际创建由文件路径触发）
                }

                // 构建安全输出路径（确保在目标目录内）
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDir)) {
                    throw new IOException("非法路径尝试跳出目标目录: " + entry.getName());
                }

                // 创建父目录
                Path parent = outputPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                // 写入文件
                try (OutputStream fo = Files.newOutputStream(outputPath)) {
                    IOUtils.copy(ti, fo);
                }
            }
        }

        return targetDir;
    }

    /**
     * 检查目录是否存在且非空
     */
    private static boolean isDirectoryNonEmpty(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isPresent();
        }
    }



}