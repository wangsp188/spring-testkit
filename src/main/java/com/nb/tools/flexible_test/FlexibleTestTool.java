package com.nb.tools.flexible_test;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nb.util.HttpUtil;
import com.nb.view.LocalStorageHelper;
import com.nb.view.VisibleApp;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.view.PluginToolWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class FlexibleTestTool extends BasePluginTool  implements ActionTool {


    private JComboBox<MethodAction> actionComboBox;


    private MethodAction lastMethodAction;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }

    protected JPanel createTopPanel() {
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

    private void handleAction(String action) {
        VisibleApp app = getSelectedApp();
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
        req.put("script", LocalStorageHelper.getAppScript(getProject(), getSelectedAppName()));
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
