package com.fling.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fling.FlingHelper;
import com.fling.ReqStorageHelper;
import com.fling.tools.call_method.CallMethodIconProvider;
import com.fling.tools.call_method.CallMethodTool;
import com.fling.tools.flexible_test.FlexibleTestIconProvider;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ReqStoreDialog {

    public static final Icon UNKNOWN_ICON = IconLoader.getIcon("/icons/unknown.svg", ReqStoreDialog.class);

    private JDialog dialog;

    private JComboBox<String> appBox;

    private FlingToolWindow toolWindow;

    private DefaultMutableTreeNode root;

    private DefaultTreeModel treeModel;

    private JTree tree;

    private JLabel iconLabel;

    private JTextField titleTextField;

    private JLabel instanceLabel;
    private JComboBox<String> instanceComboBox;

    private JComboBox<ReqStorageHelper.SavedReq> reqsComboBox;

    private LanguageTextField jsonInputField;

    private JTextPane outputTextPane;

    private ReqStorageHelper.Item selectedItem;


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
        dialog.setSize(800, 600);
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
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof ReqStorageHelper.Item) {
                updateRightPanel((ReqStorageHelper.Item) node.getUserObject());
            }
        });
    }

    public void refresh(List<ReqStorageHelper.GroupItems> groups) {
        root.removeAllChildren();
        if (groups != null) {
            for (ReqStorageHelper.GroupItems group : groups) {
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
                root.add(groupNode);

                for (ReqStorageHelper.Item item : group.getItems()) {
                    groupNode.add(new DefaultMutableTreeNode(item));
                }
            }
        }
        treeModel.reload();
    }

    public void initApps(List<String> apps) {
        if (apps != null) {
            apps.stream().forEach(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    appBox.addItem(s);
                }
            });
        }
    }


    public void visible(boolean visible) {
        String selectedItem = (String) appBox.getSelectedItem();
        String selectedApp = toolWindow.getSelectedAppName();
        selectedApp = selectedApp == null ? selectedItem : selectedApp;
        List<String> projectAppList = toolWindow.getProjectAppList();
        if (selectedApp != null && projectAppList.contains(selectedApp)) {
            appBox.setSelectedItem(selectedApp);
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
                String app = (String) appBox.getSelectedItem();
                if (app == null) {
                    refresh(null);
                    return;
                }
                List<ReqStorageHelper.GroupItems> appReqs = ReqStorageHelper.getAppReqs(toolWindow.getProject(), app);
                refresh(appReqs);
            }
        });

        panel.add(appBox);

        // 创建文本标签
        JLabel textLabel = new JLabel("Hello " + FlingHelper.getPluginName()+", store is developing");
        panel.add(textLabel);
        return panel;
    }


    // 创建右侧详情面板
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        // 创建一个容器面板，使用 GridBagLayout 进行布局
        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // 第一行：图标和标题
        JPanel reqPanel = new JPanel(new BorderLayout(10, 0));
        iconLabel = new JLabel(CallMethodIconProvider.CALL_METHOD_ICON); // 默认图标，后续可更新

        reqsComboBox = new JComboBox<>();
        reqsComboBox.setPreferredSize(new Dimension(150, 30));

        titleTextField = new JTextField();

        reqsComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem==null) {
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
                switch (type) {
                    case call_method -> {
                        ReqStorageHelper.CallMethodMeta callMethodMeta = selectedReq.metaObj(ReqStorageHelper.CallMethodMeta.class);
                        jsonInputField.setText(callMethodMeta == null || callMethodMeta.getArgs() == null ? "[]" : callMethodMeta.getArgs().toJSONString());
                        titleTextField.setText(selectedReq.getTitle());
                    }
                    default -> {
                        jsonInputField.setText("");
                        titleTextField.setText("");
                    }
                }
            }
        });

        JButton saveButton = new JButton(AllIcons.Actions.MenuSaveall);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq==null || selectedItem==null) {
                    return;
                }

                selectedReq.setTitle(titleTextField.getText());
                switch (selectedItem.getType()) {
                    case call_method -> {
                        ReqStorageHelper.CallMethodMeta callMethodMeta = selectedReq.metaObj(ReqStorageHelper.CallMethodMeta.class);
                        callMethodMeta.setArgs(JSON.parseArray(jsonInputField.getText()));
                        selectedReq.setMeta(JSON.parseObject(JSONObject.toJSONString(callMethodMeta)));
                    }
                    default -> {

                    }
                }
                ReqStorageHelper.saveAppReq(getToolWindow().getProject(), app, selectedItem.getGroup(),selectedItem.getType(),selectedItem.getName(), selectedReq);
            }
        });

        JButton delButton = new JButton(AllIcons.Actions.ClearCash);
        delButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq==null || selectedItem==null) {
                    return;
                }
                ReqStorageHelper.delAppReq(getToolWindow().getProject(), app, selectedItem.getGroup(), selectedItem.getType(), selectedItem.getName(), selectedReq.getTitle());
            }
        });

        reqPanel.add(iconLabel, BorderLayout.WEST);
        reqPanel.add(reqsComboBox, BorderLayout.CENTER);
        reqPanel.add(saveButton, BorderLayout.EAST);
        reqPanel.add(delButton, BorderLayout.EAST);


        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(0, 0, 10, 0);

        contentPanel.add(reqPanel, gbc);

        gbc.gridy = 1;
        contentPanel.add(titleTextField, gbc);


        // 第二行：下拉框和执行按钮
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        instanceLabel = new JLabel("RuntimeApp:");
        instanceComboBox = new JComboBox<>();
        instanceComboBox.setPreferredSize(new Dimension(150, 30));

        JButton executeButton = new JButton(AllIcons.Actions.Execute);
        executeButton.addActionListener(e -> executeAction());

        controlPanel.add(instanceLabel);
        controlPanel.add(instanceComboBox);
        controlPanel.add(executeButton);

        gbc.gridy = 2;
        gbc.insets = JBUI.insets(0, 0, 10, 0);
        contentPanel.add(controlPanel, gbc);

        // 创建输入输出面板
        JPanel ioPanel = new JPanel(new GridBagLayout());
        GridBagConstraints ioGbc = new GridBagConstraints();

        // 第三行：JSON 输入框
        jsonInputField = new LanguageTextField(JsonLanguage.INSTANCE, null, "", true);
