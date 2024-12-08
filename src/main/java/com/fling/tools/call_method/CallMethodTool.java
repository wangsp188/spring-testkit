package com.fling.tools.call_method;

import com.alibaba.fastjson.JSONObject;
import com.fling.tools.ToolHelper;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.intellij.ui.components.JBRadioButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Supplier;

public class CallMethodTool extends BasePluginTool {

    public static final Icon CALL_METHOD_DISABLE_ICON = IconLoader.getIcon("/icons/spring-fling-disable.svg", CallMethodIconProvider.class);

    public static final Icon PROXY_DISABLE_ICON = IconLoader.getIcon("/icons/proxy-disable.svg", CallMethodIconProvider.class);
    public static final Icon PROXY_ICON = IconLoader.getIcon("/icons/proxy.svg", CallMethodIconProvider.class);




    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    private JToggleButton useProxyButton;

    {
        this.tool = PluginToolEnum.CALL_METHOD;
    }

    public CallMethodTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }

    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(CallMethodIconProvider.CALL_METHOD_ICON,CALL_METHOD_DISABLE_ICON,
                "<strong>call-method</strong>\n<ul>\n" +
                "    <li>spring bean 的 public 函数</li>\n" +
                "    <li>非init/main</li>\n" +
                "    <li>非test source</li>\n" +
                "</ul>", topPanel, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });
        // Populate methodComboBox with method names


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 3, 3, 3);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 2;
        gbc.gridy = 0;

        // Add the radio button
        useProxyButton = new JToggleButton(PROXY_ICON,true);
        useProxyButton.setPreferredSize(new Dimension(32, 32));
        useProxyButton.setToolTipText("use proxy obj call method");
        useProxyButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyButton.isSelected()) {
                    useProxyButton.setIcon(PROXY_ICON);
                    useProxyButton.setToolTipText("use proxy obj call method");
                } else {
                    useProxyButton.setIcon(PROXY_DISABLE_ICON);
                    useProxyButton.setToolTipText("use original obj call method");
                }
            }
        });
        topPanel.add(useProxyButton,gbc);

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
                            setOutputText("input parameter must be json object");
                            return null;
                        }
                        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            setOutputText("pls select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonInput);
                        JSONObject params = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.CALL_METHOD.getCode());
                        try {
                            return HttpUtil.sendPost("http://localhost:" + app.getSidePort() + "/", params, JSONObject.class);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }
        });

        gbc.gridx = 3;
        topPanel.add(runButton,gbc);

        return topPanel;
    }

    private JSONObject buildParams(PsiMethod method, JSONObject args, String action) {
        JSONObject params = new JSONObject();
        PsiClass containingClass = method.getContainingClass();
        String typeClass = containingClass.getQualifiedName();
        params.put("typeClass", typeClass);

        String beanName = ToolHelper.getBeanNameFromClass(containingClass);
        params.put("beanName", beanName);
        params.put("methodName", method.getName());

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }
        params.put("argTypes", JSONObject.toJSONString(argTypes));
        params.put("args", ToolHelper.adapterParams(method, args).toJSONString());
        params.put("original", !useProxyButton.isSelected());
        JSONObject req = new JSONObject();
        req.put("method", action);
        req.put("params", params);
        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(getProject());
        req.put("monitor", monitorConfig.isEnable());
        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
        if(useScript){
            req.put("script", LocalStorageHelper.getAppScript(getProject(), getSelectedAppName()));
        }
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
        if (methodAction == null || methodAction.getMethod() == null || !methodAction.getMethod().isValid()) {
            inputEditorTextField.setText("{}");
            return;
        }
        ToolHelper.initParamsTextField(inputEditorTextField, methodAction);
    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox != null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }
}
