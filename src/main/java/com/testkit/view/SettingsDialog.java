package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.util.ui.JBUI;
import com.testkit.TestkitHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.tools.function_call.FunctionCallTool;
import com.testkit.util.HttpUtil;
import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.*;
import com.testkit.util.JsonUtil;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.StringReader;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SettingsDialog {

    private static final Icon FIRE_TEST_ICON = IconLoader.getIcon("/icons/fire-test.svg", SettingsDialog.class);
    private static final Icon SHOW_ICON = IconLoader.getIcon("/icons/show.svg", SettingsDialog.class);
    private static final Icon HIDDEN_ICON = IconLoader.getIcon("/icons/hidden.svg", SettingsDialog.class);
    public static final Icon EXPORT_ICON = IconLoader.getIcon("/icons/export.svg", SettingsDialog.class);
    public static final Icon IMPORT_ICON = IconLoader.getIcon("/icons/import.svg", SettingsDialog.class);

    private TestkitToolWindow toolWindow;

    private JDialog dialog;

    private JBTextField flexibleTestPackageNameField;

    private JBTextField beanAnnotationField;

    private ComboBox<String> interceptorAppBox;

    private ComboBox<String> controllerScriptAppBox;
    private ComboBox<String> feignScriptAppBox;


    private ComboBox<String> propertiesAppBox;

    private JBTextField controllerEnvTextField;

    private JBTextField feignEnvTextField;


    private LanguageTextField datasourcePropertiesField;
    private JButton showDatasourceButton;
    private OnOffButton traceToggleButton;
    private JPanel traceOptionsPanel;
    private OnOffButton traceWebToggleButton;
    private OnOffButton traceMybatisToggleButton;
    private OnOffButton logMybatisToggleButton;
    private JBTextField tracePackagesField;

    private JBTextField traceClassSuffixField;
    private JBTextField traceWhiteListField;
    private JBTextField traceBlackListField;
//    private JBTextField traceSingleClsDepthField;


    public SettingsDialog(TestkitToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        init();
    }

    public void visible() {
        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
            refreshSettings();
            dialog.revalidate();
            dialog.repaint();
            dialog.setVisible(true);
        }
    }

    public void initApps(List<String> apps) {
        if (apps == null) {
            return;
        }
        apps.stream().forEach(new Consumer<String>() {
            @Override
            public void accept(String app) {
                interceptorAppBox.addItem(app);
                controllerScriptAppBox.addItem(app);
                feignScriptAppBox.addItem(app);
                propertiesAppBox.addItem(app);
            }
        });
    }

    private void refreshSettings() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                flexibleTestPackageNameField.setText(SettingsStorageHelper.getFlexibleTestPackage(toolWindow.getProject()));
                RuntimeHelper.VisibleApp selectedApp = RuntimeHelper.getSelectedApp(toolWindow.getProject().getName());
                String selectedAppName = selectedApp == null ? null : selectedApp.getAppName();
                if (selectedAppName == null) {
                    if (interceptorAppBox.getItemCount() > 0) {
                        interceptorAppBox.setSelectedIndex(0);
                    }
                    if (controllerScriptAppBox.getItemCount() > 0) {
                        controllerScriptAppBox.setSelectedIndex(0);
                    }
                    if (feignScriptAppBox.getItemCount() > 0) {
                        feignScriptAppBox.setSelectedIndex(0);
                    }
                    if (propertiesAppBox.getItemCount() > 0) {
                        // 选择第一个选项
                        propertiesAppBox.setSelectedIndex(0);
                    }
                } else {
                    if (interceptorAppBox.getItemCount() > 0) {
                        interceptorAppBox.setSelectedItem(selectedAppName);
                    }
                    if (controllerScriptAppBox.getItemCount() > 0) {
                        controllerScriptAppBox.setSelectedItem(selectedAppName);
                    }
                    if (feignScriptAppBox.getItemCount() > 0) {
                        feignScriptAppBox.setSelectedItem(selectedAppName);
                    }
                    if (propertiesAppBox.getItemCount() > 0) {
                        // 选择第一个选项
                        propertiesAppBox.setSelectedItem(selectedAppName);
                    }
                }

//        trace
                SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
                traceToggleButton.setSelected(traceConfig.isEnable());
                traceOptionsPanel.setVisible(traceConfig.isEnable());
                traceWebToggleButton.setSelected(traceConfig.isTraceWeb());
                traceMybatisToggleButton.setSelected(traceConfig.isTraceMybatis());
                logMybatisToggleButton.setSelected(traceConfig.isLogMybatis());
                tracePackagesField.setText(traceConfig.getPackages());
                traceClassSuffixField.setText(traceConfig.getClsSuffix());
                traceBlackListField.setText(traceConfig.getBlacks());
                traceWhiteListField.setText(traceConfig.getWhites());
//        traceSingleClsDepthField.setText(String.valueOf(traceConfig.getSingleClsDepth()));

//        SettingsStorageHelper.SqlConfig sqlConfig = SettingsStorageHelper.getSqlConfig(toolWindow.getProject());
                datasourcePropertiesField.setText("");
                datasourcePropertiesField.setVisible(false);
                showDatasourceButton.setIcon(HIDDEN_ICON);
            }
        });

    }

    private void init() {
        dialog = new JDialog((Frame) null, TestkitHelper.getPluginName() + " Settings", true);
        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 左侧选项列表
        String[] options = {TestkitHelper.getPluginName(), "Trace", "Tool interceptor", "Spring properties", "Controller command", "FeignClient command", "SQL tool"};
        JBList<String> optionList = new JBList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 默认选中第一个选项
        optionList.setSelectedIndex(0);
        // 右侧内容显示区域
        JPanel rightContent = new JPanel(new CardLayout());
        rightContent.add(createBasicOptionPanel(), TestkitHelper.getPluginName());
        rightContent.add(createTraceOptionPanel(), "Trace");
        rightContent.add(createSqlPanel(), "SQL tool");
        rightContent.add(createInterceptorOptionPanel(), "Tool interceptor");
        rightContent.add(createPropertiesOptionPanel(), "Spring properties");
        rightContent.add(createControllerOptionPanel(), "Controller command");
        rightContent.add(createFeignOptionPanel(), "FeignClient command");

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
        dialog.setContentPane(contentPanel);
        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.7);
        int height = (int) (screenSize.height * 0.7);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(null); // 居中显示
    }


    private JPanel createBasicOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Dimension labelDimension = new Dimension(120, 20);

        JButton importButton = new JButton(IMPORT_ICON);
        importButton.setToolTipText("Import/Overwrite " + TestkitHelper.getPluginName() + " Settings");
        importButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 创建弹出对话框
                JDialog dialog = new JDialog();
                dialog.setTitle("Import/Overwrite " + TestkitHelper.getPluginName() + " Settings for project " + toolWindow.getProject().getName());
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                // 创建说明文本标签
                JLabel instructionLabel = new JLabel("<html>Paste the data you want to import here<br>Usually json content exported from other device or project</html>");
