package com.halo.plugin.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiMethod;
import com.halo.plugin.view.PluginToolWindow;
import com.halo.plugin.view.VisibleApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;

public class BasePluginTool {

    protected PluginToolWindow toolWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected JButton settingsButton;
    protected JComboBox<String> actionBox;
    protected JButton runButton;
    protected JTextArea resultArea;

    public BasePluginTool(PluginToolWindow pluginToolWindow) {
        // 初始化panel
        this.toolWindow = pluginToolWindow;
        this.panel = new JPanel(new GridBagLayout());
    }

    protected void addSettingsButton(ActionListener actionListener) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START;

        Icon settingsIcon = new ImageIcon(
                new ImageIcon(getClass().getResource("/icons/settings.png"))
                        .getImage()
                        .getScaledInstance(16, 16, Image.SCALE_SMOOTH)
        );
        settingsButton = new JButton(settingsIcon);
        settingsButton.setPreferredSize(new Dimension(32, 32));
        panel.add(settingsButton, gbc);

        // 添加按钮点击事件以显示设置窗口
        settingsButton.addActionListener(actionListener);
    }



    protected void addActionBox() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        actionBox = new ComboBox<>(new String[]{tool + " Action 1", "Action 2", "Action 3"});
        panel.add(actionBox, gbc);
    }

    protected void addRunButton() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;

        runButton = new JButton("Run");
        panel.add(runButton, gbc);
    }

    protected void addResultArea() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(resultArea);
        panel.add(scrollPane, gbc);
    }


    public PluginToolEnum getTool() {
        return tool;
    }

    public JPanel getPanel() {
        return panel;
    }

    public Project getProject() {
        return toolWindow.getProject();
    }

    public VisibleApp getVisibleApp() {
        return toolWindow.getSelectedApp();
    }

    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    public static class MethodAction {
        private final String name;
        private final PsiMethod method;

        public MethodAction(String name, PsiMethod method) {
            this.name = name;
            this.method = method;
        }

        @Override
        public String toString() {
            return name; // 返回方法名作为显示文本
        }

        public PsiMethod getMethod() {
            return method;
        }

        public String getName() {
            return name;
        }
    }

}