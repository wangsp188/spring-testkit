package com.halo.plugin.tools.spring_cache;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.WindowHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

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

        Project project = elt.getProject();
        WindowHelper.switch2Tool(project, PluginToolEnum.SPRING_CACHE,(PsiMethod) elt);
//
//
//         // Get the project from PsiElement
//        if (frame == null) {
//            frame = new JFrame("Cacheable Method Input");
//            // 注册窗口关闭事件
//            frame.addWindowListener(new WindowAdapter() {
//                @Override
//                public void windowClosing(WindowEvent e) {
//                    // 执行清理函数
//                    System.out.println("Cleaning up resources...");
//                    // 销毁窗口
//                    frame.dispose();
//                    frame = null;
//                }
//            });
//        }
//
//        frame.getContentPane().removeAll();
//        frame.setLayout(new BorderLayout());
//        frame.setSize(800, 600);
//        frame.setLocationRelativeTo(null);
//
//        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
//
//        JPanel inputPanel = new JPanel(new BorderLayout());
//        JLabel inputLabel = new JLabel("Input JSON Array:");
//        ArrayList initParams = new ArrayList();
////      遍历方法参数，如果是简单对象，则设置为空字符，如果是array/collection则设置为空集合，否则就是空map
//        PsiMethod method = (PsiMethod) elt;
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        for (PsiParameter parameter : parameters) {
//            PsiType type = parameter.getType();
//            if (type.equalsToText("boolean") || type.equalsToText("java.lang.Boolean")) {
//                initParams.add(false);
//            } else if (type.equalsToText("char") || type.equalsToText("java.lang.Character")) {
//                initParams.add('\0');
//            } else if (type.equalsToText("byte") || type.equalsToText("java.lang.Byte")) {
//                initParams.add((byte) 0);
//            } else if (type.equalsToText("short") || type.equalsToText("java.lang.Short")) {
//                initParams.add((short) 0);
//            } else if (type.equalsToText("int") || type.equalsToText("java.lang.Integer")) {
//                initParams.add(0);
//            } else if (type.equalsToText("long") || type.equalsToText("java.lang.Long")) {
//                initParams.add(0L);
//            } else if (type.equalsToText("float") || type.equalsToText("java.lang.Float")) {
//                initParams.add(0.0f);
//            } else if (type.equalsToText("double") || type.equalsToText("java.lang.Double")) {
//                initParams.add(0.0);
//            } else if (type.equalsToText("java.lang.String")) {
//                initParams.add("");
//            } else if (type.equalsToText("java.util.Collection") || type.equalsToText("java.util.List")|| type.equalsToText("java.util.ArrayList") || type.equalsToText("java.util.HashSet") || type.equalsToText("java.util.Set")) {
//                initParams.add(new ArrayList<>());
//            } else if (type.equalsToText("java.util.Map")) {
//                initParams.add(new HashMap<>());
//            } else if (type instanceof PsiArrayType) {
//                initParams.add(new ArrayList<>()); // 可以使用空集合来表示数组
//            } else {
//                initParams.add(new HashMap<>()); // 为复杂对象类型初始化空映射
//            }
//        }
//
//
//        LanguageTextField inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE,project, JSON.toJSONString(initParams,true),false);
//        inputPanel.add(inputLabel, BorderLayout.NORTH);
//        inputPanel.add(new JScrollPane(inputEditorTextField), BorderLayout.CENTER);
//
//        JPanel outputPanel = new JPanel(new BorderLayout());
//        JLabel outputLabel = new JLabel("Output:");
//        LanguageTextField outputTextArea = new LanguageTextField(JsonLanguage.INSTANCE, project, "", false);
//        outputPanel.add(outputLabel, BorderLayout.NORTH);
//        outputPanel.add(new JScrollPane(outputTextArea), BorderLayout.CENTER);
//
//        splitPane.setLeftComponent(inputPanel);
//        splitPane.setRightComponent(outputPanel);
//        splitPane.setDividerLocation(400);
//
//        frame.add(splitPane, BorderLayout.CENTER);
//
//        // 新增端口输入区域
//        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
//        JLabel portLabel = new JLabel("Port:");
//        JTextField portTextField = new JTextField("9002", 10); // 默认值为 9002 并设置宽度
//        portPanel.add(portLabel);
//        portPanel.add(portTextField);
//
//        // 将submit按钮移到port输入框的右边，在一行
//        JButton testPort = new JButton("Test port");
//        portPanel.add(testPort);
//        testPort.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                String port = portTextField.getText();
//                if (port == null || port.isBlank() || !StringUtils.isNumeric(port)) {
//                    outputTextArea.setText("端口号不可为空，且必须为数字");
//                    return;
//                }
//                JSONObject req = new JSONObject();
//                req.put("method", "hello");
//                JSONObject response = null;
//                try {
//                    response = HttpUtil.sendPost("http://localhost:" + (Integer.parseInt(port) + 10086) + "/", req, JSONObject.class);
//                    if (response == null) {
//                        outputTextArea.setText("返回结果为空");
//                    } else if (!response.getBooleanValue("success")) {
//                        outputTextArea.setText("失败：" + response.getString("message"));
//                    }else{
//                        outputTextArea.setText("棒棒");
//                    }
//                } catch (Exception ex) {
//                    outputTextArea.setText("僚机连接失败，请检查端口是否正确或者查看目标应用是否启动！");
//                }
//            }
//        });
//
//        JButton submitButton = new JButton("Build key");
//        portPanel.add(submitButton);
//
//        JButton getButton = new JButton("Get val");
//        portPanel.add(getButton);
//
//        JButton delButton = new JButton("Del val");
//        portPanel.add(delButton);
//
//        frame.add(portPanel, BorderLayout.NORTH);
//
//        ActionListener actionListener = new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent event) {
//                String jsonInput = inputEditorTextField.getDocument().getText();
//                if (jsonInput == null || jsonInput.isBlank()) {
//                    outputTextArea.setText("参数框不可为空");
//                    return;
//                }
//                String port = portTextField.getText();
//                if (port == null || port.isBlank() || !StringUtils.isNumeric(port)) {
//                    outputTextArea.setText("端口号不可为空，且必须为数字");
//                    return;
//                }
//                JSONArray jsonArray = null;
//                try {
//                    jsonArray = JSONObject.parseArray(jsonInput);
//                } catch (Exception ex) {
//                    outputTextArea.setText("参数必须是json数组");
//                    return;
//                }
//                inputEditorTextField.setText(JSONObject.toJSONString(jsonArray, true));
//                if (method.getParameterList().getParameters().length != jsonArray.size()) {
//                    outputTextArea.setText("参数量不对,预期是：" + method.getParameterList().getParameters().length + "个，提供了：" + jsonArray.size() + "个");
//                    return;
//                }
//                try {
//                    JSONObject paramsJson = buildParams(event,method, jsonArray);
//                    System.out.println("zcc_plugin,cache_req," + paramsJson.toJSONString());
//                    JSONObject response = HttpUtil.sendPost("http://localhost:" + (Integer.parseInt(port) + 10086) + "/", paramsJson, JSONObject.class);
//                    if (response == null) {
//                        outputTextArea.setText("返回结果为空");
//                    } else if (!response.getBooleanValue("success")) {
//                        outputTextArea.setText("失败：" + response.getString("message"));
//                    } else {
//                        outputTextArea.setText(JSONObject.toJSONString(response.get("data"), true));
//                    }
//                } catch (Exception ex) {
//                    if ("Connection refused".equals(ex.getMessage())) {
//                        outputTextArea.setText("僚机连接失败，请检查端口是否正确或者查看目标应用是否启动！");
//                    } else {
//                        outputTextArea.setText("Error: " + getStackTrace(ex));
//                    }
//                }
//            }
//        };
//        submitButton.addActionListener(actionListener);
//        getButton.addActionListener(actionListener);
//        delButton.addActionListener(actionListener);
//
//        frame.setVisible(true);
    }
