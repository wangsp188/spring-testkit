package com.zcc.plugin.spring_cache;

import com.alibaba.fastjson.JSONObject;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.zcc.plugin.HttpUtil;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

public class CacheableIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final PsiMethod method;

    CacheableIconNavigationHandler(PsiMethod method) {
        this.method = method;
    }

    @Override
    public void navigate(MouseEvent e, PsiElement elt) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException("Cannot display UI elements in a headless environment.");
        }

        Project project = elt.getProject(); // Get the project from PsiElement

        JFrame frame = new JFrame("Cacheable Method Input");


        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        JPanel inputPanel = new JPanel(new BorderLayout());
        JLabel inputLabel = new JLabel("Input JSON:");
        JTextArea inputTextArea = new JTextArea();
        inputPanel.add(inputLabel, BorderLayout.NORTH);
        inputPanel.add(new JScrollPane(inputTextArea), BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new BorderLayout());
        JLabel outputLabel = new JLabel("Output:");
        JTextArea outputTextArea = new JTextArea();
        outputTextArea.setEditable(false);
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
        frame.add(portPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton submitButton = new JButton("Submit");
        buttonPanel.add(submitButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String jsonInput = inputTextArea.getText();
                if (jsonInput == null || jsonInput.isBlank()) {
                    outputTextArea.setText("参数框不可为空");
                    return;
                }
                String port = portTextField.getText();
                if (port == null || port.isBlank() || !StringUtils.isNumeric(port)) {
                    outputTextArea.setText("端口号不可为空，且必须为数字");
                    return;
                }

                try {
                    JSONObject paramsJson = buildParams(method, jsonInput);
                    System.out.println(paramsJson.toJSONString());
                    JSONObject response = HttpUtil.sendPost("http://localhost:" + (Integer.parseInt(port) + 10086) + "/", paramsJson, JSONObject.class);
                    if (response == null) {
                        outputTextArea.setText("返回结果为空");
                    } else if (!response.getBooleanValue("success")) {
                        outputTextArea.setText(response.getString("message"));
                    } else {
                        outputTextArea.setText(JSONObject.toJSONString(response.get("data"),true));
                    }
                } catch (Exception ex) {
                    if("Connection refused".equals(ex.getMessage())) {
                        outputTextArea.setText("僚机连接失败，请检查端口是否正确或者查看目标应用是否启动！");
                    }else{
                        outputTextArea.setText("Error: " + ex.getMessage());
                    }
                }
            }
        });

        frame.setVisible(true);
    }

    private JSONObject buildParams(PsiMethod method, String args) throws Exception {
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
        params.put("args", args);
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