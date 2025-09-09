package com.testkit.view;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.testkit.tools.mcp_function.McpHelper;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestkitStoreWindowFactory implements ToolWindowFactory {


    private static final Map<Project, TestkitStoreWindow> windows = new HashMap<>();

    private static boolean isRegisterWindow(Project project) {
        return windows.containsKey(project);
    }



    public static void refreshStore(Project project){
        TestkitStoreWindowFactory.getOrInitStoreWindow(project, new Consumer<TestkitStoreWindow>() {
            @Override
            public void accept(TestkitStoreWindow testkitStoreWindow) {
                testkitStoreWindow.refreshTree();
            }
        });

    }

    public static void initApps(Project project, List<String> apps){
        TestkitStoreWindowFactory.getOrInitStoreWindow(project, new Consumer<TestkitStoreWindow>() {
            @Override
            public void accept(TestkitStoreWindow testkitStoreWindow) {
                testkitStoreWindow.initApps(apps);
            }
        });
    }

    private static void registerWindow(Project project, TestkitStoreWindow window) {
        if (windows.containsKey(project)) {
            System.err.println("当前project:" + project.getName() + "已存在window");
            return;
        }
        windows.put(project, window);
    }

    public static void show(Project project){
        TestkitStoreWindowFactory.getOrInitStoreWindow(project, new Consumer<TestkitStoreWindow>() {
            @Override
            public void accept(TestkitStoreWindow testkitStoreWindow) {
                testkitStoreWindow.show();
            }
        });
    }

    public static void getOrInitStoreWindow(Project project, Consumer<TestkitStoreWindow> consumer) {
        TestkitStoreWindow toolWindow = windows.get(project);
        if (toolWindow != null) {
            consumer.accept(toolWindow);
            return;
        }
        // 尝试通过 ToolWindowManager 获取 ToolWindow 实例
        ToolWindow ideToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Testkit-Store");
        if (ideToolWindow == null) {
            Messages.showMessageDialog(project,
                    "Failed to open the project window",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        TestkitStoreWindowFactory factory = new TestkitStoreWindowFactory();

        // 确保初始化在EDT进行
        SwingUtilities.invokeLater(() -> {
            factory.createToolWindowContent(project, ideToolWindow);
            // 获取并更新实例
            TestkitStoreWindow initializedToolWindow = windows.get(project);
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
            TestkitStoreWindow testkitToolWindow = new TestkitStoreWindow(project,toolWindow);
            registerWindow(project, testkitToolWindow);
            // 使用 ApplicationManager 获取 ContentFactory 实例
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(testkitToolWindow.getContent(), "", false);

            ContentManager contentManager = toolWindow.getContentManager();
            contentManager.addContent(content);
        }

    }

}