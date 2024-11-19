package com.fling.view;

import com.fling.tools.PluginToolEnum;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FlingToolWindowFactory implements ToolWindowFactory {

    private static final Map<Project, FlingToolWindow> windows = new HashMap<>();

    private static boolean isRegisterWindow(Project project) {
        return windows.containsKey(project);
    }

    private static void registerWindow(Project project, FlingToolWindow window) {
        if (windows.containsKey(project)) {
            System.err.println("当前project:" + project.getName() + "已存在window");
            return;
        }
        windows.put(project, window);
    }

    public static void switch2Tool(Project project, PluginToolEnum tool, PsiElement element) {
        getOrInitToolWindow(project, new Consumer<FlingToolWindow>() {
            @Override
            public void accept(FlingToolWindow flingToolWindow) {
                flingToolWindow.switchTool(tool, element);
            }
        });
    }

    private static void getOrInitToolWindow(Project project, Consumer<FlingToolWindow> consumer) {
        FlingToolWindow toolWindow = windows.get(project);

        if (toolWindow != null) {
            consumer.accept(toolWindow);
            return;
        }
        // 尝试通过 ToolWindowManager 获取 ToolWindow 实例
        ToolWindow ideToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Fling");
        if (ideToolWindow == null) {
            Messages.showMessageDialog(project,
                    "Failed to open the project window",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        FlingToolWindowFactory factory = new FlingToolWindowFactory();

        // 确保初始化在EDT进行
        SwingUtilities.invokeLater(() -> {
            factory.createToolWindowContent(project, ideToolWindow);
            // 获取并更新实例
            FlingToolWindow initializedToolWindow = windows.get(project);
            if (initializedToolWindow != null) {
                consumer.accept(initializedToolWindow);
            }
        });
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        if (isRegisterWindow(project)) {
            System.err.println("project:" + project.getName() + "已存在window");
            return;
        }
        synchronized (project) {
            FlingToolWindow flingToolWindow = new FlingToolWindow(project,toolWindow);
            registerWindow(project, flingToolWindow);
            // 使用 ApplicationManager 获取 ContentFactory 实例
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(flingToolWindow.getContent(), "", false);

            ContentManager contentManager = toolWindow.getContentManager();
            contentManager.addContent(content);
        }

    }
}