package com.fling.view;

import com.alibaba.fastjson.JSON;
import com.fling.RuntimeAppHelper;
import com.fling.tools.CurlDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.LanguageTextField;
import com.fling.FlingHelper;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.fling.tools.call_method.CallMethodTool;
import com.fling.tools.flexible_test.FlexibleTestTool;
import com.fling.tools.mybatis_sql.MybatisSqlTool;
import com.fling.tools.spring_cache.SpringCacheTool;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FlingToolWindow {

    private static final Icon settingsIcon = IconLoader.getIcon("/icons/settings.svg", BasePluginTool.class);
    private static final Icon dagreIcon = IconLoader.getIcon("/icons/dagre.svg", BasePluginTool.class);

    public static final String PROJECT_DEFAULT = "project:default";


    private Project project;
    private ToolWindow toolWindow;
    private JPanel windowContent;
    private JButton tipsButton;
    private JButton settingsButton;
    private JComboBox<String> toolBox;

    private JLabel appLabel;
    private JComboBox<String> appBox;
    private Map<String, Boolean> monitorMap;
    private JDialog settingsDialog;
    private CurlDialog curlDialog;
    private JPanel whitePanel = new JPanel();
    private Map<PluginToolEnum, BasePluginTool> tools = new HashMap<>();

    private JComboBox<String> scriptAppBox;
    private JComboBox<String> propertiesAppBox;

    protected JTextPane outputTextPane;

    private static String linkRenderHtml;
    protected DefaultActionGroup actionGroup;
    protected AnAction dagreAction;
    protected List<Map<String, String>> outputProfile;


    static {
        URL resource = FlingToolWindow.class.getResource("/html/horse_up_down_class.html");
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

    public FlingToolWindow(Project project, ToolWindow toolWindow) {

        this.project = project;
        this.toolWindow = toolWindow;
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

    public BasePluginTool getNowTool() {
        Object selectedItem = toolBox.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        PluginToolEnum byCode = PluginToolEnum.getByCode(selectedItem.toString());
        return byCode == null ? null : tools.get(byCode);
    }

    private JPanel buildHeaderPanel() {
        // 创建一个新的 JPanel 用于存放第一行的组件
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5)); // 使用 FlowLayout 确保组件的水平排列和固定间距

        // 添加 settingsButton 到 topPanel
        settingsButton = new JButton(settingsIcon);
        settingsButton.setPreferredSize(new Dimension(32, 32));
        settingsButton.setToolTipText("Settings");
        topPanel.add(settingsButton);

        // 添加 tipsButton 到 topPanel
        tipsButton = new JButton(AllIcons.General.Information);
        tipsButton.setToolTipText("how to use?");
        tipsButton.setPreferredSize(new Dimension(32, 32));
        tipsButton.setText(null);
        tipsButton.addActionListener(e -> openTipsDoc());
        topPanel.add(tipsButton);

        curlDialog = new CurlDialog(this);

        // 居中显示

//        下方用一个东西撑起来整个window的下半部分
//        当切换toolbox时根据选中的内容，从tools中找出对应的tool，然后用内部的内容填充该部分
//        初始化所有tool的面板，但是不加载
        tools.put(PluginToolEnum.CALL_METHOD, new CallMethodTool(this));
        tools.put(PluginToolEnum.FLEXIBLE_TEST, new FlexibleTestTool(this));
        tools.put(PluginToolEnum.SPRING_CACHE, new SpringCacheTool(this));
        tools.put(PluginToolEnum.MYBATIS_SQL, new MybatisSqlTool(this));
        // 添加 toolBox 到 topPanel
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.CALL_METHOD.getCode(), PluginToolEnum.SPRING_CACHE.getCode(), PluginToolEnum.FLEXIBLE_TEST.getCode(), PluginToolEnum.MYBATIS_SQL.getCode()});
        toolBox.setPreferredSize(new Dimension(120, 32));
