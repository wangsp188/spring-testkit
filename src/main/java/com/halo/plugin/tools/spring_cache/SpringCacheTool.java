package com.halo.plugin.tools.spring_cache;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.ui.LanguageTextField;
import com.halo.plugin.tools.ActionTool;
import com.halo.plugin.tools.BasePluginTool;
import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.util.HttpUtil;
import com.halo.plugin.view.PluginToolWindow;
import com.halo.plugin.view.VisibleApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;

public class SpringCacheTool extends BasePluginTool  implements ActionTool {

    private JComboBox<MethodAction> actionComboBox;

    private LanguageTextField inputEditorTextField;
    private LanguageTextField outputTextArea;

    private MethodAction lastMethodAction;

    {
        this.tool = PluginToolEnum.SPRING_CACHE;
    }

    public SpringCacheTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
        initializePanel();
        // 假设有一个方法来传递选定的方法对象
        // handleMethodSelection(PsiMethod method);
    }

    private void initializePanel() {
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.1; // Top panel takes 10% of the space

        // Top panel for method selection and actions
        JPanel topPanel = createTopPanel();
        panel.add(topPanel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.3; // Middle panel takes 30% of the space

        // Middle panel for input parameters
        JPanel middlePanel = createMiddlePanel();
        panel.add(middlePanel, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.6; // Bottom panel takes 60% of the space

        // Bottom panel for output
        JPanel bottomPanel = createBottomPanel();
        panel.add(bottomPanel, gbc);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionComboBox = new ComboBox<>();
        // Populate methodComboBox with method names
        topPanel.add(actionComboBox);

        actionComboBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });

        JButton getKeyButton = new JButton("Get Key");
        JButton getValButton = new JButton("Get Val");
        JButton delValButton = new JButton("Del Val");

        getKeyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAction("build_cache_key");
            }
        });

        getValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAction("get_cache");
            }
        });

        delValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAction("delete_cache");
            }
        });

        topPanel.add(getKeyButton);
        topPanel.add(getValButton);
        topPanel.add(delValButton);

        return topPanel;
    }

    private JPanel createMiddlePanel() {
        JPanel middlePanel = new JPanel(new BorderLayout());
        inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        middlePanel.add(new JScrollPane(inputEditorTextField), BorderLayout.CENTER);

        return middlePanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        outputTextArea = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        bottomPanel.add(new JScrollPane(outputTextArea), BorderLayout.CENTER);

        return bottomPanel;
    }

    private void handleAction(String action) {
        VisibleApp app = getVisibleApp();
        if(app==null){
            Messages.showMessageDialog(getProject(),
                    "Failed to find visible app",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        String jsonInput = inputEditorTextField.getDocument().getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            outputTextArea.setText("参数框不可为空");
            return;
        }
        JSONArray jsonArray;
        try {
            jsonArray = JSONObject.parseArray(jsonInput);
        } catch (Exception ex) {
            outputTextArea.setText("参数必须是json数组");
            return;
        }
        inputEditorTextField.setText(JSONObject.toJSONString(jsonArray, true));
        MethodAction selectedItem = (MethodAction) actionComboBox.getSelectedItem();
        if (selectedItem==null) {
            outputTextArea.setText("未选中函数，请先选中");
            return;
        }
        PsiMethod method = selectedItem.getMethod(); // 需要从实际应用中获取当前的选中的 PsiMethod
        if (method.getParameterList().getParameters().length != jsonArray.size()) {
            outputTextArea.setText("参数量不对,预期是：" + method.getParameterList().getParameters().length + "个，提供了：" + jsonArray.size() + "个");
            return;
        }
        try {
            JSONObject paramsJson = buildParams(method, jsonArray, action);
            JSONObject response = HttpUtil.sendPost("http://localhost:" + app.getSidePort() + "/api-endpoint", paramsJson, JSONObject.class); // 替换为实际的端口和URL
            if (response == null) {
                outputTextArea.setText("返回结果为空");
            } else if (!response.getBooleanValue("success")) {
                outputTextArea.setText("失败：" + response.getString("message"));
            } else {
                outputTextArea.setText(JSONObject.toJSONString(response.get("data"), true));
            }
        } catch (Exception ex) {
            outputTextArea.setText("Error: " + getStackTrace(ex));
        }
    }

    private JSONObject buildParams(PsiMethod method, JSONArray args, String action) throws Exception {
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
        req.put("method", action);
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

    @Override
    public void onSwitchAction(PsiElement psiElement) {
        if(!(psiElement instanceof PsiMethod)) {
            Messages.showMessageDialog(getProject(),
                    "un support element",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        PsiMethod method = (PsiMethod) psiElement;
        MethodAction methodAction = (MethodAction) actionComboBox.getSelectedItem();
        if (methodAction != null && methodAction.getMethod().equals(method)) {
            return;
        }

        // 检查下拉框中是否已经包含当前方法的名称
        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            if (actionComboBox.getItemAt(i).getName().equals(method.getName())) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }
        // 如果当前下拉框没有此选项，则新增该选项
        MethodAction item = new MethodAction(method.getName(), method);
        actionComboBox.addItem(item);
        actionComboBox.setSelectedItem(item);
    }

    private void triggerChangeAction() {
        // 获取当前选中的值
        MethodAction methodAction = (MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null) {
            inputEditorTextField.setText("[]");
            return;
        }
        if(lastMethodAction!=null && methodAction==lastMethodAction){
            return;
        }
        initParams(methodAction.getMethod());
        lastMethodAction = methodAction;
    }


    private void initParams(PsiMethod method){
        ArrayList initParams = new ArrayList();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();
            if (type.equalsToText("boolean") || type.equalsToText("java.lang.Boolean")) {
                initParams.add(false);
            } else if (type.equalsToText("char") || type.equalsToText("java.lang.Character")) {
                initParams.add('\0');
            } else if (type.equalsToText("byte") || type.equalsToText("java.lang.Byte")) {
                initParams.add((byte) 0);
            } else if (type.equalsToText("short") || type.equalsToText("java.lang.Short")) {
                initParams.add((short) 0);
            } else if (type.equalsToText("int") || type.equalsToText("java.lang.Integer")) {
                initParams.add(0);
            } else if (type.equalsToText("long") || type.equalsToText("java.lang.Long")) {
                initParams.add(0L);
            } else if (type.equalsToText("float") || type.equalsToText("java.lang.Float")) {
                initParams.add(0.0f);
            } else if (type.equalsToText("double") || type.equalsToText("java.lang.Double")) {
                initParams.add(0.0);
            } else if (type.equalsToText("java.lang.String")) {
                initParams.add("");
            } else if (type.equalsToText("java.util.Collection") || type.equalsToText("java.util.List")|| type.equalsToText("java.util.ArrayList") || type.equalsToText("java.util.HashSet") || type.equalsToText("java.util.Set")) {
                initParams.add(new ArrayList<>());
            } else if (type.equalsToText("java.util.Map")) {
                initParams.add(new HashMap<>());
            } else if (type instanceof PsiArrayType) {
                initParams.add(new ArrayList<>()); // 可以使用空集合来表示数组
            } else {
                initParams.add(new HashMap<>()); // 为复杂对象类型初始化空映射
            }
        }
        inputEditorTextField.setText(JSONObject.toJSONString(initParams, true));
    }




}