package com.testkit.tools;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.*;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.util.JsonUtil;
import com.testkit.view.TestkitStoreWindowFactory;
import com.testkit.view.TestkitToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.json.JsonLanguage;
import com.intellij.notification.NotificationType;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.testkit.util.HttpUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class BasePluginTool {

    public static final Icon JSON_ICON = IconLoader.getIcon("/icons/json.svg", BasePluginTool.class);
    public static final Icon CMD_ICON = IconLoader.getIcon("/icons/cmd.svg", BasePluginTool.class);


    protected Set<String> cancelReqs = new HashSet<>(128);
    protected String lastReqId;

    protected Set<String> cancelLocalTasks = new HashSet<>(128);
    protected String lastLocalTaskId;
    protected Thread lastLocalThread;

    protected JPanel actionPanel;
    protected TestkitToolWindow toolWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected EditorTextField jsonInputField;
    protected boolean useInterceptor;
    protected DefaultActionGroup actionGroup;

    public BasePluginTool(TestkitToolWindow testkitToolWindow) {
        // 初始化panel
        this.toolWindow = testkitToolWindow;
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
        return toolWindow.getProject();
    }


    protected void initializePanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // 顶部面板不占用垂直空间

        // Top panel for method selection and actions
        actionPanel = createActionPanel();
        panel.add(actionPanel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.3; // Middle panel takes 30% of the space

        // Middle panel for input parameters
        JPanel inputPanel = new JPanel(new BorderLayout());

        // 创建一个ActionGroup来包含所有的动作
        actionGroup = new DefaultActionGroup();


        if (hasActionBox()) {
            AnAction refreshAction = new AnAction("Refresh parameters structure", "Refresh parameters structure", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    WriteCommandAction.runWriteCommandAction(toolWindow.getProject(), new Runnable() {
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
        AnAction copyAction = new AnAction("Copy input", "Copy input", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                handleCopyInput(e);
            }
        };
        actionGroup.add(copyAction);

        if (canStore()) {
            fillStoreAction();
        }



        jsonInputField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        // 先创建中部输入内容，保证 jsonInputField 已初始化（供工具栏动作使用）
        JComponent centerComponent = createInputContent(inputPanel);

        // 创建ActionToolbar（在 jsonInputField 初始化之后设置 target）
        ActionToolbar actionToolbar = new ActionToolbarImpl("InputToolbar", actionGroup, false);
        actionToolbar.setTargetComponent(centerComponent);
        JComponent toolbarComponent = actionToolbar.getComponent();
        toolbarComponent.setLayout(new BoxLayout(toolbarComponent, BoxLayout.Y_AXIS));

        // 将工具栏添加到控制面板的左侧
        inputPanel.add(toolbarComponent, BorderLayout.WEST);

        // 添加中部输入内容
        inputPanel.add(centerComponent, BorderLayout.CENTER);
        panel.add(inputPanel, gbc);
    }

    /**
     * 子类可重写该方法以自定义中部输入区域的渲染。
     * 默认实现提供 JSON 编辑器。
     */
    protected JComponent createInputContent(JPanel inputPanel) {
        JBScrollPane scrollPane = new JBScrollPane(jsonInputField);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Input"));
        jsonInputField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent event) {
                scrollPane.revalidate();
                scrollPane.repaint();
            }
        });
        return scrollPane;
    }

    protected boolean canStore() {
        return true;
    }

    protected List<String> verifyNowStore() throws Throwable {
        return null;
    }


    protected void handleStore(String app, String group, String title) {
    }


    protected void handleCopyInput(AnActionEvent e){
        TestkitHelper.copyToClipboard(getProject(), jsonInputField.getText(), "Input was copied");
    }

    private void fillStoreAction() {
        AnAction storeAction = new AnAction("Save this to store", "Save this to store", AllIcons.Actions.MenuSaveall) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                List<String> apps = null;
                try {
                    apps = verifyNowStore();
                } catch (Throwable ex) {
                    TestkitHelper.alert(getProject(), Messages.getErrorIcon(), ex.getMessage());
                    return;
                }
                if (CollectionUtils.isEmpty(apps)) {
                    TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Failed to find adapter app");
                    return;
                }


                // 创建面板
                JPanel panel = new JPanel();
                panel.setLayout(new GridBagLayout());
                panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = JBUI.insets(5);

                // 创建下拉框
                String[] options = apps.toArray(new String[apps.size()]);
                ComboBox<String> comboBox = new ComboBox<>(options);

                // 创建两个文本框
                JBTextField groupField = new JBTextField("default");
                groupField.getEmptyText().setText("Which group to save to");
                groupField.setEditable(true);
                groupField.setEnabled(true);
                groupField.setFocusable(true);
                JBTextField titleField = new JBTextField("default");
                titleField.getEmptyText().setText("Give this parameter a name");
                titleField.setEditable(true);
                titleField.setEnabled(true);
                titleField.setFocusable(true);

                // 创建提交按钮
                JButton submitButton = new JButton("Save");

                // 添加组件到面板
                gbc.gridx = 0;
                gbc.gridy = 0;
                JLabel appLabel = new JLabel("App:");
                appLabel.setLabelFor(comboBox);
                appLabel.setToolTipText("Which app domain you want to save to");
                panel.add(appLabel, gbc);
                gbc.gridx = 1;
                panel.add(comboBox, gbc);

                gbc.gridx = 0;
                gbc.gridy = 1;
                JLabel groupLabel = new JLabel("Group:");
                appLabel.setToolTipText("Grouping functions");
                panel.add(groupLabel, gbc);
                gbc.gridx = 1;
                panel.add(groupField, gbc);

                gbc.gridx = 0;
                gbc.gridy = 2;
                JLabel titleLabel = new JLabel("Title:");
                titleLabel.setToolTipText("Multiple params can be stored in a function");
                panel.add(titleLabel, gbc);
                gbc.gridx = 1;
                panel.add(titleField, gbc);

                gbc.gridx = 0;
                gbc.gridy = 3;
                gbc.gridwidth = 2;
                panel.add(submitButton, gbc);

                // 创建弹出框
                JBPopupFactory popupFactory = JBPopupFactory.getInstance();
                JBPopup popup = popupFactory.createComponentPopupBuilder(panel, titleField)
                        .setRequestFocus(true)
                        .setFocusable(true)
                        .setTitle("Save this to store")
                        .setMovable(true)
                        .setResizable(false)
                        .createPopup();


                // 添加提交按钮事件
                submitButton.addActionListener(actionEvent -> {
                    // 在这里处理提交的数据
                    handleStore((String) comboBox.getSelectedItem(), groupField.getText(), titleField.getText());
                    new Thread() {
                        public void run() {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            TestkitStoreWindowFactory.refreshStore(getProject());
                        }
                    }.start();
                    TestkitHelper.notify(getProject(), NotificationType.INFORMATION, "Save success<br>You can click the store button in the upper right corner of the plugin to open the store window");
                    popup.dispose();
                });

                // 显示弹出框
                popup.show(new RelativePoint(jsonInputField, new Point(0, 0)));
            }
        };
        actionGroup.add(storeAction);
    }

    protected abstract boolean hasActionBox();

    protected abstract void refreshInputByActionBox();

    protected abstract JPanel createActionPanel();

    public abstract boolean needAppBox();

    public abstract void onSwitchAction(PsiElement psiElement);


    protected void triggerTestkitServerTask(JButton triggerBtn, Icon executeIcon, int sidePort, Supplier<JSONObject> submit) {
        if (AllIcons.Hierarchy.MethodNotDefined.equals(triggerBtn.getIcon())) {
            return;
        }


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
                    return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class,5,30);
                }

                @Override
                protected void done() {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    try {
                        JSONObject result = get();
                        if (cancelReqs.remove(lastReqId)) {
                            setOutputText("req is cancel\nreqId:" + lastReqId, null);
                        }
                    } catch (Throwable e) {
                        if (cancelReqs.remove(lastReqId)) {
                            setOutputText("cancel req error\nreqId:" + lastReqId + "\n" + ToolHelper.getStackTrace(e), null);
                        }
                    }
                }
            }.execute();
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing " + getTool().getCode() + ", please wait ...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                // 发起任务请求，获取请求ID
                JSONObject response = null;
                try {
                    JSONObject request = submit.get();
                    if (request == null) {
                        return;
                    }
                    response = HttpUtil.sendPost("http://localhost:" + sidePort + "/", request, JSONObject.class,5,30);
                } catch (Throwable e) {
                    setOutputText("submit req error \n" + ToolHelper.getStackTrace(e), null);
                    return;
                }
                if (response == null || !response.getBooleanValue("success") || response.getString("data") == null) {
                    setOutputText("submit req error \n" + response.getString("message"), null);
                    return;
                }
                String reqId = response.getString("data");
                lastReqId = reqId;
                triggerBtn.setIcon(AllIcons.Actions.Suspend);
                setOutputText("req is send\nreqId:" + reqId, null);

                try {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("method", "get_task_ret");
                    HashMap<Object, Object> params = new HashMap<>();
                    params.put("reqId", reqId);
                    map.put("params", params);

                    JSONObject result = HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class,5,600);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (cancelReqs.remove(reqId)) {
                            System.out.println("请求已被取消，结果丢弃");
                        } else {
                            if (result == null) {
                                setOutputText("req is error\n result is null", null);
                            } else {
                                List<Map<String, String>> profile = result.getObject("profile", new TypeReference<List<Map<String, String>>>() {
                                });
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

    protected void triggerLocalTask(JButton triggerBtn, Icon executeIcon, String msg, Supplier<String> submit) {
        if (AllIcons.Actions.Suspend.equals(triggerBtn.getIcon())) {
            if (lastLocalTaskId == null) {
                triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                return;
            }
            cancelLocalTasks.add(lastLocalTaskId);
            try {
                if (lastLocalThread != null) {
                    lastLocalThread.interrupt();
                }
            } catch (Throwable ignore) {
            } finally {
                setOutputText("req is cancel");
                triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
            }
            return;
        }


        ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), msg + ", please wait ...", false) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String taskId = UUID.randomUUID().toString();
                lastLocalTaskId = taskId;
                // 立即更新UI
                setOutputText(msg + " ...");
                triggerBtn.setIcon(AllIcons.Actions.Suspend);
                lastLocalThread = Thread.currentThread();
                try {
                    String ret = submit.get();
                    if (!cancelLocalTasks.remove(taskId)) {
                        setOutputText(ret);
                    }
                } catch (Throwable ex) {
                    if (!cancelLocalTasks.remove(taskId)) {
                        setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(ex));
                    }
                } finally {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    if (Objects.equals(lastLocalTaskId, taskId)) {
                        lastLocalTaskId = null;
                    }
                    lastLocalThread = null;
                }
            }
        });
    }


    protected void setOutputText(String content) {
        content = content == null ? "" : content;
        if(!Objects.equals("null", content) && !content.isEmpty()) {
            try {
                content = JsonUtil.formatObj(JSONObject.parse(content));
            } catch (Throwable ignore) {
            }
        }
        toolWindow.setOutputText(content);
        toolWindow.setOutputProfile(null);
    }

    protected void setOutputText(String content, List<Map<String, String>> outputProfile) {
        toolWindow.setOutputText(content);
        toolWindow.setOutputProfile(outputProfile);
    }


    protected ComboBox addActionComboBox(Icon icon, Icon disableIcon, String tooltips, JPanel topPanel, ActionListener actionListener) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        JButton testBtn = new JButton(icon);
        if (icon != disableIcon) {
            useInterceptor = SettingsStorageHelper.isDefaultUseInterceptor(getProject());
            if(useInterceptor){
                testBtn.setToolTipText("<html>\n" +
                        "<meta charset=\"UTF-8\">\n" +
                        "<strong>Tool interceptor is enable</strong><br>\n" + tooltips + "\n</html>");
            }else{
                testBtn.setIcon(disableIcon);
                testBtn.setToolTipText("<html>\n" +
                        "<meta charset=\"UTF-8\">\n" +
                        "<strong>Tool interceptor is disable</strong><br>\n" + tooltips + "\n</html>");
            }

            testBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (testBtn.getIcon() == icon) {
                        useInterceptor = false;
                        testBtn.setIcon(disableIcon);
                        testBtn.setToolTipText("<html>\n" +
                                "<meta charset=\"UTF-8\">\n" +
                                "<strong>Tool interceptor is disable</strong><br>\n" + tooltips + "\n</html>");
                        TestkitHelper.notify(getProject(), NotificationType.INFORMATION, "Tool interceptor is disable in " + getTool().getCode());
                    } else {
                        useInterceptor = true;
                        testBtn.setIcon(icon);
                        testBtn.setToolTipText("<html>\n" +
                                "<meta charset=\"UTF-8\">\n" +
                                "<strong>Tool interceptor is enable</strong><br>\n" + tooltips + "\n</html>");
                        TestkitHelper.notify(getProject(), NotificationType.INFORMATION, "Tool interceptor is enable in " + getTool().getCode());
                    }
                }
            });
        } else {
            useInterceptor = false;
            testBtn.setToolTipText("<html>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    "<strong>Unsupport Tool interceptor</strong><br>\n" + tooltips + "\n</html>");
            testBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    TestkitHelper.notify(getProject(), NotificationType.INFORMATION, getTool().getCode() + " don't support Tool-script");
                }
            });
        }

        testBtn.setPreferredSize(new Dimension(32, 32));
        topPanel.add(testBtn, gbc);

        JButton pointButton = new JButton(AllIcons.General.Locate);
        ComboBox actionComboBox = new ComboBox<>();
        actionComboBox.setPreferredSize(new Dimension(50, 32));

        pointButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object selectedItem = actionComboBox.getSelectedItem();
                if (selectedItem instanceof ToolHelper.MethodAction) {
                    PsiMethod method = ((ToolHelper.MethodAction) selectedItem).getMethod();
                    if (!method.isValid()) {
                        TestkitHelper.notify(getProject(), NotificationType.ERROR, "Current method is invalid");
                        return;
                    }
                    method.navigate(true);
                } else if (selectedItem instanceof ToolHelper.XmlTagAction xmlTagAction) {
                    XmlTag xmlTag = xmlTagAction.getXmlTag();
                    if (!xmlTag.isValid()) {
                        TestkitHelper.notify(getProject(), NotificationType.ERROR, "Current tag is invalid");
                        return;
                    }
                    PsiNavigationSupport.getInstance().createNavigatable(
                            getProject(),
                            xmlTag.getContainingFile().getVirtualFile(),
                            xmlTag.getTextOffset()
                    ).navigate(true);
                }else if(selectedItem instanceof ToolHelper.McpFunctionAction){
                    toolWindow.openMcpServerDialog();
                }
            }
        });
        pointButton.setToolTipText("Navigate to the selected");
        pointButton.setPreferredSize(new Dimension(32, 32));
        gbc.gridx = 1;
        topPanel.add(pointButton, gbc);


        actionComboBox.addActionListener(e -> {
            Object selectedItem = actionComboBox.getSelectedItem();
            if (selectedItem instanceof ToolHelper.MethodAction) {
                boolean currentValid = ((ToolHelper.MethodAction) selectedItem).getMethod().isValid();
                // 收集需要删除的元素
                List<Object> itemsToRemove = new ArrayList<>();
                for (int i = 0; i < actionComboBox.getItemCount(); i++) {
                    Object item = actionComboBox.getItemAt(i);
                    if (item instanceof ToolHelper.MethodAction) {
                        PsiMethod method = ((ToolHelper.MethodAction) item).getMethod();
                        if (!method.isValid()) {
                            itemsToRemove.add(item);
                            System.err.println("节点已失效，" + method);
                        }
                    }
                }

                // 直接删除收集到的元素
                itemsToRemove.forEach(actionComboBox::removeItem);

                // 如果还有有效项，选择第一个
                if (!currentValid && actionComboBox.getItemCount() > 0) {
                    actionComboBox.setSelectedIndex(0);
                }
            } else if (selectedItem instanceof ToolHelper.XmlTagAction) {

                boolean currentValid = ((ToolHelper.XmlTagAction) selectedItem).getXmlTag().isValid();
                // 收集需要删除的元素
                List<Object> itemsToRemove = new ArrayList<>();
                for (int i = 0; i < actionComboBox.getItemCount(); i++) {
                    Object item = actionComboBox.getItemAt(i);
                    if (item instanceof ToolHelper.XmlTagAction) {
                        XmlTag method = ((ToolHelper.XmlTagAction) item).getXmlTag();
                        if (!method.isValid()) {
                            itemsToRemove.add(item);
                            System.err.println("节点已失效，" + method);
                        }
                    }
                }

                // 直接删除收集到的元素
                itemsToRemove.forEach(actionComboBox::removeItem);

                // 如果还有有效项，选择第一个
                if (!currentValid && actionComboBox.getItemCount() > 0) {
                    actionComboBox.setSelectedIndex(0);
                }
            }

            String tooltip = "";
            if(selectedItem instanceof ToolHelper.MethodAction){
                tooltip = ToolHelper.buildMethodKey(((ToolHelper.MethodAction) selectedItem).getMethod());
            }else if(selectedItem instanceof ToolHelper.XmlTagAction){
                tooltip = ToolHelper.buildXmlTagKey(((ToolHelper.XmlTagAction) selectedItem).getXmlTag());
            }else if(selectedItem instanceof ToolHelper.McpFunctionAction){
                tooltip = ((ToolHelper.McpFunctionAction) selectedItem).buildDescription();
            }

            actionComboBox.setToolTipText(tooltip); // 动态更新 ToolTipText
            if (actionListener != null) {
                try {
                    ApplicationManager.getApplication().runWriteIntentReadAction(new ThrowableComputable<Object, Throwable>() {
                        @Override
                        public Object compute() throws Throwable {
                            actionListener.actionPerformed(e);
                            return null;
                        }
                    });
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 2;
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
        }else if (selectedItem instanceof ToolHelper.McpFunctionAction) {
            ((ToolHelper.McpFunctionAction) selectedItem).setArgs(null);
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

    public EditorTextField getJsonInputField() {
        return jsonInputField;
    }
}