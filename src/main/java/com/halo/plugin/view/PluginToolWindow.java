package com.halo.plugin.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.halo.plugin.tools.*;
import com.halo.plugin.tools.call_method.CallMethodTool;
import com.halo.plugin.tools.flexible_test.FlexibleTestTool;
import com.halo.plugin.tools.spring_cache.SpringCacheTool;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class PluginToolWindow {

    private Project project;
    private ToolWindow toolWindow;
    private JPanel windowContent;
    private JButton tipsButton;
    private JComboBox<String> toolBox;
    private JButton refreshButton;
    private JComboBox<String> appBox;

    private JPanel whitePanel = new JPanel();
    private Map<PluginToolEnum, BasePluginTool> tools = new HashMap<>();


    public PluginToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        // 初始化主面板
        windowContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件之间的间距

        // 第一行
        // tips按钮
        Icon tipsIcon = new ImageIcon(new ImageIcon(getClass().getResource("/icons/tips.png")).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
        tipsButton = new JButton(tipsIcon);
        tipsButton.setPreferredSize(new Dimension(32, 32));
        tipsButton.setText(null); // 去除文本
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START; // 按钮靠左
        windowContent.add(tipsButton, gbc);


        // 工具拉框A
        toolBox = new ComboBox<>(new String[]{PluginToolEnum.CALL_METHOD.getCode(), PluginToolEnum.FLEXIBLE_TEST.getCode(),PluginToolEnum.SPRING_CACHE.getCode()});
        gbc.gridx = 1;
        gbc.weightx = 1.0; // 设置组件水平扩展
        gbc.fill = GridBagConstraints.HORIZONTAL;
        windowContent.add(toolBox, gbc);


        // 刷新按钮
        Icon refreshIcon = new ImageIcon(new ImageIcon(getClass().getResource("/icons/refresh.png")).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
        refreshButton = new JButton(refreshIcon);
        refreshButton.setPreferredSize(new Dimension(32, 32));
        gbc.gridx = 2;
        gbc.weightx = 0.0; // 不需要水平拉伸按钮
        windowContent.add(refreshButton, gbc);

        // 运行时app下拉框
        appBox = new ComboBox<>(new String[]{"Web:9002:19088","Api:9090:19176","Op:9060:19146"});
        gbc.gridx = 3;
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

        tipsButton.addActionListener(e -> openTipsDoc());
        toolBox.addActionListener(e -> onSwitchTool());
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
        if(pluginTool instanceof ActionTool){
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
        if (selectedItem==null) {
            return null;
        }
        VisibleApp visibleApp = new VisibleApp();
        String[] split = selectedItem.split(":");
        if(split.length!=3){
            Messages.showMessageDialog(project,
                    "app is not valid",
                    "Error",
                    Messages.getErrorIcon());
            return null;
        }
        visibleApp.setAppName(split[0]);
        visibleApp.setPort(Integer.parseInt(split[1]));
        visibleApp.setSidePort(Integer.parseInt(split[2]));
        return visibleApp;
    }
}