// 启用自动换行
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 创建JSON输入框
                JTextArea jsonInput = new JTextArea();
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);

                // 创建导入按钮
                JButton importConfirmButton = new JButton("Import");
                importConfirmButton.addActionListener(e1 -> {
                    SettingsStorageHelper.ProjectConfig importData = null;
                    try {
                        importData = JSON.parseObject(jsonInput.getText().trim(), SettingsStorageHelper.ProjectConfig.class);
                    } catch (Exception ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Import must be json, " + ex.getMessage());
                        return;
                    }

                    try {
                        SettingsStorageHelper.saveProjectConfig(toolWindow.getProject(), importData);
                        refreshSettings();
                        TestkitHelper.refresh(toolWindow.getProject());
                        dialog.dispose();
                        TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Import successfully");
                    } catch (Exception ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Import error," + ex.getMessage());
                    }
                });

                // 布局
                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 将组件添加到面板
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(importConfirmButton, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
        });

        JButton exportButton = new JButton(EXPORT_ICON);
        exportButton.setToolTipText("Export " + TestkitHelper.getPluginName() + " Settings");
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
// 创建弹出对话框
                JDialog dialog = new JDialog();
                dialog.setTitle("Export " + TestkitHelper.getPluginName() + " Settings for project " + toolWindow.getProject().getName());
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                SettingsStorageHelper.ProjectConfig exportData = SettingsStorageHelper.loadProjectConfig(toolWindow.getProject());
                // 创建说明文本标签
                JLabel instructionLabel = new JLabel("<html>The exported content is already below<br/>You can copy it and import it on another device or project</html>");
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
// 启用自动换行
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 创建JSON输入框
                JTextArea jsonInput = new JTextArea();
                jsonInput.setEditable(false);
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);
                jsonInput.setText(exportData == null ? "{}" : JsonUtil.formatObj(exportData));
                // 创建导入按钮
                JButton copyConfirmButton = new JButton("Copy");
                copyConfirmButton.addActionListener(e1 -> {
                    TestkitHelper.copyToClipboard(toolWindow.getProject(), jsonInput.getText(), null);
                    dialog.dispose();
                });

                // 布局
                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 将组件添加到面板
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(copyConfirmButton, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
        });

        // 创建按钮面板
        JPanel button1Panel = new JPanel(new BorderLayout()); // 左对齐，按钮间距5
        JBLabel comp = new JBLabel("Import/Export Settings");
