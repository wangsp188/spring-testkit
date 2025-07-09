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
import com.intellij.util.ui.JBUI;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.testkit.coding_guidelines.CodingGuidelinesIconProvider;
import com.testkit.sql_review.MysqlUtil;
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
import com.testkit.util.HttpUtil;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestkitToolWindow {

    public static final Icon BROWSER_ICON = IconLoader.getIcon("/icons/browser.svg", CodingGuidelinesIconProvider.class);
    private static final Icon settingsIcon = IconLoader.getIcon("/icons/settings.svg", TestkitToolWindow.class);
    private static final Icon dagreIcon = IconLoader.getIcon("/icons/trace.svg", TestkitToolWindow.class);
    private static final Icon cmdIcon = IconLoader.getIcon("/icons/cmd.svg", TestkitToolWindow.class);


    private Project project;
    private ToolWindow window;
    private JPanel windowContent;
    private JButton tipsButton;
    private JButton settingsButton;
    private JComboBox<String> toolBox;

    private JPanel appPanel;
    private JComboBox<String> appBox;

    private SettingsDialog settingsDialog;
    private ReqStoreDialog storeDialog;
    private CurlDialog curlDialog;
    private SqlDialog sqlDialog;
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
                actionGroup.add(new AnAction("Init coding-guidelines config file", null, CodingGuidelinesIconProvider.DOC_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        // 使用 Application.runWriteAction 进行写操作
                        Application application = ApplicationManager.getApplication();
                        application.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                CodingGuidelinesHelper.initDocDirectory(project);
                            }
                        });

                    }
                });
                fillDynamicDoc(actionGroup);
                JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("TipsPopup", actionGroup).getComponent();
                popupMenu.show(tipsButton, 0, tipsButton.getHeight());
            }
        });
        topPanel.add(tipsButton);

        curlDialog = new CurlDialog(this);
        sqlDialog = new SqlDialog(this);
//        下方用一个东西撑起来整个window的下半部分
//        当切换toolbox时根据选中的内容，从tools中找出对应的tool，然后用内部的内容填充该部分
//        初始化所有tool的面板，但是不加载
        tools.put(PluginToolEnum.FUNCTION_CALL, new FunctionCallTool(this));
        tools.put(PluginToolEnum.FLEXIBLE_TEST, new FlexibleTestTool(this));
        tools.put(PluginToolEnum.MAPPER_SQL, new MapperSqlTool(this));
        // 添加 toolBox 到 topPanel
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.FUNCTION_CALL.getCode(), PluginToolEnum.FLEXIBLE_TEST.getCode(), PluginToolEnum.MAPPER_SQL.getCode()});
        toolBox.setToolTipText("Tool list");
        toolBox.setPreferredSize(new Dimension(120, 32));
//        toolBox.setEnabled(false);
        toolBox.addActionListener(e -> onSwitchTool());
        topPanel.add(toolBox);


        appPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        ActionButton addBtn = new ActionButton(new AnAction("No automatic refresh or remote connection?", null, AllIcons.General.InlineAdd) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // 创建面板
                JPanel panel = new JPanel();
                panel.setLayout(new GridBagLayout());
                panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = JBUI.insets(5);

                List<RuntimeHelper.AppMeta> appMetas = RuntimeHelper.getAppMetas(project.getName());
                if (CollectionUtils.isEmpty(appMetas)) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "No app metas found, Pls wait index refresh");
                    return;
                }

                // 创建下拉框
                String[] options = appMetas.stream().map(RuntimeHelper.AppMeta::getApp).toArray(String[]::new);
                ComboBox<String> comboBox = new ComboBox<>(options);

                // 创建两个文本框
                JBTextField ipField = new JBTextField("localhost");
                ipField.getEmptyText().setText("Which ip connect to? Support remote connection, localhost is equivalent to 127.0.0.1");
                ipField.setEditable(true);
                ipField.setEnabled(true);
                ipField.setFocusable(true);
                JBTextField portField = new JBTextField("18080");
                portField.getEmptyText().setText("It is generally ${tomcat_port}+10000");
                portField.setEditable(true);
                portField.setEnabled(true);
                portField.setFocusable(true);

                // 创建提交按钮
                JButton injectButton = new JButton("Dynamic inject", cmdIcon);
                injectButton.setToolTipText("If the Testkit-server is not injected when the project starts, Then we provided the ability to dynamically inject runtime projects");
                JButton saveButton = new JButton("Test&Add connection", AllIcons.General.InlineAdd);
                saveButton.setToolTipText("Manual add the connection information of testkit-server, Support remote connection");

                // 添加组件到面板
                gbc.gridx = 0;
                gbc.gridy = 0;
                JLabel appLabel = new JLabel("App:");
                appLabel.setLabelFor(comboBox);
                appLabel.setToolTipText("Which app domain you want connect to");
                panel.add(appLabel, gbc);
                gbc.gridx = 1;
                panel.add(comboBox, gbc);

                gbc.gridx = 0;
                gbc.gridy = 1;
                JLabel ipLabel = new JLabel("Ip:");
                ipLabel.setToolTipText("Which ip you want connect to, Support remote connection, localhost is equivalent to 127.0.0.1");
                panel.add(ipLabel, gbc);
                gbc.gridx = 1;
                panel.add(ipField, gbc);

                gbc.gridx = 0;
                gbc.gridy = 2;
                JLabel portLabel = new JLabel("Testkit Port:");
                portLabel.setToolTipText("The port occupied by testkit startup is generally the project tomcat port +10000");
                panel.add(portLabel, gbc);
                gbc.gridx = 1;
                panel.add(portField, gbc);

                gbc.gridx = 0;
                gbc.gridy = 3;
