package com.fling.tools.flexible_test;

import com.alibaba.fastjson.JSONObject;
import com.fling.FlingHelper;
import com.fling.ReqStorageHelper;
import com.fling.RuntimeAppHelper;
import com.fling.SettingsStorageHelper;
import com.fling.tools.ToolHelper;
import com.fling.tools.method_call.MethodCallIconProvider;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FlexibleTestTool extends BasePluginTool {

    public static final Icon FLEXIBLE_TEST_DISABLE_ICON = IconLoader.getIcon("/icons/test-code-disable.svg", MethodCallIconProvider.class);


    private JComboBox<ToolHelper.MethodAction> actionComboBox;


    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }

    @Override
    protected boolean canStore() {
        return true;
    }

    @Override
    protected List<String> verifyNowStore() {
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null || !methodAction.getMethod().isValid()) {
            throw new RuntimeException("Please select a valid method");
        }
        try {
            JSONObject.parseObject(inputEditorTextField.getText().trim());
        } catch (Exception e) {
            throw new RuntimeException("Input parameter must be json object");
        }
        return RuntimeAppHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeAppHelper.AppMeta>() {
                    @Override
                    public boolean test(RuntimeAppHelper.AppMeta appMeta) {
                        return ToolHelper.isDependency(methodAction.getMethod(),getProject(),appMeta.getModule());
                    }
                }).map(new Function<RuntimeAppHelper.AppMeta, String>() {
                    @Override
                    public String apply(RuntimeAppHelper.AppMeta appMeta) {
                        return appMeta.getApp();
                    }
                })
                .toList();
    }

    @Override
    protected void handleStore(String app, String group, String title) {
        PsiMethod method = ((ToolHelper.MethodAction) actionComboBox.getSelectedItem()).getMethod();
        String text = inputEditorTextField.getText();
        JSONObject args = JSONObject.parseObject(text);
        PsiClass containingClass = method.getContainingClass();
        // 获取包含这个类的文件
        String code = containingClass.getContainingFile().getText();

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }

        List<String> argNames = ToolHelper.fetchNames(method);

        ReqStorageHelper.SavedReq savedReq = new ReqStorageHelper.SavedReq();
        savedReq.setTitle(StringUtils.isBlank(title) ? "undefined" : title);
        savedReq.setArgs(args);

        ReqStorageHelper.FlexibleTestMeta methodMeta = new ReqStorageHelper.FlexibleTestMeta();
        methodMeta.setCode(code);
        methodMeta.setMethodName(method.getName());
        methodMeta.setArgNames(argNames);
        methodMeta.setArgTypes(JSONObject.toJSONString(argTypes));
        methodMeta.setUseScript(useScript);
        ReqStorageHelper.saveAppReq(getProject(), app, StringUtils.isBlank(group) ? "undefined" : group, ReqStorageHelper.ItemType.flexible_test, ToolHelper.buildMethodKey(method), methodMeta, savedReq.getTitle(), savedReq);
    }

    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(FlexibleTestIconProvider.FLEXIBLE_TEST_ICON,FLEXIBLE_TEST_DISABLE_ICON,"<strong>flexible-test</strong><br>\n" +
                "<ul>\n" +
                "    <li>module test source 下 ${Test Package} 内的 public 函数</li>\n" +
                "    <li>非static</li>\n" +
                "</ul>",topPanel, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.gridx = 3;
        gbc.gridy = 0;

        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("Execute this");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RuntimeAppHelper.VisibleApp app = RuntimeAppHelper.getSelectedApp(getProject().getName());
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
                        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            FlingHelper.alert(getProject(),Messages.getErrorIcon(),"Please select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonInput);
                        return buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FLEXIBLE_TEST.getCode());
                    }
                });
            }
        });
        topPanel.add(runButton,gbc);
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
        if(useScript){
            RuntimeAppHelper.VisibleApp visibleApp = RuntimeAppHelper.getSelectedApp(getProject().getName());
            req.put("interceptor", SettingsStorageHelper.getAppScript(getProject(), visibleApp==null?null:visibleApp.getAppName()));
        }
        SettingsStorageHelper.MonitorConfig monitorConfig = SettingsStorageHelper.getMonitorConfig(getProject());
        req.put("monitor", monitorConfig.isEnable());
        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
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
        return actionComboBox!=null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }

}
