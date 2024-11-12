package com.nb.tools.spring_cache;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.util.HttpUtil;
import com.nb.util.LocalStorageHelper;
import com.nb.view.WindowHelper;
import com.nb.view.PluginToolWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.function.Supplier;

public class SpringCacheTool extends BasePluginTool  implements ActionTool {
    private static final Icon KIcon = IconLoader.getIcon("/icons/K.svg", SpringCacheTool.class);

    private JComboBox<MethodAction> actionComboBox;

    private MethodAction lastMethodAction;

    {
        this.tool = PluginToolEnum.SPRING_CACHE;
    }

    public SpringCacheTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }


//    private JPanel createTopPanel() {
//        JPanel topPanel = new JPanel(new BorderLayout());
//        // 创建下拉框并添加到左侧
//        actionComboBox = new ComboBox<>();
//        topPanel.add(actionComboBox, BorderLayout.WEST);
//
//        // 创建按钮并设置图标和提示文字
//        JButton getKeyButton = new JButton(new ImageIcon(new ImageIcon(getClass().getResource("/icons/K.png")).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
//        getKeyButton.setToolTipText("get keys");
//        JButton getValButton = new JButton(AllIcons.Actions.Find);
//        getValButton.setToolTipText("get keys and get values for every key");
//        JButton delValButton = new JButton(AllIcons.Actions.GC);
//        delValButton.setToolTipText("get keys and delete these");
//
//        // 设置按钮大小
//        Dimension buttonSize = new Dimension(32, 32);
//        getKeyButton.setPreferredSize(buttonSize);
//        getValButton.setPreferredSize(buttonSize);
//        delValButton.setPreferredSize(buttonSize);
//
//        // 创建一个面板来放置按钮
//        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        buttonPanel.add(getKeyButton);
//        buttonPanel.add(getValButton);
//        buttonPanel.add(delValButton);
//
//        // 将按钮面板添加到右侧
//        topPanel.add(buttonPanel, BorderLayout.EAST);
//
//        // 设置下拉框的首选宽度
//        actionComboBox.setPreferredSize(new Dimension(200, 32));
//
//        // 设置整个面板的高度
//        topPanel.setPreferredSize(new Dimension(600, 32));
//
//        // 添加动作监听器
//        actionComboBox.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                triggerChangeAction();
//            }
//        });
//
//        getKeyButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                handleAction("build_cache_key");
//            }
//        });
//
//        getValButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                handleAction("get_cache");
//            }
//        });
//
//        delValButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                handleAction("delete_cache");
//            }
//        });
//
//        return topPanel;
//    }


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


        JButton getKeyButton = new JButton(KIcon);
        getKeyButton.setToolTipText("get keys");
        JButton getValButton = new JButton(AllIcons.Actions.Find);
        getValButton.setToolTipText("get keys and get values for every key");
        JButton delValButton = new JButton(AllIcons.Actions.GC);
        delValButton.setToolTipText("get keys and delete these");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        getKeyButton.setPreferredSize(buttonSize);
        getValButton.setPreferredSize(buttonSize);
        delValButton.setPreferredSize(buttonSize);

        getKeyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WindowHelper.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find visible app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerTask(getKeyButton, KIcon, outputTextArea, app.getSidePort(), new Supplier<JSONObject>() {
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
                WindowHelper.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find visible app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerTask(getValButton, AllIcons.Actions.GC, outputTextArea, app.getSidePort(), new Supplier<JSONObject>() {
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
                WindowHelper.VisibleApp app = getSelectedApp();
                if(app==null){
                    Messages.showMessageDialog(getProject(),
                            "Failed to find visible app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerTask(getValButton, AllIcons.Actions.Find, outputTextArea, app.getSidePort(), new Supplier<JSONObject>() {
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
        JSONObject paramsJson = buildParams(method, jsonArray, action);
        try {
            return HttpUtil.sendPost("http://localhost:" + sidePort + "/", paramsJson, JSONObject.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        params.put("action", action);
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.SPRING_CACHE.getCode());
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
        lastMethodAction = methodAction;
        initParams(inputEditorTextField, methodAction.getMethod());
    }


}