//
//    public static String getStackTrace(Throwable throwable) {
//        if (throwable == null) {
//            return "";
//        }
//        final StringWriter sw = new StringWriter();
//        throwable.printStackTrace(new PrintWriter(sw, true));
//        return sw.toString();
//    }
//
//    private JSONObject buildParams(ActionEvent event, PsiMethod method, JSONArray args) throws Exception {
//        JSONObject params = new JSONObject();
//        PsiClass containingClass = method.getContainingClass();
//        String typeClass = containingClass.getQualifiedName();
//        params.put("typeClass", typeClass);
//
//        String beanName = getBeanNameFromClass(containingClass);
//        params.put("beanName", beanName);
//        params.put("methodName", method.getName());
//
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        String[] argTypes = new String[parameters.length];
//        for (int i = 0; i < parameters.length; i++) {
//            argTypes[i] = parameters[i].getType().getCanonicalText();
//        }
//        params.put("argTypes", JSONObject.toJSONString(argTypes));
//        params.put("args", args.toJSONString());
//        JSONObject req = new JSONObject();
//        JButton source = (JButton) event.getSource();
//        if("Build key".equals(source.getText())){
//            req.put("method", "build_cache_key");
//        }else if("Get val".equals(source.getText())){
//            req.put("method", "get_cache");
//        }else if("Del val".equals(source.getText())){
//            req.put("method", "delete_cache");
//        }else{
//            throw new IllegalArgumentException("unknown button");
//        }
//        req.put("params", params);
//        return req;
//    }
//
//    private String getBeanNameFromClass(PsiClass psiClass) {
//        PsiModifierList modifierList = psiClass.getModifierList();
//        String beanName = null;
//
//        if (modifierList != null) {
//            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
//                String qualifiedName = annotation.getQualifiedName();
//                if (qualifiedName != null &&
//                        (qualifiedName.equals("org.springframework.stereotype.Component") ||
//                                qualifiedName.equals("org.springframework.stereotype.Service") ||
//                                qualifiedName.equals("org.springframework.stereotype.Repository") ||
//                                qualifiedName.equals("org.springframework.context.annotation.Configuration") ||
//                                qualifiedName.equals("org.springframework.stereotype.Controller") ||
//                                qualifiedName.equals("org.springframework.web.bind.annotation.RestController"))) {
//
//                    if (annotation.findAttributeValue("value") != null) {
//                        beanName = annotation.findAttributeValue("value").getText().replace("\"", "");
//                    }
//                }
//            }
//        }
//
//        if (beanName == null || beanName.isBlank()) {
//            String className = psiClass.getName();
//            if (className != null && !className.isEmpty()) {
//                beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
//            }
//        }
//
//        return beanName;
//    }

}