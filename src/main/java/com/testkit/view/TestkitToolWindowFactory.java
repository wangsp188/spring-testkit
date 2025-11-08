package com.testkit.view;

import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
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
import com.testkit.tools.mcp_function.McpAdapter;
import com.testkit.tools.mcp_function.McpHelper;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TestkitToolWindowFactory implements ToolWindowFactory {

    public static final Icon CURL_ICON = IconLoader.getIcon("/icons/curl.svg", FunctionCallIconProvider.class);

    public static final Icon SQL_TOOL_ICON = IconLoader.getIcon("/icons/sql-tool.svg", FunctionCallIconProvider.class);
    public static final Icon MCP_ICON = IconLoader.getIcon("/icons/mcp.svg", FunctionCallIconProvider.class);



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
            McpHelper.subscribe(testkitToolWindow);
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
        AnAction mcp = new AnAction("MCP Servers", "MCP Servers", MCP_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        testkitToolWindow.openMcpServerDialog();
                    }
                });
            }
        };



        // 添加第三个个按钮
        AnAction refresh = new AnAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                // 所有耗时操作都在后台线程执行，避免阻塞 EDT
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        // 1. 查找 Spring Boot 应用类（已经在后台线程中执行）
                        testkitToolWindow.findSpringBootApplicationClasses();
                        
                        // 2. 刷新文档（文件 I/O 操作，在后台线程执行）
                        CodingGuidelinesHelper.refreshDoc(project);
                        
                        // 3. 刷新代码分析器（在后台线程执行）
                        TestkitHelper.refresh(project);
                        
                        // 4. 读取并刷新 MCP 配置（文件 I/O 操作，在后台线程执行）
                        try {
                            JSONObject cursorConfig = McpHelper.fetchMcpJson();
                            McpAdapter.McpInitRet mcpInitRet = McpAdapter.parseMcpServers(cursorConfig);
                            // refreshServers 内部会调用 SwingUtilities.invokeLater 来更新 UI
                            McpHelper.refreshServers(mcpInitRet.servers());
                        } catch (Exception mcpEx) {
                            // MCP 配置读取失败不影响其他操作
                            System.err.println("Failed to refresh MCP config: " + mcpEx.getMessage());
                        }
                        
                        // 5. 在 EDT 上显示成功通知
                        SwingUtilities.invokeLater(() -> {
                            TestkitHelper.notify(project, NotificationType.INFORMATION, "Refresh success");
                        });
                    } catch (Exception ex) {
                        // 在 EDT 上显示错误通知
                        SwingUtilities.invokeLater(() -> {
                            TestkitHelper.notify(project, NotificationType.ERROR, "Refresh failed," + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                        });
                    }
                });
            }
        };

        // 添加第二个按钮
        AnAction store = new AnAction("Testkit Store", "Testkit Store", TestkitStoreWindow.STORE_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        TestkitStoreWindowFactory.show(project);
                    }
                });
            }
        };

        // 将按钮添加到工具窗口标题栏
        toolWindow.setTitleActions(Arrays.asList(curlAction, sql,mcp,store,refresh));
    }
}