//                gbc.gridwidth = 2;
                panel.add(injectButton, gbc);
                gbc.gridx = 1;
                panel.add(saveButton, gbc);

                // 创建弹出框
                JBPopupFactory popupFactory = JBPopupFactory.getInstance();
                JBPopup popup = popupFactory.createComponentPopupBuilder(panel, portField)
                        .setRequestFocus(true)
                        .setFocusable(true)
                        .setTitle("Manual configure the connector")
                        .setMovable(true)
                        .setResizable(false)
                        .createPopup();


                // 添加提交按钮事件
                injectButton.addActionListener(actionEvent -> {
                    //copy
                    DefaultActionGroup copyGroup = new DefaultActionGroup();
                    //显示的一个图标加上标题
                    AnAction copyDirect = new AnAction("Step1: Click here to Copy and execute this cmd, According to the guided injection capacity", "Step1: Click here to Copy and execute this cmd, According to the guided injection capacity", AllIcons.Actions.Copy) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            SettingsStorageHelper.CliConfig cliConfig = SettingsStorageHelper.getCliConfig(project);
                            StringBuilder command = new StringBuilder("java ");
                            String ctx = cliConfig.getCtx();
                            if (ctx != null && ctx.trim().split("#").length == 2) {
                                command.append("-Dtestkit.cli.ctx=").append(ctx.trim()).append(" ");
                            }
                            String envKey = cliConfig.getEnvKey();
                            if (envKey != null && !envKey.trim().isBlank()) {
                                command.append("-Dtestkit.cli.env-key=").append(envKey.trim()).append(" ");
                            }

                            String cliPath = Paths.get(
                                    PathManager.getPluginsPath(),
                                    TestkitHelper.PLUGIN_ID,
                                    "lib",
                                    "testkit-cli-1.0.jar"
                            ).toFile().getAbsolutePath();
                            command.append("-jar \"" + cliPath + "\"");
                            TestkitHelper.copyToClipboard(project, command.toString(), "CMD copy success\nYou can run this in terminal to  dynamic inject plugin.");
                        }
                    };
                    copyGroup.add(copyDirect); // 将动作添加到动作组中

                    //显示的一个图标加上标题
                    AnAction infoAction = new AnAction("Step2: Fill in the information of successful injection in the form on the right and \"Add connection\"", "Step2: Fill in the information of successful injection in the form on the right and \"Add connection\"", AllIcons.Actions.Edit) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            TestkitHelper.notify(project, NotificationType.INFORMATION, "After the injection is successful\nplease fill it in manually according to the injection information");
                        }
                    };
                    copyGroup.add(infoAction); // 将动作添加到动作组中

                    JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("CopyFunctionCallPopup", copyGroup).getComponent();
                    popupMenu.show(injectButton, 0, 0);
                });

                // 添加提交按钮事件
                saveButton.addActionListener(actionEvent -> {
                    String selectedApp = (String) comboBox.getSelectedItem();
                    String ipStr = ipField.getText().trim();
                    String portStr = portField.getText().trim();
                    if (selectedApp == null) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Select app to connect to");
                        return;
                    }
                    if (StringUtils.isBlank(ipStr) || !StringUtils.isNumeric(portStr)) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "host and port must be not null, port must be Numeric");
                        return;
                    }

                    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing test connection " + ipStr + ":" + portStr + ", please wait ...", false) {
                        @Override
                        public void run(ProgressIndicator indicator) {
                            String ip = ipStr.equals("localhost") || ipStr.equals("127.0.0.1") ? "local" : ipStr;
                            try {
                                HashMap<String, String> requestData = new HashMap<>();
                                requestData.put("method", "hi");
                                // 发送请求获取实时数据
                                JSONObject response = HttpUtil.sendPost("http://" + (ip.equals("local") ? "localhost" : ip) + ":" + portStr + "/", requestData, JSONObject.class, 5,5);
                                String app = response.getJSONObject("data").getString("app");
                                if (!Objects.equals(selectedApp, app)) {
                                    TestkitHelper.notify(project, NotificationType.ERROR, "Connected to " + ip + ":" + portStr + " success<br>but app not match, expect is " + selectedApp + ", got " + app);
                                    return;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                TestkitHelper.notify(project, NotificationType.ERROR, "Connected to " + selectedApp + ":" + ip + ":" + portStr + " failed<br>" + e.getMessage());
                                return;
                            }
                            List<String> tempApps = RuntimeHelper.getTempApps(project.getName());
                            if (tempApps.contains(selectedApp + ":" + ip + ":" + portStr)) {
                                TestkitHelper.notify(project, NotificationType.WARNING, selectedApp + ":" + ip + ":" + portStr + "already exists in " + tempApps);
                                return;
                            }
                            tempApps.add(selectedApp + ":" + ip + ":" + portStr);
                            RuntimeHelper.setTempApps(project.getName(), tempApps);
                            TestkitHelper.notify(project, NotificationType.INFORMATION, "Add connection success<br>" + selectedApp + ":" + ip + ":" + portStr);
                            //关闭弹窗
                            SwingUtilities.invokeLater(() -> {
                                popup.cancel();
                            });
                        }
                    });

                });

                // 显示弹出框
                popup.show(new RelativePoint(appPanel, new Point(0, 0)));
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
        JPanel rightAppPanel = new JPanel(new BorderLayout(2, 2));

        rightAppPanel.add(appPanel, BorderLayout.WEST);
        // 设置扩展策略
        rightAppPanel.add(appBox, BorderLayout.CENTER);

        // 将右侧面板添加到主面板东侧
        outPanel.add(rightAppPanel, BorderLayout.CENTER);

        //        topPanel.add(appBox);

        initSettingsDialog();
        initStoreDialog();
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
                        refreshVisibleApp();
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

    public void visibleStoreDialog() {
        storeDialog.visible(true);
    }

    private void refreshVisibleApp() {
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
            try {
                // 发送请求获取实时数据
                JSONObject response = HttpUtil.sendPost("http://" + (visibleApp.judgeIsLocal() ? "localhost" : visibleApp.getIp()) + ":" + visibleApp.getTestkitPort() + "/", requestData, JSONObject.class, null,null);
                boolean enableTrace = response.getJSONObject("data").getBooleanValue("enableTrace");
                System.out.println("链接探活:"+item+",true,"+enableTrace);
                newMap.put(item, enableTrace);
            }catch (Throwable e) {
                e.printStackTrace();
                iterator.remove();
                System.out.println("链接探活:"+item+",false,");

                if(e instanceof ConnectException && e.getMessage()!=null && e.getMessage().contains("Connection refused") ) {
                    //将这个加入到临时的链接集里
                    List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                    if (tempApps1.contains(item)) {
                        tempApps1.remove(item);
                        RuntimeHelper.setTempApps(project.getName(),tempApps1);
                    }
                }else{
                    //将这个加入到临时的链接集里
                    List<String> tempApps1 = RuntimeHelper.getTempApps(project.getName());
                    if (!tempApps1.contains(item)) {
                        tempApps1.add(item);
                        RuntimeHelper.setTempApps(project.getName(),tempApps1);
                    }
                }
                RuntimeHelper.removeApp(project.getName(), visibleApp);
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
            }
        });
    }

    private void openTipsDoc() {
        String url = "https://github.com/wangsp188/spring-testkit/blob/master/how-to-use/spring-testkit.md";
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

        appPanel.setVisible(tool.needAppBox());
        appBox.setVisible(tool.needAppBox());
        whitePanel.setVisible(false);
        // 隐藏所有工具面板，并显示选中工具面板
        for (BasePluginTool t : tools.values()) {
            t.getPanel().setVisible(false);
        }

        // 显示当前选中的工具面板
        tool.getPanel().setVisible(true);
        // 刷新界面
        windowContent.revalidate();
        windowContent.repaint();
    }


    private void initStoreDialog() {
        storeDialog = new ReqStoreDialog(this);
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


    public void findSpringBootApplicationClasses() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {

            // 当所有的操作完成后，更新UI或进一步处理结果
            // 用UI相关的操作需要放在EDT上执行
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {

                    List<RuntimeHelper.AppMeta> apps = RuntimeHelper.getAppMetas(project.getName());
                    if (apps == null || apps.isEmpty()) {
                        apps = new ArrayList<>(TestkitHelper.findSpringBootClass(project).values());
                    }
                    List<String> appNames = apps.stream().map(new Function<RuntimeHelper.AppMeta, String>() {
                        @Override
                        public String apply(RuntimeHelper.AppMeta appMeta) {
                            return appMeta.getApp();
                        }
                    }).collect(Collectors.toUnmodifiableList());
                    storeDialog.initApps(appNames);
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


    public void refreshStore() {
        storeDialog.refreshTree();
    }


}


