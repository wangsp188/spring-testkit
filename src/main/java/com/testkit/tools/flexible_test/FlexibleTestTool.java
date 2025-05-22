package com.testkit.tools.flexible_test;

import com.alibaba.fastjson.JSONObject;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Computable;
import com.testkit.TestkitHelper;
import com.testkit.ReqStorageHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.testkit.view.TestkitToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FlexibleTestTool extends BasePluginTool {

    public static final Icon FLEXIBLE_TEST_DISABLE_ICON = IconLoader.getIcon("/icons/test-code-disable.svg", FunctionCallIconProvider.class);


    private JComboBox<ToolHelper.MethodAction> actionComboBox;


    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public FlexibleTestTool(TestkitToolWindow testkitToolWindow) {
        super(testkitToolWindow);
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
            JSONObject.parseObject(jsonInputField.getText().trim());
        } catch (Exception e) {
            throw new RuntimeException("Input parameter must be json object");
        }
        return RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                    @Override
                    public boolean test(RuntimeHelper.AppMeta appMeta) {
                        return ToolHelper.isDependency(methodAction.getMethod(), getProject(), appMeta.getModule());
                    }
                }).map(new Function<RuntimeHelper.AppMeta, String>() {
                    @Override
                    public String apply(RuntimeHelper.AppMeta appMeta) {
                        return appMeta.getApp();
                    }
                })
                .toList();
    }

    @Override
    protected void handleStore(String app, String group, String title) {
        PsiMethod method = ((ToolHelper.MethodAction) actionComboBox.getSelectedItem()).getMethod();
        String text = jsonInputField.getText();
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
        methodMeta.setUseInterceptor(useInterceptor);
        ReqStorageHelper.saveAppReq(getProject(), app, StringUtils.isBlank(group) ? "undefined" : group, ReqStorageHelper.ItemType.flexible_test, ToolHelper.buildMethodKey(method), methodMeta, savedReq.getTitle(), savedReq);
    }

    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(FlexibleTestIconProvider.FLEXIBLE_TEST_ICON, FLEXIBLE_TEST_DISABLE_ICON, "<strong>flexible-test</strong><br>\n" +
                "<ul>\n" +
                "    <li>module test source ,public method of  package : ${Test Package}</li>\n" +
                "    <li>not static</li>\n" +
                "</ul>", topPanel, new ActionListener() {

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
                RuntimeHelper.VisibleApp app = RuntimeHelper.getSelectedApp(getProject().getName());
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(runButton, AllIcons.Actions.Execute, app.getTestkitPort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        String jsonInput = jsonInputField.getDocument().getText();
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
                            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Please select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonInput);
                        return buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FLEXIBLE_TEST.getCode());
                    }
                });
            }
        });
        topPanel.add(runButton, gbc);
        return topPanel;
    }

    @Override
    protected void handleCopyInput(AnActionEvent e) {
        String jsonInput = jsonInputField.getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "input parameter is blank");
            return;
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonInput);
        } catch (Exception ex) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "input parameter must be json object");
            return;
        }
        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (selectedItem == null) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "Please select method");
            return;
        }

        DefaultActionGroup copyGroup = new DefaultActionGroup();
        //显示的一个图标加上标题
        AnAction copyDirect = new AnAction("Copy the input params", "Copy the input params", JSON_ICON) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                FlexibleTestTool.super.handleCopyInput(e);
            }
        };
        copyGroup.add(copyDirect); // 将动作添加到动作组中

        //找出所有可能依赖的服务
        Map<String, String> appInterceptors = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
            @Override
            public boolean test(RuntimeHelper.AppMeta appMeta) {
                return ToolHelper.isDependency(selectedItem.getMethod(), getProject(), appMeta.getModule());
            }
        }).collect(Collectors.toMap(new Function<RuntimeHelper.AppMeta, String>() {
            @Override
            public String apply(RuntimeHelper.AppMeta appMeta) {
                return appMeta.getApp();
            }
        }, new Function<RuntimeHelper.AppMeta, String>() {
            @Override
            public String apply(RuntimeHelper.AppMeta appMeta) {
                String s = SettingsStorageHelper.encodeInterceptor(toolWindow.getProject(), appMeta.getApp());
                return s == null ? "" : s;
            }
        }));

        if (useInterceptor && !appInterceptors.isEmpty()) {
            for (Map.Entry<String, String> stringStringEntry : appInterceptors.entrySet()) {
                AnAction copyCmd = new AnAction("Copy Flexible-test " + stringStringEntry.getKey() + " Cmd", "Copy Flexible-test " + stringStringEntry.getKey() + " Cmd", CMD_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        JSONObject callReq = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FLEXIBLE_TEST.getCode());
                        callReq.put("interceptor", "".equals(stringStringEntry.getValue()) ? null : stringStringEntry.getValue());
                        String cmd = PluginToolEnum.FLEXIBLE_TEST.getCode() + " " + JSONObject.toJSONString(callReq);
                        TestkitHelper.copyToClipboard(getProject(), cmd, "Cmd copied<br>You can execute this directly in testkit-dig");
                    }
                };
                copyGroup.add(copyCmd); // 将动作添加到动作组中
            }
        } else {
            AnAction copyCmd = new AnAction("Copy Flexible-test Cmd", "Copy Flexible-test Cmd", CMD_ICON) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    JSONObject callReq = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FLEXIBLE_TEST.getCode());
                    callReq.put("interceptor", null);
                    String cmd = PluginToolEnum.FLEXIBLE_TEST.getCode() + " " + JSONObject.toJSONString(callReq);
                    TestkitHelper.copyToClipboard(getProject(), cmd, "Cmd copied<br>You can execute this directly in testkit-dig");
                }
            };
            copyGroup.add(copyCmd); // 将动作添加到动作组中
        }

        JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("CopyFlexibleTestPopup", copyGroup).getComponent();
        popupMenu.show(jsonInputField, 0, 0);
    }

    private JSONObject buildParams(PsiMethod method, JSONObject args, String action) {
        return ApplicationManager.getApplication().runReadAction(new Computable<JSONObject>() {
            @Override
            public JSONObject compute() {
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
                if (useInterceptor) {
                    RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(getProject().getName());
                    req.put("interceptor", SettingsStorageHelper.encodeInterceptor(getProject(), visibleApp == null ? null : visibleApp.getAppName()));
                }
                SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(getProject());
                req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
                return req;
            }
        });
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
            if (Objects.equals(ToolHelper.buildMethodKey(actionComboBox.getItemAt(i).getMethod()),ToolHelper.buildMethodKey(method))) {
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
            jsonInputField.setText("{}");
            return;
        }
        ToolHelper.initParamsTextField(jsonInputField, methodAction);
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
