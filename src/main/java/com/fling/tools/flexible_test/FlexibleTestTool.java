package com.fling.tools.flexible_test;

import com.alibaba.fastjson.JSONObject;
import com.fling.tools.ToolHelper;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

public class FlexibleTestTool extends BasePluginTool {


    private JComboBox<ToolHelper.MethodAction> actionComboBox;


    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }

    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionComboBox = addActionComboBox("<html>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<strong>flexible-test</strong><br>\n" +
                "<ul>\n" +
                "    <li>module test source 下 ${Flexible Test Package} 包内的 public 函数</li>\n" +
                "    <li>非static</li>\n" +
                "</ul>\n" +
                "</html>",topPanel, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });


        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("test method");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingToolWindow.VisibleApp app = getSelectedApp();
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(runButton, AllIcons.Actions.Execute, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        String jsonInput = inputEditorTextField.getDocument().getText();
                        if (jsonInput == null || jsonInput.isBlank()) {
                            setOutputText("input parameter is blank");
                            return null;
                        }
                        JSONObject jsonObject;
                        try {
                            jsonObject = JSONObject.parseObject(jsonInput);
                        } catch (Exception ex) {
                            setOutputText("input parameter must be json obj");
                            return null;
                        }
                        String jsonString = JSONObject.toJSONString(jsonObject, true);
                        inputEditorTextField.setText(jsonString);
                        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            setOutputText("pls select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonString);
                        JSONObject params = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FLEXIBLE_TEST.getCode());
                        try {
                            return HttpUtil.sendPost("http://localhost:" + app.getSidePort() + "/", params, JSONObject.class);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }
        });
        topPanel.add(runButton);
        return topPanel;
    }


    private JSONObject buildParams(PsiMethod method, JSONObject args, String action) {
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
        params.put("args", ToolHelper.adapterParams(method, args).toJSONString());
        JSONObject req = new JSONObject();
        req.put("method", action);
        req.put("params", params);
        req.put("script", LocalStorageHelper.getAppScript(getProject(), getSelectedAppName()));
        return req;
    }

    @Override
    public boolean needAppBox() {
        return true;
    }


    @Override
    public void onSwitchAction(PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            Messages.showMessageDialog(getProject(),
                    "un support element",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        PsiMethod method = (PsiMethod) psiElement;
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction != null && methodAction.getMethod().equals(method)) {
            return;
        }

        // 检查下拉框中是否已经包含当前方法的名称
        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            if (actionComboBox.getItemAt(i).toString().equals(ToolHelper.buildMethodKey(method))) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }

        // 如果当前下拉框没有此选项，则新增该选项
        ToolHelper.MethodAction item = new ToolHelper.MethodAction(method);
        actionComboBox.addItem(item);
        actionComboBox.setSelectedItem(item);
    }

    private void triggerChangeAction() {
        // 获取当前选中的值
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null) {
            inputEditorTextField.setText("[]");
            return;
        }
        ToolHelper.initParamsTextField(inputEditorTextField, methodAction);
    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox!=null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }

}
