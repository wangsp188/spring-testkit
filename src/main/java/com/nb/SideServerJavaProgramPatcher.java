package com.nb;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.application.PathManager;

import java.io.File;

public class SideServerJavaProgramPatcher extends JavaProgramPatcher {

    @Override
    public void patchJavaParameters(Executor executor, RunProfile runProfile, JavaParameters javaParameters) {
        try {
            // 获取插件安装目录
            String pluginPath = PathManager.getPluginsPath();
            // 相对路径到你的 JAR 包
            String relativeJarPath = "no-bug/lib/no-bug_side_server-0.0.1-SNAPSHOT.jar";
            String springStarterJarPath = pluginPath + File.separator + relativeJarPath;
            // 添加 Jar 到 classpath
            javaParameters.getClassPath().add(springStarterJarPath);

            // 设置系统属性
            javaParameters.getVMParametersList().addProperty("nb.project.name", "unknown");
            javaParameters.getVMParametersList().addProperty("nb.app.name", runProfile.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}