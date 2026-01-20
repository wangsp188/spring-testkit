package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBTextField;
import com.testkit.RemoteScriptExecutor;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.testkit.coding_guidelines.CodingGuidelinesIconProvider;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.sql_review.SqlDialog;
import com.testkit.tools.mapper_sql.MapperSqlTool;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.*;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallTool;
import com.testkit.tools.flexible_test.FlexibleTestTool;
import com.intellij.ui.components.JBScrollPane;
import com.testkit.tools.mcp_function.MCPServerDialog;
import com.testkit.tools.mcp_function.McpFunctionTool;
import com.testkit.util.HttpUtil;
import com.testkit.util.RemoteScriptCallUtils;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestkitToolWindow {

    public static final Icon BROWSER_ICON = IconLoader.getIcon("/icons/browser.svg", CodingGuidelinesIconProvider.class);
    private static final Icon settingsIcon = IconLoader.getIcon("/icons/settings.svg", TestkitToolWindow.class);
    private static final Icon dagreIcon = IconLoader.getIcon("/icons/trace.svg", TestkitToolWindow.class);
    private static final Icon cmdIcon = IconLoader.getIcon("/icons/cmd.svg", TestkitToolWindow.class);
    private static final Icon connectionIcon = IconLoader.getIcon("/icons/connection.svg", TestkitToolWindow.class);
    // ToolWindow 原始图标
    public static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/icons/spring-testkit.svg", TestkitToolWindow.class);

    // Remote Script 函数说明
    private static final String REMOTE_SCRIPT_INFO = """
================================================================================
                        Remote Script 函数规范
================================================================================

插件会调用 Groovy 脚本中的三个函数，脚本需要实现以下接口：

────────────────────────────────────────────────────────────────────────────────
1. loadInfra()
────────────────────────────────────────────────────────────────────────────────
   功能: 获取可用的 App 和 Partition 列表
   参数: 无
   返回: Map<String, List<String>>
         Key   = appName (对应 Spring Boot 启动类名，如 "WebApplication")
         Value = partition 列表 (如 ["us01", "qa01", "dev01"])
   
   示例返回:
   [
       "WebApplication"    : ["us01", "qa01"],
       "ApiWebApplication" : ["us01"]
   ]

────────────────────────────────────────────────────────────────────────────────
2. loadInstances(String appName, String partition)
────────────────────────────────────────────────────────────────────────────────
   功能: 获取指定 App + Partition 下的实例列表
   参数: appName   - 应用名
         partition - 分区名
   返回: List<Map> 实例信息列表
   
   必填字段:
     - ip           : String  实例标识（显示用，会存储为 [partition]ip 格式）
     - port         : int     Testkit 端口
     - env          : String  环境标识（如 "prod", "qa"）
     - success      : boolean 是否可用
     - errorMessage : String  错误信息（success=false 时）
   
   可选字段:
     - enableTrace  : boolean 是否支持 Trace（默认 false）
     - expireTime   : long    过期时间 UTC 时间戳（默认 24 小时后过期）
   
   示例返回:
   [
       [ip: "pod-001", port: 18080, success: true,  errorMessage: "", enableTrace: true, env: "prod"],
       [ip: "pod-002", port: 18080, success: false, errorMessage: "Connection refused"]
   ]

────────────────────────────────────────────────────────────────────────────────
3. sendRequest(String appName, String partition, String ip, int port, Map request)
────────────────────────────────────────────────────────────────────────────────
   功能: 向指定实例发送请求（转发到 Testkit Server）
   参数: appName   - 应用名
         partition - 分区名
         ip        - 实例标识（与 loadInstances 返回的 ip 对应）
         port      - Testkit 端口
         request   - 请求对象 Map
   返回: Map 响应结果（透传 Testkit Server 响应）
   
   request 结构:
     - method      : String            请求方法
     - params      : Map<String,String> 请求参数
     - trace       : boolean           是否追踪（可选）
     - prepare     : boolean           是否预处理（可选）
     - interceptor : String            拦截器配置（可选）
   
   常见 method 值:
     - "hi"           : 健康检查
     - "function-call": 提交[function-call]任务
     - "flexible-test": 提交[flexible-test]任务
     - "view-value"   : 查看变量值
     - "get_task_ret" : 获取任务结果
     - "stop_task"    : 停止任务

================================================================================
                              Demo Script
================================================================================

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ==================== 1. loadInfra ====================
def loadInfra() {
// 可以返回自己服务的集群架构定义，在后面的api中会使用这些当作参数
    return [
        "WebApplication": ["dev01", "qa01", "us01"]
    ]
}

// ==================== 2. loadInstances ====================
// 注意: 需要先在目标机器上通过 testkit-cli attach 启动 Testkit Server，如果目标机器默认启动则可以忽略
//       success=true 表示 Testkit Server 已启动且可连接
//       success=false 表示启动失败或连接不上，errorMessage 填写原因
def loadInstances(String appName, String partition) {
    def pods = getPodList(appName, partition)  // 获取 pod 列表（自行实现）
    
    return pods.collect { pod ->
        def result = [
            ip   : pod.name,
            port : 18080,
            env  : partition
        ]
        try {
            // 1) 通过 SSH/kubectl 在目标机器上启动 testkit-cli（如果未启动）
            def resp =  startTestkitCli(pod.ip, appName)
            result.success      = resp.success == true
            result.errorMessage = result.success ? "" : (resp.message ?: "Unknown error")
            result.enableTrace  = resp.data?.enableTrace ?: false
        } catch (Exception e) {
            result.success      = false
            result.errorMessage = e.message ?: "Connection failed"
            result.enableTrace  = false
        }
        return result
    }
}

// ==================== 3. sendRequest ====================
// 如果目标 IP 可直连，直接发送 HTTP POST 请求即可
def sendRequest(String appName, String partition, String ip, int port, Map request) {
    def address = getAddress(ip, partition)  // 根据 ip 获取实际地址（自行实现）
    return httpPost("http://${address}:${port}/", request)
}

// ==================== 辅助函数 ====================
def httpPost(String url, Map data) {
    def conn = new URL(url).openConnection()
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(600000)
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.withWriter { it << JsonOutput.toJson(data) }
    return new JsonSlurper().parseText(conn.inputStream.text)
}

// TODO: 实现以下函数
// def getPodList(appName, partition) { ... }
// def getAddress(ip, partition) { ... }
// def startTestkitCli(podIp, appName) { ... }

================================================================================
""";

    // Remote Script 超时配置
    private static final int LOAD_INFRA_TIMEOUT = 30;       // loadInfra 超时 30 秒
    private static final int LOAD_INSTANCES_TIMEOUT = 60;   // loadInstances 超时 60 秒

    private Project project;
    private ToolWindow window;
    private JPanel windowContent;
    private JButton tipsButton;
    private JButton settingsButton;
    private JComboBox<String> toolBox;

    private JPanel appPanel;
    private JComboBox<String> appBox;
    private JButton killButton; // kill 进程按钮
    private JPanel rightAppPanel; // RuntimeApp 面板（包含 appBox 和 killButton）

    private SettingsDialog settingsDialog;
    private CurlDialog curlDialog;
    private SqlDialog sqlDialog;
    private MCPServerDialog mcpServerDialog;
    private JPanel whitePanel = new JPanel();
    private Map<PluginToolEnum, BasePluginTool> tools = new HashMap<>();


    protected JTextPane outputTextPane;

    private static String linkRenderHtml;
    protected DefaultActionGroup actionGroup;
    protected AnAction traceAction;
    protected List<Map<String, String>> outputProfile;


    static {
        URL resource = TestkitToolWindow.class.getResource("/html/horse_up_down_class.html");
        if (resource != null) {
            try {
                try (InputStream is = resource.openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    linkRenderHtml = reader.lines().collect(Collectors.joining("\n"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public TestkitToolWindow(Project project, ToolWindow window) {

        this.project = project;
        this.window = window;
        // 初始化主面板
        windowContent = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件之间的间距
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; // 允许 topPanel 拉伸以占据水平空间
        JPanel headPanel = buildHeaderPanel();
        windowContent.add(headPanel, gbc);

        // 添加工具面板（初次加载时隐藏）
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        windowContent.add(whitePanel, gbc);
        // 添加所有工具面板，并设置为不可见
        for (BasePluginTool tool : tools.values()) {
            JPanel panel = tool.getPanel();
            panel.setVisible(false); // 初始时不显示
            windowContent.add(panel, gbc);
        }
        onSwitchTool();

        gbc.gridy = 2;
        gbc.weighty = 0.6; // Bottom panel takes 60% of the space
        // Bottom panel for output
        JComponent bottomPanel = createOutputPanel(project);
        windowContent.add(bottomPanel, gbc);
    }

    public static JTextArea createTips(String content) {
        JTextArea tipArea = new JTextArea(content);
        tipArea.setToolTipText(content);
        tipArea.setEditable(false); // 不可编辑
        tipArea.setOpaque(false);
        tipArea.setForeground(new Color(0x72A96B));
        tipArea.setFont(new Font("Arial", Font.BOLD, 13)); // 设置字体
        return tipArea;
    }

    public BasePluginTool getNowTool() {
        Object selectedItem = toolBox.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        PluginToolEnum byCode = PluginToolEnum.getByCode(selectedItem.toString());
        return byCode == null ? null : tools.get(byCode);
    }

    private JPanel buildHeaderPanel() {
        // 使用BorderLayout作为主布局
        JPanel outPanel = new JPanel(new BorderLayout(5, 2));
        // 创建一个新的 JPanel 用于存放第一行的组件
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2)); // 使用 FlowLayout 确保组件的水平排列和固定间距

        // 添加 settingsButton 到 topPanel
        settingsButton = new JButton(settingsIcon);
        settingsButton.setPreferredSize(new Dimension(32, 32));
        settingsButton.setToolTipText("Settings");
        topPanel.add(settingsButton);

        // 添加 tipsButton 到 topPanel
        tipsButton = new JButton(AllIcons.General.Information);
        tipsButton.setToolTipText("docs");
        tipsButton.setPreferredSize(new Dimension(32, 32));
        tipsButton.setText(null);
        tipsButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultActionGroup actionGroup = new DefaultActionGroup();
//                actionGroup.setSearchable(true);
                // 创建一个动作组
                actionGroup.add(new AnAction("How to use " + TestkitHelper.getPluginName() + " ?", null, AllIcons.General.Information) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        openTipsDoc();
                    }
                });
                fillDynamicDoc(actionGroup);

                // 判断 actionGroup 中的子项数量
                int childrenCount = actionGroup.getChildrenCount();
                if (childrenCount == 1) {
                    openTipsDoc();
                } else {
                    // 如果有多个 action，显示菜单
                    JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("TipsPopup", actionGroup).getComponent();
                    popupMenu.show(tipsButton, 0, tipsButton.getHeight());
                }
            }
        });
        topPanel.add(tipsButton);

        curlDialog = new CurlDialog(this);
        sqlDialog = new SqlDialog(this);
        mcpServerDialog = new MCPServerDialog(this);

        // toolBox 初始化（不添加到 topPanel，将在 BasePluginTool 的第二行显示）
        // 注意：必须在创建工具之前初始化，因为工具面板会引用 toolBox
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.FUNCTION_CALL.getCode(), PluginToolEnum.FLEXIBLE_TEST.getCode(), PluginToolEnum.MAPPER_SQL.getCode(),PluginToolEnum.MCP_FUNCTION.getCode()});
        toolBox.setToolTipText("Tool list");
        toolBox.setPreferredSize(new Dimension(120, 32));
        toolBox.addActionListener(e -> onSwitchTool());
