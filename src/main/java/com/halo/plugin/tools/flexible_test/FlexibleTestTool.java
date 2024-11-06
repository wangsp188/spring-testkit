package com.halo.plugin.tools.flexible_test;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.halo.plugin.tools.spring_cache.SpringCacheTool;
import com.halo.plugin.util.HttpUtil;
import com.halo.plugin.view.VisibleApp;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.halo.plugin.tools.ActionTool;
import com.halo.plugin.tools.BasePluginTool;
import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.PluginToolWindow;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;

public class FlexibleTestTool extends BasePluginTool  implements ActionTool {


    private JComboBox<MethodAction> actionComboBox;

    private LanguageTextField inputEditorTextField;
    private LanguageTextField outputTextArea;

    private MethodAction lastMethodAction;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
        initializePanel();
    }

    private void initializePanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // 顶部面板不占用垂直空间

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
        actionComboBox.setPreferredSize(new Dimension(280, 32));
        // Populate methodComboBox with method names
        topPanel.add(actionComboBox);

        actionComboBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });


        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("test function");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAction("flexible_test");
            }
        });


        topPanel.add(runButton);

        return topPanel;
    }

    private JPanel createMiddlePanel() {
        JPanel middlePanel = new JPanel(new BorderLayout());
        inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        middlePanel.add(new JBScrollPane(inputEditorTextField), BorderLayout.CENTER);

        return middlePanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        outputTextArea = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        bottomPanel.add(new JBScrollPane(outputTextArea), BorderLayout.CENTER);

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
        JSONObject params = buildParams(method, jsonArray, action);
        outputTextArea.setText("......");
        // 使用 SwingWorker 在后台线程执行耗时操作
        SwingWorker<JSONObject, Void> worker = new SwingWorker<>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                return HttpUtil.sendPost("http://localhost:" + app.getSidePort() + "/", params, JSONObject.class);
            }

            @Override
            protected void done() {
                try {
                    JSONObject response = get();
                    if (response == null) {
                        outputTextArea.setText("返回结果为空");
                    } else if (!response.getBooleanValue("success")) {
                        outputTextArea.setText("失败：" + response.getString("message"));
                    } else {
                        outputTextArea.setText(JSONObject.toJSONString(response.get("data"), true));
                    }
                } catch (Throwable ex) {
                    outputTextArea.setText("Error: " + getStackTrace(ex.getCause()));
                }
            }
        };

        worker.execute();
    }

    private JSONObject buildParams(PsiMethod method, JSONArray args, String action) {
        JSONObject params = new JSONObject();
        PsiClass containingClass = method.getContainingClass();
        // 获取包含这个类的文件
        PsiFile file = containingClass.getContainingFile();
        params.put("code", file.getText());
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
            if (actionComboBox.getItemAt(i).toString().equals(buildMethodKey(method))) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }
        // 如果当前下拉框没有此选项，则新增该选项
        MethodAction item = new MethodAction(method);
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
        ArrayList initParams = initParams(methodAction.getMethod());
        inputEditorTextField.setText(JSONObject.toJSONString(initParams, true));
        lastMethodAction = methodAction;
    }


}
