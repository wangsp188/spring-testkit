package com.halo.plugin.view;

import com.halo.plugin.util.Container;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.halo.plugin.tools.PluginToolEnum;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class WindowHelper {


    private static final Map<Project, PluginToolWindow> windows = new HashMap<>();


    static boolean isRegisterWindow(Project project){
        return windows.containsKey(project);
    }

    static void registerWindow(Project project, PluginToolWindow window) {
        if (windows.containsKey(project)) {
            System.err.println("当前project:" + project.getName() + "已存在window");
            return;
        }
        windows.put(project, window);
    }

    public static String getFlexibleTestPackage(Project project) {
        Container<String> packageNames = new Container<>();
        getOrInitToolWindow(project, new Consumer<PluginToolWindow>() {
            @Override
            public void accept(PluginToolWindow pluginToolWindow) {
                packageNames.set(pluginToolWindow.getFlexibleTestPackage());
            }
        });
        return packageNames.get();
    }


    public static void switch2Tool(Project project, PluginToolEnum tool, PsiElement element) {
        getOrInitToolWindow(project, new Consumer<PluginToolWindow>() {
            @Override
            public void accept(PluginToolWindow pluginToolWindow) {
                pluginToolWindow.switchTool(tool, element);
            }
        });
    }



    private static void getOrInitToolWindow(Project project, Consumer<PluginToolWindow> consumer) {
        PluginToolWindow toolWindow = windows.get(project);

        if (toolWindow != null) {
            consumer.accept(toolWindow);
            return;
        }
        // 尝试通过 ToolWindowManager 获取 ToolWindow 实例
        ToolWindow ideToolWindow = ToolWindowManager.getInstance(project).getToolWindow("halo");
        if (ideToolWindow == null) {
            Messages.showMessageDialog(project,
                    "Failed to open the project window",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        PluginToolWindowFactory factory = new PluginToolWindowFactory();

        // 确保初始化在EDT进行
        SwingUtilities.invokeLater(() -> {
            factory.createToolWindowContent(project, ideToolWindow);
            // 获取并更新实例
            PluginToolWindow initializedToolWindow = windows.get(project);
            if (initializedToolWindow != null) {
                consumer.accept(initializedToolWindow);
            }
        });
    }
}