//        下方用一个东西撑起来整个window的下半部分
//        当切换toolbox时根据选中的内容，从tools中找出对应的tool，然后用内部的内容填充该部分
//        初始化所有tool的面板，但是不加载
        tools.put(PluginToolEnum.FUNCTION_CALL, new FunctionCallTool(this));
        tools.put(PluginToolEnum.FLEXIBLE_TEST, new FlexibleTestTool(this));
        tools.put(PluginToolEnum.MAPPER_SQL, new MapperSqlTool(this));
        tools.put(PluginToolEnum.MCP_FUNCTION, new McpFunctionTool(this));


        appPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        ActionButton addBtn = new ActionButton(new AnAction("Add Remote Connection", null, connectionIcon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                showConnectionConfigPopup();
            }
        }, null, "1", new Dimension(16, 32));
        // 添加 VisibleApp Label
        JLabel appLabel = new JLabel("RuntimeApp:");
//        appLabel.setPreferredSize(new Dimension(80, 32));
        appLabel.setToolTipText("The list of currently connected apps,You can click the + on the right to manually add connection");
        appPanel.add(appLabel);
        appPanel.add(addBtn);

        topPanel.add(appPanel);

        // 添加 appBox 到 topPanel
        appBox = new ComboBox<>(new String[]{});
        appBox.setPreferredSize(new Dimension(150, 32));
        Color defColor = appBox.getForeground();
        appBox.addItemListener(e -> {
            String selectedItem = (String) appBox.getSelectedItem();
            RuntimeHelper.updateSelectedApp(getProject().getName(), selectedItem == null ? null : RuntimeHelper.parseApp(selectedItem));
            appBox.setToolTipText(selectedItem == null ? "" : selectedItem); // 动态更新 ToolTipText
            if (selectedItem != null && RuntimeHelper.isEnableTrace(selectedItem)) {
                appBox.setForeground(new Color(114, 169, 107));
            } else {
                appBox.setForeground(defColor);
            }
        });

        // 将左侧按钮组添加到主面板西侧
        outPanel.add(topPanel, BorderLayout.WEST);
        // 应用标签
        rightAppPanel = new JPanel(new BorderLayout(2, 2));

        rightAppPanel.add(appPanel, BorderLayout.WEST);
        // 设置扩展策略
        rightAppPanel.add(appBox, BorderLayout.CENTER);

        // 添加 kill 进程按钮到 appBox 右边
        killButton = new JButton(AllIcons.Actions.GC);
        killButton.setToolTipText("⚠️ Stop/Kill: Local app kills process, Remote app sends stop request and removes from list");
        killButton.setPreferredSize(new Dimension(32, 32));
        // 设置红色前景色，使其更加醒目和具有警告性
        killButton.setForeground(new Color(220, 53, 69)); // 红色
        killButton.setFocusPainted(false);
        killButton.setBorderPainted(true);
        // 设置鼠标悬停时的效果
        killButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                killButton.setBackground(new Color(220, 53, 69, 30)); // 半透明红色背景
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                killButton.setBackground(null);
            }
        });
        killButton.addActionListener(e -> {
            String selectedItem = (String) appBox.getSelectedItem();
            if (selectedItem == null) {
                TestkitHelper.notify(project, NotificationType.WARNING, "Please select an app first");
                return;
            }

            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(selectedItem);
            if (visibleApp == null) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Failed to parse app: " + selectedItem);
                return;
            }

            // 检查是否是 remote app
            if (visibleApp.isRemoteScript()) {
                // Remote app: 仅从列表移除，不停止远程服务
                int result = Messages.showYesNoDialog(
                        project,
                        "Remove this remote instance from list?\n" +
                                "App: " + visibleApp.getAppName() + "\n" +
                                "IP: " + visibleApp.getRemoteIp() + "\n\n" +
                                "(Note: This only disconnects from plugin, the remote service will NOT be stopped)",
                        "Remove Remote Instance",
                        Messages.getQuestionIcon()
                );

                if (result == Messages.YES) {
                    removeRemoteInstance(visibleApp, selectedItem);
                }
                return;
            }

            // 检查是否是 local app
            if (!visibleApp.judgeIsLocal()) {
                TestkitHelper.notify(project, NotificationType.WARNING, "Only local or remote apps can be stopped. Current app IP: " + visibleApp.getIp());
                return;
            }

            int port = visibleApp.getTestkitPort();
            String appName = visibleApp.getAppName();

            // 确认弹窗
            int result = Messages.showYesNoDialog(
                    project,
                    "Are you sure you want to kill the process using port " + port + "?\n" +
                            "App: " + appName + "\n" +
                            "This action cannot be undone.",
                    "Confirm Kill Process",
                    Messages.getWarningIcon()
            );

            if (result == Messages.YES) {
                killProcessByPort(port, appName);
            }
        });
        rightAppPanel.add(killButton, BorderLayout.EAST);

        // 将右侧面板添加到主面板东侧
        outPanel.add(rightAppPanel, BorderLayout.CENTER);

        //        topPanel.add(appBox);

        initSettingsDialog();
        DumbService.getInstance(project).smartInvokeLater(() -> {
            // 事件或代码在索引准备好后运行
            findSpringBootApplicationClasses();
        });


        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5 * 1000); // 每隔一分钟调用一次
                    boolean[] active = new boolean[]{false};
                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        Window window = WindowManager.getInstance().suggestParentWindow(project);
                        active[0] = window != null && window.isActive();
                    });
                    System.out.println("刷新app任务,"+project.getName()+"," + active[0] + "," + new Date());
                    if (active[0]) {
                        refreshVisibleApp(true);
                    }
                } catch (Throwable e) {
                    System.err.println("刷新app失败,"+project.getName());
                    e.printStackTrace(System.err);
                }
            }
        }).start();

        return outPanel;
    }

    private void fillDynamicDoc(DefaultActionGroup actionGroup) {

        //home
        Set<CodingGuidelinesHelper.Doc> homeDocs = new LinkedHashSet<>();
        Collection<CodingGuidelinesHelper.Doc> scopeDocs = CodingGuidelinesHelper.getScopeDocs(project);
        if (CollectionUtils.isNotEmpty(scopeDocs)) {
            homeDocs.addAll(scopeDocs);
        }
        List<CodingGuidelinesHelper.Doc> preSetDocs = CodingGuidelinesHelper.getHomeDocs(window.getProject());
        if (CollectionUtils.isNotEmpty(preSetDocs)) {
            homeDocs.addAll(preSetDocs);
        }
        if (!homeDocs.isEmpty()) {
            //pre-set
            DefaultActionGroup subActionGroup = new DefaultActionGroup("pre-set", true);
            for (CodingGuidelinesHelper.Doc value : homeDocs) {
                fillSubDoc(value, subActionGroup);
            }
            // 将二级菜单添加到主菜单
            actionGroup.add(subActionGroup);
        }

        //各个sources
        Map<CodingGuidelinesHelper.DocSource, Map<String, CodingGuidelinesHelper.Doc>> projectDocs = CodingGuidelinesHelper.getProjectDocs(project);
        if (projectDocs == null) {
            return;
        }
        for (CodingGuidelinesHelper.DocSource docSource : CodingGuidelinesHelper.DocSource.values()) {
            Map<String, CodingGuidelinesHelper.Doc> values = projectDocs.get(docSource);
            if (values == null || values.isEmpty()) {
                continue;
            }
            Collection<CodingGuidelinesHelper.Doc> values1 = values.values();
            DefaultActionGroup subActionGroup = new DefaultActionGroup(docSource.name().replace("_", "-"), true);
            for (CodingGuidelinesHelper.Doc doc : values1) {
                if (doc == null) {
                    continue;
                }
                fillSubDoc(doc, subActionGroup);

            }
            if (subActionGroup.getChildrenCount() > 0) {
                actionGroup.add(subActionGroup);
            }
        }
    }

    private static void fillSubDoc(CodingGuidelinesHelper.Doc preSetDoc, DefaultActionGroup subActionGroup) {
        if (preSetDoc == null) {
            return;
        }
        Icon icon = preSetDoc.getType() == CodingGuidelinesHelper.DocType.markdown ? CodingGuidelinesIconProvider.MARKDOWN_ICON : BROWSER_ICON;
        subActionGroup.add(new AnAction(preSetDoc.getTitle() == null ? "unknown" : preSetDoc.getTitle(), null, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                Application application = ApplicationManager.getApplication();
                application.invokeLater(() -> CodingGuidelinesHelper.navigateToDocumentation(preSetDoc));
            }
        });
    }


    protected JPanel createOutputPanel(Project project) {
        // 创建主面板
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputTextPane = new JTextPane();
        outputTextPane.setEditable(false);

        // 使用outputTextPane占用中心区域
        outputPanel.add(new JBScrollPane(outputTextPane), BorderLayout.CENTER);

        // 创建工具栏
        actionGroup = new DefaultActionGroup();
        AnAction copyAction = new AnAction("Copy output", "Copy output", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                // 调用复制功能
                TestkitHelper.copyToClipboard(project, outputTextPane.getText(), "Output was copied");
            }
        };
        actionGroup.add(copyAction);

        if (linkRenderHtml != null) {
            traceAction = new AnAction("Request visualization", "Request visualization", dagreIcon) {

                private JFrame frame;
                private JBCefBrowser jbCefBrowser;

                {
                    // 创建 JFrame
                    frame = new JFrame("Link Window");
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

                    // 计算70%窗口尺寸
                    int frameWidth = (int) (screenSize.getWidth() * 0.7);
                    int frameHeight = (int) (screenSize.getHeight() * 0.7);
                    // 设置窗口大小
                    frame.setSize(frameWidth, frameHeight);
                    // 计算居中位置
                    int x = (int) ((screenSize.getWidth() - frameWidth) / 2);
                    int y = (int) ((screenSize.getHeight() - frameHeight) / 2);
                    frame.setLocation(x, y);

                    frame.setResizable(true); // 允许用户调整窗口大小
                    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                    // 创建 JBCefBrowser 实例
                    jbCefBrowser = new JBCefBrowser();
                    jbCefBrowser.loadHTML(linkRenderHtml);

                    // 将 JBCefBrowser 的组件添加到 JFrame
                    frame.getContentPane().add(jbCefBrowser.getComponent(), BorderLayout.CENTER);

                    // 注册监听器来处理项目关闭事件并关闭窗口
                    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
                        @Override
                        public void projectClosing(@NotNull Project closingProject) {
                            if (closingProject.equals(project) && frame.isDisplayable()) {
                                frame.dispose();
                            }
                        }
                    });
                }


                @Override
                public void actionPerformed(AnActionEvent e) {
                    if (outputProfile == null) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Output profile is null.");
                        return;
                    }

                    // 获取新的内容作为示例（根据实际情况构建 render_str）
                    String renderStr = StringEscapeUtils.escapeJson(JSON.toJSONString(outputProfile));

                    // 页面加载成功之后执行 JavaScript
                    jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
                        @Override
                        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                            super.onLoadEnd(browser, frame, httpStatusCode);
                            browser.executeJavaScript("$('#source').val('" + renderStr + "');maulRender();", frame.getURL(), 0);
                        }
                    }, jbCefBrowser.getCefBrowser());

                    // 重新加载 HTML 内容，如果有必要
                    jbCefBrowser.loadHTML(linkRenderHtml);

                    // 设置窗口可见
                    SwingUtilities.invokeLater(() -> frame.setVisible(true));
                }

            };
        }


        ActionToolbar actionToolbar = new ActionToolbarImpl("OutputToolbar", actionGroup, false);
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.Y_AXIS));
        // 设置目标组件为此面板
        actionToolbar.setTargetComponent(outputTextPane);
