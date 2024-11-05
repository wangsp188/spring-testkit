package com.halo.plugin.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.halo.plugin.tools.PluginToolEnum;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

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


    public static void switch2Tool(Project project, PluginToolEnum tool, PsiElement element) {
        PluginToolWindow toolWindow = windows.get(project);

        if (toolWindow != null) {
            toolWindow.switchTool(tool, element);
        }
        // 尝试通过 ToolWindowManager 获取 ToolWindow 实例
        ToolWindow ideToolWindow = ToolWindowManager.getInstance(project).getToolWindow("zcc halo");
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
                initializedToolWindow.switchTool(tool, element);
            }
        });
    }

}