//        toolBox.setEnabled(false);
        toolBox.addActionListener(e -> onSwitchTool());
        topPanel.add(toolBox);


        // 添加 VisibleApp Label
        appLabel = new JLabel("RuntimeApp:");
        topPanel.add(appLabel);

        // 添加 appBox 到 topPanel
        appBox = new ComboBox<>(new String[]{});
        appBox.setPreferredSize(new Dimension(150, 30));
        Border border = appBox.getBorder();
        appBox.addItemListener(e -> {
            Object selectedItem = appBox.getSelectedItem();
            appBox.setToolTipText(selectedItem == null ? "" : selectedItem.toString()); // 动态更新 ToolTipText

            if (selectedItem != null && monitorMap != null && Boolean.TRUE.equals(monitorMap.get(selectedItem.toString()))) {
                appBox.setBorder(new LineBorder(new Color(114, 169, 107), 1));
            } else {
                appBox.setBorder(border);
            }
        });
        new Thread(() -> {
            while (true) {
                try {
                    refreshVisibleApp();
                    Thread.sleep(3 * 1000); // 每隔一分钟调用一次
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }).start();
        topPanel.add(appBox);

        initSettingsDialog();
        return topPanel;
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
        AnAction copyAction = new AnAction("Copy output to clipboard", "Copy output to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                // 调用复制功能
                FlingHelper.copyToClipboard(project, outputTextPane.getText(), "Output is copied");
            }
        };
        actionGroup.add(copyAction);

        if (linkRenderHtml != null) {
            dagreAction = new AnAction("graph this req", "Dagre this req", dagreIcon) {

                private JFrame frame;
                private JBCefBrowser jbCefBrowser;

                {
                    // 创建 JFrame
                    frame = new JFrame("Link Window");
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    frame.setSize(screenSize);
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
                        FlingHelper.notify(project, NotificationType.ERROR, "Output profile is null.");
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
        actionToolbar.setTargetComponent(toolbarPanel);
        toolbarPanel.add(actionToolbar.getComponent());

        // 将工具栏添加到输出面板的左侧
        outputPanel.add(toolbarPanel, BorderLayout.WEST);
        return outputPanel;
    }

    private void refreshVisibleApp() {
        List<String> items = RuntimeAppHelper.loadProjectRuntimes(project.getName());
        if (items == null) {
            return;
        }
        HashMap<String, Boolean> map2 = new HashMap<>();
        Iterator<String> iterator = items.iterator();
        HashMap<String, String> map = new HashMap<>();
        map.put("method", "hello");
        while (iterator.hasNext()) {
            String item = iterator.next();
            if (item == null) {
                iterator.remove();
                continue;
            }
            VisibleApp visibleApp = parseApp(item);
            try {
                Map map1 = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", map, Map.class);
                map2.put(item.substring(0, item.lastIndexOf(":")), "true".equals(String.valueOf(map1.get("data"))));
            } catch (Exception e) {
                e.printStackTrace();
                iterator.remove();
                RuntimeAppHelper.removeApp(project.getName(), visibleApp.getAppName(), visibleApp.getPort(), visibleApp.getSidePort());
            }
        }

        // 清理 appBox 的内容，并使用 newStrings 重新赋值
        appBox.removeAllItems();
        for (String item : items) {
            appBox.addItem(item.substring(0, item.lastIndexOf(":")));
        }
        monitorMap = map2;
    }

    private void openTipsDoc() {
        String url = "https://gitee.com/wangsp188/spring-fling/blob/master/doc/Spring-Fling.md";
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
        curlDialog.setVisible(true);
    }


    public void switchTool(PluginToolEnum tool, PsiElement element) {
        toolBox.setSelectedItem(tool.getCode());
        BasePluginTool pluginTool = tools.get(tool);
        pluginTool.onSwitchAction(element);
        toolWindow.show();
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

        appLabel.setVisible(tool.needAppBox());
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


    private @Nullable FlingToolWindow.VisibleApp parseApp(String selectedItem) {
        String[] split = selectedItem.split(":");
        if (split.length == 2) {
            VisibleApp visibleApp = new VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[1]) + 10000);
            return visibleApp;

        } else if (split.length == 3) {
            VisibleApp visibleApp = new VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[2]));
            return visibleApp;
        }
        throw new IllegalArgumentException("un support app item");
    }


    private void initSettingsDialog() {
        JTextField flexibleTestPackageNameField = new JTextField(LocalStorageHelper.getFlexibleTestPackage(project), 20);

        settingsDialog = new JDialog((Frame) null, FlingHelper.getPluginName() + " Settings", true);
        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 左侧选项列表
        String[] options = {"fling", "script", "spring-properties"};
        JBList<String> optionList = new JBList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 默认选中第一个选项
        optionList.setSelectedIndex(0);
        // 右侧内容显示区域
        JPanel rightContent = new JPanel(new CardLayout());
        rightContent.add(createBasicOptionPanel(flexibleTestPackageNameField), "fling");
        rightContent.add(createScriptOptionPanel(), "script");
        rightContent.add(createPropertiesOptionPanel(), "spring-properties");
        DumbService.getInstance(project).smartInvokeLater(() -> {
            // 事件或代码在索引准备好后运行
            findSpringBootApplicationClasses();
        });
        // 监听选项改变，更新右侧内容
        optionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CardLayout cl = (CardLayout) (rightContent.getLayout());
                cl.show(rightContent, optionList.getSelectedValue());
            }
        });

        // SplitPane 分割左右
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JBScrollPane(optionList), rightContent);
//        splitPane.setDividerLocation(150); // 设置初始分割条位置
        contentPanel.add(splitPane, BorderLayout.CENTER);
        // 去掉分隔条
        splitPane.setDividerSize(0); // 隐藏分隔条
        splitPane.setOneTouchExpandable(false); // 禁用单触展开
        splitPane.setDividerLocation(150); // 设置初始分割条位置

        // 设置对话框内容
        settingsDialog.setContentPane(contentPanel);

        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        settingsDialog.setSize(screenSize.width / 2, screenSize.height / 2);
        settingsDialog.setLocationRelativeTo(null); // 居中显示
        // 添加按钮点击事件以显示设置窗口
        settingsButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flexibleTestPackageNameField.setText(LocalStorageHelper.getFlexibleTestPackage(project));
                scriptAppBox.setSelectedIndex(0);
                if (propertiesAppBox.getItemCount() > 0) {
                    // 选择第一个选项
                    propertiesAppBox.setSelectedIndex(0);
                }
                settingsDialog.setVisible(true);
            }
        });
    }
    private JPanel createBasicOptionPanel(JTextField flexibleTestPackageNameField) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局
        Dimension labelDimension = new Dimension(100, 20);

        // 输入框
        JLabel packageNameLabel = new JLabel("Test Package:");
        packageNameLabel.setPreferredSize(labelDimension);
        packageNameLabel.setLabelFor(flexibleTestPackageNameField); // 关联标签和输入框
        packageNameLabel.setToolTipText("Which package do you want to enable flexible test?"); // 提示信息

        // 布局输入框和标签
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(packageNameLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // 让输入框占据剩余空间
        panel.add(flexibleTestPackageNameField, gbc);

        // 添加分割线
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0); // 上下方间距稍大一些
        panel.add(new JSeparator(), gbc);

        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(project);


        JRadioButton monitorToggleButton = new JRadioButton("Enable Monitor", false);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0; // Reset weightx
        panel.add(monitorToggleButton, gbc);

        JPanel monitorOptionsPanel = new JPanel(new GridBagLayout());
        monitorOptionsPanel.setVisible(false);
        monitorToggleButton.addActionListener(e -> {
            monitorOptionsPanel.setVisible(monitorToggleButton.isSelected());
            if (monitorToggleButton.isSelected()) {
                monitorToggleButton.setText("We will use bytecode staking to enhance the classes for link monitoring");
            } else {
                monitorToggleButton.setText("Enable Monitor");
            }
        });

        if (monitorConfig.isEnable()) {
            monitorToggleButton.setSelected(true);
            monitorOptionsPanel.setVisible(true);
        }
        gbc.gridwidth = 1; // Reset gridwidth


        JLabel monitorWebLabel = new JLabel("Monitor Web:");
        monitorWebLabel.setToolTipText("We can monitor the DispatcherServlet for spring-web");
        monitorWebLabel.setPreferredSize(labelDimension);
        JRadioButton monitorWeb = new JRadioButton("", false);
        monitorWeb.addActionListener(e -> {
            if (monitorWeb.isSelected()) {
                monitorWeb.setText("We will monitor the DispatcherServlet for spring-web");
            } else {
                monitorWeb.setText("");
            }
        });
        if (monitorConfig.isMonitorWeb()) {
            monitorWeb.setSelected(true);
            monitorWeb.setText("We will monitor the DispatcherServlet for spring-web");
        }