//        actionToolbar.setTargetComponent(toolbarPanel);

        toolbarPanel.add(actionToolbar.getComponent());

        // 将工具栏添加到输出面板的左侧
        outputPanel.add(toolbarPanel, BorderLayout.WEST);
        return outputPanel;
    }

    /**
     * 刷新可见应用列表
     * @param skipRemote 是否跳过 remote 类型的连接探活
     */
    synchronized void refreshVisibleApp(boolean skipRemote) {
        Set<String> newItems = new LinkedHashSet<>();
        List<String> localItems = RuntimeHelper.loadProjectRuntimes(project.getName());
        if (localItems != null) {
            newItems.addAll(localItems);
        }
        List<String> tempApps = RuntimeHelper.getTempApps(project.getName());
        if (tempApps != null) {
            newItems.addAll(tempApps);
        }
        // 用于比较的 map，判断是否有变化
        HashMap<String, Boolean> newMap = new HashMap<>();
        HashMap<String, String> requestData = new HashMap<>();
        requestData.put("method", "hi");

        Iterator<String> iterator = newItems.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            if (item == null) {
                iterator.remove();
                continue;
            }

            // 获取 visibleApp 实例
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(item);

            // remote 类型的连接处理
            if (visibleApp.isRemoteScript()) {
                if (skipRemote) {
                    // 检查是否过期
                    if (RuntimeHelper.isConnectionExpired(item)) {
                        System.out.println("链接探活:remote 已过期，移除:" + item);
                        iterator.remove();
                        List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                        if (tempApps1.contains(item)) {
                            tempApps1.remove(item);
                            RuntimeHelper.setTempApps(project.getName(), tempApps1);
                        }
                        RuntimeHelper.removeConnectionMeta(item);  // 清理元数据
                        continue;
                    }
                    // 未过期，跳过探活，保留原有的 trace 状态
                    System.out.println("链接探活:跳过 remote 类型:" + item);
                    newMap.put(item, RuntimeHelper.isEnableTrace(item));
                    continue;
                }
                // 不跳过时，通过脚本探活
                try {
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isNotBlank(scriptPath)) {
                        JSONObject hiReq = new JSONObject();
                        hiReq.put("method", "hi");
                        JSONObject resp = RemoteScriptCallUtils.sendRequest(project, visibleApp, hiReq, 5);
                        if (resp.getBooleanValue("success")) {
                            boolean enableTrace = false;
                            JSONObject data = resp.getJSONObject("data");
                            if (data != null) {
                                enableTrace = data.getBooleanValue("enableTrace");
                            }
                            System.out.println("链接探活(remote):" + item + ",true," + enableTrace);
                            newMap.put(item, enableTrace);
                        } else {
                            throw new RuntimeException(resp.getString("message"));
                        }
                    } else {
                        // 没有配置脚本，保留原状态
                        newMap.put(item, RuntimeHelper.isEnableTrace(item));
                    }
                } catch (Exception e) {
                    System.out.println("链接探活(remote):" + item + ",false," + e.getMessage());
                    // 探活失败，从列表中移除
                    iterator.remove();
                    List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                    if (tempApps1.contains(item)) {
                        tempApps1.remove(item);
                        RuntimeHelper.setTempApps(project.getName(), tempApps1);
                    }
                }
                continue;
            }

            try {
                // 发送请求获取实时数据
                JSONObject response = HttpUtil.sendPost("http://" + (visibleApp.judgeIsLocal() ? "localhost" : visibleApp.getIp()) + ":" + visibleApp.getTestkitPort() + "/", requestData, JSONObject.class, null,null);
                JSONObject data = response.getJSONObject("data");
                boolean enableTrace = data.getBooleanValue("enableTrace");
                String env = data.getString("env");
                System.out.println("链接探活:"+item+",true,"+enableTrace+",env="+env);
                newMap.put(item, enableTrace);
                // 更新 env 到元数据（local/manual 连接不设过期时间，用 Long.MAX_VALUE）
                RuntimeHelper.updateConnectionMeta(item, enableTrace, env, Long.MAX_VALUE);
            }catch (Throwable e) {
                e.printStackTrace();
                System.out.println("链接探活:"+item+",false,");

                if(e instanceof ConnectException && e.getMessage()!=null && e.getMessage().contains("Connection refused") ) {
                    // Connection refused 表示服务已停止，从列表中移除
                    iterator.remove();
                    List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                    if (tempApps1.contains(item)) {
                        tempApps1.remove(item);
                        RuntimeHelper.setTempApps(project.getName(),tempApps1);
                    }
                    RuntimeHelper.removeApp(project.getName(), visibleApp);
                } else {
                    // 其他错误（超时等）暂时保留连接，加入临时列表
                    newMap.put(item, RuntimeHelper.isEnableTrace(item));  // 保留原有 trace 状态
                    List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                    if (!tempApps1.contains(item)) {
                        tempApps1.add(item);
                        RuntimeHelper.setTempApps(project.getName(),tempApps1);
                    }
                }
            }
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
// 记录当前选中的项
                String selectedItem = (String) appBox.getSelectedItem();

                // 获取当前下拉框中的所有项
                List<String> currentItems = new ArrayList<>();
                for (int i = 0; i < appBox.getItemCount(); i++) {
                    currentItems.add(appBox.getItemAt(i).toString());
                }

                // 更新 monitorMap
                RuntimeHelper.updateTraces(newMap);
                // 比较新旧项是否有变化
                boolean hasChanges = !(newItems.isEmpty() && currentItems.isEmpty()) && !new HashSet<>(newItems).containsAll(currentItems) || !new HashSet<>(currentItems).containsAll(newItems);
                if (!hasChanges) {
                    return;
                }
                // 更新下拉框内容
                appBox.removeAllItems();
                ArrayList<RuntimeHelper.VisibleApp> objects = new ArrayList<>();
                for (String item : newItems) {
                    objects.add(RuntimeHelper.parseApp(item));
                    appBox.addItem(item);
                }
                RuntimeHelper.updateVisibleApps(project.getName(), objects);

                // 保持选中项不变
                if (selectedItem != null && appBox.getItemCount() > 0) {
                    for (int i = 0; i < appBox.getItemCount(); i++) {
                        if (appBox.getItemAt(i).equals(selectedItem)) {
                            appBox.setSelectedIndex(i);
                            break;
                        }
                    }
                }

                //更新索引
                TestkitHelper.refresh(project);
                // 更新 ToolWindow 图标角标
                updateToolWindowIcon(newItems.size());
            }
        });
    }

    /**
     * 更新 ToolWindow 图标，当有连接的 RuntimeApp 时显示角标
     * @param connectedCount 已连接的 RuntimeApp 数量
     */
    public void updateToolWindowIcon(int connectedCount) {
        if (window == null) {
            return;
        }

        if (connectedCount > 0) {
            // 创建带角标的图标
            Icon badgeIcon = createBadgeIcon(TOOLWINDOW_ICON, connectedCount);
            window.setIcon(badgeIcon);
        } else {
            // 恢复原始图标
            window.setIcon(TOOLWINDOW_ICON);
        }
    }

    /**
     * 创建带数字角标的图标
     * @param baseIcon 基础图标
     * @param count 角标数字
     * @return 带角标的图标
     */
    static Icon createBadgeIcon(Icon baseIcon, int count) {
        return new com.intellij.openapi.util.ScalableIcon() {
            private float scale = 1.0f;
            private Icon scaledBaseIcon = baseIcon;

            @Override
            public float getScale() {
                return scale;
            }

            @Override
            public com.intellij.openapi.util.ScalableIcon scale(float scaleFactor) {
                this.scale = scaleFactor;
                // 如果基础图标也支持缩放，则同时缩放
                if (baseIcon instanceof com.intellij.openapi.util.ScalableIcon) {
                    this.scaledBaseIcon = ((com.intellij.openapi.util.ScalableIcon) baseIcon).scale(scaleFactor);
                }
                return this;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                // 绘制基础图标（使用可能已缩放的版本）
                scaledBaseIcon.paintIcon(c, g, x, y);

                // 绘制角标
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 角标位置（左上角）
                String text = count > 9 ? "9+" : String.valueOf(count);
                int badgeSize = count > 9 ? 10 : 8; // 9+ 时稍大一点
                int badgeX = x;
                int badgeY = y;

                // 绘制绿色圆形背景
                g2d.setColor(new Color(40, 167, 69)); // 绿色
                g2d.fillOval(badgeX, badgeY, badgeSize, badgeSize);

                // 绘制白色数字
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, count > 9 ? 6 : 7));
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getAscent();
                int textX = badgeX + (badgeSize - textWidth) / 2;
                int textY = badgeY + (badgeSize + textHeight) / 2 - 1;
                g2d.drawString(text, textX, textY);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return scaledBaseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return scaledBaseIcon.getIconHeight();
            }
        };
    }

    private void openTipsDoc() {
        String url = "https://github.com/wangsp188/product/blob/master/spring-testkit/spring-testkit.md";
        Object selectedItem = toolBox.getSelectedItem();

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.browse(new URI(url));
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                // 处理打开浏览器失败的情况
                Messages.showMessageDialog(project,
                        "Failed to open the document. Please check your system settings.",
                        "Error",
                        Messages.getErrorIcon());
            } catch (Exception e) {
                // 捕获其他可能的异常
                Messages.showMessageDialog(project,
                        "An unexpected error occurred.",
                        "Error",
                        Messages.getErrorIcon());
            }
        } else {
            Messages.showMessageDialog(project,
                    "Opening URLs in the browser is not supported on your system.",
                    "Unsupported Operation",
                    Messages.getWarningIcon());
        }
    }

    public void openCurlDialog() {
        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
            curlDialog.resizeDialog();
            curlDialog.setVisible(true);
        }
    }

    public void refreshSqlDatasources() {
        sqlDialog.refreshDatasources();
    }

    public void openSqlDialog() {
        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
            sqlDialog.resizeDialog();
            sqlDialog.setVisible(true);
        }
    }

    public void refreshMcpFunctions() {
        BasePluginTool basePluginTool = tools.get(PluginToolEnum.MCP_FUNCTION);
        basePluginTool.onSwitchAction(null);
    }

    public void openMcpServerDialog() {
        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
            //设置
            mcpServerDialog.refreshMcpJson();
            mcpServerDialog.resizeDialog();
            mcpServerDialog.setVisible(true);
        }
    }

    public void switchTool(PluginToolEnum tool, PsiElement element) {
        toolBox.setSelectedItem(tool.getCode());
        BasePluginTool pluginTool = tools.get(tool);
        pluginTool.onSwitchAction(element);
        window.show();
    }

    private void onSwitchTool() {
        // 获取当前选中的工具名称
        String selectedTool = (String) toolBox.getSelectedItem();

        // 从工具映射中获取相应的工具
        BasePluginTool tool = tools.get(PluginToolEnum.getByCode(selectedTool));
        if (tool == null) {
            whitePanel.setVisible(true);
            Messages.showMessageDialog(project,
                    "Failed to open the " + selectedTool + " panel",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }

        boolean showRuntimeApp = tool.needAppBox();
        appPanel.setVisible(showRuntimeApp);
        appBox.setVisible(showRuntimeApp);
        killButton.setVisible(showRuntimeApp); // kill 按钮只在显示 RuntimeApp 时显示
        whitePanel.setVisible(false);
        // 隐藏所有工具面板，并显示选中工具面板
        for (BasePluginTool t : tools.values()) {
            t.getPanel().setVisible(false);
        }

        // 显示当前选中的工具面板
        tool.getPanel().setVisible(true);
        // 更新工具切换按钮的图标
        tool.updateToolSwitchButtonIcon(PluginToolEnum.getByCode(selectedTool));
        // 刷新界面
        windowContent.revalidate();
        windowContent.repaint();
    }


    private void initSettingsDialog() {
        settingsDialog = new SettingsDialog(this);
        settingsButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                settingsDialog.visible();
            }
        });
    }

    public SettingsDialog getSettingsDialog() {
        return settingsDialog;
    }


    public void findSpringBootApplicationClasses() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            // 在后台线程中执行索引查询操作（慢操作）
            List<RuntimeHelper.AppMeta> apps = RuntimeHelper.getAppMetas(project.getName());
            if (apps == null || apps.isEmpty()) {
                apps = new ArrayList<>(TestkitHelper.findSpringBootClass(project).values());
            }
            
            // 将查询结果保存为 final 变量，传递到 EDT 线程
            final List<RuntimeHelper.AppMeta> finalApps = apps;
            
            // 当所有的操作完成后，更新UI或进一步处理结果
            // 用UI相关的操作需要放在EDT上执行
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    List<String> appNames = finalApps.stream().map(new Function<RuntimeHelper.AppMeta, String>() {
                        @Override
                        public String apply(RuntimeHelper.AppMeta appMeta) {
                            return appMeta.getApp();
                        }
                    }).collect(Collectors.toUnmodifiableList());
                    TestkitStoreWindowFactory.initApps(project,appNames);
                    settingsDialog.initApps(appNames);
