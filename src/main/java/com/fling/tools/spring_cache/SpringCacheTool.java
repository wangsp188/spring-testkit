package com.fling.tools.spring_cache;

import com.alibaba.fastjson.JSONObject;
import com.fling.tools.ToolHelper;
import com.fling.tools.flexible_test.FlexibleTestIconProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.fling.view.FlingToolWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

public class SpringCacheTool extends BasePluginTool {
    private static final Icon KIcon = IconLoader.getIcon("/icons/k.svg", SpringCacheTool.class);

    public static final Icon CACHEABLE_DISABLE_ICON = IconLoader.getIcon("/icons/cacheable-disable.svg",SpringCacheIconProvider.class);


    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    {
        this.tool = PluginToolEnum.SPRING_CACHE;
    }

    public SpringCacheTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }



    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionComboBox = addActionComboBox(SpringCacheIconProvider.CACHEABLE_ICON,CACHEABLE_DISABLE_ICON, "<strong>spring-cache</strong><br>\n" +
                "<ul>\n" +
                "    <li>spring bean 中带有 @Cacheable/@CacheEvict/@CachePut/@Caching 的 public 函数</li>\n" +
                "    <li>非 static</li>\n" +
                "</ul>",topPanel, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });


        JButton getKeyButton = new JButton(KIcon);
        getKeyButton.setToolTipText("build keys");
        JButton getValButton = new JButton(AllIcons.Actions.Find);
        getValButton.setToolTipText("build keys and get values for every key");
        JButton delValButton = new JButton(AllIcons.Actions.GC);
        delValButton.setToolTipText("build keys and delete these");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(30, 30);
        getKeyButton.setPreferredSize(buttonSize);
        getValButton.setPreferredSize(buttonSize);
        delValButton.setPreferredSize(buttonSize);

        getKeyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingToolWindow.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(getKeyButton, KIcon, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleAction(app.getSidePort(),"build_cache_key");
                    }
                });
            }
        });

        getValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingToolWindow.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(getValButton, AllIcons.Actions.Find, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleAction(app.getSidePort(),"get_cache");
                    }
                });
            }
        });

        delValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingToolWindow.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(delValButton, AllIcons.Actions.GC, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleAction(app.getSidePort(),"delete_cache");
                    }
                });
            }
        });

        topPanel.add(getKeyButton);
        topPanel.add(getValButton);
        topPanel.add(delValButton);

        return topPanel;
    }

    private JSONObject handleAction(int sidePort,String action) {
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
        if (selectedItem==null) {
            setOutputText("pls select method");
            return null;
        }
        selectedItem.setArgs(jsonInput);
        JSONObject paramsJson = buildParams(selectedItem.getMethod(), jsonObject, action);
        try {
            return HttpUtil.sendPost("http://localhost:" + sidePort + "/", paramsJson, JSONObject.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        params.put("args", ToolHelper.adapterParams(method,args).toJSONString());
        params.put("action", action);
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.SPRING_CACHE.getCode());
        req.put("params", params);
        if(useScript){
            req.put("script", LocalStorageHelper.getAppScript(getProject(), getSelectedAppName()));
        }
        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(getProject());
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
        if(!(psiElement instanceof PsiMethod)) {
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