package com.testkit.view;

import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallIconProvider;
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

import javax.swing.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TestkitToolWindowFactory implements ToolWindowFactory {

    public static final Icon CURL_ICON = IconLoader.getIcon("/icons/curl.svg", FunctionCallIconProvider.class);

    public static final Icon SQL_TOOL_ICON = IconLoader.getIcon("/icons/sql-tool.svg", FunctionCallIconProvider.class);


    private static final Map<Project, TestkitToolWindow> windows = new HashMap<>();

    private static boolean isRegisterWindow(Project project) {
        return windows.containsKey(project);
    }


    public static TestkitToolWindow getToolWindow(Project project) {
        if (project == null) {
            return null;
        }
        return windows.get(project);
    }

    private static void registerWindow(Project project, TestkitToolWindow window) {
        if (windows.containsKey(project)) {
            System.err.println("当前project:" + project.getName() + "已存在window");
            return;
        }
        windows.put(project, window);
    }

    public static void switch2Tool(Project project, PluginToolEnum tool, PsiElement element) {
        getOrInitToolWindow(project, new Consumer<TestkitToolWindow>() {
            @Override
            public void accept(TestkitToolWindow testkitToolWindow) {
                testkitToolWindow.switchTool(tool, element);
            }
        });
    }

    private static void getOrInitToolWindow(Project project, Consumer<TestkitToolWindow> consumer) {
        TestkitToolWindow toolWindow = windows.get(project);

        if (toolWindow != null) {
            consumer.accept(toolWindow);
            return;
        }
        // 尝试通过 ToolWindowManager 获取 ToolWindow 实例
        ToolWindow ideToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Spring-Testkit");
        if (ideToolWindow == null) {
            Messages.showMessageDialog(project,
                    "Failed to open the project window",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        TestkitToolWindowFactory factory = new TestkitToolWindowFactory();

        // 确保初始化在EDT进行
        SwingUtilities.invokeLater(() -> {
            factory.createToolWindowContent(project, ideToolWindow);
            // 获取并更新实例
            TestkitToolWindow initializedToolWindow = windows.get(project);
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
            TestkitToolWindow testkitToolWindow = new TestkitToolWindow(project, toolWindow);
            registerWindow(project, testkitToolWindow);
            // 使用 ApplicationManager 获取 ContentFactory 实例
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(testkitToolWindow.getContent(), "", false);

            ContentManager contentManager = toolWindow.getContentManager();
            contentManager.addContent(content);
            addHeaderActions(toolWindow, project, testkitToolWindow);
        }

    }

    private void addHeaderActions(ToolWindow toolWindow, Project project, TestkitToolWindow testkitToolWindow) {
        // 添加第一个按钮
        AnAction curlAction = new AnAction("Curl Parser", "Curl Parser", CURL_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        testkitToolWindow.openCurlDialog();
                    }
                });

            }
        };

        // 添加第二个按钮
        AnAction sql = new AnAction("SQL Tool", "SQL Tool", SQL_TOOL_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        testkitToolWindow.openSqlDialog();
                    }
                });
            }
        };

        // 添加第二个按钮
        AnAction cli = new AnAction("Testkit-Cli", "Testkit-Cli", BasePluginTool.CMD_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        testkitToolWindow.openCliDialog();
                    }
                });
            }
        };

        // 将按钮添加到工具窗口标题栏
        toolWindow.setTitleActions(Arrays.asList(curlAction, sql,cli));
    }
}