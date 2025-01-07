package com.fling;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class FlingHelper {

    private static final String pluginName = "Spring-Fling";

    public static void notify(Project project, NotificationType type, String content) {
        Notification notification = new Notification(pluginName, pluginName, content, type);
        Notifications.Bus.notify(notification, project);
    }


    public static void alert(Project project, Icon icon, String message) {
        String title = icon == Messages.getErrorIcon() ? "Error" : (icon == Messages.getWarningIcon() ? "Warning" : "Information");
        SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, message, title, icon));
    }

    public static void showMessageWithCopy(Project project, String message) {
        // 自定义按钮的名称（要有"OK"按钮）
        String[] options = new String[]{"OK","Copy"};

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                int chosen = Messages.showDialog(
                        project,
                        message,
                        "Information",
                        options,
                        0,     // 默认选择“OK”，索引从0开始
                        Messages.getInformationIcon()   // 对话框的图标
                );

                if (chosen == 1) {
                    copyToClipboard(project,message,"Copy success");
                }
            }
        });
                // 显示对话框，返回用户点击的按钮索引

    }

    public static void copyToClipboard(Project project, String text, String copyMsg) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        FlingHelper.notify(project, NotificationType.INFORMATION, copyMsg == null ? "Copy success" : copyMsg);
    }

    public static void refresh(Project project) {
        if (project == null) {
            System.err.println("project is null, not refresh");
            return;
        }
        DaemonCodeAnalyzer.getInstance(project).restart();
    }


    public static String getPluginName() {
        return pluginName;
    }


}
