package com.fling.tools;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.FlingHelper;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.fling.util.HttpUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class BasePluginTool {


    protected Set<String> cancelReqs = new HashSet<>(128);
    protected String lastReqId;

    protected SwingWorker lastLocalFuture;


    protected FlingToolWindow flingWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected EditorTextField inputEditorTextField;

    public BasePluginTool(FlingToolWindow flingToolWindow) {
        // 初始化panel
        this.flingWindow = flingToolWindow;
        this.panel = new JPanel(new GridBagLayout());
        // 设置inputPanel的边框为不可见
        this.panel.setBorder(BorderFactory.createEmptyBorder());
        initializePanel();
    }


    public PluginToolEnum getTool() {
        return tool;
    }

    public JPanel getPanel() {
        return panel;
    }

    public Project getProject() {
        return flingWindow.getProject();
    }

    public FlingToolWindow.VisibleApp getSelectedApp() {
        return flingWindow.getSelectedApp();
    }

    public String getSelectedAppName() {
        return flingWindow.getSelectedAppName();
    }


    protected void initializePanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // 顶部面板不占用垂直空间

        // Top panel for method selection and actions
        JPanel actionPanel = createActionPanel();
        panel.add(actionPanel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.3; // Middle panel takes 30% of the space

        // Middle panel for input parameters
        JPanel inputPanel = new JPanel(new BorderLayout());

        // 创建一个ActionGroup来包含所有的动作
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        if (hasActionBox()) {
            AnAction refreshAction = new AnAction("Refresh method parameters structure", "Refresh method parameters structure", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    refreshInputByActionBox();
                }
            };
            actionGroup.add(refreshAction);
        }


        // 添加复制按钮的动作
        AnAction copyAction = new AnAction("Copy input to clipboard", "Copy input to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                FlingHelper.copyToClipboard(getProject(), inputEditorTextField.getText(), "Input is copied");
            }
        };
        actionGroup.add(copyAction);


        // 创建ActionToolbar
        ActionToolbar actionToolbar = new ActionToolbarImpl("InputToolbar", actionGroup, false);
        JComponent toolbarComponent = actionToolbar.getComponent();
        toolbarComponent.setLayout(new BoxLayout(toolbarComponent, BoxLayout.Y_AXIS));


        // 将工具栏添加到控制面板的左侧
        inputPanel.add(toolbarComponent, BorderLayout.WEST);

        inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        inputPanel.add(new JBScrollPane(inputEditorTextField), BorderLayout.CENTER);

        panel.add(inputPanel, gbc);
    }

    protected abstract boolean hasActionBox();

    protected abstract void refreshInputByActionBox();

    protected abstract JPanel createActionPanel();

    public abstract boolean needAppBox();

    public abstract void onSwitchAction(PsiElement psiElement);


    protected void triggerHttpTask(JButton triggerBtn, Icon executeIcon, int sidePort, Supplier<JSONObject> submit) {
        if (AllIcons.Actions.Suspend.equals(triggerBtn.getIcon())) {
            if (lastReqId == null) {
                triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                return;
            }
            cancelReqs.add(lastReqId);
            new SwingWorker<JSONObject, Void>() {
                @Override
                protected JSONObject doInBackground() throws Exception {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("method", "stop_task");
                    HashMap<Object, Object> params = new HashMap<>();
                    params.put("reqId", lastReqId);
                    map.put("params", params);
                    return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);
                }

                @Override
                protected void done() {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    try {
                        JSONObject result = get();
                        if (cancelReqs.remove(lastReqId)) {
                            setOutputText("req is cancel, reqId:" + lastReqId);
                        }
                    } catch (Throwable e) {
                        if (cancelReqs.remove(lastReqId)) {
                            setOutputText("cancel req error, reqId:" + lastReqId + "\n" + ToolHelper.getStackTrace(e));
                        }
                    }
                }
            }.execute();
            return;
        }
        // 发起任务请求，获取请求ID
        JSONObject response = null;
        try {
            response = submit.get();
        } catch (Throwable e) {
            setOutputText("submit req error \n" + ToolHelper.getStackTrace(e));
            return;
        }
        if (response == null) {
            return;
        }
        if (!response.getBooleanValue("success") || response.getString("data") == null) {
            setOutputText("submit req error \n" + response.getString("message"));
            return;
        }
        String reqId = response.getString("data");
        lastReqId = reqId;
        triggerBtn.setIcon(AllIcons.Actions.Suspend);
        setOutputText("req is sent，reqId:" + reqId);
        // 第二个 SwingWorker 用于获取结果
        new SwingWorker<JSONObject, Void>() {

            @Override
            protected JSONObject doInBackground() throws Exception {
                HashMap<String, Object> map = new HashMap<>();
                map.put("method", "get_task_ret");
                HashMap<Object, Object> params = new HashMap<>();
                params.put("reqId", reqId);
                map.put("params", params);
                return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);
            }

            @Override
            protected void done() {
                try {
                    JSONObject result = get();
                    if (cancelReqs.remove(reqId)) {
                        System.out.println("请求已被取消，结果丢弃");
                    } else if (!result.getBooleanValue("success")) {
                        setOutputText("req is error\n" + result.getString("message"));
                    } else {

                        Object data = result.get("data");
                        if (data == null) {
                            setOutputText("null");
                        } else if (data instanceof String
                                || data instanceof Byte
                                || data instanceof Short
                                || data instanceof Integer
                                || data instanceof Long
                                || data instanceof Float
                                || data instanceof Double
                                || data instanceof Character
                                || data instanceof Boolean
                                || data.getClass().isEnum()) {
                            setOutputText(data.toString());
                        } else {
                            String jsonString = JSONObject.toJSONString(data, SerializerFeature.PrettyFormat, SerializerFeature.WriteNonStringKeyAsString);
                            setOutputText(jsonString);
                        }
                    }
                } catch (Throwable ex) {
                    setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(ex));
                } finally {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                }
            }
        }.execute();
    }

    protected void triggerLocalTask(JButton triggerBtn, Icon executeIcon, Supplier<String> submit) {
        if (AllIcons.Hierarchy.MethodNotDefined.equals(triggerBtn.getIcon())) {
//            triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
//            if (lastLocalFuture == null) {
//                return;
//            }
//            try {
//                lastLocalFuture.cancel(true);
//            } catch (Throwable e) {
//                e.printStackTrace();
//            }finally {
//                lastLocalFuture = null;
//                setOutputText("req is cancel");
//            }
            return;
        }
        //使用线程池提交任务
        lastLocalFuture = new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {
                return WriteCommandAction.runWriteCommandAction(getProject(), new Computable<String>() {
                    @Override
                    public String compute() {
                        return submit.get();
                    }
                });
            }

            @Override
            protected void done() {
                try {
                    String val = get();
                    SwingUtilities.invokeLater(() -> setOutputText(val));
                } catch (Throwable e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(e)));
                } finally {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                }
            }
        };
        setOutputText("req is sent");
        triggerBtn.setIcon(AllIcons.Hierarchy.MethodNotDefined);
        lastLocalFuture.execute();
    }


    protected void setOutputText(String content) {
        flingWindow.setOutputText(content);
    }

    protected ComboBox addActionComboBox(Icon icon, String tooltips, JPanel topPanel, ActionListener actionListener) {
        JButton testBtn = new JButton(icon == null ? AllIcons.Nodes.Method : icon);
        testBtn.setPreferredSize(new Dimension(32, 32));
        testBtn.setToolTipText(tooltips == null ? "" : tooltips);
        topPanel.add(testBtn);
        ComboBox actionComboBox = new ComboBox<>();
        actionComboBox.setPreferredSize(new Dimension(200, 32));
        actionComboBox.addActionListener(e -> {
            Object selectedItem = actionComboBox.getSelectedItem();
            if (selectedItem instanceof ToolHelper.MethodAction) {
                PsiMethod method = ((ToolHelper.MethodAction) selectedItem).getMethod();
                if (!method.isValid()) {
                    // If not, remove the current item
                    actionComboBox.removeItem(selectedItem);
                    // Then, select the first valid item
                    for (int i = 0; i < actionComboBox.getItemCount(); i++) {
                        ToolHelper.MethodAction item = (ToolHelper.MethodAction) actionComboBox.getItemAt(i);
                        PsiMethod itemMethod = item.getMethod();
                        if (itemMethod.isValid()) {
                            actionComboBox.setSelectedIndex(i);
                            break;
                        }
                    }
                    System.err.println("节点已失效，" + method);
                    return;
                }
            } else if (selectedItem instanceof ToolHelper.XmlTagAction) {

                XmlTag xmlTag = ((ToolHelper.XmlTagAction) selectedItem).getXmlTag();
                if (!xmlTag.isValid()) {
                    // If not, remove the current item
                    actionComboBox.removeItem(selectedItem);
                    // Then, select the first valid item
                    for (int i = 0; i < actionComboBox.getItemCount(); i++) {
                        ToolHelper.XmlTagAction item = (ToolHelper.XmlTagAction) actionComboBox.getItemAt(i);
                        XmlTag itemMethod = item.getXmlTag();
                        if (itemMethod.isValid()) {
                            actionComboBox.setSelectedIndex(i);
                            break;
                        }
                    }
                    System.err.println("节点已失效，" + xmlTag);
                    return;
                }
            }


            actionComboBox.setToolTipText(selectedItem == null ? "" : selectedItem.toString()); // 动态更新 ToolTipText
            if (actionListener != null) {
                actionListener.actionPerformed(e);
            }
        });
        topPanel.add(actionComboBox);

        return actionComboBox;
    }

    protected void refreshInputByActionBox(JComboBox actionComboBox) {
        Object selectedItem = actionComboBox.getSelectedItem();
        if (selectedItem instanceof ToolHelper.MethodAction) {
            ((ToolHelper.MethodAction) selectedItem).setArgs(null);
        } else if (selectedItem instanceof ToolHelper.XmlTagAction) {
            ((ToolHelper.XmlTagAction) selectedItem).setArgs(null);
        }
        actionComboBox.setSelectedItem(selectedItem);

        List<Object> itemsToDelete = new ArrayList<>();

        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            Object item = actionComboBox.getItemAt(i);
            if (item instanceof ToolHelper.MethodAction) {
                PsiMethod method = ((ToolHelper.MethodAction) item).getMethod();
                if (method == null || !method.isValid()) {
                    itemsToDelete.add(item);
                }
            } else if (item instanceof ToolHelper.XmlTagAction) {
                XmlTag xmlTag = ((ToolHelper.XmlTagAction) item).getXmlTag();
                if (xmlTag == null || !xmlTag.isValid()) {
                    itemsToDelete.add(item);
                }
            }
        }

        for (Object item : itemsToDelete) {
            actionComboBox.removeItem(item);
        }
    }

}