package com.fling.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.FlingHelper;
import com.fling.LocalStorageHelper;
import com.fling.ReqStorageHelper;
import com.fling.tools.PluginToolEnum;
import com.fling.tools.ToolHelper;
import com.fling.tools.call_method.CallMethodIconProvider;
import com.fling.tools.call_method.CallMethodTool;
import com.fling.tools.flexible_test.FlexibleTestIconProvider;
import com.fling.util.HttpUtil;
import com.fling.util.JsonUtil;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.refactoring.changeClassSignature.New;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ReqStoreDialog {

    public static final Icon UNKNOWN_ICON = IconLoader.getIcon("/icons/unknown.svg", ReqStoreDialog.class);

    private JDialog dialog;

    private JComboBox<String> appBox;
    private AtomicBoolean expandToggle = new AtomicBoolean(true);

    private FlingToolWindow toolWindow;

    private DefaultMutableTreeNode root;

    private DefaultTreeModel treeModel;

    private JTree tree;

    private JLabel iconLabel;

    private JTextField titleTextField;

    private JPanel inputPanel;
    private JPanel actionPanel;

    private JComboBox<String> visibleAppComboBox;
    private JToggleButton useProxyButton;
    private JButton executeButton;
    private JButton controllerCommandButton;

    private JComboBox<ReqStorageHelper.SavedReq> reqsComboBox;

    private LanguageTextField jsonInputField;

    private LanguageTextField metaInputField;

    private JTextPane outputTextPane;

    private ReqStorageHelper.Item selectedItem;

    protected Set<String> cancelReqs = new HashSet<>(128);
    private String lastReqId;


    public ReqStoreDialog(FlingToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        init();
    }

    private void init() {
        dialog = new JDialog((Frame) null, FlingHelper.getPluginName() + " Store", true);

        // 定义主面板
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 1. 创建顶部参数面板
        JPanel topPanel = createTopPanel();
        contentPanel.add(topPanel, BorderLayout.NORTH);

        // 初始化树结构
        initializeTree();

        // 创建分隔面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setDividerSize(0); // 隐藏分隔条
        splitPane.setOneTouchExpandable(false);

        // 添加滚动面板
        JBScrollPane leftScrollPane = new JBScrollPane(tree);
        leftScrollPane.setBorder(JBUI.Borders.empty());

        JPanel rightPanel = createRightPanel();
        JBScrollPane rightScrollPane = new JBScrollPane(rightPanel);
        rightScrollPane.setBorder(JBUI.Borders.empty());

        splitPane.setLeftComponent(leftScrollPane);
        splitPane.setRightComponent(rightScrollPane);

        contentPanel.add(splitPane, BorderLayout.CENTER);

        // 设置对话框大小和位置
        dialog.setContentPane(contentPanel);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.8);
        int height = (int) (screenSize.height * 0.8);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(null);

    }



    private void initializeTree() {
        root = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);

        // 设置树的基本属性
        tree.setCellRenderer(new HistoryTreeRenderer());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(28);
        tree.setBackground(new JBColor(new Color(250, 250, 250), new Color(43, 43, 43)));
        tree.setBorder(JBUI.Borders.empty(5));

        // 添加选择监听器
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null) {
                updateRightPanel(node.getUserObject());
            }
        });
    }

    // 2. 新增一级节点展开/闭合的功能函数
    public void expandFirstLevelNodes(boolean expand) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            TreePath path = new TreePath(node.getPath());
            if (expand) {
                tree.expandPath(path);
            } else {
                tree.collapsePath(path);
            }
        }
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            // 保存当前选中的节点信息
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            String selectedGroup = null;
            String selectedItemNameAndType = null;

            if (selectedNode != null) {
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof ReqStorageHelper.GroupItems) {
                    selectedGroup = ((ReqStorageHelper.GroupItems) userObject).getGroup();
                } else if (userObject instanceof ReqStorageHelper.Item) {
                    ReqStorageHelper.Item item = (ReqStorageHelper.Item) userObject;
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
                    if (parentNode != null && parentNode.getUserObject() instanceof ReqStorageHelper.GroupItems) {
                        selectedGroup = ((ReqStorageHelper.GroupItems) parentNode.getUserObject()).getGroup();
                        selectedItemNameAndType = item.getName() + "|" + item.getType();
                    }
                }
            }


            String app = (String) appBox.getSelectedItem();
            List<ReqStorageHelper.GroupItems> groups = app == null ? null : ReqStorageHelper.getAppReqs(toolWindow.getProject(), app);
            updateRightPanel(null);

            root.removeAllChildren();
            if (CollectionUtils.isNotEmpty(groups)) {
                for (ReqStorageHelper.GroupItems group : groups) {
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
                    root.add(groupNode);

                    for (ReqStorageHelper.Item item : group.getItems()) {
                        groupNode.add(new DefaultMutableTreeNode(item));
                    }
                }
            }
            treeModel.reload();
            if (expandToggle.get()) {
                expandFirstLevelNodes(true);
            }
            // 然后尝试恢复之前的选择
            if (selectedGroup == null) {
                return;
            }

            // 遍历查找匹配的节点
            for (int i = 0; i < root.getChildCount(); i++) {
                DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) root.getChildAt(i);
                ReqStorageHelper.GroupItems group = (ReqStorageHelper.GroupItems) groupNode.getUserObject();
                if (!selectedGroup.equals(group.getGroup())) {
                    continue;
                }
                // 如果之前选中的是组节点，直接选中组
                TreePath path = new TreePath(groupNode.getPath());
                tree.setSelectionPath(path);
                tree.expandPath(path);
                if (selectedItemNameAndType == null) {
                    break;
                }
                // 如果之前选中的是子节点，查找对应的子节点
                for (int j = 0; j < groupNode.getChildCount(); j++) {
                    DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) groupNode.getChildAt(j);
                    ReqStorageHelper.Item item = (ReqStorageHelper.Item) childNode.getUserObject();
                    String currentItemNameAndType = item.getName() + "|" + item.getType();
                    if (!selectedItemNameAndType.equals(currentItemNameAndType)) {
                        continue;
                    }
                    path = new TreePath(childNode.getPath());
                    tree.setSelectionPath(path);
                    break;
                }
                break;
            }
        });
    }

    public void initApps(List<String> apps) {
        if (apps == null) {
            return;
        }
        apps.stream().forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                appBox.addItem(s);
            }
        });
    }


    public void visible(boolean visible) {
        if (appBox.getItemCount() > 0) {
            String selectedItem = (String) appBox.getSelectedItem();
            String selectedApp = toolWindow.getSelectedAppName();
            List<String> projectAppList = toolWindow.getProjectAppList();
            if (selectedApp != null && projectAppList.contains(selectedApp) && !Objects.equals(selectedItem, selectedApp)) {
                appBox.setSelectedItem(selectedApp);
            }
        }

        if (!EventQueue.isDispatchThread()) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                dialog.setVisible(visible);
            });
        } else {
            dialog.setVisible(visible);
        }
    }

