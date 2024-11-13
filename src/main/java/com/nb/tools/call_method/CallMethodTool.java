package com.nb.tools.call_method;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.nb.util.HttpUtil;
import com.nb.util.LocalStorageHelper;
import com.nb.view.WindowHelper;
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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Supplier;

public class CallMethodTool extends BasePluginTool implements ActionTool {


    private JComboBox<MethodAction> actionComboBox;

    private JRadioButton useProxyRadioButton;

    {
        this.tool = PluginToolEnum.CALL_METHOD;
    }

    public CallMethodTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }

    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel visibleAppLabel = new JLabel("action:");
        topPanel.add(visibleAppLabel);
        actionComboBox = buildActionComboBox();
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
                WindowHelper.VisibleApp app = getSelectedApp();
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerTask(runButton, AllIcons.Actions.Execute, outputTextArea, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        String jsonInput = inputEditorTextField.getDocument().getText();
                        if (jsonInput == null || jsonInput.isBlank()) {
                            outputTextArea.setText("参数框不可为空");
                            return null;
                        }
                        JSONArray jsonArray;
                        try {
                            jsonArray = JSONObject.parseArray(jsonInput);
                        } catch (Exception ex) {
                            outputTextArea.setText("参数必须是json数组");
                            return null;
                        }
                        inputEditorTextField.setText(JSONObject.toJSONString(jsonArray, true));
                        MethodAction selectedItem = (MethodAction) actionComboBox.getSelectedItem();
                        if (selectedItem==null) {
                            outputTextArea.setText("未选中函数，请先选中");
                            return null;
                        }
                        PsiMethod method = selectedItem.getMethod(); // 需要从实际应用中获取当前的选中的 PsiMethod
                        if (method.getParameterList().getParameters().length != jsonArray.size()) {
                            outputTextArea.setText("参数量不对,预期是：" + method.getParameterList().getParameters().length + "个，提供了：" + jsonArray.size() + "个");
                            return null;
                        }
                        JSONObject params = buildParams(method, jsonArray, PluginToolEnum.CALL_METHOD.getCode());
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


        // Add the radio button
        useProxyRadioButton = new JRadioButton();
        useProxyRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyRadioButton.isSelected()) {
                    useProxyRadioButton.setText("Proxy");
                    useProxyRadioButton.setToolTipText("use proxy obj call method");
                } else {
                    useProxyRadioButton.setText("Original");
                    useProxyRadioButton.setToolTipText("use original obj call method");
                }
            }
        });
        useProxyRadioButton.setSelected(true);
        topPanel.add(useProxyRadioButton);

        return topPanel;
    }

    private JSONObject buildParams(PsiMethod method, JSONArray args, String action) {
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
        params.put("original",!useProxyRadioButton.isSelected());
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
        initParams(inputEditorTextField,methodAction.getMethod());
    }

}
