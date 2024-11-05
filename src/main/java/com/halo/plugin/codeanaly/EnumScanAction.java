package com.halo.plugin.codeanaly;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;  
import com.intellij.openapi.actionSystem.PlatformDataKeys;  
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;  
import com.intellij.notification.Notifications;

public class EnumScanAction extends AnAction {  
    @Override  
    public void actionPerformed(AnActionEvent e) {  
        Project project = e.getProject();  
        PsiElement element = e.getData(PlatformDataKeys.PSI_ELEMENT);
        Notification notification = new Notification("Enum Scan", "Scan Result", "1111", NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, project);
    }  
}