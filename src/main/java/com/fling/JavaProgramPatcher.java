package com.fling;

import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.spring.boot.run.SpringBootApplicationRunConfigurationBase;

import javax.swing.*;
import java.io.File;
import java.io.StringReader;
import java.util.Properties;

public class JavaProgramPatcher extends com.intellij.execution.runners.JavaProgramPatcher {

    @Override
    public void patchJavaParameters(Executor executor, RunProfile runProfile, JavaParameters javaParameters) {
        try {
            if (!(runProfile instanceof ApplicationConfiguration configurationBase)) {
                System.err.println("当前启动类非spring-boot");
                return;
            }

            // 获取插件安装目录
            String pluginPath = PathManager.getPluginsPath();
            // 相对路径到你的 JAR 包
            String relativeJarPath = "spring-fling" + File.separator + "lib" + File.separator + "spring-fling_side_server-0.0.1.jar";
            String springStarterJarPath = pluginPath + File.separator + relativeJarPath;
            // 添加 Jar 到 classpath
            javaParameters.getClassPath().add(springStarterJarPath);
            // 设置系统属性
            ParametersList vmParametersList = javaParameters.getVMParametersList();
            vmParametersList.addProperty("fling.project.name", configurationBase.getProject().getName());
            vmParametersList.addProperty("fling.app.name", runProfile.getName());

            String appName = configurationBase.getName();
            String propertiesStr = LocalStorageHelper.getAppProperties(configurationBase.getProject(), appName);

            FlingHelper.notify(configurationBase.getProject(),NotificationType.INFORMATION,"Buddha bless, Never Bug");

            if (LocalStorageHelper.defProperties.equals(propertiesStr)) {
                return;
            }
            Properties properties = new Properties();
            properties.load(new StringReader(propertiesStr));
            // 添加系统指令：把 properties 转到 VM 参数
            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                vmParametersList.addProperty(propertyName, propertyValue);
                System.err.println("Fling addProperty:" + propertyName + "=" + propertyValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}