//
//    public boolean isListEqual(List<String> list1, List<String> list2) {
//        if (CollectionUtils.isEmpty(list1) && CollectionUtils.isEmpty(list2)) {
//            return true;
//        }
//        if ((list1 == null ? 0 : list1.size()) != (list2 == null ? 0 : list2.size())) {
//            return false;
//        }
//
//        return new HashSet<>(list1).equals(new HashSet<>(list2));
//    }


    // 创建顶部参数面板
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // 创建下拉列表
        String[] options = {};
        appBox = new JComboBox<>(options);
        appBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });

        panel.add(appBox);

        JButton expandToggleButton = new JButton(AllIcons.Actions.Collapseall);
        expandToggleButton.setPreferredSize(new Dimension(32, 32));
        expandToggleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!expandToggle.get()) {
                    expandFirstLevelNodes(true);
                    expandToggle.set(true);
                    expandToggleButton.setIcon(AllIcons.Actions.Collapseall);
                } else {
                    expandFirstLevelNodes(false);
                    expandToggle.set(false);
                    expandToggleButton.setIcon(AllIcons.Actions.Expandall);
                }
            }
        });
        panel.add(expandToggleButton);

        JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setPreferredSize(new Dimension(32, 32));
        refreshButton.setToolTipText("Reload store");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
                FlingHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Refresh success");
            }
        });
        panel.add(refreshButton);

        // 创建文本标签
        JLabel textLabel = new JLabel("Hello " + FlingHelper.getPluginName() + ", store is developing");
        panel.add(textLabel);
        return panel;
    }


    // 创建右侧详情面板
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(5));

        // 创建一个容器面板，使用 GridBagLayout 进行布局
        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // 第一行：图标和标题
        JPanel firstRow = new JPanel(new BorderLayout(5, 0));
        iconLabel = new JLabel(CallMethodIconProvider.CALL_METHOD_ICON);
        reqsComboBox = new JComboBox<>();
        firstRow.add(iconLabel, BorderLayout.WEST);
        firstRow.add(reqsComboBox, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton saveButton = new JButton(AllIcons.Actions.MenuSaveall);
        saveButton.setPreferredSize(new Dimension(32, 32));
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem == null) {
                    FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please refresh this page");
                    return;
                }

                String title = selectedReq.getTitle();
                selectedReq.setTitle(titleTextField.getText());
                try {
                    selectedReq.setArgs(JSON.parseObject(jsonInputField.getText()));
                } catch (Throwable ex) {
                    FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Input parameter must be json object");
                    return;
                }

                JSONObject meta = new JSONObject();
                switch (selectedItem.getType()) {
                    case call_method -> {
                        try {
                            meta = JSON.parseObject(metaInputField.getText().trim());
                        } catch (Throwable ex) {
                            FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Meta must be json object");
                            return;
                        }
                    }
                    case flexible_test -> {
                        ReqStorageHelper.FlexibleTestMeta metaObj = selectedItem.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                        metaObj.setCode(metaInputField.getText().trim());
                        meta = JSON.parseObject(JSON.toJSONString(metaObj, SerializerFeature.WriteMapNullValue));
                    }
                    default -> {

                    }
                }
                ReqStorageHelper.saveAppReq(getToolWindow().getProject(), app, selectedItem.getGroup(), selectedItem.getType(), selectedItem.getName(), meta, title, selectedReq);
                FlingHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Save success");
                //触发重绘
                reqsComboBox.repaint();
            }
        });

        JButton delButton = new JButton(AllIcons.Actions.GC);
        delButton.setPreferredSize(new Dimension(32, 32));
        delButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem == null) {
                    return;
                }
                ReqStorageHelper.delAppReq(getToolWindow().getProject(), app, selectedItem.getGroup(), selectedItem.getType(), selectedItem.getName(), selectedReq.getTitle());
                FlingHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Delete success");
                toolWindow.refreshStore();
            }
        });

        buttonPanel.add(saveButton);
        buttonPanel.add(delButton);
        firstRow.add(buttonPanel, BorderLayout.EAST);

        titleTextField = new JTextField();

        reqsComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem == null) {
                    jsonInputField.setText("");
                    titleTextField.setText("");
                    return;
                }
                ReqStorageHelper.ItemType type = selectedItem.getType();
                if (type == null) {
                    jsonInputField.setText("");
                    titleTextField.setText("");
                    return;
                }
                jsonInputField.setText(selectedReq.getArgs() == null ? "{}" : JSON.toJSONString(selectedReq.getArgs(), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
                titleTextField.setText(selectedReq.getTitle());
            }
        });

        // 第二行：标题输入框和按钮
        JPanel secondRow = new JPanel(new BorderLayout(5, 0));
        secondRow.add(titleTextField, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(0, 0, 10, 0);

        contentPanel.add(firstRow, gbc);

        gbc.gridy = 1;
        contentPanel.add(secondRow, gbc);


        // 第二行：下拉框和执行按钮
        inputPanel = buildInputPanel(toolWindow);

        gbc.gridy = 2;
        gbc.weighty = 0.4;
//        gbc.insets = JBUI.insets(0, 0, 10, 0);
        gbc.fill = GridBagConstraints.BOTH;
        contentPanel.add(inputPanel, gbc);

        // 第四行：输出面板
        outputTextPane = new JTextPane();
        outputTextPane.setEditable(false);
        // 添加这些设置来控制 outputTextPane 的大小行为
        outputTextPane.setPreferredSize(new Dimension(200, 100));  // 让 ScrollPane 控制大小
        JBScrollPane outputScrollPane = new JBScrollPane(outputTextPane);
        outputScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        outputScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // 将内容面板添加到主面板
        gbc.gridy = 3;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(0);
        contentPanel.add(outputScrollPane, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInputPanel(FlingToolWindow window) {
        JPanel panelResults = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelResults.setLayout(gridbag);

        JPanel panelParamsHeadersBody = new JPanel(new GridLayout(1, 2));
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;
        panelResults.add(panelParamsHeadersBody, c);

        jsonInputField = new LanguageTextField(JsonLanguage.INSTANCE, null, "", false);
        JPanel paramsPanel = createLabelTextFieldPanel(window, "Params", jsonInputField);
        metaInputField = new LanguageTextField(JavaLanguage.INSTANCE, null, "", false);
        JPanel metaPanel = createLabelTextFieldPanel(window, "Meta", metaInputField);


        panelParamsHeadersBody.add(paramsPanel);
        panelParamsHeadersBody.add(metaPanel);

        actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 1;
        panelResults.add(actionPanel, c);

        //        初始化后面用的标签
        visibleAppComboBox = new JComboBox<>();

        new Thread(() -> {
            while (true) {
                try {
                    refreshVisibleApp();
                    Thread.sleep(3 * 1000); // 每隔一分钟调用一次
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }).start();




        useProxyButton = new JToggleButton(CallMethodTool.PROXY_ICON, true);
        useProxyButton.setPreferredSize(new Dimension(32, 32));
        useProxyButton.setToolTipText("Use proxy obj call method");
        useProxyButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyButton.isSelected()) {
                    useProxyButton.setIcon(CallMethodTool.PROXY_ICON);
                    useProxyButton.setToolTipText("Use proxy obj call method");
                } else {
                    useProxyButton.setIcon(CallMethodTool.PROXY_DISABLE_ICON);
                    useProxyButton.setToolTipText("Use original obj call method");
                }
            }
        });
        executeButton = new JButton(AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute this");
        executeButton.setPreferredSize(new Dimension(32, 32));
        executeButton.addActionListener(e -> executeAction());
        controllerCommandButton = new JButton(CallMethodTool.CONTROLLER_ICON);
        controllerCommandButton.setPreferredSize(new Dimension(32, 32));
        controllerCommandButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingHelper.alert(toolWindow.getProject(),Messages.getErrorIcon(),"developing");
            }
        });
        return panelResults;
    }

    private void refreshVisibleApp() {
        List<FlingToolWindow.VisibleApp> visibleApps = toolWindow.getVisibleApps();
        List<FlingToolWindow.VisibleApp> visibleAppList = visibleApps.stream()
                .filter(visibleApp -> Objects.equals(appBox.getSelectedItem(), visibleApp.getAppName()))
                .toList();

        // 获取当前列表的内容，用于比较是否有变化
        List<String> currentItems = new ArrayList<>();
        for (int i = 0; i < visibleAppComboBox.getItemCount(); i++) {
            currentItems.add(visibleAppComboBox.getItemAt(i));
        }

        // 构建新列表
        List<String> newItems = visibleAppList.stream()
                .map(app -> app.getAppName() + ":" + app.getPort())
                .toList();

        // 如果列表内容没变，直接返回
        if (new HashSet<>(currentItems).containsAll(newItems) && new HashSet<>(newItems).containsAll(currentItems)) {
            return;
        }

        // 保存当前选中的项
        String selectedItem = (String) visibleAppComboBox.getSelectedItem();

        // 更新列表内容
        visibleAppComboBox.removeAllItems();
        for (FlingToolWindow.VisibleApp visibleApp : visibleAppList) {
            String item = visibleApp.getAppName() + ":" + visibleApp.getPort();
            visibleAppComboBox.addItem(item);
        }

        // 如果之前选中的项还在新列表中，则重新选中它
        if (selectedItem != null && newItems.contains(selectedItem)) {
            visibleAppComboBox.setSelectedItem(selectedItem);
        }
        visibleAppComboBox.repaint();
    }


    private JPanel createLabelTextFieldPanel(FlingToolWindow window, String labelText, EditorTextField field) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(labelText);
        JButton copyButton = new JButton(AllIcons.Actions.Copy);
        copyButton.setToolTipText("Copy this");
        Dimension preferredSize = new Dimension(30, 30);
        copyButton.setPreferredSize(preferredSize);
        copyButton.setMaximumSize(preferredSize);
        copyButton.setMinimumSize(preferredSize);
        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingHelper.copyToClipboard(window.getProject(), field.getText(), labelText + " was Copied");
            }
        });


        // 将 JTextComponent 放入 JScrollPane
        JScrollPane scrollPane = new JBScrollPane(field);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); // 允许横向滚动

        // 顶部面板
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        northPanel.add(label);
        northPanel.add(copyButton);

        // 添加到主面板
        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // 更新右侧面板的方法
    public void updateRightPanel(Object nowItem) {
        if (nowItem == selectedItem) {
            return;
        }
        if (!(nowItem instanceof ReqStorageHelper.Item)) {
            selectedItem = null;
            iconLabel.setIcon(null);
            reqsComboBox.removeAllItems();
            jsonInputField.setText("");
            metaInputField.setText("");
            setOutputText("", null);
            actionPanel.removeAll();
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ReqStorageHelper.Item item = (ReqStorageHelper.Item) nowItem;
                selectedItem = item;
                // 更新图标
                Icon icon = getItemIcon(item.getType());
                iconLabel.setIcon(icon);

                jsonInputField.setText("");

                // 根据条件添加script输入框
                if (item.getType() == ReqStorageHelper.ItemType.flexible_test) {
                    ReqStorageHelper.FlexibleTestMeta meta = item.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                    metaInputField.setText(meta.getCode() == null ? "" : meta.getCode());
                    metaInputField.setEnabled(false);
                } else if (item.getType() == ReqStorageHelper.ItemType.call_method) {
                    metaInputField.setText(item.getMeta() == null ? "" : JSON.toJSONString(item.getMeta(), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
                    metaInputField.setEnabled(false);
                } else {
                    FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Un support type, please contact developer");
                    return;
                }

                JLabel instanceLabel = new JLabel();
                instanceLabel.setText("RuntimeApp:");

                actionPanel.removeAll();
                // 创建底部面板使用FlowLayout
                actionPanel.add(instanceLabel);
                actionPanel.add(visibleAppComboBox);
                actionPanel.add(useProxyButton);
                actionPanel.add(executeButton);
                if (item.fetchSubType() == ReqStorageHelper.SubItemType.controller) {
                    actionPanel.add(controllerCommandButton);
                }

                // 更新标题
                List<ReqStorageHelper.SavedReq> reqs = item.getReqs();
                reqsComboBox.removeAllItems();
                if (reqs != null) {
                    for (ReqStorageHelper.SavedReq req : reqs) {
                        reqsComboBox.addItem(req);
                    }
                }
            }
        });
    }


    // 执行按钮点击事件
    private void executeAction() {
        ReqStorageHelper.Item item = selectedItem;
        String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
        String jsonInput = jsonInputField.getText();
        if (item == null || selectedInstance == null) {
            FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select valid item and runtime app");
            return;
        }
        FlingToolWindow.VisibleApp visibleApp = parseApp(selectedInstance);
        if (visibleApp == null) {
            FlingHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select runtime app");
            return;
        }

        triggerHttpTask(executeButton, AllIcons.Actions.Execute, visibleApp.getSidePort(), new Supplier<JSONObject>() {
            @Override
            public JSONObject get() {
                if (item.getType() == ReqStorageHelper.ItemType.call_method) {
                    JSONObject args = new JSONObject();
                    if (StringUtils.isNotBlank(jsonInput)) {
                        try {
                            args = JSON.parseObject(jsonInput.trim());
                        } catch (Throwable e) {
                            throw new RuntimeException("Params must be json object");
                        }
                    }
                    ReqStorageHelper.CallMethodMeta meta = null;
                    try {
                        meta = JSON.parseObject(metaInputField.getText(), ReqStorageHelper.CallMethodMeta.class);
                    } catch (Throwable e) {
                        throw new RuntimeException("Meta must be json object");
                    }
                    return buildCallMethodParams(meta,visibleApp, args);
                }else if(item.getType() == ReqStorageHelper.ItemType.flexible_test){
                    JSONObject args = new JSONObject();
                    if (StringUtils.isNotBlank(jsonInput)) {
                        try {
                            args = JSON.parseObject(jsonInput.trim());
                        } catch (Throwable e) {
                            throw new RuntimeException("Params must be json object");
                        }
                    }
                    ReqStorageHelper.FlexibleTestMeta meta = item.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                    meta.setCode(metaInputField.getText().trim());
                    return buildFlexibleParams(meta,visibleApp, args);
                }

                return null;
            }
        });
    }


    private void triggerHttpTask(JButton triggerBtn, Icon executeIcon, int sidePort, Supplier<JSONObject> submit) {
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
            response = HttpUtil.sendPost("http://localhost:" + sidePort + "/", submit.get(), JSONObject.class);
        } catch (Throwable e) {
            setOutputText("submit req error \n" + ToolHelper.getStackTrace(e), null);
            return;
        }
        if (response == null) {
            setOutputText("submit req error \n req is null", null);
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

        ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing req, please wait ...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
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
                            if (result == null) {
                                setOutputText("req is error\n result is null", null);
                            } else {
                                List<Map<String, String>> profile = result.getObject("profile", new TypeReference<List<Map<String, String>>>() {
                                });
                                if (!result.getBooleanValue("success")) {
                                    setOutputText("req is error\n" + result.getString("message"), null);
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
                        setOutputText("wait ret is error\n" + ToolHelper.getStackTrace(ex), null);
                        triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    });
                }
            }
        });
    }

    private FlingToolWindow.VisibleApp parseApp(String selectedItem) {
        String[] split = selectedItem.split(":");
        if (split.length == 2) {
            FlingToolWindow.VisibleApp visibleApp = new FlingToolWindow.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[1]) + 10000);
            return visibleApp;

        } else if (split.length == 3) {
            FlingToolWindow.VisibleApp visibleApp = new FlingToolWindow.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[2]));
            return visibleApp;
        }
        return null;
    }

    private JSONObject buildCallMethodParams(ReqStorageHelper.CallMethodMeta meta, FlingToolWindow.VisibleApp visibleApp, JSONObject args) {
        JSONObject params = new JSONObject();
        params.put("typeClass", meta.getTypeClass());
        params.put("beanName", meta.getBeanName());
        params.put("methodName", meta.getMethodName());
        params.put("argTypes", meta.getArgTypes());
        params.put("args", ToolHelper.adapterParams(meta.getArgNames(), args).toJSONString());
        params.put("original", !useProxyButton.isSelected());
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.CALL_METHOD.getCode());
        req.put("params", params);

        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(toolWindow.getProject());
        req.put("monitor", monitorConfig.isEnable());
        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
        if (meta.isUseScript()) {
            req.put("script", LocalStorageHelper.getAppScript(toolWindow.getProject(), visibleApp.getAppName()));
        }
        return req;
    }

    private JSONObject buildFlexibleParams(ReqStorageHelper.FlexibleTestMeta meta, FlingToolWindow.VisibleApp visibleApp, JSONObject args) {
        JSONObject params = new JSONObject();
        params.put("code", meta.getCode());
        params.put("methodName", meta.getMethodName());
        params.put("argTypes", meta.getArgTypes());
        params.put("args", ToolHelper.adapterParams(meta.getArgNames(), args).toJSONString());
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.FLEXIBLE_TEST.getCode());
        req.put("params", params);

        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(toolWindow.getProject());
        req.put("monitor", monitorConfig.isEnable());
        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
        if (meta.isUseScript()) {
            req.put("script", LocalStorageHelper.getAppScript(toolWindow.getProject(), visibleApp.getAppName()));
        }
        return req;
    }

    private void setOutputText(String text, List<Map<String, String>> profile) {
        outputTextPane.setText(text);
    }


    private Icon getItemIcon(ReqStorageHelper.ItemType type) {
        if (type == null) {
            return UNKNOWN_ICON;
        }
        // 根据不同的类型返回不同的图标
        switch (type) {
            case call_method:
                return CallMethodIconProvider.CALL_METHOD_ICON;
            case flexible_test:
                return FlexibleTestIconProvider.FLEXIBLE_TEST_ICON;
            default:
                return UNKNOWN_ICON;
        }
    }


    public JDialog getDialog() {
        return dialog;
    }

    public void setDialog(JDialog dialog) {
        this.dialog = dialog;
    }

    public FlingToolWindow getToolWindow() {
        return toolWindow;
    }

    public void setToolWindow(FlingToolWindow toolWindow) {
        this.toolWindow = toolWindow;
    }


    public class HistoryTreeRenderer extends ColoredTreeCellRenderer {
        private static final JBColor GROUP_COLOR = new JBColor(new Color(110, 110, 110), new Color(180, 180, 180));
        private static final JBColor ITEM_COLOR = new JBColor(new Color(0, 0, 0), new Color(220, 220, 220));

        @Override
        public void customizeCellRenderer(JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof ReqStorageHelper.GroupItems) {
                // 渲染分组节点
                ReqStorageHelper.GroupItems group = (ReqStorageHelper.GroupItems) userObject;
                append(group.getGroup(),
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, GROUP_COLOR));

                // 添加分组图标
                setIcon(AllIcons.Nodes.Folder);
            } else if (userObject instanceof ReqStorageHelper.Item) {
                // 渲染项目节点
                ReqStorageHelper.Item item = (ReqStorageHelper.Item) userObject;
                // 根据类型设置不同的图标
                setIcon(getItemIcon(item.getType()));
                // 设置主标题
                append(item.getName(),
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ITEM_COLOR));

                // 可以添加额外信息（如时间戳等）
//                append("  " + item.getType(),
//                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER | SimpleTextAttributes.STYLE_ITALIC,
//                                GROUP_COLOR));
            }
        }


    }


}