//        comp.setPreferredSize(labelDimension);
        button1Panel.add(comp, BorderLayout.WEST);

        // 创建右侧按钮容器（水平排列，固定间距）
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.add(importButton);
        rightPanel.add(Box.createHorizontalStrut(5)); // 5像素间距
        rightPanel.add(exportButton);

        button1Panel.add(rightPanel, BorderLayout.EAST);


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // 添加内边距以美化布局
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 3;
//        gbc.insets = JBUI.emptyInsets();
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(button1Panel, gbc);


        // Method Call Label
        JLabel springLabel = new JLabel("Spring enhancement");
        JLabel springDetailLabel = new JLabel("Function-call, flexible-test and so on require turned on this");
        springDetailLabel.setForeground(new Color(0x72A96B));
        springDetailLabel.setFont(new Font("Arial", Font.BOLD, 13));


        JPanel springOptionsPanel = new JPanel(new GridBagLayout());
        springOptionsPanel.setVisible(false);
        // Button
        OnOffButton springButton = new OnOffButton();
        springButton.addActionListener(e -> {
            springOptionsPanel.setVisible(springButton.isSelected());
            boolean enableSideServer = SettingsStorageHelper.isEnableSideServer(toolWindow.getProject());
            if (springButton.isSelected()) {
                if (!enableSideServer) {
                    SettingsStorageHelper.setEnableSideServer(toolWindow.getProject(), true);
                    TestkitHelper.refresh(toolWindow.getProject());
                }
                springDetailLabel.setText("We will start a side server to support function-call, flexible-test and so on");
            } else {
                if (enableSideServer) {
                    SettingsStorageHelper.setEnableSideServer(toolWindow.getProject(), false);
                    TestkitHelper.refresh(toolWindow.getProject());
                }
                springDetailLabel.setText("Function-call, flexible-test and so on require turned on this");
            }
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Spring enhancement is " + (springButton.isSelected() ? "enable" : "disable"));
        });

        if (SettingsStorageHelper.isEnableSideServer(toolWindow.getProject())) {
            springOptionsPanel.setVisible(true);
            springButton.setSelected(true); // 示例中默认启用
            // 设置初始文本
            springDetailLabel.setText("We will start a side server to support function-call, flexible-test and so on");
        }

        // 嵌套 Panel，用于将 Label 和 Button 合并到一起
        JPanel labelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        labelButtonPanel.add(springLabel, BorderLayout.WEST);
        labelButtonPanel.add(springButton, BorderLayout.EAST);

        gbc.gridwidth = 1;
        // 添加到主面板中
        gbc.gridx = 0; // 第一列
        gbc.gridy = 1; // 第二行
        gbc.weightx = 0; // 不占用多余水平空间
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; // 水平方向填充
        panel.add(labelButtonPanel, gbc);

        // 第二列
        gbc.gridx = 1; // 第二列
        gbc.weightx = 1; // 占据剩余水平空间
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // 水平方向填充
        panel.add(springDetailLabel, gbc);

        beanAnnotationField = new JBTextField(String.join(",", SettingsStorageHelper.getBeanAnnotations(toolWindow.getProject())), 20);
        beanAnnotationField.getEmptyText().setText("The annotation list will registered bean to spring container. Default: Mapper,FeignClient");

        // 输入框
        JLabel beanAnnotationLabel = new JLabel("Bean Annotations:");
        beanAnnotationLabel.setPreferredSize(labelDimension);
        beanAnnotationLabel.setLabelFor(beanAnnotationField); // 关联标签和输入框
        beanAnnotationLabel.setToolTipText("The annotation list will registered bean to spring container"); // 提示信息

        // 布局输入框和标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        springOptionsPanel.add(beanAnnotationLabel, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // 让输入框占据剩余空间
        springOptionsPanel.add(beanAnnotationField, gbc);

        // 添加新按钮到组合框右边
        JButton beanButton = new JButton(AllIcons.Actions.Rollback);
        beanButton.setToolTipText("Use default annotation list");
        beanButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                beanAnnotationField.setText(String.join(",", SettingsStorageHelper.defBeanAnnotations));
            }
        });
        gbc.gridx = 3;  // 放在同一行的尾部
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        springOptionsPanel.add(beanButton, gbc);


        flexibleTestPackageNameField = new JBTextField(SettingsStorageHelper.getFlexibleTestPackage(toolWindow.getProject()), 20);
        flexibleTestPackageNameField.getEmptyText().setText("Which package do you want to enable flexible test? Default: flexibletest");

        // 输入框
        JLabel packageNameLabel = new JLabel("Test Package:");
        packageNameLabel.setPreferredSize(labelDimension);
        packageNameLabel.setLabelFor(flexibleTestPackageNameField); // 关联标签和输入框
        packageNameLabel.setToolTipText("Which package do you want to enable flexible test?"); // 提示信息

        // 布局输入框和标签
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        springOptionsPanel.add(packageNameLabel, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // 让输入框占据剩余空间
        springOptionsPanel.add(flexibleTestPackageNameField, gbc);

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default package");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flexibleTestPackageNameField.setText(SettingsStorageHelper.defFlexibleTestPackage);
            }
        });
        gbc.gridx = 3;  // 放在同一行的尾部
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        springOptionsPanel.add(newButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(springOptionsPanel, gbc);


        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 3; // 新的一行
        gbc.weighty = 1.0; // 占用剩余空间
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH; // 使下一行占用空间
        panel.add(Box.createVerticalStrut(0), gbc); // 添加空白占位符，以便下一行显示正确

        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!springOptionsPanel.isVisible()) {
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "No config need to save");
                    return;
                }

                String packageName = flexibleTestPackageNameField.getText();
                if (StringUtils.isBlank(packageName)) {
                    SettingsStorageHelper.setFlexibleTestPackage(toolWindow.getProject(), null);
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Package name is blank, use default package name");
                } else {
                    SettingsStorageHelper.setFlexibleTestPackage(toolWindow.getProject(), packageName.trim());
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Package name is refreshed by " + packageName.trim());
                }

                String annotationFieldText = beanAnnotationField.getText();
                if (StringUtils.isBlank(annotationFieldText.trim())) {
                    SettingsStorageHelper.setBeanAnnotations(toolWindow.getProject(), null);
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Bean annotations is blank, use default list");
                } else {
                    SettingsStorageHelper.setBeanAnnotations(toolWindow.getProject(), Arrays.asList(annotationFieldText.trim().split(",")));
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Bean annotations is refreshed by " + Arrays.asList(annotationFieldText.trim().split(",")));
                }

                TestkitHelper.refresh(toolWindow.getProject());
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
        gbc.gridy = 4; // 新的一行
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);
        return panel;
    }

    private JTextArea createTips(String content) {
        JTextArea tipArea = new JTextArea(content);
        tipArea.setToolTipText(content);
        tipArea.setEditable(false); // 不可编辑
        tipArea.setOpaque(false);
        tipArea.setForeground(new Color(0x72A96B));
        tipArea.setFont(new Font("Arial", Font.BOLD, 13)); // 设置字体
        return tipArea;
    }


    private JPanel createInterceptorOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // 添加内边距以美化布局

        JTextArea tipArea = createTips("Use the following code to intercept the execution of some tool, it can be turned on or off at any time in the tool panel\nAvailable tools: Function-call, flexible-test, spring-cache\nScript language: JAVA (Classes in your project can be used, You can refer to spring-beans using @Autowired)");
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(tipArea, gbc);

        LanguageTextField interceptorField = new LanguageTextField(JavaLanguage.INSTANCE, toolWindow.getProject(), SettingsStorageHelper.defInterceptor, false);

        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(interceptorField), gbc);

        String[] apps = new String[]{};

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        interceptorAppBox = new ComboBox<>(apps);
        comboBoxLabel.setLabelFor(interceptorAppBox);
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(interceptorAppBox, gbc);

        interceptorAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) interceptorAppBox.getSelectedItem();
                if (selectedApp == null) {
                    interceptorField.setText(SettingsStorageHelper.defInterceptor);
                } else {
                    String appInterceptor = SettingsStorageHelper.getAppScript(toolWindow.getProject(), selectedApp);
                    interceptorField.setText(appInterceptor == null ? SettingsStorageHelper.defInterceptor : appInterceptor);
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default interceptor");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                interceptorField.setText(SettingsStorageHelper.defInterceptor);
            }
        });
        gbc.gridx = 2;  // 放在同一行的尾部
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        //测试按钮
        JButton testButton = new JButton(FIRE_TEST_ICON);
        testButton.setPreferredSize(new Dimension(20, 20));
        testButton.setToolTipText("Fire-Test tool-interceptor use hello world code");
        ActionListener testListener = e -> {
            Object selectedItem = interceptorAppBox.getSelectedItem();
            if (selectedItem == null) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please select a app");
                return;
            }
            String interceptor = interceptorField.getText();


            RuntimeHelper.VisibleApp visibleApp = Optional.ofNullable(RuntimeHelper.getVisibleApps(toolWindow.getProject().getName()))
                    .orElse(new ArrayList<>())
                    .stream()
                    .filter(new Predicate<RuntimeHelper.VisibleApp>() {
                        @Override
                        public boolean test(RuntimeHelper.VisibleApp visibleApp) {
                            return Objects.equals(visibleApp.getAppName(), selectedItem);
                        }
                    })
                    .findFirst()
                    .orElse(null);
            if (visibleApp == null) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please start Application named " + selectedItem);
                return;
            }
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Fire-Test tool-interceptor, please wait ...", false) {

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        String code = "import java.util.Date;\n" +
                                "\n" +
                                "class HelloTestkit {\n" +
                                "\n" +
                                "    public String hello(String name, Date date) {\n" +
                                "        return \"Hello \" + name + \", now is \" + date;\n" +
                                "    }\n" +
                                "}";
                        JSONObject params = new JSONObject();
                        params.put("code", code);
                        params.put("methodName", "hello");
                        params.put("argTypes", "[\"java.lang.String\",\"java.util.Date\"]");
                        params.put("args", "[\"" + TestkitHelper.getPluginName() + "\"," + System.currentTimeMillis() + "]");
                        JSONObject req = new JSONObject();
                        req.put("method", "flexible-test");
                        req.put("params", params);
                        req.put("interceptor", interceptor);
                        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(getProject());
                        req.put("trace", traceConfig.isEnable());