//        monitorPrivate.setSelected(monitorConfig.isMonitorPrivate());
//        JTextField functionTypeField = new JTextField("private,public,protected", 20);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(monitorWebLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(monitorWeb, gbc);


        JLabel monitorMybatisLabel = new JLabel("Monitor Mybatis:");
        monitorMybatisLabel.setToolTipText("We can use the mybatis Interceptor mechanism to monitor Executor");
        monitorMybatisLabel.setPreferredSize(labelDimension);
        JRadioButton monitorMybatis = new JRadioButton("", false);
        monitorMybatis.addActionListener(e -> {
            if (monitorMybatis.isSelected()) {
                monitorMybatis.setText("We will use the mybatis Interceptor mechanism to monitor Executor");
            } else {
                monitorMybatis.setText("");
            }
        });
        if (monitorConfig.isMonitorMybatis()) {
            monitorMybatis.setSelected(true);
            monitorMybatis.setText("We will use the mybatis Interceptor mechanism to monitor Executor");
        }
//        monitorPrivate.setSelected(monitorConfig.isMonitorPrivate());
//        JTextField functionTypeField = new JTextField("private,public,protected", 20);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(monitorMybatisLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(monitorMybatis, gbc);

        JLabel logMybatisLabel = new JLabel("Log Mybatis:");
        logMybatisLabel.setToolTipText("We can output sql in mybatis format");
        logMybatisLabel.setPreferredSize(labelDimension);
        JRadioButton logMybatis = new JRadioButton("", false);
        logMybatis.addActionListener(e -> {
            if (logMybatis.isSelected()) {
                logMybatis.setText("We will output sql in mybatis format");
            } else {
                logMybatis.setText("");
            }
        });
        if (monitorConfig.isLogMybatis()) {
            logMybatis.setSelected(true);
            logMybatis.setText("We will output sql in mybatis format");
        }
