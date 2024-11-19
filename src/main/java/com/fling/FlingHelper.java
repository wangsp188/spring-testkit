package com.fling;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class FlingHelper {

    private static final String pluginName = "Spring-Fling";

    public static void notify(Project project, NotificationType type, String content) {
        Notification notification = new Notification(pluginName, pluginName, content, type);
        Notifications.Bus.notify(notification, project);
    }

    public static void copyToClipboard(Project project, String text,String copyMsg) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        FlingHelper.notify(project, NotificationType.INFORMATION, copyMsg == null ? "copy success" : copyMsg);
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
