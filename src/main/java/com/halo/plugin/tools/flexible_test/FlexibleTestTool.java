package com.halo.plugin.tools.flexible_test;

import com.intellij.psi.PsiElement;
import com.halo.plugin.tools.ActionTool;
import com.halo.plugin.tools.BasePluginTool;
import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.PluginToolWindow;

import javax.swing.*;
import java.awt.*;

public class FlexibleTestTool extends BasePluginTool  implements ActionTool {

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
        addSettingsButton(a->showSettingsDialog());
    }

    private void showSettingsDialog() {

        // 初始化对话框
        JDialog settingsDialog = new JDialog((Frame) null, "Settings", true);

        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 左侧选项列表
        String[] options = {"Option 1", "Option 2", "Option 3"};
        JList<String> optionList = new JList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 右侧内容显示区域
        JPanel rightContent = new JPanel(new CardLayout());
        rightContent.add(new JLabel("Content for Option 1"), "Option 1");
        rightContent.add(new JLabel("Content for Option 2"), "Option 2");
        rightContent.add(new JLabel("Content for Option 3"), "Option 3");

        // 监听选项改变，更新右侧内容
        optionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CardLayout cl = (CardLayout) (rightContent.getLayout());
                cl.show(rightContent, optionList.getSelectedValue());
            }
        });

        // SplitPane 分割左右
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(optionList), rightContent);
        splitPane.setDividerLocation(150); // 设置初始分割条位置
        contentPanel.add(splitPane, BorderLayout.CENTER);

        // 保存按钮
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> JOptionPane.showMessageDialog(settingsDialog, "Settings Saved", "Info", JOptionPane.INFORMATION_MESSAGE));

        // 添加保存按钮到内容面板底部
        contentPanel.add(saveButton, BorderLayout.SOUTH);

        // 设置对话框内容
        settingsDialog.setContentPane(contentPanel);

        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        settingsDialog.setSize(screenSize.width / 2, screenSize.height / 2);
        settingsDialog.setLocationRelativeTo(null); // 居中显示

        settingsDialog.setVisible(true);
    }

    @Override
    public void onSwitchAction(PsiElement psiElement) {

    }

}