//                    更新datasources
                    updateDatasources();
                    TestkitHelper.refresh(project);

                    //当前项目没有有没有配置
                    if (!SettingsStorageHelper.hasAnySettings()) {
                        TestkitHelper.notify(project, NotificationType.INFORMATION, "Welcome to Testkit\nYou can get started quickly with the documentation");
                    }
                }
            });
        });
    }

    private void updateDatasources() {
        List<SettingsStorageHelper.DatasourceConfig> datasourceConfigs = null;
        try {
            datasourceConfigs = SettingsStorageHelper.SqlConfig.parseDatasources(SettingsStorageHelper.getSqlConfig(window.getProject()).getProperties());
        } catch (Throwable ex) {
            return;
        }
        List<SettingsStorageHelper.DatasourceConfig> finalDatasourceConfigs = datasourceConfigs;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<SettingsStorageHelper.DatasourceConfig> valids = new ArrayList<>();
                List<String> ddls = new ArrayList<>();
                List<String> writes = new ArrayList<>();
                for (SettingsStorageHelper.DatasourceConfig config : finalDatasourceConfigs) {
                    // 测试连接
                    Object result = MysqlUtil.testConnectionAndClose(config);
                    if (result instanceof Integer) {
                        valids.add(config);
                        if (Objects.equals(result, 2)) {
                            ddls.add(config.getName());
                        } else if (Objects.equals(result, 1)) {
                            writes.add(config.getName());
                        }
                    }
                }
                RuntimeHelper.updateValidDatasources(window.getProject().getName(), valids, ddls, writes);
                refreshSqlDatasources();
            }
        }).start();
    }


    public JPanel getContent() {
        return windowContent;
    }

    public Project getProject() {
        return project;
    }

    public JComboBox<String> getToolBox() {
        return toolBox;
    }

    public void setOutputText(String outputText) {
        outputTextPane.setText(outputText);
    }

    public void setOutputProfile(List<Map<String, String>> outputProfile) {
        this.outputProfile = outputProfile;
        if (traceAction != null) {
//            System.out.println("不可见,"+ (outputProfile != null && !outputProfile.isEmpty()));
            if (outputProfile != null && !outputProfile.isEmpty() && !actionGroup.containsAction(traceAction)) {
                actionGroup.add(traceAction);
            } else if ((outputProfile == null || outputProfile.isEmpty()) && actionGroup.containsAction(traceAction)) {
                actionGroup.remove(traceAction);
            }

            SwingUtilities.invokeLater(() -> {
                windowContent.revalidate(); // 重新验证布局
                windowContent.repaint();
            });
        }
    }

    /**
     * 根据端口号查找并 kill 进程
     * @param port 端口号
     * @param appName 应用名称（用于日志）
     */
    public void killProcessByPort(int port, String appName) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Killing process on port " + port + "...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    // ★★★ 一条命令完成：查找端口进程 → 过滤掉 IDEA → 杀掉第一个匹配的进程 ★★★
                    String command = String.format(
                            "for pid in $(lsof -ti:%d -sTCP:LISTEN 2>/dev/null); do " +
                                    "ps -p $pid -o comm= 2>/dev/null | grep -qvi idea && kill -9 $pid 2>/dev/null && echo $pid && break; " +
                                    "done",
                            port
                    );

                    Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String killedPid = reader.readLine();
                    int exitCode = process.waitFor();

                    if (killedPid != null && !killedPid.trim().isEmpty()) {
                        String message = "Successfully killed process (PID: " + killedPid.trim() + ") using port " + port;
                        if (appName != null && !appName.isEmpty()) {
                            message += "\nApp: " + appName;
                        }
                        TestkitHelper.notify(project, NotificationType.INFORMATION, message);
                        // 刷新 app 列表
                        ApplicationManager.getApplication().invokeLater(() -> refreshVisibleApp(true));
                    } else {
                        TestkitHelper.notify(project, NotificationType.WARNING,
                                "No non-IDEA process found using port " + port);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    TestkitHelper.notify(project, NotificationType.ERROR,
                            "Error killing process on port " + port + ": " + e.getMessage());
                }
            }
        });
    }



    /**
     * 移除远程实例（仅从列表移除，不发送 stop 请求）
     * @param visibleApp 远程应用
     * @param connectionStr 连接字符串
     */
    private void removeRemoteInstance(RuntimeHelper.VisibleApp visibleApp, String connectionStr) {
        // 从 tempApps 中移除
        List<String> tempApps = RuntimeHelper.getTempApps(project.getName());
        tempApps.remove(connectionStr);
        RuntimeHelper.setTempApps(project.getName(), tempApps);

        // 从 RuntimeHelper 中移除
        RuntimeHelper.removeApp(project.getName(), visibleApp);

        // 刷新列表
        refreshVisibleApp(true);

        TestkitHelper.notify(project, NotificationType.INFORMATION,
                "Remote instance removed from list: " + visibleApp.getRemoteIp() + "\n(Remote service is not stopped, only disconnected from plugin)");
    }

    // ==================== Connection Config Popup ====================

    /**
     * 显示连接配置弹窗（合并 Remote Instance 和 Manual Configure）
     */
    private void showConnectionConfigPopup() {
        // 创建弹出框引用
        final JBPopup[] popupHolder = new JBPopup[1];

        // 创建合并面板
        JPanel mainPanel = createCombinedConnectionPanel(popupHolder);
        mainPanel.setPreferredSize(new Dimension(520, 420));

        // 创建弹出框
        JBPopupFactory popupFactory = JBPopupFactory.getInstance();
        JBPopup popup = popupFactory.createComponentPopupBuilder(mainPanel, mainPanel)
                .setRequestFocus(true)
                .setFocusable(true)
                .setTitle("Add Remote Connection")
                .setMovable(true)
                .setResizable(true)
                .createPopup();

        popupHolder[0] = popup;

        // 显示弹出框
        popup.show(new RelativePoint(appPanel, new Point(0, 0)));
    }

    /**
     * 创建合并的连接配置面板（Remote Instance + Manual Configure）
     */
    private JPanel createCombinedConnectionPanel(JBPopup[] popupHolder) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ==================== 上半部分：Remote Instance ====================
        JPanel remotePanel = new JPanel(new BorderLayout(5, 5));
        remotePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Remote Instance",
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP));

        // 脚本路径配置
        JPanel scriptPanel = new JPanel(new BorderLayout(5, 0));

        // 左侧：Info 按钮 + Script 标签
        JPanel scriptLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton scriptInfoBtn = new JButton(AllIcons.General.Information);
        scriptInfoBtn.setToolTipText("View example infra script");
        scriptInfoBtn.setPreferredSize(new Dimension(26, 22));
        scriptLeftPanel.add(scriptInfoBtn);
        scriptLeftPanel.add(new JLabel("Remote Script:"));
        scriptPanel.add(scriptLeftPanel, BorderLayout.WEST);

        String savedPath = SettingsStorageHelper.getRemoteScriptPath(project);
        String defaultPath = System.getProperty("user.home") + "/.ccitools/src/ccitools/scripts/testkit_cci_connector.groovy";
        JBTextField scriptPathField = new JBTextField(savedPath != null ? savedPath : defaultPath);
        scriptPathField.getEmptyText().setText("Groovy script path");
        scriptPanel.add(scriptPathField, BorderLayout.CENTER);

        JPanel scriptBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton saveBtn = new JButton(AllIcons.Actions.MenuSaveall);
        saveBtn.setToolTipText("Save path");
        saveBtn.setPreferredSize(new Dimension(26, 22));
        JButton viewBtn = new JButton(AllIcons.Actions.Preview);
        viewBtn.setToolTipText("View script");
        viewBtn.setPreferredSize(new Dimension(26, 22));
        scriptBtnPanel.add(saveBtn);
        scriptBtnPanel.add(viewBtn);
        scriptPanel.add(scriptBtnPanel, BorderLayout.EAST);

        // App/Partition 选择（Refresh 按钮放在最左边）
        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton refreshBtn = new JButton(AllIcons.Actions.Refresh);
        refreshBtn.setToolTipText("Refresh infra");
        refreshBtn.setPreferredSize(new Dimension(26, 24));
        selectPanel.add(refreshBtn);
        selectPanel.add(new JLabel("App:"));
        ComboBox<String> infraAppBox = new ComboBox<>();
        infraAppBox.setPreferredSize(new Dimension(120, 24));
        selectPanel.add(infraAppBox);
        selectPanel.add(new JLabel("Partition:"));
        ComboBox<String> partitionBox = new ComboBox<>();
        partitionBox.setPreferredSize(new Dimension(100, 24));
        selectPanel.add(partitionBox);
        JButton loadMachinesBtn = new JButton("Load", AllIcons.Actions.Search);
        loadMachinesBtn.setToolTipText("Load instances from remote script");
        loadMachinesBtn.setEnabled(false);
        selectPanel.add(loadMachinesBtn);

        JPanel remoteTopPanel = new JPanel(new BorderLayout(5, 3));
        remoteTopPanel.add(scriptPanel, BorderLayout.NORTH);
        remoteTopPanel.add(selectPanel, BorderLayout.CENTER);

        // 实例表格
        String[] columnNames = {"IP", "Port", "Env", "Status", "Action"};
        javax.swing.table.DefaultTableModel tableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;  // Action 列（IP=0, Port=1, Env=2, Status=3, Action=4）
            }
        };
        JTable instanceTable = new JTable(tableModel);
        instanceTable.setRowHeight(26);
        instanceTable.getColumnModel().getColumn(0).setPreferredWidth(140);  // IP
        instanceTable.getColumnModel().getColumn(1).setPreferredWidth(50);   // Port
        instanceTable.getColumnModel().getColumn(1).setMaxWidth(60);
        instanceTable.getColumnModel().getColumn(2).setPreferredWidth(60);   // Env
        instanceTable.getColumnModel().getColumn(2).setMaxWidth(80);
        instanceTable.getColumnModel().getColumn(3).setPreferredWidth(50);   // Status
        instanceTable.getColumnModel().getColumn(3).setMaxWidth(80);
        instanceTable.getColumnModel().getColumn(4).setPreferredWidth(65);   // Action
        instanceTable.getColumnModel().getColumn(4).setMaxWidth(70);

        List<RemoteScriptExecutor.InstanceInfo> instanceDataList = new ArrayList<>();

        // Status 列渲染器 - 支持 Tooltip 显示完整错误信息
        instanceTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (row < instanceDataList.size()) {
                    RemoteScriptExecutor.InstanceInfo inst = instanceDataList.get(row);
                    if (inst.isSuccess()) {
                        label.setText("✅");
                        label.setToolTipText("Connection OK");
                    } else {
                        label.setText("❌ Error");
                        label.setForeground(new Color(200, 100, 100));
                        String errorMsg = inst.getErrorMessage();
                        if (StringUtils.isNotBlank(errorMsg)) {
                            // 使用 HTML 格式，支持换行和更好的展示
                            String htmlTooltip = "<html><div style='width:300px;'><b>Error:</b><br>" +
                                    escapeHtml(errorMsg) + "</div></html>";
                            label.setToolTipText(htmlTooltip);
                        } else {
                            label.setToolTipText("Connection failed");
                        }
                    }
                }
                return label;
            }

            private String escapeHtml(String text) {
                return text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", "<br>");
            }
        });

        // 操作列渲染器
        instanceTable.getColumnModel().getColumn(4).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            if (row < instanceDataList.size() && instanceDataList.get(row).isSuccess()) {
                JButton btn = new JButton("Add", connectionIcon);
                btn.setToolTipText("Add to connections");
                btn.setHorizontalTextPosition(SwingConstants.RIGHT);
                btn.setIconTextGap(2);
                btn.setMargin(new Insets(0, 2, 0, 2));
                btn.setFont(btn.getFont().deriveFont(11f));
                return btn;
            }
            JLabel lbl = new JLabel("—");
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            lbl.setForeground(Color.GRAY);
            return lbl;
        });

        // 操作列编辑器
        instanceTable.getColumnModel().getColumn(4).setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()) {
            private JButton button = new JButton("Add", connectionIcon);
            private int currentRow = -1;
            {
                button.setHorizontalTextPosition(SwingConstants.RIGHT);
                button.setIconTextGap(2);
                button.setMargin(new Insets(0, 2, 0, 2));
                button.setFont(button.getFont().deriveFont(11f));
                button.addActionListener(e -> {
                    if (currentRow >= 0 && currentRow < instanceDataList.size()) {
                        RemoteScriptExecutor.InstanceInfo instance = instanceDataList.get(currentRow);
                        if (instance.isSuccess()) {
                            addInstanceToConnections(instance, popupHolder);
                        }
                    }
                    fireEditingStopped();
                });
            }
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                currentRow = row;
                return (row < instanceDataList.size() && instanceDataList.get(row).isSuccess()) ? button : new JLabel("—");
            }
            @Override
            public Object getCellEditorValue() { return ""; }
        });

        JBScrollPane tableScroll = new JBScrollPane(instanceTable);
        tableScroll.setPreferredSize(new Dimension(480, 120));

        JLabel statusLabel = new JLabel("Configure script to load remote machines");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        remotePanel.add(remoteTopPanel, BorderLayout.NORTH);
        remotePanel.add(tableScroll, BorderLayout.CENTER);
        remotePanel.add(statusLabel, BorderLayout.SOUTH);

        // 存储 infra 数据
        final Map<String, List<String>>[] infraDataHolder = new Map[]{null};

        // Load Infra Action
        Runnable loadInfraAction = () -> {
            String scriptPath = scriptPathField.getText().trim();
            if (StringUtils.isBlank(scriptPath)) return;
            SettingsStorageHelper.setRemoteScriptPath(project, scriptPath);

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading...", false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    try {
                        RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                        Map<String, List<String>> infraData = executor.loadInfra(LOAD_INFRA_TIMEOUT);

                        // 获取当前项目中的 app 列表
                        List<RuntimeHelper.AppMeta> projectApps = RuntimeHelper.getAppMetas(project.getName());
                        Set<String> projectAppNames = projectApps.stream()
                                .map(RuntimeHelper.AppMeta::getApp)
                                .collect(java.util.stream.Collectors.toSet());

                        // 过滤 infraData，只保留当前项目中存在的 app
                        Map<String, List<String>> filteredData = new LinkedHashMap<>();
                        int totalApps = infraData != null ? infraData.size() : 0;
                        int filteredCount = 0;
                        if (infraData != null) {
                            for (Map.Entry<String, List<String>> entry : infraData.entrySet()) {
                                if (projectAppNames.contains(entry.getKey())) {
                                    filteredData.put(entry.getKey(), entry.getValue());
                                } else {
                                    filteredCount++;
                                }
                            }
                        }
                        infraDataHolder[0] = filteredData;

                        final int finalFilteredCount = filteredCount;
                        final int finalTotalApps = totalApps;
                        SwingUtilities.invokeLater(() -> {
                            infraAppBox.removeAllItems();
                            partitionBox.removeAllItems();
                            tableModel.setRowCount(0);
                            instanceDataList.clear();
                            if (!filteredData.isEmpty()) {
                                filteredData.keySet().forEach(infraAppBox::addItem);
                                String statusText = "✅ " + filteredData.size() + " app(s) loaded";
                                if (finalFilteredCount > 0) {
                                    statusText += " (" + finalFilteredCount + "/" + finalTotalApps + " filtered out - not in project)";
                                }
                                statusLabel.setText(statusText);
                                statusLabel.setForeground(new Color(100, 150, 100));
                                loadMachinesBtn.setEnabled(true);
                            } else if (finalTotalApps > 0) {
                                statusLabel.setText("⚠️ " + finalTotalApps + " app(s) from script not found in project");
                                statusLabel.setForeground(new Color(200, 150, 50));
                            } else {
                                statusLabel.setText("⚠️ No data");
                                statusLabel.setForeground(new Color(200, 150, 50));
                            }
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("❌ " + ex.getMessage());
                            statusLabel.setForeground(new Color(200, 100, 100));
                        });
                    }
                }
            });
        };

        // 按钮事件
        saveBtn.addActionListener(e -> {
            String path = scriptPathField.getText().trim();
            if (StringUtils.isBlank(path)) {
                Messages.showWarningDialog(project, "Script path is empty", "Save Path");
                return;
            }
            SettingsStorageHelper.setRemoteScriptPath(project, path);
            loadInfraAction.run();
        });

        viewBtn.addActionListener(e -> {
            String path = scriptPathField.getText().trim();
            if (StringUtils.isBlank(path)) {
                Messages.showWarningDialog(project, "Script path is empty", "View Script");
                return;
            }
            File f = new File(path);
            if (!f.exists()) {
                Messages.showErrorDialog(project, "Script file not found: " + path, "View Script");
                return;
            }
            try {
                JTextArea ta = new JTextArea(java.nio.file.Files.readString(f.toPath()));
                ta.setEditable(false);
                ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                JBScrollPane sp = new JBScrollPane(ta);
                sp.setPreferredSize(new Dimension(600, 400));
                JBPopupFactory.getInstance().createComponentPopupBuilder(sp, ta)
                        .setTitle("Script: " + f.getName()).setMovable(true).setResizable(true)
                        .createPopup().showInFocusCenter();
            } catch (Exception ignored) {}
        });

        scriptInfoBtn.addActionListener(e -> {
            JTextArea ta = new JTextArea(REMOTE_SCRIPT_INFO);
            ta.setEditable(false);
            ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            ta.setCaretPosition(0);
            JBScrollPane sp = new JBScrollPane(ta);
            sp.setPreferredSize(new Dimension(750, 750));
            JBPopupFactory.getInstance().createComponentPopupBuilder(sp, ta)
                    .setTitle("Remote Script API Reference").setMovable(true).setResizable(true)
                    .createPopup().showInFocusCenter();
        });

        refreshBtn.addActionListener(e -> loadInfraAction.run());

        infraAppBox.addActionListener(e -> {
            String app = (String) infraAppBox.getSelectedItem();
            partitionBox.removeAllItems();
            tableModel.setRowCount(0);
            instanceDataList.clear();
            if (app != null && infraDataHolder[0] != null) {
                List<String> parts = infraDataHolder[0].get(app);
                if (parts != null) parts.forEach(partitionBox::addItem);
            }
        });

        loadMachinesBtn.addActionListener(e -> {
            String app = (String) infraAppBox.getSelectedItem();
            String part = (String) partitionBox.getSelectedItem();
            if (app == null || part == null) return;
            String scriptPath = scriptPathField.getText().trim();

            // 加载开始时立即清空表格，避免用户点击旧数据
            tableModel.setRowCount(0);
            instanceDataList.clear();
            statusLabel.setText("⏳ Loading...");
            statusLabel.setForeground(Color.GRAY);

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading instances...", false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    try {
                        List<RemoteScriptExecutor.InstanceInfo> instances = new RemoteScriptExecutor(scriptPath).loadInstances(app, part, LOAD_INSTANCES_TIMEOUT);
                        SwingUtilities.invokeLater(() -> {
                            if (instances != null && !instances.isEmpty()) {
                                for (RemoteScriptExecutor.InstanceInfo inst : instances) {
                                    instanceDataList.add(inst);
                                    // Env 为 null 时显示 "-"，Status 显示由渲染器处理
                                    String envDisplay = inst.getEnv() != null && !inst.getEnv().isEmpty() ? inst.getEnv() : "-";
                                    tableModel.addRow(new Object[]{inst.getIp(), inst.getPort(), envDisplay, "", ""});
                                }
                                long ok = instances.stream().filter(RemoteScriptExecutor.InstanceInfo::isSuccess).count();
                                statusLabel.setText("✅ " + instances.size() + " instances, " + ok + " available");
                                statusLabel.setForeground(new Color(100, 150, 100));
                            } else {
                                statusLabel.setText("⚠️ No instances");
                                statusLabel.setForeground(new Color(200, 150, 50));
                            }
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("❌ " + ex.getMessage());
                            statusLabel.setForeground(new Color(200, 100, 100));
                        });
                    }
                }
            });
        });

        // 自动加载
        if (StringUtils.isNotBlank(savedPath) && new File(savedPath).exists()) {
            SwingUtilities.invokeLater(loadInfraAction::run);
        }

        // ==================== 下半部分：Manual Configure ====================
        JPanel manualPanel = new JPanel(new BorderLayout(5, 5));
        manualPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Manual Configure",
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP));

        JPanel manualInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        List<RuntimeHelper.AppMeta> appMetas = RuntimeHelper.getAppMetas(project.getName());
        String[] appOptions = CollectionUtils.isEmpty(appMetas)
                ? new String[]{"(No apps)"}
                : appMetas.stream().map(RuntimeHelper.AppMeta::getApp).toArray(String[]::new);
        ComboBox<String> manualAppBox = new ComboBox<>(appOptions);
        manualAppBox.setPreferredSize(new Dimension(100, 24));

        JBTextField ipField = new JBTextField("localhost");
        ipField.setPreferredSize(new Dimension(100, 24));
        ipField.setToolTipText("IP address");

        JBTextField portField = new JBTextField("18080");
        portField.setPreferredSize(new Dimension(60, 24));
        portField.setToolTipText("Testkit port");

        JButton addManualBtn = new JButton("Add", connectionIcon);
        addManualBtn.setToolTipText("Test connection and add");

        JButton injectBtn = new JButton(AllIcons.General.Information);
        injectBtn.setToolTipText("Dynamic inject guide - for projects not started with Testkit");
        injectBtn.setPreferredSize(new Dimension(26, 24));

        manualInputPanel.add(injectBtn);
        manualInputPanel.add(new JLabel("App:"));
        manualInputPanel.add(manualAppBox);
        manualInputPanel.add(new JLabel("IP:"));
        manualInputPanel.add(ipField);
        manualInputPanel.add(new JLabel("Port:"));
        manualInputPanel.add(portField);
        manualInputPanel.add(addManualBtn);

        manualPanel.add(manualInputPanel, BorderLayout.CENTER);

        // Dynamic inject 按钮事件
        injectBtn.addActionListener(e -> {
            DefaultActionGroup copyGroup = new DefaultActionGroup();
            AnAction copyDirect = new AnAction("Step1: Copy CLI command to inject Testkit into running project",
                    "Copy and execute this command in terminal", AllIcons.Actions.Copy) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent ev) {
                    SettingsStorageHelper.CliConfig cliConfig = SettingsStorageHelper.getCliConfig(project);
                    StringBuilder command = new StringBuilder("java ");
                    String ctx = cliConfig.getCtx();
                    if (ctx != null && ctx.trim().split("#").length == 2) {
                        command.append("-Dtestkit.cli.ctx=").append(ctx.trim()).append(" ");
                    }
                    String envKey = cliConfig.getEnvKey();
                    if (envKey != null && !envKey.trim().isBlank()) {
                        command.append("-Dtestkit.env-key=").append(envKey.trim()).append(" ");
                    }
                    String cliPath = Paths.get(
                            PathManager.getPluginsPath(),
                            TestkitHelper.PLUGIN_ID,
                            "lib",
                            "testkit-cli-1.0.jar"
                    ).toFile().getAbsolutePath();
                    command.append("-jar \"").append(cliPath).append("\"");
                    TestkitHelper.copyToClipboard(project, command.toString(),
                            "CLI command copied!\nPaste and run in terminal to inject Testkit into running project.");
                }
            };
            copyGroup.add(copyDirect);

            AnAction infoAction = new AnAction("Step2: Fill in App/IP/Port from injection output, then click Add",
                    "After injection succeeds, fill in the connection info", AllIcons.Actions.Edit) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent ev) {
                    TestkitHelper.notify(project, NotificationType.INFORMATION,
                            "After injection succeeds:\n" +
                            "1. Check the output for App name and Port\n" +
                            "2. Fill in App, IP (localhost), Port above\n" +
                            "3. Click 'Add' to connect");
                }
            };
            copyGroup.add(infoAction);

            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance()
                    .createActionPopupMenu("DynamicInjectPopup", copyGroup).getComponent();
            popupMenu.show(injectBtn, 0, injectBtn.getHeight());
        });

        // Manual Add 按钮事件
        addManualBtn.addActionListener(e -> {
            String app = (String) manualAppBox.getSelectedItem();
            String ip = ipField.getText().trim();
            String port = portField.getText().trim();

            if (app == null || app.equals("(No apps)") || StringUtils.isBlank(ip) || !StringUtils.isNumeric(port)) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Please fill in all fields correctly");
                return;
            }

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Testing connection...", false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    String ipStr = ip.equals("localhost") || ip.equals("127.0.0.1") ? "local" : ip;
                    try {
                        HashMap<String, String> req = new HashMap<>();
                        req.put("method", "hi");
                        JSONObject resp = HttpUtil.sendPost("http://" + (ipStr.equals("local") ? "localhost" : ipStr) + ":" + port + "/", req, JSONObject.class, 5, 5);
                        String respApp = resp.getJSONObject("data").getString("app");
                        if (!Objects.equals(app, respApp)) {
                            TestkitHelper.notify(project, NotificationType.ERROR, "App mismatch: expected " + app + ", got " + respApp);
                            return;
                        }
                    } catch (Exception ex) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Connection failed: " + ex.getMessage());
                        return;
                    }

                    List<String> tempApps = RuntimeHelper.getTempApps(project.getName());
                    String connStr = app + ":" + ipStr + ":" + port;
                    if (tempApps.contains(connStr)) {
                        TestkitHelper.notify(project, NotificationType.WARNING, "Already exists: " + connStr);
                        return;
                    }
                    tempApps.add(connStr);
                    RuntimeHelper.setTempApps(project.getName(), tempApps);
                    TestkitHelper.notify(project, NotificationType.INFORMATION, "Added: " + connStr);

                    if (popupHolder[0] != null) {
                        SwingUtilities.invokeLater(() -> popupHolder[0].cancel());
                    }
                }
            });
        });

        // ==================== 组装 ====================
        panel.add(remotePanel, BorderLayout.CENTER);
        panel.add(manualPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 添加实例到连接列表
     */
    private void addInstanceToConnections(RemoteScriptExecutor.InstanceInfo instance, JBPopup[] popupHolder) {
        String connectionStr = instance.toConnectionString();

        // 检查 appBox 中是否已存在（包括 tempApps 和 localItems）
        for (int i = 0; i < appBox.getItemCount(); i++) {
            if (connectionStr.equals(appBox.getItemAt(i))) {
                TestkitHelper.notify(project, NotificationType.WARNING, "Already connected: " + connectionStr);
                return;
            }
        }

        List<String> tempApps = RuntimeHelper.getTempApps(project.getName());
        tempApps.add(connectionStr);
        RuntimeHelper.setTempApps(project.getName(), tempApps);

        // 更新连接元数据（trace、env、expireTime）
        RuntimeHelper.updateConnectionMeta(
            connectionStr,
            instance.isEnableTrace(),
            instance.getEnv(),
            instance.getExpireTime()
        );

        // 刷新 appBox
        refreshVisibleApp(true);

        TestkitHelper.notify(project, NotificationType.INFORMATION, "Added: " + connectionStr);
    }

}


