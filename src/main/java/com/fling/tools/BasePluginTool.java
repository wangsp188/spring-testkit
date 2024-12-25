package com.fling.tools;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fling.FlingHelper;
import com.fling.tools.call_method.CallMethodIconProvider;
import com.fling.util.JsonUtil;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.fling.util.HttpUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class BasePluginTool {

    public static final Icon CURL_ICON = IconLoader.getIcon("/icons/curl.svg", CallMethodIconProvider.class);


    protected Set<String> cancelReqs = new HashSet<>(128);
    protected String lastReqId;

//    protected SwingWorker lastLocalFuture;


    protected FlingToolWindow flingWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected EditorTextField inputEditorTextField;
    protected boolean useScript;
    protected DefaultActionGroup actionGroup;

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

    public List<String> getPorjectAppList() {
        return flingWindow.getProjectAppList();
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
        actionGroup = new DefaultActionGroup();



        if (hasActionBox()) {
            AnAction refreshAction = new AnAction("Refresh method parameters structure", "Refresh method parameters structure", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            refreshInputByActionBox();
                        }
                    });

                }
            };
            actionGroup.add(refreshAction);
        }


        // 添加复制按钮的动作
        AnAction copyAction = new AnAction("Copy input to clipboard", "Copy input to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                FlingHelper.copyToClipboard(getProject(), inputEditorTextField.getText(), "Input was copied");
            }
        };
        actionGroup.add(copyAction);

        // 添加复制按钮的动作
        AnAction curlAction = new AnAction("Parse curl", "Parse curl", CURL_ICON) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        flingWindow.openCurlDialog();
                    }
                });

            }
        };
        actionGroup.add(curlAction);


        // 创建ActionToolbar
        ActionToolbar actionToolbar = new ActionToolbarImpl("InputToolbar", actionGroup, false);
        actionToolbar.setTargetComponent(inputEditorTextField);
        JComponent toolbarComponent = actionToolbar.getComponent();
        toolbarComponent.setLayout(new BoxLayout(toolbarComponent, BoxLayout.Y_AXIS));


        // 将工具栏添加到控制面板的左侧
        inputPanel.add(toolbarComponent, BorderLayout.WEST);

        inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        inputEditorTextField.setPlaceholder("json params, plugin will init first, click refresh can parse again");
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
                            setOutputText("req is cancel, reqId:" + lastReqId, null);
                        }
                    } catch (Throwable e) {
                        if (cancelReqs.remove(lastReqId)) {
                            setOutputText("cancel req error, reqId:" + lastReqId + "\n" + ToolHelper.getStackTrace(e), null);
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
            setOutputText("submit req error \n" + ToolHelper.getStackTrace(e), null);
            return;
        }
        if (response == null) {
            return;
        }
        if (!response.getBooleanValue("success") || response.getString("data") == null) {
            setOutputText("submit req error \n" + response.getString("message"), null);
            return;
        }
        String reqId = response.getString("data");
        lastReqId = reqId;
        triggerBtn.setIcon(AllIcons.Actions.Suspend);
        setOutputText("req is send，reqId:" + reqId, null);
        // 第二个 SwingWorker 用于获取结果
//        new SwingWorker<JSONObject, Void>() {
//
//            @Override
//            protected JSONObject doInBackground() throws Exception {
//                HashMap<String, Object> map = new HashMap<>();
//                map.put("method", "get_task_ret");
//                HashMap<Object, Object> params = new HashMap<>();
//                params.put("reqId", reqId);
//                map.put("params", params);
//                return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);
//            }
//
//            @Override
//            protected void done() {
//                try {
//                    JSONObject result = get();
//                    if (cancelReqs.remove(reqId)) {
//                        System.out.println("请求已被取消，结果丢弃");
//                    } else {
//                        List<Map<String, String>> profile = result.getObject("profile", new TypeReference<List<Map<String, String>>>() {
//                        });
//                        if (!result.getBooleanValue("success")) {
//                            setOutputText("req is error\n" + result.getString("message"), profile);
//                        } else {
//                            Object data = result.get("data");
//                            if (data == null) {
//                                setOutputText("null", profile);
//                            } else if (data instanceof String
//                                    || data instanceof Byte
//                                    || data instanceof Short
//                                    || data instanceof Integer
//                                    || data instanceof Long
//                                    || data instanceof Float
//                                    || data instanceof Double
//                                    || data instanceof Character
//                                    || data instanceof Boolean
//                                    || data.getClass().isEnum()) {
//                                setOutputText(data.toString(), profile);
//                            } else {
//                                setOutputText(JsonUtil.formatObj(data), profile);
//                            }
//                        }
//                    }
//                } catch (Throwable ex) {
//                    setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(ex));
//                } finally {
//                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
//                }
//            }
//        }.execute();


        ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Req is send，wait ...", false) {
            @Override
            public void run( ProgressIndicator indicator) {
                try {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("method", "get_task_ret");
                    HashMap<Object, Object> params = new HashMap<>();
                    params.put("reqId", reqId);
                    map.put("params", params);

                    JSONObject result = HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (cancelReqs.remove(reqId)) {
                            System.out.println("请求已被取消，结果丢弃");
                        } else {
                            List<Map<String, String>> profile = result.getObject("profile", new TypeReference<List<Map<String, String>>>() {});
                            if (!result.getBooleanValue("success")) {
                                setOutputText("req is error\n" + result.getString("message"), profile);
                            } else {
                                Object data = result.get("data");
                                if (data == null) {
                                    setOutputText("null", profile);
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
                                    setOutputText(data.toString(), profile);
                                } else {
                                    setOutputText(JsonUtil.formatObj(data), profile);
                                }
                            }
                        }
                        triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    });
                } catch (Throwable ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(ex));
                        triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    });
                }
            }
        });
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
//        使用线程池提交任务
        SwingWorker worker = new SwingWorker<String, Void>() {

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

        // 立即更新UI
        setOutputText("Build sql ...");
        triggerBtn.setIcon(AllIcons.Hierarchy.MethodNotDefined);
        worker.execute();
    }


    protected void setOutputText(String content) {
        flingWindow.setOutputText(content);
        flingWindow.setOutputProfile(null);
    }

    protected void setOutputText(String content, List<Map<String, String>> outputProfile) {
        flingWindow.setOutputText(content);
        flingWindow.setOutputProfile(outputProfile);
    }


    protected ComboBox addActionComboBox(Icon icon, Icon disableIcon, String tooltips, JPanel topPanel, ActionListener actionListener) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        JButton testBtn = new JButton(icon);
        if (icon != disableIcon) {
            useScript = true;
            testBtn.setToolTipText("<html>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    "<strong>脚本已打开</strong><br>\n" + tooltips + "\n</html>");
            testBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (testBtn.getIcon() == icon) {
                        useScript = false;
                        testBtn.setIcon(disableIcon);
                        testBtn.setToolTipText("<html>\n" +
                                "<meta charset=\"UTF-8\">\n" +
                                "<strong>脚本已关闭</strong><br>\n" + tooltips + "\n</html>");
                    } else {
                        useScript = true;
                        testBtn.setIcon(icon);
                        testBtn.setToolTipText("<html>\n" +
                                "<meta charset=\"UTF-8\">\n" +
                                "<strong>脚本已打开</strong><br>\n" + tooltips + "\n</html>");
                    }
                }
            });
        } else {
            useScript = false;
            testBtn.setToolTipText("<html>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    "<strong>不支持脚本</strong><br>\n" + tooltips + "\n</html>");
        }

        testBtn.setPreferredSize(new Dimension(32, 32));
        topPanel.add(testBtn, gbc);
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
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 1;
        gbc.gridy = 0;
        topPanel.add(actionComboBox, gbc);
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