//                        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
                        String reqId;
                        try {
                            JSONObject submitRes = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", req, JSONObject.class);
                            if (submitRes == null) {
                                TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-interceptor error\nFailed to submit req\nsubmitRes is null");
                                return;
                            }
                            if (!submitRes.getBooleanValue("success") || submitRes.getString("data") == null) {
                                TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-interceptor error\nFailed to submit req\n" + submitRes.getString("message"));
                                return;
                            }
                            reqId = submitRes.getString("data");
                        } catch (Exception ex) {
                            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-interceptor error\nFailed to submit req\n" + ex.getClass().getSimpleName() + "," + ex.getMessage());
                            return;
                        }

                        HashMap<String, Object> getRetReq = new HashMap<>();
                        getRetReq.put("method", "get_task_ret");
                        params.clear();
                        params.put("reqId", reqId);
                        getRetReq.put("params", params);

                        JSONObject result = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", getRetReq, JSONObject.class);
                        if (result == null || !result.getBooleanValue("success")) {
                            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-interceptor error\n" + (result == null ? "result is null" : result.getString("message")));
                            return;
                        }
                        TestkitHelper.alert(getProject(), Messages.getInformationIcon(), "Test tool-interceptor success\n" + result.get("data"));
                    } catch (Throwable ex) {
                        TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-interceptor error\n" + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                    }
                }
            });

        };
        testButton.addActionListener(testListener);

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String interceptor = interceptorField.getText();
            String selectedApp = (String) interceptorAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }
            if (StringUtils.isBlank(interceptor)) {
                SettingsStorageHelper.setAppScript(toolWindow.getProject(), selectedApp, null);
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Tool-interceptor is blank");
                return;
            }
            SettingsStorageHelper.setAppScript(toolWindow.getProject(), selectedApp, interceptor);
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Tool-interceptor is saved");
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
        buttonPanel.add(testButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 3; // 新的一行
        gbc.gridwidth = 3; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private JPanel createControllerOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

        JTextArea tipArea = createTips("When the method is spring-web requestMapping, we will execute the following script with context\nDefault is return curl-command, of course, you can even make a request through an http function and return the result\nScript language: Groovy(You cannot use classes in your project)");
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 5;
        gbc.weightx = 1;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(tipArea, gbc);


        LanguageTextField scriptField = new LanguageTextField(GroovyLanguage.INSTANCE, null, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 5; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        String[] apps = new String[]{};

        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        controllerEnvTextField = new JBTextField(10); // 设定文本框的列数
        controllerEnvTextField.getEmptyText().setText("Optional environment list; multiple use,split");
        controllerEnvTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateToolTip();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateToolTip();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateToolTip();
            }

            private void updateToolTip() {
                String text = controllerEnvTextField.getText().trim();
                if (StringUtils.isBlank(text)) {
                    controllerEnvTextField.setToolTipText("not config env list");
                } else {
                    controllerEnvTextField.setToolTipText("env list is " + JSON.toJSONString(text.split(",")));
                }
            }
        });
        controllerEnvTextField.setText("");

        JLabel envLabel = new JLabel("Env:");
        envLabel.setLabelFor(controllerEnvTextField);
        envLabel.setToolTipText("Optional environment list; multiple use,split");

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        controllerScriptAppBox = new ComboBox<>(apps);
        comboBoxLabel.setLabelFor(controllerScriptAppBox);

        gbc.gridx = 0;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(controllerScriptAppBox, gbc);


        gbc.gridx = 2;
        gbc.gridy = 1; // 第一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(envLabel, gbc);

        // 添加环境文本框
        gbc.gridx = 3;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(controllerEnvTextField, gbc);


        controllerScriptAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) controllerScriptAppBox.getSelectedItem();
                if (selectedApp == null) {
                    scriptField.setText(SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript());
                    controllerEnvTextField.setText("");
                } else {
                    SettingsStorageHelper.HttpCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(toolWindow.getProject(), selectedApp);
                    scriptField.setText(controllerCommand.getScript() == null ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript() : controllerCommand.getScript());
                    controllerEnvTextField.setText(CollectionUtils.isEmpty(controllerCommand.getEnvs()) ? "" : String.join(",", controllerCommand.getEnvs()));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default interceptor");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptField.setText(SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript());
                controllerEnvTextField.setText("");
            }
        });
        gbc.gridx = 4;  // 放在同一行的尾部
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        //测试按钮
        JButton testButton = new JButton(FIRE_TEST_ICON);
        testButton.setPreferredSize(new Dimension(20, 20));
        testButton.setToolTipText("Fire-Test generate function use simple params");
        ActionListener testListener = e -> {
            RuntimeHelper.VisibleApp selectedApp = RuntimeHelper.getSelectedApp(toolWindow.getProject().getName());
            String scriptCode = scriptField.getText();
            String app = String.valueOf(controllerScriptAppBox.getSelectedItem());
            if (StringUtils.isBlank(controllerEnvTextField.getText().trim())) {
                fireTestGenerateFunction(scriptCode, null, selectedApp);
                return;
            }

            String[] envs = controllerEnvTextField.getText().trim().split(",");
            DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
            for (String env : envs) {
                //显示的一个图标加上标题
                AnAction documentation = new AnAction("Fire-Test with " + app + ":" + env, "Fire-Test with " + app + ":" + env, FunctionCallTool.CONTROLLER_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        fireTestGenerateFunction(scriptCode, env, selectedApp);
                    }
                };
                controllerActionGroup.add(documentation); // 将动作添加到动作组中
            }

            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("ControllerCommandPopup", controllerActionGroup).getComponent();
            popupMenu.show(testButton, 0, 0);
        };
        testButton.addActionListener(testListener);

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String script = scriptField.getText();
            String selectedApp = (String) controllerScriptAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }

            SettingsStorageHelper.HttpCommand controllerCommand = new SettingsStorageHelper.HttpCommand();
            controllerCommand.setScript(StringUtils.isBlank(script) ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript() : script);
            controllerCommand.setEnvs(StringUtils.isBlank(controllerEnvTextField.getText().trim()) ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getEnvs() : Arrays.asList(controllerEnvTextField.getText().trim().split(",")));
            SettingsStorageHelper.setAppControllerCommand(toolWindow.getProject(), selectedApp, controllerCommand);
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "ControllerCommand is saved");
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
        buttonPanel.add(testButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 3; // 新的一行
        gbc.gridwidth = 5; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private void fireTestGenerateFunction(String scriptCode, String env, RuntimeHelper.VisibleApp selectedApp) {
        ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Fire-Test generate function, please wait ...", false) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    GroovyShell groovyShell = new GroovyShell();
                    Script script = groovyShell.parse(scriptCode);
                    HashMap<String, String> params = new HashMap<>();
                    params.put("param1", "v1");
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("T-header", "1");
                    Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.getPort(), "POST", "/health", params, headers, "{}"});
                    String ret = build == null ? "" : String.valueOf(build);
                    TestkitHelper.alert(getProject(), Messages.getInformationIcon(), "Test generate function success\n" + ret);
                } catch (Throwable ex) {
                    TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test generate function error\n" + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                }
            }
        });
    }


    private JPanel createFeignOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // 添加内边距以美化布局

        JTextArea tipArea = createTips("When the interface is FeignClient, we will execute the following script with context\nDefault is return curl-command, of course, you can even make a request through an http function and return the result\nScript language: Groovy(You cannot use classes in your project)");
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 5;
        gbc.weightx = 1;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(tipArea, gbc);


        LanguageTextField scriptField = new LanguageTextField(GroovyLanguage.INSTANCE, null, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript(), false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 5; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        String[] apps = new String[]{};

        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        feignEnvTextField = new JBTextField(10); // 设定文本框的列数
        feignEnvTextField.getEmptyText().setText("Optional environment list; multiple use,split");
        feignEnvTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateToolTip();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateToolTip();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateToolTip();
            }

            private void updateToolTip() {
                String text = feignEnvTextField.getText().trim();
                if (StringUtils.isBlank(text)) {
                    feignEnvTextField.setToolTipText("not config env list");
                } else {
                    feignEnvTextField.setToolTipText("env list is " + JSON.toJSONString(text.split(",")));
                }
            }
        });
        feignEnvTextField.setText("");

        JLabel envLabel = new JLabel("Env:");
        envLabel.setLabelFor(feignEnvTextField);
        envLabel.setToolTipText("Optional environment list; multiple use,split");

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        feignScriptAppBox = new ComboBox<>(apps);
        comboBoxLabel.setLabelFor(feignScriptAppBox);

        gbc.gridx = 0;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(feignScriptAppBox, gbc);


        gbc.gridx = 2;
        gbc.gridy = 1; // 第一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(envLabel, gbc);

        // 添加环境文本框
        gbc.gridx = 3;
        gbc.gridy = 1; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(feignEnvTextField, gbc);


        feignScriptAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) feignScriptAppBox.getSelectedItem();
                if (selectedApp == null) {
                    scriptField.setText(SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript());
                    feignEnvTextField.setText("");
                } else {
                    SettingsStorageHelper.HttpCommand httpCommand = SettingsStorageHelper.getAppFeignCommand(toolWindow.getProject(), selectedApp);
                    scriptField.setText(httpCommand.getScript() == null ? SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript() : httpCommand.getScript());
                    feignEnvTextField.setText(CollectionUtils.isEmpty(httpCommand.getEnvs()) ? "" : String.join(",", httpCommand.getEnvs()));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default interceptor");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptField.setText(SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript());
                feignEnvTextField.setText("");
            }
        });
        gbc.gridx = 4;  // 放在同一行的尾部
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        //测试按钮
        JButton testButton = new JButton(FIRE_TEST_ICON);
        testButton.setPreferredSize(new Dimension(20, 20));
        testButton.setToolTipText("Fire-Test feign generate function use simple params");
        ActionListener testListener = e -> {
            String scriptCode = scriptField.getText();
            String app = String.valueOf(feignScriptAppBox.getSelectedItem());
            if (StringUtils.isBlank(feignEnvTextField.getText().trim())) {
                fireTestFeignFunction(scriptCode, null);
                return;
            }

            String[] envs = feignEnvTextField.getText().trim().split(",");
            DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
            for (String env : envs) {
                //显示的一个图标加上标题
                AnAction documentation = new AnAction("Fire-Test with " + app + ":" + env, "Fire-Test with " + app + ":" + env, FunctionCallTool.FEIGN_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        fireTestFeignFunction(scriptCode, env);
                    }
                };
                controllerActionGroup.add(documentation); // 将动作添加到动作组中
            }

            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("FeignCommandPopup", controllerActionGroup).getComponent();
            popupMenu.show(testButton, 0, 0);
        };
        testButton.addActionListener(testListener);

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String script = scriptField.getText();
            String selectedApp = (String) feignScriptAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }

            SettingsStorageHelper.HttpCommand httpCommand = new SettingsStorageHelper.HttpCommand();
            httpCommand.setScript(StringUtils.isBlank(script) ? SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript() : script);
            httpCommand.setEnvs(StringUtils.isBlank(feignEnvTextField.getText().trim()) ? SettingsStorageHelper.DEF_FEIGN_COMMAND.getEnvs() : Arrays.asList(feignEnvTextField.getText().trim().split(",")));
            SettingsStorageHelper.setAppFeignCommand(toolWindow.getProject(), selectedApp, httpCommand);
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "FeignClient Command is saved");
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
        buttonPanel.add(testButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 3; // 新的一行
        gbc.gridwidth = 5; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private void fireTestFeignFunction(String scriptCode, String env) {
        ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Fire-Test generate function, please wait ...", false) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    GroovyShell groovyShell = new GroovyShell();
                    Script script = groovyShell.parse(scriptCode);
                    HashMap<String, String> params = new HashMap<>();
                    params.put("param1", "v1");

                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("T-header", "1");
                    Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, "third-api", null, "POST", "/health", params, headers, "{}"});
                    String ret = build == null ? "" : String.valueOf(build);
                    TestkitHelper.alert(getProject(), Messages.getInformationIcon(), "Test generate function success\n" + ret);
                } catch (Throwable ex) {
                    TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Test generate function error\n" + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                }
            }
        });
    }

    private JPanel createSqlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.emptyInsets(); // 添加内边距以美化布局

        JTextArea tipArea = createTips("After configuring the database information, you can use SQL tool\n" +
                "Blew is config example\n" +
                "#Multiple database are supported\n" +
                "datasource.nam1.url=jdbc:mysql:///test\n" +
                "datasource.nam1.username=your_account\n" +
                "datasource.nam1.password=your_pwd");
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        panel.add(tipArea, gbc);
        showDatasourceButton = new JButton(HIDDEN_ICON);
        showDatasourceButton.setToolTipText("Show/hidden config");
        showDatasourceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean visible = datasourcePropertiesField.isVisible();
                String dats = "";
                if(!visible){
                    SettingsStorageHelper.SqlConfig sqlConfig = SettingsStorageHelper.getSqlConfig(toolWindow.getProject());
                    if (StringUtils.isNotBlank(sqlConfig.getProperties())) {
                        dats = sqlConfig.getProperties();
                    }
                }
                datasourcePropertiesField.setText(dats);
                datasourcePropertiesField.setVisible(!visible);
                showDatasourceButton.setIcon(!visible ? SHOW_ICON : HIDDEN_ICON);
            }
        });

        JButton resetButton = new JButton(AllIcons.Actions.Rollback);
        resetButton.setToolTipText("Use demo config template");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                datasourcePropertiesField.setVisible(true);
                showDatasourceButton.setIcon(SHOW_ICON);
                datasourcePropertiesField.setText(SettingsStorageHelper.datasourceTemplateProperties);
            }
        });

        // 创建按钮面板
        JPanel button1Panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); // 左对齐，按钮间距5
        button1Panel.add(showDatasourceButton);
        button1Panel.add(resetButton);

        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.weighty = 0.0;
