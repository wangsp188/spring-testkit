package com.testkit.view;

import com.alibaba.fastjson.JSON;
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
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallTool;
import com.testkit.tools.flexible_test.FlexibleTestTool;
import com.testkit.tools.spring_cache.SpringCacheTool;
import com.intellij.ui.components.JBScrollPane;
import com.testkit.util.HttpUtil;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestkitToolWindow {

    private static final Icon settingsIcon = IconLoader.getIcon("/icons/settings.svg", TestkitToolWindow.class);
    private static final Icon dagreIcon = IconLoader.getIcon("/icons/trace.svg", TestkitToolWindow.class);


    private Project project;
    private ToolWindow window;
    private JPanel windowContent;
    private JButton tipsButton;
    private JButton settingsButton;
    private JComboBox<String> toolBox;

    private JLabel appLabel;
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
                actionGroup.add(new AnAction("Refresh coding-guidelines", null, CodingGuidelinesIconProvider.DOC_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        Application application = ApplicationManager.getApplication();
                        application.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    CodingGuidelinesHelper.refreshDoc(project);
                                    TestkitHelper.refresh(project);
                                    TestkitHelper.notify(project, NotificationType.INFORMATION, "Refresh coding-guidelines success");
                                } catch (Exception ex) {
                                    TestkitHelper.notify(project, NotificationType.ERROR, "Refresh coding-guidelines failed," + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                                }
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
        tools.put(PluginToolEnum.SPRING_CACHE, new SpringCacheTool(this));
        tools.put(PluginToolEnum.MAPPER_SQL, new MapperSqlTool(this));
        // 添加 toolBox 到 topPanel
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.FUNCTION_CALL.getCode(), PluginToolEnum.SPRING_CACHE.getCode(), PluginToolEnum.FLEXIBLE_TEST.getCode(), PluginToolEnum.MAPPER_SQL.getCode()});
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
            String selectedItem = (String) appBox.getSelectedItem();
            RuntimeHelper.updateSelectedApp(getProject().getName(), selectedItem == null ? null : parseApp(selectedItem));
            appBox.setToolTipText(selectedItem == null ? "" : selectedItem); // 动态更新 ToolTipText

            if (selectedItem != null && RuntimeHelper.isMonitor(selectedItem)) {
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
        initStoreDialog();
        DumbService.getInstance(project).smartInvokeLater(() -> {
            // 事件或代码在索引准备好后运行
            findSpringBootApplicationClasses();
        });
        return topPanel;
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
        Icon icon = preSetDoc.getType() == CodingGuidelinesHelper.DocType.markdown ? CodingGuidelinesIconProvider.MARKDOWN_ICON : CodingGuidelinesIconProvider.URL_ICON;
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
        AnAction copyAction = new AnAction("Copy output to clipboard", "Copy output to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                // 调用复制功能
                TestkitHelper.copyToClipboard(project, outputTextPane.getText(), "Output was copied");
            }
        };
        actionGroup.add(copyAction);

        if (linkRenderHtml != null) {
            traceAction = new AnAction("Dagre this req", "Dagre this req", dagreIcon) {

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
        List<String> newItems = RuntimeHelper.loadProjectRuntimes(project.getName());
        if (newItems == null) {
            return;
        }

        // 用于比较的 map，判断是否有变化
        HashMap<String, Boolean> newMap = new HashMap<>();
        HashMap<String, String> requestData = new HashMap<>();
        requestData.put("method", "hello");

        Iterator<String> iterator = newItems.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            if (item == null) {
                iterator.remove();
                continue;
            }

            // 获取 visibleApp 实例
            RuntimeHelper.VisibleApp visibleApp = parseApp(item);
            try {
                // 发送请求获取实时数据
                Map response = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", requestData, Map.class);
                newMap.put(item.substring(0, item.lastIndexOf(":")), "true".equals(String.valueOf(response.get("data"))));
            } catch (Exception e) {
                e.printStackTrace();
                iterator.remove();
                RuntimeHelper.removeApp(project.getName(), visibleApp.getAppName(), visibleApp.getPort(), visibleApp.getSidePort());
            }
        }

        // 记录当前选中的项
        String selectedItem = (String) appBox.getSelectedItem();

        // 获取当前下拉框中的所有项
        List<String> currentItems = new ArrayList<>();
        for (int i = 0; i < appBox.getItemCount(); i++) {
            currentItems.add(appBox.getItemAt(i).toString());
        }

        // 更新 monitorMap
        RuntimeHelper.updateMonitors(newMap);
        // 比较新旧项是否有变化
        boolean hasChanges = !new HashSet<>(newItems).containsAll(currentItems) || !new HashSet<>(currentItems).containsAll(newItems);
        if (!hasChanges) {
            return;
        }
        // 更新下拉框内容
        appBox.removeAllItems();
        ArrayList<RuntimeHelper.VisibleApp> objects = new ArrayList<>();
        for (String item : newItems) {
            appBox.addItem(item.substring(0, item.lastIndexOf(":")));
            objects.add(parseApp(item));
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

    private void openTipsDoc() {
        String url = "https://gitee.com/wangsp188/shaopeng/blob/master/testkit_doc/spring-testkit.md";
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
            curlDialog.setVisible(true);
        }
    }

    public void refreshSqlDatasources(){
        sqlDialog.refreshDatasources();
    }

    public void openSqlDialog() {
        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
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


    private @Nullable RuntimeHelper.VisibleApp parseApp(String selectedItem) {
        String[] split = selectedItem.split(":");
        if (split.length == 2) {
            RuntimeHelper.VisibleApp visibleApp = new RuntimeHelper.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[1]) + 10000);
            return visibleApp;

        } else if (split.length == 3) {
            RuntimeHelper.VisibleApp visibleApp = new RuntimeHelper.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[2]));
            return visibleApp;
        }
        throw new IllegalArgumentException("un support app item");
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
            List<PsiClass> springBootApplicationClasses = ApplicationManager.getApplication().runReadAction((Computable<List<PsiClass>>) () -> {
                List<PsiClass> result = new ArrayList<>();

                // 使用更高效的查询方式
                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                PsiClass springBootAnnotation = facade.findClass(
                        "org.springframework.boot.autoconfigure.SpringBootApplication",
                        GlobalSearchScope.allScope(project)
                );

                if (springBootAnnotation != null) {
                    Collection<PsiClass> candidates = AnnotatedElementsSearch
                            .searchPsiClasses(springBootAnnotation, GlobalSearchScope.projectScope(project))
                            .findAll();
                    result.addAll(candidates);
                }

                return result;
            });

            // 当所有的操作完成后，更新UI或进一步处理结果
            // 用UI相关的操作需要放在EDT上执行
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {

                    HashMap<String, RuntimeHelper.AppMeta> map = new HashMap<>();
                    springBootApplicationClasses.forEach(new Consumer<PsiClass>() {
                        @Override
                        public void accept(PsiClass psiClass) {
                            RuntimeHelper.AppMeta appMeta = new RuntimeHelper.AppMeta();
                            appMeta.setApp(psiClass.getName());
                            appMeta.setFullName(psiClass.getQualifiedName());

                            appMeta.setModule(ModuleUtil.findModuleForPsiElement(psiClass));
                            map.put(psiClass.getName(), appMeta);
                        }
                    });

                    RuntimeHelper.updateAppMetas(project.getName(), new ArrayList<>(map.values()));
                    storeDialog.initApps(new ArrayList<>(map.keySet()));
                    settingsDialog.initApps(new ArrayList<>(map.keySet()));

//                    更新datasources
                    updateDatasources();
                    TestkitHelper.refresh(project);
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