//        monitorPrivate.setSelected(monitorConfig.isMonitorPrivate());
//        JTextField functionTypeField = new JTextField("private,public,protected", 20);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(logMybatisLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(logMybatis, gbc);

        JLabel packageLabel = new JLabel("Package:");
        packageLabel.setToolTipText("Please fill in the package name of the class you want to monitor, multiple use, split;\napp.three.package means that the first three packages of the startup class are automatically intercepted");
        packageLabel.setPreferredSize(labelDimension);
        JTextField packagesField = new JTextField(monitorConfig.getPackages(), 20);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(packageLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(packagesField, gbc);

        JLabel classSuffixLabel = new JLabel("Class Suffix:");
        classSuffixLabel.setToolTipText("Please fill in the class suffix of the class you want to monitor, multiple use, split");
        classSuffixLabel.setPreferredSize(labelDimension);
        JTextField classSuffixField = new JTextField(monitorConfig.getClsSuffix(), 20);
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(classSuffixLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(classSuffixField, gbc);


        JLabel whiteClassLabel = new JLabel("White Class:");
        whiteClassLabel.setToolTipText("Monitoring class whitelist list, multiple use, split");
        whiteClassLabel.setPreferredSize(labelDimension);
        JTextField whiteListField = new JTextField(monitorConfig.getWhites(), 20);
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(whiteClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(whiteListField, gbc);

        JLabel blackClassLabel = new JLabel("Black Class:");
        blackClassLabel.setToolTipText("Monitoring class black list, multiple use, split");
        blackClassLabel.setPreferredSize(labelDimension);
        JTextField blackListField = new JTextField(monitorConfig.getBlacks(), 20);
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(blackClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(blackListField, gbc);


        JLabel monitorPrivateLabel = new JLabel("Method Type:");
        monitorPrivateLabel.setToolTipText("You can choose which methods of the class to monitor, this config support hot reload");
        monitorPrivateLabel.setPreferredSize(labelDimension);
        JRadioButton monitorPrivate = new JRadioButton("We will monitor the public and protected methods of the selected class", false);
        monitorPrivate.addActionListener(e -> {
            if (monitorPrivate.isSelected()) {
                monitorPrivate.setText("We will monitor the all methods of the selected class");
            } else {
                monitorPrivate.setText("We will monitor the public and protected methods of the selected class");
            }
        });
        if (monitorConfig.isMonitorPrivate()) {
            monitorPrivate.setSelected(true);
            monitorPrivate.setText("We will monitor the all methods of the selected class");
        }
//        monitorPrivate.setSelected(monitorConfig.isMonitorPrivate());
//        JTextField functionTypeField = new JTextField("private,public,protected", 20);
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(monitorPrivateLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(monitorPrivate, gbc);


        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(monitorOptionsPanel, gbc);


        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 4; // 新的一行
        gbc.gridwidth = 2; // 占据两列
        gbc.weighty = 1.0; // 占用剩余空间
        gbc.fill = GridBagConstraints.BOTH; // 使下一行占用空间
        panel.add(Box.createVerticalStrut(0), gbc); // 添加空白占位符，以便下一行显示正确

        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String packageName = flexibleTestPackageNameField.getText();
                if (StringUtils.isBlank(packageName)) {
                    LocalStorageHelper.setFlexibleTestPackage(project, null);
                    FlingHelper.notify(project, NotificationType.INFORMATION, "Package name is blank, use default package name");
                } else {
                    LocalStorageHelper.setFlexibleTestPackage(project, packageName.trim());
                    FlingHelper.notify(project, NotificationType.INFORMATION, "Package name is refreshed by " + packageName.trim());
                }

                LocalStorageHelper.MonitorConfig saveConfig = new LocalStorageHelper.MonitorConfig();
                saveConfig.setEnable(monitorToggleButton.isSelected());
                saveConfig.setMonitorPrivate(monitorPrivate.isSelected());
                saveConfig.setPackages(packagesField.getText().trim());
                saveConfig.setClsSuffix(classSuffixField.getText().trim());
                saveConfig.setWhites(whiteListField.getText().trim());
                saveConfig.setBlacks(blackListField.getText().trim());
                saveConfig.setMonitorWeb(monitorWeb.isSelected());
                saveConfig.setMonitorMybatis(monitorMybatis.isSelected());
                saveConfig.setLogMybatis(logMybatis.isSelected());
                LocalStorageHelper.setMonitorConfig(project, saveConfig);
                FlingHelper.notify(project, NotificationType.INFORMATION, "Monitor is " + (saveConfig.isEnable() ? "enable" : "disable"));
                FlingHelper.refresh(project);
            }
        };
        saveButton.addActionListener(saveListener);

        // 关闭按钮
        JButton closeButton = new JButton("OK");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveListener.actionPerformed(e);
                // 关闭当前窗口
                Window window = SwingUtilities.getWindowAncestor(panel);
                if (window != null) {
                    window.dispose();
                }
            }
        });

        // 将按钮添加到按钮面板
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 5; // 新的一行
        gbc.gridwidth = 2; // 占据两列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);
        return panel;
    }


    public void findSpringBootApplicationClasses() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<PsiClass> springBootApplicationClasses = ApplicationManager.getApplication().runReadAction((Computable<List<PsiClass>>) () -> {
                List<PsiClass> result = new ArrayList<>();

                // 使用PsiManager获取项目中的所有文件
                PsiManager psiManager = PsiManager.getInstance(project);
                GlobalSearchScope globalSearchScope = GlobalSearchScope.projectScope(project);

                // 获取所有的Java文件
                FileType javaFileType = StdFileTypes.JAVA;
                Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(javaFileType, globalSearchScope);

                for (VirtualFile file : javaFiles) {
                    PsiFile psiFile = psiManager.findFile(file);
                    if (psiFile instanceof PsiJavaFile) {
                        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
                        for (PsiClass psiClass : javaFile.getClasses()) {
                            // 检查每个类的注解
                            PsiAnnotation[] annotations = psiClass.getAnnotations();
                            for (PsiAnnotation annotation : annotations) {
                                if ("org.springframework.boot.autoconfigure.SpringBootApplication".equals(annotation.getQualifiedName())) {
                                    result.add(psiClass);
                                }
                            }
                        }
                    }
                }
                return result;
            });

            // 当所有的操作完成后，更新UI或进一步处理结果
            // 用UI相关的操作需要放在EDT上执行
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    for (PsiClass applicationClass : springBootApplicationClasses) {
                        scriptAppBox.addItem(applicationClass.getName());
                        propertiesAppBox.addItem(applicationClass.getName());
                    }
                }
            });
        });
    }

    private JPanel createScriptOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

        LanguageTextField scriptField = new LanguageTextField(JavaLanguage.INSTANCE, getProject(), LocalStorageHelper.getScript(project), false);

        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        String[] apps = new String[]{PROJECT_DEFAULT};

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("Scope:");
        scriptAppBox = new ComboBox<>(apps);
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 0; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(scriptAppBox, gbc);

        scriptAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) scriptAppBox.getSelectedItem();
                if (selectedApp == null) {
                    scriptField.setText(null);
                } else if (PROJECT_DEFAULT.equals(selectedApp)) {
                    scriptField.setText(LocalStorageHelper.getScript(project));
                } else {
                    scriptField.setText(LocalStorageHelper.getAppScript(project, selectedApp));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("use default script");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptField.setText(LocalStorageHelper.defScript);
            }
        });
        gbc.gridx = 2;  // 放在同一行的尾部
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String script = scriptField.getText();
            String selectedApp = (String) scriptAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }
            if (StringUtils.isBlank(script)) {
                if (PROJECT_DEFAULT.equals(selectedApp)) {
                    LocalStorageHelper.setScript(project, null);
                } else {
                    LocalStorageHelper.setAppScript(project, selectedApp, null);
                }
                FlingHelper.notify(project, NotificationType.INFORMATION, "Script is blank");
                return;
            }
            if (PROJECT_DEFAULT.equals(selectedApp)) {
                LocalStorageHelper.setScript(project, script);
            } else {
                LocalStorageHelper.setAppScript(project, selectedApp, script);
            }
            FlingHelper.notify(project, NotificationType.INFORMATION, "Script is saved");
        };
        saveButton.addActionListener(saveListener);

        // 关闭按钮
        JButton closeButton = new JButton("OK");
        closeButton.addActionListener(e -> {
            saveListener.actionPerformed(e);
            // 关闭当前窗口
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) {
                window.dispose();
            }
        });

        // 将按钮添加到按钮面板
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 2; // 新的一行
        gbc.gridwidth = 3; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }


    private JPanel createPropertiesOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

