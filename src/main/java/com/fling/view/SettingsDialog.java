package com.fling.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fling.FlingHelper;
import com.fling.SettingsStorageHelper;
import com.fling.RuntimeAppHelper;
import com.fling.util.HttpUtil;
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
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.OnOffButton;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringReader;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SettingsDialog {

    private static final Icon FIRE_TEST_ICON = IconLoader.getIcon("/icons/fire-test.svg", FlingToolWindow.class);


    private FlingToolWindow toolWindow;

    private JDialog dialog;

    private JTextField flexibleTestPackageNameField;

    private ComboBox<String> scriptAppBox;

    private ComboBox<String> controllerScriptAppBox;

    private ComboBox<String> propertiesAppBox;

    private ComboBox<String> sqlAppBox;

    private JTextField controllerEnvTextField;


    public SettingsDialog(FlingToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        init();
    }

    public void visible(){
        refreshSettings();
        dialog.setVisible(true);
    }

    public void initApps(List<String> apps) {
        if (apps == null) {
            return;
        }
        apps.stream().forEach(new Consumer<String>() {
            @Override
            public void accept(String app) {
                scriptAppBox.addItem(app);
                controllerScriptAppBox.addItem(app);
                propertiesAppBox.addItem(app);
                sqlAppBox.addItem(app);
            }
        });
    }

    private void refreshSettings(){
        flexibleTestPackageNameField.setText(SettingsStorageHelper.getFlexibleTestPackage(toolWindow.getProject()));
        RuntimeAppHelper.VisibleApp selectedApp = RuntimeAppHelper.getSelectedApp(toolWindow.getProject().getName());
        String selectedAppName = selectedApp == null ? null : selectedApp.getAppName();
        if (selectedAppName == null) {
            if (scriptAppBox.getItemCount() > 0) {
                scriptAppBox.setSelectedIndex(0);
            }
            if (controllerScriptAppBox.getItemCount() > 0) {
                controllerScriptAppBox.setSelectedIndex(0);
            }
            if (propertiesAppBox.getItemCount() > 0) {
                // 选择第一个选项
                propertiesAppBox.setSelectedIndex(0);
            }
        } else {
            if (scriptAppBox.getItemCount() > 0) {
                scriptAppBox.setSelectedItem(selectedAppName);
            }
            if (controllerScriptAppBox.getItemCount() > 0) {
                controllerScriptAppBox.setSelectedItem(selectedAppName);
            }
            if (propertiesAppBox.getItemCount() > 0) {
                // 选择第一个选项
                propertiesAppBox.setSelectedItem(selectedAppName);
            }
        }
    }

    private void init(){
        dialog = new JDialog((Frame) null, FlingHelper.getPluginName() + " Settings", true);
        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 左侧选项列表
        String[] options = {"fling","trace", "tool-script", "spring-properties","controller-command","sql-analysis"};
        JBList<String> optionList = new JBList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 默认选中第一个选项
        optionList.setSelectedIndex(0);
        // 右侧内容显示区域
        JPanel rightContent = new JPanel(new CardLayout());
        rightContent.add(createBasicOptionPanel(), "fling");
        rightContent.add(createTraceOptionPanel(), "trace");
        rightContent.add(createSqlPanel(), "sql-analysis");
        rightContent.add(createScriptOptionPanel(), "tool-script");
        rightContent.add(createPropertiesOptionPanel(), "spring-properties");
        rightContent.add(createControllerOptionPanel(), "controller-command");
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
        int width = (int) (screenSize.width * 0.8);
        int height = (int) (screenSize.height * 0.8);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(null); // 居中显示
    }


    private JPanel createBasicOptionPanel() {
        flexibleTestPackageNameField = new JTextField(SettingsStorageHelper.getFlexibleTestPackage(toolWindow.getProject()), 20);
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

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default package");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flexibleTestPackageNameField.setText(SettingsStorageHelper.defFlexibleTestPackage);
            }
        });
        gbc.gridx = 2;  // 放在同一行的尾部
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;  // 不强制按钮填满可用空间
        gbc.anchor = GridBagConstraints.EAST;  // 靠右对齐
        panel.add(newButton, gbc);


        // Method Call Label
        JLabel methodCallLabel = new JLabel("Method-call");
        JLabel methodCallDetailLabel = new JLabel("Method-call and flexible-test need enable this");

        // Button
        OnOffButton methodCallButton = new OnOffButton();
        methodCallButton.addActionListener(e -> {
            if (methodCallButton.isSelected()) {
                SettingsStorageHelper.setEnableSideServer(toolWindow.getProject(), true);
                methodCallDetailLabel.setText("We will start a side server to provide method-call and flexible-test");
            } else {
                SettingsStorageHelper.setEnableSideServer(toolWindow.getProject(), false);
                methodCallDetailLabel.setText("Method-call and flexible-test need enable this");
            }
            FlingHelper.refresh(toolWindow.getProject());
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Method-call is " + (methodCallButton.isSelected() ? "enable" : "disable"));
        });

        if (SettingsStorageHelper.isEnableSideServer(toolWindow.getProject())) {
            methodCallButton.setSelected(true); // 示例中默认启用
            // 设置初始文本
            methodCallDetailLabel.setText("We will start a side server to provide method-call and flexible-test");
        }

        // 嵌套 Panel，用于将 Label 和 Button 合并到一起
        JPanel labelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        labelButtonPanel.add(methodCallLabel, BorderLayout.WEST);
        labelButtonPanel.add(methodCallButton, BorderLayout.EAST);

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
        gbc.fill = GridBagConstraints.HORIZONTAL; // 水平方向填充
        panel.add(methodCallDetailLabel, gbc);



        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 2; // 新的一行
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
                String packageName = flexibleTestPackageNameField.getText();
                if (StringUtils.isBlank(packageName)) {
                    SettingsStorageHelper.setFlexibleTestPackage(toolWindow.getProject(), null);
                    FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Package name is blank, use default package name");
                } else {
                    SettingsStorageHelper.setFlexibleTestPackage(toolWindow.getProject(), packageName.trim());
                    FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Package name is refreshed by " + packageName.trim());
                }

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
        gbc.gridy = 3; // 新的一行
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);
        return panel;
    }


    private JPanel createScriptOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

        LanguageTextField scriptField = new LanguageTextField(JavaLanguage.INSTANCE, toolWindow.getProject(), SettingsStorageHelper.defScript, false);

        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        String[] apps = new String[]{};

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
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
                    scriptField.setText(SettingsStorageHelper.defScript);
                } else {
                    String appScript = SettingsStorageHelper.getAppScript(toolWindow.getProject(), selectedApp);
                    scriptField.setText(appScript == null ? SettingsStorageHelper.defScript : appScript);
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default script");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptField.setText(SettingsStorageHelper.defScript);
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

        //测试按钮
        JButton testButton = new JButton(FIRE_TEST_ICON);
        testButton.setPreferredSize(new Dimension(20, 20));
        testButton.setToolTipText("Fire-Test tool-script use hello world code");
        ActionListener testListener = e -> {
            Object selectedItem = scriptAppBox.getSelectedItem();
            if (selectedItem == null) {
                FlingHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please select a app");
                return;
            }
            String script = scriptField.getText();


            RuntimeAppHelper.VisibleApp visibleApp = Optional.ofNullable(RuntimeAppHelper.getVisibleApps(toolWindow.getProject().getName()))
                    .orElse(new ArrayList<>())
                    .stream()
                    .filter(new Predicate<RuntimeAppHelper.VisibleApp>() {
                        @Override
                        public boolean test(RuntimeAppHelper.VisibleApp visibleApp) {
                            return Objects.equals(visibleApp.getAppName(), selectedItem);
                        }
                    })
                    .findFirst()
                    .orElse(null);
            if (visibleApp == null) {
                FlingHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please start Application named " + selectedItem);
                return;
            }
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Fire-Test tool-script, please wait ...", false) {

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        String code = "import java.util.Date;\n" +
                                "\n" +
                                "class HelloFling {\n" +
                                "\n" +
                                "    public String hello(String name, Date date) {\n" +
                                "        return \"Hello \" + name + \", now is \" + date;\n" +
                                "    }\n" +
                                "}";
                        JSONObject params = new JSONObject();
                        params.put("code", code);
                        params.put("methodName", "hello");
                        params.put("argTypes", "[\"java.lang.String\",\"java.util.Date\"]");
                        params.put("args", "[\"Fling\"," + System.currentTimeMillis() + "]");
                        JSONObject req = new JSONObject();
                        req.put("method", "flexible-test");
                        req.put("params", params);
                        req.put("script", script);
                        SettingsStorageHelper.MonitorConfig monitorConfig = SettingsStorageHelper.getMonitorConfig(getProject());
                        req.put("monitor", monitorConfig.isEnable());
                        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
                        String reqId;
                        try {
                            JSONObject submitRes = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", req, JSONObject.class);
                            if (submitRes == null) {
                                FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-script error\nFailed to submit req\nsubmitRes is null");
                                return;
                            }
                            if (!submitRes.getBooleanValue("success") || submitRes.getString("data") == null) {
                                FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-script error\nFailed to submit req\n" + submitRes.getString("message"));
                                return;
                            }
                            reqId = submitRes.getString("data");
                        } catch (Exception ex) {
                            FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-script error\nFailed to submit req\n" + ex.getClass().getSimpleName() + "," + ex.getMessage());
                            return;
                        }

                        HashMap<String, Object> getRetReq = new HashMap<>();
                        getRetReq.put("method", "get_task_ret");
                        params.clear();
                        params.put("reqId", reqId);
                        getRetReq.put("params", params);

                        JSONObject result = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", getRetReq, JSONObject.class);
                        if (result == null || !result.getBooleanValue("success")) {
                            FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-script error\n" + (result == null ? "result is null" : result.getString("message")));
                            return;
                        }
                        FlingHelper.alert(getProject(), Messages.getInformationIcon(), "Test tool-script success\n" + result.get("data"));
                    } catch (Throwable ex) {
                        FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test tool-script error\n" + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                    }
                }
            });

        };
        testButton.addActionListener(testListener);

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String script = scriptField.getText();
            String selectedApp = (String) scriptAppBox.getSelectedItem();
            if (selectedApp == null) {
                return;
            }
            if (StringUtils.isBlank(script)) {
                SettingsStorageHelper.setAppScript(toolWindow.getProject(), selectedApp, null);
                FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Script is blank");
                return;
            }
            SettingsStorageHelper.setAppScript(toolWindow.getProject(), selectedApp, script);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Script is saved");
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
        gbc.gridy = 2; // 新的一行
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
        LanguageTextField scriptField = new LanguageTextField(GroovyLanguage.INSTANCE, toolWindow.getProject(), SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), false);
        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 5; // 占据3列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        String[] apps = new String[]{};

        // 添加标签到新行
        gbc.gridx = 0;
        gbc.gridy = 0; // 新的一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        controllerEnvTextField = new JTextField(10); // 设定文本框的列数
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
        envLabel.setToolTipText("Optional environment list; multiple use , split");

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        controllerScriptAppBox = new ComboBox<>(apps);


        gbc.gridx = 0;
        gbc.gridy = 0; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(comboBoxLabel, gbc);

        // 添加组合框到标签右边
        gbc.gridx = 1;
        gbc.gridy = 0; // 同一行
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(controllerScriptAppBox, gbc);


        gbc.gridx = 2;
        gbc.gridy = 0; // 第一行
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(envLabel, gbc);

        // 添加环境文本框
        gbc.gridx = 3;
        gbc.gridy = 0; // 同一行
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
                    SettingsStorageHelper.ControllerCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(toolWindow.getProject(), selectedApp);
                    scriptField.setText(controllerCommand.getScript() == null ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript() : controllerCommand.getScript());
                    controllerEnvTextField.setText(CollectionUtils.isEmpty(controllerCommand.getEnvs()) ? "" : String.join(",", controllerCommand.getEnvs()));
                }
            }
        });

        // 添加新按钮到组合框右边
        JButton newButton = new JButton(AllIcons.Actions.Rollback);
        newButton.setToolTipText("Use default script");
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scriptField.setText(SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript());
                controllerEnvTextField.setText("");
            }
        });
        gbc.gridx = 4;  // 放在同一行的尾部
        gbc.gridy = 0;
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
            RuntimeAppHelper.VisibleApp selectedApp = RuntimeAppHelper.getSelectedApp(toolWindow.getProject().getName());
            String scriptCode = scriptField.getText();
            String env = StringUtils.isBlank(controllerEnvTextField.getText().trim()) ? "local" : controllerEnvTextField.getText().trim().split(",")[0];
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Fire-Test generate function, please wait ...", false) {

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        GroovyShell groovyShell = new GroovyShell();
                        Script script = groovyShell.parse(scriptCode);
                        HashMap<String, String> params = new HashMap<>();
                        params.put("param1", "v1");
                        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.getPort(), "POST", "/health", params, "{}"});
                        String ret = build == null ? "" : String.valueOf(build);
                        FlingHelper.alert(getProject(), Messages.getInformationIcon(), "Test generate function success\n" + ret);
                    } catch (Throwable ex) {
                        FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Test generate function error\n" + ex.getClass().getSimpleName() + ", " + ex.getMessage());
                    }
                }
            });

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

            SettingsStorageHelper.ControllerCommand controllerCommand = new SettingsStorageHelper.ControllerCommand();
            controllerCommand.setScript(StringUtils.isBlank(script) ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript() : script);
            controllerCommand.setEnvs(StringUtils.isBlank(controllerEnvTextField.getText().trim()) ? SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getEnvs() : Arrays.asList(controllerEnvTextField.getText().trim().split(",")));
            SettingsStorageHelper.setAppControllerCommand(toolWindow.getProject(), selectedApp, controllerCommand);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "ControllerCommand is saved");
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
        gbc.gridy = 2; // 新的一行
        gbc.gridwidth = 5; // 占据三列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private JPanel createSqlPanel(){
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

        String[] apps = new String[]{};

        // 添加标签和组合框
        JLabel comboBoxLabel = new JLabel("App:");
        sqlAppBox = new ComboBox<>(apps);


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
        panel.add(sqlAppBox, gbc);

        sqlAppBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedApp = (String) sqlAppBox.getSelectedItem();

            }
        });
        return panel;
    }

    private JPanel createTraceOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        Dimension labelDimension = new Dimension(100, 20);
        SettingsStorageHelper.MonitorConfig monitorConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());

        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局
        JLabel traceLabel = new JLabel("Trace");
        OnOffButton traceToggleButton = new OnOffButton();
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
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(traceDetailLabel, gbc);

        JPanel monitorOptionsPanel = new JPanel(new GridBagLayout());
        monitorOptionsPanel.setVisible(false);
        traceToggleButton.addActionListener(e -> {
            monitorOptionsPanel.setVisible(traceToggleButton.isSelected());
            if (traceToggleButton.isSelected()) {
                traceDetailLabel.setText("We will use bytecode staking to enhance the classes to support link trace");
            } else {
                traceDetailLabel.setText("Enabling trace helps you analyze system links");
            }
            SettingsStorageHelper.MonitorConfig nowConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());
            nowConfig.setEnable(traceToggleButton.isSelected());
            SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace is " + (traceToggleButton.isSelected() ? "enable" : "disable"));
            FlingHelper.refresh(toolWindow.getProject());
        });

        if (monitorConfig.isEnable()) {
            traceToggleButton.setSelected(true);
            monitorOptionsPanel.setVisible(true);
            traceDetailLabel.setText("We will use bytecode staking to enhance the classes to support link trace");
        }
        gbc.gridwidth = 1; // Reset gridwidth



        JLabel traceWebLabel = new JLabel("Trace Web");
        OnOffButton traceWebToggleButton = new OnOffButton();
        JPanel traceWebLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        traceWebLabelButtonPanel.add(traceWebLabel, BorderLayout.WEST);
        traceWebLabelButtonPanel.add(traceWebToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        monitorOptionsPanel.add(traceWebLabelButtonPanel, gbc);

        JLabel traceWebDetailLabel = new JLabel("Trace Web can intercept the DispatcherServlet for spring-web");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        monitorOptionsPanel.add(traceWebDetailLabel, gbc);

        traceWebToggleButton.addActionListener(e -> {
            if (traceWebToggleButton.isSelected()) {
                traceWebDetailLabel.setText("We will trace the DispatcherServlet for spring-web");
            } else {
                traceWebDetailLabel.setText("Trace Web can intercept the DispatcherServlet for spring-web");
            }
            SettingsStorageHelper.MonitorConfig nowConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());
            nowConfig.setMonitorWeb(traceWebToggleButton.isSelected());
            SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace Web is " + (traceWebToggleButton.isSelected() ? "enable" : "disable"));
            FlingHelper.refresh(toolWindow.getProject());
        });

        if (monitorConfig.isMonitorWeb()) {
            traceWebToggleButton.setSelected(true);
            traceWebDetailLabel.setText("We will trace the DispatcherServlet for spring-web");
        }


        JLabel traceMybatisLabel = new JLabel("Trace Mybatis");
        OnOffButton traceMybatisToggleButton = new OnOffButton();
        JPanel traceMybatisLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        traceMybatisLabelButtonPanel.add(traceMybatisLabel, BorderLayout.WEST);
        traceMybatisLabelButtonPanel.add(traceMybatisToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        monitorOptionsPanel.add(traceMybatisLabelButtonPanel, gbc);

        JLabel traceMybatisDetailLabel = new JLabel("Trace Mybatis can intercept the mapper");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        monitorOptionsPanel.add(traceMybatisDetailLabel, gbc);

        traceMybatisToggleButton.addActionListener(e -> {
            if (traceMybatisToggleButton.isSelected()) {
                traceMybatisDetailLabel.setText("We will use the mybatis Interceptor mechanism to trace Executor");
            } else {
                traceMybatisDetailLabel.setText("Trace Mybatis can intercept the mapper");
            }
            SettingsStorageHelper.MonitorConfig nowConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());
            nowConfig.setMonitorMybatis(traceMybatisToggleButton.isSelected());
            SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace Mybatis is " + (traceMybatisToggleButton.isSelected() ? "enable" : "disable"));
            FlingHelper.refresh(toolWindow.getProject());
        });

        if (monitorConfig.isMonitorMybatis()) {
            traceMybatisToggleButton.setSelected(true);
            traceMybatisDetailLabel.setText("We will use the mybatis Interceptor mechanism to trace Executor");
        }


        JLabel logMybatisLabel = new JLabel("Log Mybatis");
        OnOffButton logMybatisToggleButton = new OnOffButton();
        JPanel logMybatisLabelButtonPanel = new JPanel(new BorderLayout()); // 左对齐，5px 垂直和水平间隔
        logMybatisLabelButtonPanel.add(logMybatisLabel, BorderLayout.WEST);
        logMybatisLabelButtonPanel.add(logMybatisToggleButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0; // Reset weightx
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        monitorOptionsPanel.add(logMybatisLabelButtonPanel, gbc);

        JLabel logMybatisDetailLabel = new JLabel("We can output sql in mybatis format");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        monitorOptionsPanel.add(logMybatisDetailLabel, gbc);

        logMybatisToggleButton.addActionListener(e -> {
            if (logMybatisToggleButton.isSelected()) {
                logMybatisDetailLabel.setText("We will output sql in mybatis format");
            } else {
                logMybatisDetailLabel.setText("We can output sql in mybatis format");
            }
            SettingsStorageHelper.MonitorConfig nowConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());
            nowConfig.setLogMybatis(logMybatisToggleButton.isSelected());
            SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Log Mybatis is " + (logMybatisToggleButton.isSelected() ? "enable" : "disable"));
            FlingHelper.refresh(toolWindow.getProject());
        });

        if (monitorConfig.isLogMybatis()) {
            logMybatisToggleButton.setSelected(true);
            logMybatisDetailLabel.setText("We will output sql in mybatis format");
        }


        JLabel packageLabel = new JLabel("Package:");
        packageLabel.setToolTipText("Please fill in the package name of the class you want to trace, multiple use, split;\napp.three.package means that the first three packages of the startup class are automatically intercepted");
        packageLabel.setPreferredSize(labelDimension);
        JTextField packagesField = new JTextField(monitorConfig.getPackages(), 20);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(packageLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(packagesField, gbc);

        JLabel classSuffixLabel = new JLabel("Class Suffix:");
        classSuffixLabel.setToolTipText("Please fill in the class suffix of the class you want to trace, multiple use, split");
        classSuffixLabel.setPreferredSize(labelDimension);
        JTextField classSuffixField = new JTextField(monitorConfig.getClsSuffix(), 20);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(classSuffixLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(classSuffixField, gbc);


        JLabel whiteClassLabel = new JLabel("Allow Class:");
        whiteClassLabel.setToolTipText("Tracing class allow list, multiple use, split");
        whiteClassLabel.setPreferredSize(labelDimension);
        JTextField whiteListField = new JTextField(monitorConfig.getWhites(), 20);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(whiteClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(whiteListField, gbc);

        JLabel blackClassLabel = new JLabel("Deny Class:");
        blackClassLabel.setToolTipText("Tracing class deny list, multiple use, split");
        blackClassLabel.setPreferredSize(labelDimension);
        JTextField blackListField = new JTextField(monitorConfig.getBlacks(), 20);
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(blackClassLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(blackListField, gbc);


        JLabel monitorPrivateLabel = new JLabel("Method Type:");
        monitorPrivateLabel.setToolTipText("You can choose which methods of the class to trace, this config support hot reload");
        monitorPrivateLabel.setPreferredSize(labelDimension);
        JRadioButton monitorPrivate = new JBRadioButton("We will trace the public and protected methods of the selected class", false);
        monitorPrivate.addActionListener(e -> {
            if (monitorPrivate.isSelected()) {
                monitorPrivate.setText("We will trace the all methods of the selected class");
            } else {
                monitorPrivate.setText("We will trace the public and protected methods of the selected class");
            }
        });
        if (monitorConfig.isMonitorPrivate()) {
            monitorPrivate.setSelected(true);
            monitorPrivate.setText("We will trace the all methods of the selected class");
        }
//        monitorPrivate.setSelected(monitorConfig.isMonitorPrivate());
//        JTextField functionTypeField = new JTextField("private,public,protected", 20);
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0.0;
        monitorOptionsPanel.add(monitorPrivateLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        monitorOptionsPanel.add(monitorPrivate, gbc);


        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(monitorOptionsPanel, gbc);


        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 10; // 新的一行
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
                SettingsStorageHelper.MonitorConfig nowConfig = SettingsStorageHelper.getMonitorConfig(toolWindow.getProject());
                nowConfig.setPackages(packagesField.getText().trim());
                nowConfig.setClsSuffix(classSuffixField.getText().trim());
                nowConfig.setWhites(whiteListField.getText().trim());
                nowConfig.setBlacks(blackListField.getText().trim());
                SettingsStorageHelper.setMonitorConfig(toolWindow.getProject(), nowConfig);
                FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Trace config was saved");
                FlingHelper.refresh(toolWindow.getProject());
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
        gbc.gridy = 11; // 新的一行
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

//        EditorTextField editorTextField = new EditorTextField(dummyFile.getViewProvider().getDocument(), project, PropertiesLanguage.INSTANCE.getAssociatedFileType(), true, false);

        LanguageTextField propertiesField = new LanguageTextField(PropertiesLanguage.INSTANCE, toolWindow.getProject(), SettingsStorageHelper.getAppProperties(toolWindow.getProject(), ""), false);
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
                SettingsStorageHelper.setAppProperties(toolWindow.getProject(), selectedApp, null);
                FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Properties is blank");
                return;
            }
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(propertiesStr));
            } catch (Throwable ex) {
                FlingHelper.notify(toolWindow.getProject(), NotificationType.WARNING, "Properties parsed error<br/> please check");
                return;
            }
            SettingsStorageHelper.setAppProperties(toolWindow.getProject(), selectedApp, propertiesStr);
            FlingHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Properties is saved");
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
}
