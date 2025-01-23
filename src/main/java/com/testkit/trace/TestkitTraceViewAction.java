package com.testkit.trace;

import com.alibaba.fastjson.JSON;
import com.testkit.TestkitHelper;
import com.testkit.util.TraceParser;
import com.testkit.view.TestkitToolWindow;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.lang3.StringEscapeUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestkitTraceViewAction extends AnAction {

    private static String renderHtml;

    static {
        URL resource = TestkitToolWindow.class.getResource("/html/horse_up_down_class.html");
        if (resource != null) {
            try {
                try (InputStream is = resource.openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    renderHtml = reader.lines().collect(Collectors.joining("\n"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }



    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        // 获取编辑器和项目
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();

        // 默认隐藏action
        e.getPresentation().setEnabledAndVisible(false);

        if (editor == null || project == null) {
            return;
        }

        // 获取选中的文本
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return;
        }

        String selectedText = selectionModel.getSelectedText();
        if (selectedText != null && selectedText.contains("TRACE_PROFILER")) {
            e.getPresentation().setEnabledAndVisible(true);
        }
    }

    @Override  
    public void actionPerformed(AnActionEvent e) {
        // 获取项目和编辑器信息
        Project project = e.getProject();
        if (project == null){
            return;
        }
        if (!JBCefApp.isSupported()) {
            Messages.showMessageDialog(project, "un support jcef.", "Information", Messages.getInformationIcon());
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null){
            return;
        }

        // 获取选中文本内容
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showMessageDialog(project, "No text selected.", "Information", Messages.getInformationIcon());
            return;
        }

        // 处理选中文本内容（解析逻辑）
        List<Map<String, String>> processedText = TraceParser.parseTestkitLogs(selectedText);
        if (processedText == null || processedText.isEmpty()) {
            // 在右下角显示提示
            TestkitHelper.notify(project,NotificationType.WARNING,"don't find valid link");
            return;
        }


        // 创建一个 JFrame 来展示网页
        JFrame frame = new JFrame("Link log Window");
        // 获取屏幕大小
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(screenSize);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // 注册监听器来处理项目关闭事件并关闭窗口
        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project closingProject) {
                if (closingProject.equals(project) && frame.isDisplayable()) {
                    frame.dispose();
                }
            }
        });

//        String localFileURL = getOrCreateLocalFileURL();
        String htmlContent = renderHtml;
        if (htmlContent == null) {
            Messages.showMessageDialog(project, "Local file not found.", "Error", Messages.getErrorIcon());
            return;
        }

        // 创建 JBCefBrowser 实例
        JBCefBrowser jbCefBrowser = new JBCefBrowser();
        jbCefBrowser.loadHTML(htmlContent);

        // 将 JBCefBrowser 的组件添加到 JFrame
        frame.getContentPane().add(jbCefBrowser.getComponent(), BorderLayout.CENTER);
        frame.setVisible(true);

        String render_str = StringEscapeUtils.escapeJson(JSON.toJSONString(processedText));
        // 监听页面加载事件
        jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                super.onLoadEnd(browser, frame, httpStatusCode);
                browser.executeJavaScript("$('#source').val('"+render_str+"');maulRender();", frame.getURL(), 0);
            }
        },jbCefBrowser.getCefBrowser());

    }

}