//        EditorTextField editorTextField = new EditorTextField(dummyFile.getViewProvider().getDocument(), project, PropertiesLanguage.INSTANCE.getAssociatedFileType(), true, false);

        LanguageTextField propertiesField = new LanguageTextField(PropertiesLanguage.INSTANCE, getProject(), LocalStorageHelper.getAppProperties(project, ""), false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(propertiesField), gbc);

        String[] apps = new String[]{};

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        propertiesAppBox = new ComboBox<>(apps);


        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 0; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(propertiesAppBox, gbc);

        propertiesAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) propertiesAppBox.getSelectedItem();
                if (selectedApp == null) {
                    propertiesField.setText(null);
                } else {
                    propertiesField.setText(LocalStorageHelper.getAppProperties(project, selectedApp));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("use default properties");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                propertiesField.setText(LocalStorageHelper.defProperties);
            }
        });
        gbc.gridx = 2;  // 放在同一行的尾部
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String propertiesStr = propertiesField.getText();
            String selectedApp = (String) propertiesAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }
            if (StringUtils.isBlank(propertiesStr)) {
                LocalStorageHelper.setAppProperties(project, selectedApp, null);
                FlingHelper.notify(project, NotificationType.INFORMATION, "Properties is blank");
                return;
            }
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(propertiesStr));
            } catch (Throwable ex) {
                FlingHelper.notify(project, NotificationType.WARNING, "Properties parsed error<br/> pls check");
                return;
            }
            LocalStorageHelper.setAppProperties(project, selectedApp, propertiesStr);
            FlingHelper.notify(project, NotificationType.INFORMATION, "Properties is saved");
        };
        saveButton.addActionListener(saveListener);

        // 关闭按钮
        JButton closeButton = new JButton("OK");
        closeButton.addActionListener(e -> {
            saveListener.actionPerformed(e);
            // 关闭当前窗口
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) {
                window.dispose();
            }
        });

        // 将按钮添加到按钮面板
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 2; // 新的一行
        gbc.gridwidth = 3; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }

    public static class VisibleApp {

        private String appName;

        private int port;

        private int sidePort;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getSidePort() {
            return sidePort;
        }

        public void setSidePort(int sidePort) {
            this.sidePort = sidePort;
        }
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
        if (dagreAction != null) {
//            System.out.println("不可见,"+ (outputProfile != null && !outputProfile.isEmpty()));
            if (outputProfile != null && !outputProfile.isEmpty() && !actionGroup.containsAction(dagreAction)) {
                actionGroup.add(dagreAction);
            } else if ((outputProfile == null || outputProfile.isEmpty()) && actionGroup.containsAction(dagreAction)) {
                actionGroup.remove(dagreAction);
            }
            windowContent.revalidate(); // 重新验证布局
            windowContent.repaint();
        }
    }

    public VisibleApp getSelectedApp() {
        String selectedItem = (String) appBox.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        return parseApp(selectedItem);
    }

    public String getSelectedAppName() {
        VisibleApp app = getSelectedApp();
        if (app == null) {
            return null;
        }
        return app.getAppName();
    }

}