//        gbc.insets = JBUI.emptyInsets();
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(button1Panel, gbc);


//        EditorTextField editorTextField = new EditorTextField(dummyFile.getViewProvider().getDocument(), project, PropertiesLanguage.INSTANCE.getAssociatedFileType(), true, false);
        datasourcePropertiesField = new LanguageTextField(PropertiesLanguage.INSTANCE, toolWindow.getProject(), "", false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1; // 占据1
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
//        gbc.insets = JBUI.insets(5);
        panel.add(new JBScrollPane(datasourcePropertiesField), gbc);

        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));


        JButton testButton = new JButton(FIRE_TEST_ICON);
        testButton.setPreferredSize(new Dimension(20, 20));
        testButton.setToolTipText("Test connection");
        ActionListener testListener = e -> {
            String datasourceProperties = datasourcePropertiesField.getText();
            List<SettingsStorageHelper.DatasourceConfig> datasourceConfigs = null;
            try {
                datasourceConfigs = SettingsStorageHelper.SqlConfig.parseDatasources(datasourceProperties);
            } catch (Throwable ex) {
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.ERROR, "Config must be properties model");
                return;
            }
//            properties文件格式
//            datasource.name1.url=
//            datasource.name1.username=
//            datasource.name1.password=
            //            datasource.name2.url
