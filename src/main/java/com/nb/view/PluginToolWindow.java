package com.nb.view;

import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LanguageTextField;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.tools.call_method.CallMethodTool;
import com.nb.tools.flexible_test.FlexibleTestTool;
import com.nb.tools.spring_cache.SpringCacheTool;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.nb.util.HttpUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;

public class PluginToolWindow {

    private static final Icon settingsIcon = IconLoader.getIcon("/icons/settings.svg", BasePluginTool.class);


    private Project project;
    private ToolWindow toolWindow;
    private JPanel windowContent;
    private JButton tipsButton;
    private JButton settingsButton;
    private JComboBox<String> toolBox;
    private JButton refreshButton;
    private JComboBox<String> appBox;
    private JDialog settingsDialog;
    private JPanel whitePanel = new JPanel();
    private Map<PluginToolEnum, BasePluginTool> tools = new HashMap<>();


    private JTextField flexibleTestPackageNameField;

    private LanguageTextField scriptField;



    public PluginToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        // 初始化主面板
        windowContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件之间的间距

        // 第一行
        // tips按钮
        tipsButton = new JButton(AllIcons.General.Information);
        tipsButton.setToolTipText("how to use?");
        tipsButton.setPreferredSize(new Dimension(32, 32));
        tipsButton.setMaximumSize(new Dimension(32, 32));
        tipsButton.setText(null); // 去除文本
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START; // 按钮靠左
        windowContent.add(tipsButton, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;


        settingsButton = new JButton(settingsIcon);
        settingsButton.setPreferredSize(new Dimension(32, 32));
        settingsButton.setMaximumSize(new Dimension(32, 32));
        windowContent.add(settingsButton, gbc);

        // 添加按钮点击事件以显示设置窗口
        settingsButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flexibleTestPackageNameField.setText(LocalStorageHelper.getFlexibleTestPackage(project));
                scriptField.setText(LocalStorageHelper.getScript(project));
                settingsDialog.setVisible(true);
            }
        });


        // 工具拉框A
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.CALL_METHOD.getCode(), PluginToolEnum.SPRING_CACHE.getCode(),PluginToolEnum.FLEXIBLE_TEST.getCode()});
        toolBox.setEnabled(false);
        toolBox.setPreferredSize(new Dimension(120, 32));
        toolBox.setMaximumSize(new Dimension(120, 32));
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        windowContent.add(toolBox, gbc);


        // 刷新按钮
        refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("refresh runtime spring project");
        refreshButton.setPreferredSize(new Dimension(32, 32));
        refreshButton.setMaximumSize(new Dimension(32, 32));
        refreshButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshVisibleApp();
                Notification notification = new Notification("No-Bug", "Info", "Runtime App Refresh Success!", NotificationType.INFORMATION);
                Notifications.Bus.notify(notification, project);
            }
        });
        gbc.gridx = 3;
        gbc.weightx = 0.0;
        windowContent.add(refreshButton, gbc);

        // 运行时app下拉框
        appBox = new ComboBox<>(new String[]{});
        new Thread(() -> {
            while (true) {
                try {
                    refreshVisibleApp();
                    Thread.sleep(60 * 1000); // 每隔一分钟调用一次
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }).start();

        gbc.gridx = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        windowContent.add(appBox, gbc);


//        下方用一个东西撑起来整个window的下半部分
//        当切换toolbox时根据选中的内容，从tools中找出对应的tool，然后用内部的内容填充该部分
//        初始化所有tool的面板，但是不加载
        tools.put(PluginToolEnum.CALL_METHOD, new CallMethodTool(this));
        tools.put(PluginToolEnum.FLEXIBLE_TEST, new FlexibleTestTool(this));
        tools.put(PluginToolEnum.SPRING_CACHE, new SpringCacheTool(this));

        // 添加工具面板（初次加载时隐藏）
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        windowContent.add(whitePanel, gbc);
        // 添加所有工具面板，并设置为不可见
        for (BasePluginTool tool : tools.values()) {
            JPanel panel = tool.getPanel();
            panel.setVisible(false); // 初始时不显示
            windowContent.add(panel, gbc);
        }
        onSwitchTool();
        tipsButton.addActionListener(e -> openTipsDoc());
        toolBox.addActionListener(e -> onSwitchTool());

        initSettingsDialog();
    }


    private void refreshVisibleApp(){
        List<String> items = AppRuntimeHelper.loadProjectRuntimes("unknown");
        Iterator<String> iterator = items.iterator();
        HashMap<String, String> map = new HashMap<>();
        map.put("method", "hello");
        while (iterator.hasNext()){
            String item = iterator.next();
            if (item==null) {
                iterator.remove();
                continue;
            }
            VisibleApp visibleApp = parseApp(item);
            try {
                HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", map, Map.class);
            } catch (Exception e) {
                e.printStackTrace();
                iterator.remove();
                AppRuntimeHelper.removeApp("unknown",visibleApp.getAppName(), visibleApp.getPort(), visibleApp.getSidePort());
            }
        }

        // 清理 appBox 的内容，并使用 newStrings 重新赋值
        appBox.removeAllItems();
        for (String item : items) {
            appBox.addItem(item.substring(0,item.lastIndexOf(":")));
        }
    }

    private void openTipsDoc() {
        String url = "https://docs.zoom.us/doc/LXz2PDQkTo27BkDvEnbUVg";
        Object selectedItem = toolBox.getSelectedItem();
        if ("spring-cache".equals(selectedItem)) {
            url += "#4b4cacc4f7784057976c4a2ccb197d5b";
        } else if ("call-method".equals(selectedItem)) {
            url += "#e0db2d19e4a5470d9f06b0ec833ebad3";
        } else if ("flexible-test".equals(selectedItem)) {
            url += "#79befe5c554f435bb7afffc1b855cd8f";
        }

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


    public void switchTool(PluginToolEnum tool, PsiElement element) {
        toolBox.setSelectedItem(tool.getCode());
        BasePluginTool pluginTool = tools.get(tool);
        if (pluginTool instanceof ActionTool) {
            ((ActionTool) pluginTool).onSwitchAction(element);
        }
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

    public JPanel getContent() {
        return windowContent;
    }

    public Project getProject() {
        return project;
    }

    public VisibleApp getSelectedApp() {
        String selectedItem = (String) appBox.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        return parseApp(selectedItem);
    }

    private @Nullable VisibleApp parseApp(String selectedItem) {
        String[] split = selectedItem.split(":");
        if (split.length == 2) {
            VisibleApp visibleApp = new VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[1])+10000);
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

    public String getSelectedAppName() {
        VisibleApp app = getSelectedApp();
        if (app==null) {
            return null;
        }
        return app.getAppName();
    }

    private void initSettingsDialog() {
        settingsDialog = new JDialog((Frame) null, "No-Bug Settings", true);
        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 左侧选项列表
        String[] options = {"basic","script"};
        JBList<String> optionList = new JBList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 默认选中第一个选项
        optionList.setSelectedIndex(0);
        // 右侧内容显示区域
        JPanel rightContent = new JPanel(new CardLayout());
        rightContent.add(createBasicOptionPanel(), "basic");
        rightContent.add(createScriptOptionPanel(), "script");

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
    }

    private JPanel createBasicOptionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 添加内边距以美化布局

        flexibleTestPackageNameField = new JTextField(LocalStorageHelper.getFlexibleTestPackage(project), 20);

        // 输入框
        JLabel packageNameLabel = new JLabel("Flexible Test Package:");
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

        // 新增一行值得占用
        gbc.gridx = 0;
        gbc.gridy = 1; // 新的一行
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
                    Notification notification = new Notification("No-Bug", "Info", "Package name is blank, none fun can execute", NotificationType.INFORMATION);
                    Notifications.Bus.notify(notification, project);
                    return;
                }
                LocalStorageHelper.setFlexibleTestPackage(project, packageName);
                Notification notification = new Notification("No-Bug", "Info", "Flexible-test is refreshed by " + packageName + ", please restart project", NotificationType.INFORMATION);
                Notifications.Bus.notify(notification, project);
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
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 2; // 占据两列
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

        scriptField = new LanguageTextField(JavaLanguage.INSTANCE, getProject(), LocalStorageHelper.getScript(project), false);

        // 布局输入框
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // 占据两列
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBScrollPane(scriptField), gbc);

        // 创建按钮面板，使用FlowLayout以右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 保存按钮
        JButton saveButton = new JButton("Apply");
        ActionListener saveListener = e -> {
            String script = scriptField.getText();
            if (StringUtils.isBlank(script)) {
                LocalStorageHelper.setScript(project, null);
                Notification notification = new Notification("No-Bug", "Info", "Script is blank", NotificationType.INFORMATION);
                Notifications.Bus.notify(notification, project);
                return;
            }
            LocalStorageHelper.setScript(project, script);
            Notification notification = new Notification("No-Bug", "Info", "Script is saved", NotificationType.INFORMATION);
            Notifications.Bus.notify(notification, project);
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
        gbc.gridy = 1; // 新的一行
        gbc.gridwidth = 2; // 占据两列
        gbc.weightx = 0.0; // 重置权重
        gbc.weighty = 0.0; // 重置权重
        gbc.fill = GridBagConstraints.HORIZONTAL; // 按钮面板充满水平空间
        gbc.anchor = GridBagConstraints.SOUTH; // 向下对齐
        panel.add(buttonPanel, gbc);

        return panel;
    }
}


