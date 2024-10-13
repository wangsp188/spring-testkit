package com.zcc.plugin.spring_cache;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.zcc.plugin.HttpUtil;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CacheableIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final PsiMethod method;
    JFrame frame = null;

    CacheableIconNavigationHandler(PsiMethod method) {
        this.method = method;
    }

    @Override
    public void navigate(MouseEvent e, PsiElement elt) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException("Cannot display UI elements in a headless environment.");
        }

        Project project = elt.getProject(); // Get the project from PsiElement
        if (frame == null) {
            frame = new JFrame("Cacheable Method Input");
            // 注册窗口关闭事件
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // 执行清理函数
                    System.out.println("Cleaning up resources...");
                    // 销毁窗口
                    frame.dispose();
                    frame = null;
                }
            });
        }

        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        JPanel inputPanel = new JPanel(new BorderLayout());
        JLabel inputLabel = new JLabel("Input JSON Array:");
        LanguageTextField inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE,project,"[]",false);
        inputPanel.add(inputLabel, BorderLayout.NORTH);
        inputPanel.add(new JScrollPane(inputEditorTextField), BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new BorderLayout());
        JLabel outputLabel = new JLabel("Output:");
        LanguageTextField outputTextArea = new LanguageTextField(JsonLanguage.INSTANCE, project, "", false);
        outputPanel.add(outputLabel, BorderLayout.NORTH);
        outputPanel.add(new JScrollPane(outputTextArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(inputPanel);
        splitPane.setRightComponent(outputPanel);
        splitPane.setDividerLocation(400);

        frame.add(splitPane, BorderLayout.CENTER);

        // 新增端口输入区域
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel portLabel = new JLabel("Port:");
        JTextField portTextField = new JTextField("9002", 10); // 默认值为 9002 并设置宽度
        portPanel.add(portLabel);
        portPanel.add(portTextField);

        // 将submit按钮移到port输入框的右边，在一行
        JButton submitButton = new JButton("Build key");
        portPanel.add(submitButton);

        frame.add(portPanel, BorderLayout.NORTH);

        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String jsonInput = inputEditorTextField.getDocument().getText();
                if (jsonInput == null || jsonInput.isBlank()) {
                    outputTextArea.setText("参数框不可为空");
                    return;
                }
                String port = portTextField.getText();
                if (port == null || port.isBlank() || !StringUtils.isNumeric(port)) {
                    outputTextArea.setText("端口号不可为空，且必须为数字");
                    return;
                }
                JSONArray jsonArray = null;
                try {
                    jsonArray = JSONObject.parseArray(jsonInput);
                } catch (Exception ex) {
                    outputTextArea.setText("参数必须是json数组");
                    return;
                }
                inputEditorTextField.setText(JSONObject.toJSONString(jsonArray, true));
                if (method.getParameterList().getParameters().length != jsonArray.size()) {
                    outputTextArea.setText("参数量不对,预期是：" + method.getParameterList().getParameters().length + "个，提供了：" + jsonArray.size() + "个");
                    return;
                }
                try {
                    JSONObject paramsJson = buildParams(method, jsonArray);
                    System.out.println("zcc_plugin,cache_req," + paramsJson.toJSONString());
                    JSONObject response = HttpUtil.sendPost("http://localhost:" + (Integer.parseInt(port) + 10086) + "/", paramsJson, JSONObject.class);
                    if (response == null) {
                        outputTextArea.setText("返回结果为空");
                    } else if (!response.getBooleanValue("success")) {
                        outputTextArea.setText("失败：" + response.getString("message"));
                    } else {
                        outputTextArea.setText(JSONObject.toJSONString(response.get("data"), true));
                    }
                } catch (Exception ex) {
                    if ("Connection refused".equals(ex.getMessage())) {
                        outputTextArea.setText("僚机连接失败，请检查端口是否正确或者查看目标应用是否启动！");
                    } else {
                        outputTextArea.setText("Error: " + getStackTrace(ex));
                    }
                }
            }
        });

        frame.setVisible(true);
    }

    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    private JSONObject buildParams(PsiMethod method, JSONArray args) throws Exception {
        JSONObject params = new JSONObject();
        PsiClass containingClass = method.getContainingClass();
        String typeClass = containingClass.getQualifiedName();
        params.put("typeClass", typeClass);

        String beanName = getBeanNameFromClass(containingClass);
        params.put("beanName", beanName);
        params.put("methodName", method.getName());

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }
        params.put("argTypes", JSONObject.toJSONString(argTypes));
        params.put("args", args.toJSONString());
        JSONObject req = new JSONObject();
        req.put("method", "build_cache_key");
        req.put("params", params);
        return req;
    }

    private String getBeanNameFromClass(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        String beanName = null;

        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null &&
                        (qualifiedName.equals("org.springframework.stereotype.Component") ||
                                qualifiedName.equals("org.springframework.stereotype.Service") ||
                                qualifiedName.equals("org.springframework.stereotype.Repository") ||
                                qualifiedName.equals("org.springframework.context.annotation.Configuration") ||
                                qualifiedName.equals("org.springframework.stereotype.Controller") ||
                                qualifiedName.equals("org.springframework.web.bind.annotation.RestController"))) {

                    if (annotation.findAttributeValue("value") != null) {
                        beanName = annotation.findAttributeValue("value").getText().replace("\"", "");
                    }
                }
            }
        }

        if (beanName == null || beanName.isBlank()) {
            String className = psiClass.getName();
            if (className != null && !className.isEmpty()) {
                beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
        }

        return beanName;
    }
}