//            datasource.name2.username
//            datasource.name2.password
//            我现在需要找出datasource开头的内容，并且根据参数挨个验证数据库连接的连通性
//            name是为每个链接取的名字

            List<SettingsStorageHelper.DatasourceConfig> finalDatasourceConfigs = datasourceConfigs;
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing test connection, please wait ...", false) {
                                                  @Override
                                                  public void run(ProgressIndicator indicator) {
                                                      // 存储测试结果
                                                      Map<String, String> results = new HashMap<>();

                                                      // 遍历每个数据源配置，逐个验证连接
                                                      for (SettingsStorageHelper.DatasourceConfig config : finalDatasourceConfigs) {
                                                          // 测试连接
                                                          Object result = MysqlUtil.testConnectionAndClose(config);
                                                          // 存储结果
                                                          results.put(config.getName(), !(result instanceof String) ? ("connect success, DDL: " + result) : result.toString());
                                                      }
                                                      TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "Test result is " + JSON.toJSONString(results));
                                                  }
                                              }
            );
        };

        ;
        testButton.addActionListener(testListener);

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String propertiesStr = datasourcePropertiesField.getText();
            List<SettingsStorageHelper.DatasourceConfig> datasourceConfigs = null;
            try {
                datasourceConfigs = SettingsStorageHelper.SqlConfig.parseDatasources(propertiesStr);
            } catch (Throwable ex) {
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.ERROR, "Config must be properties model");
                return;
            }
            SettingsStorageHelper.SqlConfig sqlConfig = SettingsStorageHelper.getSqlConfig(toolWindow.getProject());
            sqlConfig.setProperties(propertiesStr.trim());
            SettingsStorageHelper.setSqlConfig(toolWindow.getProject(), sqlConfig);
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Config was saved, " + TestkitHelper.getPluginName() + " will test connection");

            List<SettingsStorageHelper.DatasourceConfig> finalDatasourceConfigs = datasourceConfigs;
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing test connection, please wait ...", false) {
                                                  @Override
                                                  public void run(ProgressIndicator indicator) {
                                                      Map<String, String> results = new HashMap<>();
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
                                                          results.put(config.getName(), !(result instanceof String) ? ("connect success, DDL: " + result) : result.toString());
                                                      }

                                                      RuntimeHelper.updateValidDatasources(toolWindow.getProject().getName(), valids, ddls, writes);
                                                      toolWindow.refreshSqlDatasources();
                                                      TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "SQL Tool is updated, Connection result is " + JSON.toJSONString(results));
                                                  }
                                              }
            );
        };
        saveButton.addActionListener(saveListener);

        // 关闭按钮
        JButton closeButton = new JButton("OK");
        closeButton.addActionListener(e ->

        {
            saveListener.actionPerformed(e);
            // 关闭当前窗口
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) {
                window.dispose();
            }
        });

        // 将按钮添加到按钮面板
        buttonPanel.add(testButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);

        // 将按钮面板添加到主面板的底部
        gbc.gridx = 0;
        gbc.gridy = 4; // 新的一行
        gbc.gridwidth = 1; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }


    /**
     * 解析 properties 格式的字符串，提取所有数据源配置
     *
     * @param propertiesText properties 文件格式的字符串
     * @return 数据源名称到配置的映射
     */
    public static List<SettingsStorageHelper.DatasourceConfig> parseDatasourceConfigs(String propertiesText) {
        Properties properties = new Properties();
        try {
            // 加载字符串为 properties 对象
            properties.load(new java.io.StringReader(propertiesText));
        } catch (Exception e) {
            throw new RuntimeException("parse properties error", e);
        }

        // 提取所有以 "datasource" 开头的配置
        Map<String, Map<String, String>> groupedConfigs = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("datasource."))
                .collect(Collectors.groupingBy(
                        key -> key.split("\\.")[1], // 按数据源名称分组
                        Collectors.toMap(
                                key -> key.split("\\.")[2], // 提取属性名（url, username, password）
                                properties::getProperty // 提取属性值
                        )
                ));

        // 转换为 DatasourceConfig 对象
        List<SettingsStorageHelper.DatasourceConfig> datasourceConfigs = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : groupedConfigs.entrySet()) {
            String name = entry.getKey();
            Map<String, String> configMap = entry.getValue();
            SettingsStorageHelper.DatasourceConfig config = new SettingsStorageHelper.DatasourceConfig();
            config.setUrl(configMap.get("url"));
            config.setUsername(configMap.get("username"));
            config.setPassword(configMap.get("password"));
            config.setName(name);
            datasourceConfigs.add(config);
        }

        return datasourceConfigs;
    }

    private JPanel createTraceOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        Dimension labelDimension = new Dimension(100, 20);
        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());

        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局
        JLabel traceLabel = new JLabel("Trace");
        traceToggleButton = new OnOffButton();
        JPanel labelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        labelButtonPanel.add(traceLabel, BorderLayout.WEST);
        labelButtonPanel.add(traceToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(labelButtonPanel, gbc);

        JLabel traceDetailLabel = new JLabel("Enabling trace helps you analyze system links");
        traceDetailLabel.setForeground(new Color(0x72A96B));
        traceDetailLabel.setFont(new Font("Arial", Font.BOLD, 13));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(traceDetailLabel, gbc);

        traceOptionsPanel = new JPanel(new GridBagLayout());
        traceOptionsPanel.setVisible(false);
        traceToggleButton.addActionListener(e -> {
            traceOptionsPanel.setVisible(traceToggleButton.isSelected());
            if (traceToggleButton.isSelected()) {
                traceDetailLabel.setText("We will use bytecode staking to enhance the classes to support link trace");
            } else {
                traceDetailLabel.setText("Enabling trace helps you analyze system links");
            }
            SettingsStorageHelper.TraceConfig nowConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
            if (!Objects.equals(nowConfig.isEnable(), traceToggleButton.isSelected())) {
                nowConfig.setEnable(traceToggleButton.isSelected());
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            }
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace is " + (traceToggleButton.isSelected() ? "enable" : "disable"));
        });

        if (traceConfig.isEnable()) {
            traceToggleButton.setSelected(true);
            traceOptionsPanel.setVisible(true);
            traceDetailLabel.setText("We will use bytecode staking to enhance the classes to support link trace");
        }
        gbc.gridwidth = 1; // Reset gridwidth


        JLabel traceWebLabel = new JLabel("Trace Web");
        traceWebToggleButton = new OnOffButton();
        JPanel traceWebLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        traceWebLabelButtonPanel.add(traceWebLabel, BorderLayout.WEST);
        traceWebLabelButtonPanel.add(traceWebToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        traceOptionsPanel.add(traceWebLabelButtonPanel, gbc);

        JLabel traceWebDetailLabel = new JLabel("Trace Web can intercept the DispatcherServlet for spring-web");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        traceOptionsPanel.add(traceWebDetailLabel, gbc);

        traceWebToggleButton.addActionListener(e -> {
            if (traceWebToggleButton.isSelected()) {
                traceWebDetailLabel.setText("We will trace the DispatcherServlet for spring-web");
            } else {
                traceWebDetailLabel.setText("Trace Web can intercept the DispatcherServlet for spring-web");
            }
            SettingsStorageHelper.TraceConfig nowConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
            if (!Objects.equals(nowConfig.isTraceWeb(), traceWebToggleButton.isSelected())) {
                nowConfig.setTraceWeb(traceWebToggleButton.isSelected());
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            }
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace Web is " + (traceWebToggleButton.isSelected() ? "enable" : "disable"));
        });

        if (traceConfig.isTraceWeb()) {
            traceWebToggleButton.setSelected(true);
            traceWebDetailLabel.setText("We will trace the DispatcherServlet for spring-web");
        }


        JLabel traceMybatisLabel = new JLabel("Trace Mybatis");
        traceMybatisToggleButton = new OnOffButton();
        JPanel traceMybatisLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        traceMybatisLabelButtonPanel.add(traceMybatisLabel, BorderLayout.WEST);
        traceMybatisLabelButtonPanel.add(traceMybatisToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        traceOptionsPanel.add(traceMybatisLabelButtonPanel, gbc);

        JLabel traceMybatisDetailLabel = new JLabel("Trace Mybatis can intercept the mapper");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        traceOptionsPanel.add(traceMybatisDetailLabel, gbc);

        traceMybatisToggleButton.addActionListener(e -> {
            if (traceMybatisToggleButton.isSelected()) {
                traceMybatisDetailLabel.setText("We will use the mybatis Interceptor mechanism to trace Executor");
            } else {
                traceMybatisDetailLabel.setText("Trace Mybatis can intercept the mapper");
            }
            SettingsStorageHelper.TraceConfig nowConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
            if (!Objects.equals(nowConfig.isTraceMybatis(), traceMybatisToggleButton.isSelected())) {
                nowConfig.setTraceMybatis(traceMybatisToggleButton.isSelected());
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            }
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace Mybatis is " + (traceMybatisToggleButton.isSelected() ? "enable" : "disable"));
        });

        if (traceConfig.isTraceMybatis()) {
            traceMybatisToggleButton.setSelected(true);
            traceMybatisDetailLabel.setText("We will use the mybatis Interceptor mechanism to trace Executor");
        }


        JLabel logMybatisLabel = new JLabel("Log Mybatis");
        logMybatisToggleButton = new OnOffButton();
        JPanel logMybatisLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        logMybatisLabelButtonPanel.add(logMybatisLabel, BorderLayout.WEST);
        logMybatisLabelButtonPanel.add(logMybatisToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        traceOptionsPanel.add(logMybatisLabelButtonPanel, gbc);

        JLabel logMybatisDetailLabel = new JLabel("Sql output in mybatis format is easy to understand");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        traceOptionsPanel.add(logMybatisDetailLabel, gbc);

        logMybatisToggleButton.addActionListener(e -> {
            if (logMybatisToggleButton.isSelected()) {
                logMybatisDetailLabel.setText("We will output sql in mybatis format");
            } else {
                logMybatisDetailLabel.setText("Sql output in mybatis format is easy to understand");
            }
            SettingsStorageHelper.TraceConfig nowConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
            if (!Objects.equals(nowConfig.isLogMybatis(), logMybatisToggleButton.isSelected())) {
                nowConfig.setLogMybatis(logMybatisToggleButton.isSelected());
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            }
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Log Mybatis is " + (logMybatisToggleButton.isSelected() ? "enable" : "disable"));
        });

        if (traceConfig.isLogMybatis()) {
            logMybatisToggleButton.setSelected(true);
            logMybatisDetailLabel.setText("We will output sql in mybatis format");
        }


        JLabel packageLabel = new JLabel("Package:");
        packageLabel.setToolTipText("In the package's class you want to trace, multiple use,split;\napp.three.package means that the first three packages of the startup class are automatically intercepted");
        packageLabel.setPreferredSize(labelDimension);
        tracePackagesField = new JBTextField(traceConfig.getPackages(), 20);
        tracePackagesField.getEmptyText().setText("In the package's class you want to trace, multiple use,split; Default:app.three.package means that the first three packages of the startup class");

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        traceOptionsPanel.add(packageLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        traceOptionsPanel.add(tracePackagesField, gbc);

        JLabel classSuffixLabel = new JLabel("Class Suffix:");
        classSuffixLabel.setToolTipText("Class with the suffix you want to trace, multiple use,split");
        classSuffixLabel.setPreferredSize(labelDimension);
        traceClassSuffixField = new JBTextField(traceConfig.getClsSuffix(), 20);
        traceClassSuffixField.getEmptyText().setText("Class with the suffix you want to trace, multiple use,split;");

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        traceOptionsPanel.add(classSuffixLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        traceOptionsPanel.add(traceClassSuffixField, gbc);


        JLabel whiteClassLabel = new JLabel("Allow Class:");
        whiteClassLabel.setToolTipText("Tracing class allow list, multiple use,split");
        whiteClassLabel.setPreferredSize(labelDimension);
        traceWhiteListField = new JBTextField(traceConfig.getWhites(), 20);
        traceWhiteListField.getEmptyText().setText("Tracing class allow list, multiple use,split");

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.0;
        traceOptionsPanel.add(whiteClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        traceOptionsPanel.add(traceWhiteListField, gbc);

        JLabel blackClassLabel = new JLabel("Deny Class:");
        blackClassLabel.setToolTipText("Tracing class deny list, multiple use,split");
        blackClassLabel.setPreferredSize(labelDimension);
        traceBlackListField = new JBTextField(traceConfig.getBlacks(), 20);
        traceBlackListField.getEmptyText().setText("Tracing class deny list, multiple use,split");

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0.0;
        traceOptionsPanel.add(blackClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        traceOptionsPanel.add(traceBlackListField, gbc);


//        JLabel monitorPrivateLabel = new JLabel("Single Class Depth:");
//        monitorPrivateLabel.setToolTipText("Maximum tracking depth for a single class");
//        monitorPrivateLabel.setPreferredSize(labelDimension);
//
//
//        gbc.gridx = 0;
//        gbc.gridy = 8;
//        gbc.weightx = 0.0;
//        traceOptionsPanel.add(monitorPrivateLabel, gbc);

//        traceSingleClsDepthField = new JBTextField(String.valueOf(traceConfig.getSingleClsDepth()), 10);
//        traceSingleClsDepthField.getEmptyText().setText("Maximum tracking depth for a single class, Default: 2");
//        gbc.gridx = 1;
//        gbc.weightx = 1.0;
//        traceOptionsPanel.add(traceSingleClsDepthField, gbc);


        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(traceOptionsPanel, gbc);


        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 9; // 新的一行
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
                if (!traceOptionsPanel.isVisible()) {
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "No config need to save");
                    return;
                }
//                String text = traceSingleClsDepthField.getText();
//                if (StringUtils.isNotBlank(text) && !StringUtils.isNumeric(text.trim())) {
//                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Single Class Depth must be integer");
//                    return;
//                }
//                int singleClsDepth = Integer.parseInt(traceSingleClsDepthField.getText().trim());
//                if (singleClsDepth > 100) {
//                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Single Class Depth max num is 100");
//                    return;
//                }


                SettingsStorageHelper.TraceConfig nowConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
                nowConfig.setPackages(tracePackagesField.getText().trim());
                nowConfig.setClsSuffix(traceClassSuffixField.getText().trim());
                nowConfig.setWhites(traceWhiteListField.getText().trim());
                nowConfig.setBlacks(traceBlackListField.getText().trim());
//                nowConfig.setSingleClsDepth(singleClsDepth);
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace config was saved");
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
        gbc.gridy = 10; // 新的一行
        gbc.gridwidth = 2; // 占据两列
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

        JTextArea tipArea = createTips("Here properties takes precedence over spring.properties, you can customize some configurations for local startup\nFor example: customize the log level locally");
        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(tipArea, gbc);


//        EditorTextField editorTextField = new EditorTextField(dummyFile.getViewProvider().getDocument(), project, PropertiesLanguage.INSTANCE.getAssociatedFileType(), true, false);

        LanguageTextField propertiesField = new LanguageTextField(PropertiesLanguage.INSTANCE, toolWindow.getProject(), SettingsStorageHelper.getAppProperties(toolWindow.getProject(), ""), false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 2;
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
        comboBoxLabel.setLabelFor(propertiesAppBox);

        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 1; // 同一行
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
                    propertiesField.setText(SettingsStorageHelper.getAppProperties(toolWindow.getProject(), selectedApp));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default properties");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                propertiesField.setText(SettingsStorageHelper.defProperties);
            }
        });
        gbc.gridx = 2;  // 放在同一行的尾部
        gbc.gridy = 1;
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
                SettingsStorageHelper.setAppProperties(toolWindow.getProject(), selectedApp, null);
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Properties is blank");
                return;
            }
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(propertiesStr));
            } catch (Throwable ex) {
                TestkitHelper.notify(toolWindow.getProject(), NotificationType.WARNING, "Properties parsed error<br/> please check");
                return;
            }
            SettingsStorageHelper.setAppProperties(toolWindow.getProject(), selectedApp, propertiesStr);
            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Properties is saved");
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
        gbc.gridy = 3; // 新的一行
        gbc.gridwidth = 3; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }
}