//        jsonInputField.setPreferredSize(new Dimension(500, 200));

        ioGbc.gridx = 0;
        ioGbc.gridy = 0;
        ioGbc.weightx = 1.0;
        ioGbc.weighty = 0.4;
        ioGbc.fill = GridBagConstraints.BOTH;
        ioGbc.insets = JBUI.insets(0, 0, 10, 0);
        ioPanel.add(jsonInputField, ioGbc);

        // 第四行：输出面板
        outputTextPane = new JTextPane();
        outputTextPane.setEditable(false);
        JBScrollPane outputScrollPane = new JBScrollPane(outputTextPane);

        ioGbc.gridy = 1;
        ioGbc.weighty = 0.6;
        ioGbc.insets = JBUI.insets(0);
        ioPanel.add(outputScrollPane, ioGbc);

        // 将内容面板添加到主面板
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(0);
        contentPanel.add(ioPanel, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    // 更新右侧面板的方法
    public void updateRightPanel(ReqStorageHelper.Item item) {
        if (item == null) {
            return;
        }
        selectedItem = item;
        // 更新图标
        Icon icon = getItemIcon(item.getType());
        iconLabel.setIcon(icon);

        switch (item.getType()){
            case call_method:
            case flexible_test:
                instanceLabel.setText("RuntimeApp:");
                instanceComboBox.removeAllItems();
                List<FlingToolWindow.VisibleApp> visibleApps = toolWindow.getVisibleApps();
                List<FlingToolWindow.VisibleApp> visibleAppList = visibleApps.stream()
                        .filter(visibleApp -> Objects.equals(appBox.getSelectedItem(), visibleApp.getAppName()))
                        .toList();
                for (FlingToolWindow.VisibleApp visibleApp : visibleAppList) {
                    instanceComboBox.addItem(visibleApp.getAppName()+":"+visibleApp.getPort());
                }
            break;
            default:
        }

        // 更新标题
        List<ReqStorageHelper.SavedReq> reqs = item.getReqs();
        reqsComboBox.removeAllItems();
        if (reqs!=null) {
            for (ReqStorageHelper.SavedReq req : reqs) {
                reqsComboBox.addItem(req);
            }
        }

    }


    // 执行按钮点击事件
    private void executeAction() {
        String selectedInstance = (String) instanceComboBox.getSelectedItem();
        String selectedReqs = (String) reqsComboBox.getSelectedItem();
        String jsonInput = jsonInputField.getText();

        // TODO: 处理执行逻辑
        outputTextPane.setText("Executing with:\nInstance: " + selectedInstance +
                "\nReqs: " + selectedReqs +
                "\nInput: " + jsonInput);
    }


    private Icon getItemIcon(ReqStorageHelper.ItemType type) {
        if (type == null) {
            return UNKNOWN_ICON;
        }
        // 根据不同的类型返回不同的图标
        switch (type) {
            case call_method:
                return CallMethodIconProvider.CALL_METHOD_ICON;
            case controller_command:
                return CallMethodTool.CONTROLLER_ICON;
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
