package com.nb;

import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.SingleClassConfiguration;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.spring.boot.run.SpringBootApplicationRunConfigurationBase;
import com.nb.util.LocalStorageHelper;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class SideServerJavaProgramPatcher extends JavaProgramPatcher {

    @Override
    public void patchJavaParameters(Executor executor, RunProfile runProfile, JavaParameters javaParameters) {
        try {
            if(!(runProfile instanceof SpringBootApplicationRunConfigurationBase configurationBase)){
                Project project = null;
                if(runProfile instanceof JavaRunConfigurationBase){
                    Module[] modules = ((JavaRunConfigurationBase) runProfile).getModules();
                    if(modules != null && modules.length > 0){
                        project = modules[0].getProject();
                    }
                }
                Notification notification = new Notification("No-Bug", "Warn", "not spring-boot, Good luck to you.", NotificationType.WARNING);
                Notifications.Bus.notify(notification, project);
                return;
            }

            // 获取插件安装目录
            String pluginPath = PathManager.getPluginsPath();
            // 相对路径到你的 JAR 包
            String relativeJarPath = "no-bug/lib/no-bug_side_server-0.0.1-SNAPSHOT.jar";
            String springStarterJarPath = pluginPath + File.separator + relativeJarPath;
            // 添加 Jar 到 classpath
            javaParameters.getClassPath().add(springStarterJarPath);
            // 设置系统属性
            ParametersList vmParametersList = javaParameters.getVMParametersList();
            vmParametersList.addProperty("nb.project.name", configurationBase.getProject().getName());
            vmParametersList.addProperty("nb.app.name", runProfile.getName());


            String appName = configurationBase.getMainClass().getName();
            String propertiesStr = LocalStorageHelper.getAppProperties(configurationBase.getProject(), appName);
            if (LocalStorageHelper.defProperties.equals(propertiesStr)) {
                return;
            }

            Properties properties = new Properties();
            properties.load(new StringReader(propertiesStr));
            // 添加系统指令：把 properties 转到 VM 参数
            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                vmParametersList.addProperty(propertyName,propertyValue);
                System.err.println("新增Property:"+propertyName+"="+propertyValue);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}