package com.testkit;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.testkit.view.TestkitToolWindowFactory;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class TestkitHelper {

    public static final String PLUGIN_ID = "Spring-Testkit";
    private static final String pluginName = "Spring-Testkit";

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
        TestkitHelper.notify(project, NotificationType.INFORMATION, copyMsg == null ? "Copy success" : copyMsg);
    }

    /**
     * Show error dialog with option to open Settings and navigate to specific panel
     *
     * @param project   Project instance
     * @param panelName Settings panel name to navigate to, e.g., "Controller command"
     * @param title     Dialog title
     * @param message   Error message (HTML format)
     */
    public static void showErrorWithSettingsNavigation(Project project, String panelName, String title, String message) {
        if (project == null) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Open Settings"};
            int choice = Messages.showDialog(
                project,
                message,
                title,
                options,
                0,  // Default to "Open Settings"
                Messages.getErrorIcon()
            );
            
            if (choice == 0 && panelName != null) {
                // Open Settings and navigate to specified panel
                com.testkit.view.TestkitToolWindow toolWindow = TestkitToolWindowFactory.getToolWindow(project);
                if (toolWindow != null && toolWindow.getSettingsDialog() != null) {
                    toolWindow.getSettingsDialog().visible(panelName);
                }
            }
        });
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

    public static String getPluginId() {
        return PLUGIN_ID;
    }


    public static HashMap<String, RuntimeHelper.AppMeta> findSpringBootClass(Project project) {

        java.util.List<PsiClass> springBootApplicationClasses = ApplicationManager.getApplication().runReadAction((Computable<java.util.List<PsiClass>>) () -> {
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


        HashMap<String, RuntimeHelper.AppMeta> map = new HashMap<>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
                springBootApplicationClasses.forEach(new Consumer<PsiClass>() {
                    @Override
                    public void accept(PsiClass psiClass) {
                        VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
                        if (virtualFile == null || virtualFile.getPath().contains("/src/test/java/")) {
                            return;
                        }

                        RuntimeHelper.AppMeta appMeta = new RuntimeHelper.AppMeta();
                        appMeta.setApp(psiClass.getName());
                        appMeta.setFullName(psiClass.getQualifiedName());

                        appMeta.setModule(ModuleUtil.findModuleForPsiElement(psiClass));
                        map.put(psiClass.getName(), appMeta);
                    }
                });
            }
        });

        return map;
    }


}
