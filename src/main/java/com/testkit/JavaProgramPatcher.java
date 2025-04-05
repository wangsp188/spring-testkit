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

import java.io.File;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

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

            if(show){
                TestkitHelper.notify(project,NotificationType.INFORMATION,"佛祖保佑 永无BUG");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}