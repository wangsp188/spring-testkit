package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.testkit.TestkitHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.ReqStorageHelper;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.ToolHelper;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.testkit.tools.function_call.FunctionCallTool;
import com.testkit.tools.flexible_test.FlexibleTestIconProvider;
import com.testkit.util.Container;
import com.testkit.util.HttpUtil;
import com.testkit.util.JsonUtil;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReqStoreDialog {

    public static final Icon UNKNOWN_ICON = IconLoader.getIcon("/icons/unknown.svg", ReqStoreDialog.class);


    private JDialog dialog;

    private JComboBox<String> appBox;
    private AtomicBoolean expandToggle = new AtomicBoolean(true);

    private TestkitToolWindow toolWindow;

    private DefaultMutableTreeNode root;

    private DefaultTreeModel treeModel;

    private JTree tree;

    private JLabel iconLabel;

    private JTextField titleTextField;

    private JPanel inputPanel;
    private JPanel actionPanel;

    private JComboBox<String> visibleAppComboBox;
    private JButton copyRetButton;
    private JToggleButton useProxyButton;
    private JButton executeButton;
    private JButton controllerCommandButton;

    private JButton feignCommandButton;

    private JButton getKeyButton;
    private JButton getValButton;
    private JButton delValButton;

    private JComboBox<ReqStorageHelper.SavedReq> reqsComboBox;

    private LanguageTextField jsonInputField;

    private JTextPane metaTextPane;

    private JTextPane outputTextPane;

    private ReqStorageHelper.Item selectedItem;

    protected Set<String> cancelReqs = new HashSet<>(128);
    private String lastReqId;


    public ReqStoreDialog(TestkitToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        init();
    }

    private void init() {
        dialog = new JDialog((Frame) null, TestkitHelper.getPluginName() + " Store", true);

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
        resizeDialog();

    }

    private void resizeDialog() {
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
            updateRightPanel();
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

    public void refreshTree() {
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
            updateRightPanel();

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
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(toolWindow.getProject().getName());
            String selectedApp = visibleApp == null ? null : visibleApp.getAppName();
            List<String> projectAppList = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().map(new Function<RuntimeHelper.AppMeta, String>() {
                @Override
                public String apply(RuntimeHelper.AppMeta appMeta) {
                    return appMeta.getApp();
                }
            }).collect(Collectors.toCollection(ArrayList::new));
            if (selectedApp != null && projectAppList.contains(selectedApp) && !Objects.equals(selectedItem, selectedApp)) {
                appBox.setSelectedItem(selectedApp);
            }
        }

        try (var token = com.intellij.concurrency.ThreadContext.resetThreadContext()) {
            resizeDialog();
            dialog.setVisible(true);
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
                refreshTree();
            }
        });
        JLabel appLabel = new JLabel("App:");
        appLabel.setLabelFor(appBox);
        panel.add(appLabel);
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
                refreshTree();
                TestkitHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Reload success");
            }
        });
        panel.add(refreshButton);

        JButton importButton = new JButton(SettingsDialog.IMPORT_ICON);
        importButton.setPreferredSize(new Dimension(32, 32));
        importButton.setToolTipText("Import/Overwrite item to the selected app");
        importButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                if (app == null) {
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please select a app");
                    return;
                }
                //常见一个弹出层
//              标题是 import reqs
//              一个大面积的json输入框
//                最下面是个import按钮，点击按钮后触发一个动作

                // 创建弹出对话框
                JDialog dialog = new JDialog();
                dialog.setTitle("Import/Update item to the selected app");
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                // 创建说明文本标签
                JLabel instructionLabel = new JLabel("<html>Paste the data you want to import here<br>Usually json content exported from other device or project</html>");
// 启用自动换行
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 创建JSON输入框
                JTextArea jsonInput = new JTextArea();
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);

                // 创建导入按钮
                JButton importConfirmButton = new JButton("Import");
                importConfirmButton.addActionListener(e1 -> {
                    ReqStorageHelper.GroupItems groupItems = null;
                    try {
                        groupItems = JSON.parseObject(jsonInput.getText().trim(), ReqStorageHelper.GroupItems.class);
                    } catch (Exception ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Import item must be group items, " + ex.getMessage());
                        return;
                    }
                    try {
                        ReqStorageHelper.saveAppGroupItems(toolWindow.getProject(), app, groupItems);
                        refreshTree();
                        dialog.dispose();
                        TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Import successfully");
                    } catch (Exception ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Import error," + ex.getMessage());
                    }
                });

                // 布局
                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 将组件添加到面板
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(importConfirmButton, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
        });
        panel.add(importButton);

        JButton exportButton = new JButton(SettingsDialog.EXPORT_ICON);
        exportButton.setPreferredSize(new Dimension(32, 32));
        exportButton.setToolTipText("Export the selected group or item");
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                if (app == null) {
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please select a app");
                    return;
                }

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (node == null) {
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please select a group or item");
                    return;
                }
                ReqStorageHelper.GroupItems exportObj = exportReqs(app, node);

                // 创建弹出对话框
                JDialog dialog = new JDialog();
                dialog.setTitle("Export the selected group or item");
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                String title = "";
                if (node.getUserObject() instanceof ReqStorageHelper.GroupItems) {
                    title = ((ReqStorageHelper.GroupItems) node.getUserObject()).getGroup();
                } else if (node.getUserObject() instanceof ReqStorageHelper.Item) {
                    title = ((ReqStorageHelper.Item) node.getUserObject()).getGroup() + "." + ((ReqStorageHelper.Item) node.getUserObject()).getName();
                }
                // 创建说明文本标签
                JLabel instructionLabel = new JLabel("<html>Current export scope: " + title + "<br>The exported content is already below<br/>You can copy it and import it on another device or project</html>");
// 启用自动换行
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 创建JSON输入框
                JTextArea jsonInput = new JTextArea();
                jsonInput.setEditable(false);
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);
                jsonInput.setText(JsonUtil.formatObj(exportObj));
                // 创建导入按钮
                JButton copyConfirmButton = new JButton("Copy");
                copyConfirmButton.addActionListener(e1 -> {
                    TestkitHelper.copyToClipboard(toolWindow.getProject(), jsonInput.getText(), (exportObj == null || exportObj.getItems() == null ? 0 : exportObj.getItems().size()) + " item have been copied");
                    dialog.dispose();
                });

                // 布局
                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                // 将组件添加到面板
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(copyConfirmButton, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
        });
        panel.add(exportButton);

        // 创建文本标签
//        JLabel textLabel = new JLabel("Good luck");
//        panel.add(textLabel);
        return panel;
    }

    private ReqStorageHelper.GroupItems exportReqs(String app, DefaultMutableTreeNode node) {
        ReqStorageHelper.GroupItems exportObj = null;
        List<ReqStorageHelper.GroupItems> groups = ReqStorageHelper.getAppReqs(toolWindow.getProject(), app);
        Object userObject = node.getUserObject();
        if (userObject instanceof ReqStorageHelper.GroupItems) {
            Optional<ReqStorageHelper.GroupItems> groupItems = Optional.ofNullable(groups)
                    .orElse(new ArrayList<>())
                    .stream().filter(new Predicate<ReqStorageHelper.GroupItems>() {
                        @Override
                        public boolean test(ReqStorageHelper.GroupItems groupItems) {
                            return groupItems != null && Objects.equals(((ReqStorageHelper.GroupItems) userObject).getGroup(), groupItems.getGroup());
                        }
                    }).findFirst();
            exportObj = (ReqStorageHelper.GroupItems) groupItems.orElseGet(new Supplier<ReqStorageHelper.GroupItems>() {
                @Override
                public ReqStorageHelper.GroupItems get() {
                    ReqStorageHelper.GroupItems items = new ReqStorageHelper.GroupItems();
                    items.setGroup(((ReqStorageHelper.GroupItems) userObject).getGroup());
                    return items;
                }
            });
        } else if (userObject instanceof ReqStorageHelper.Item) {
            Optional<ReqStorageHelper.GroupItems> groupItems = Optional.ofNullable(groups)
                    .orElse(new ArrayList<>())
                    .stream().filter(new Predicate<ReqStorageHelper.GroupItems>() {
                        @Override
                        public boolean test(ReqStorageHelper.GroupItems groupItems) {
                            return groupItems != null && Objects.equals(((ReqStorageHelper.Item) userObject).getGroup(), groupItems.getGroup());
                        }
                    }).findFirst();
            exportObj = new ReqStorageHelper.GroupItems();
            exportObj.setGroup(((ReqStorageHelper.Item) userObject).getGroup());
            if (groupItems.isPresent()) {
                Optional<ReqStorageHelper.Item> first = Optional.ofNullable(groupItems.get().getItems())
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(new Predicate<ReqStorageHelper.Item>() {
                            @Override
                            public boolean test(ReqStorageHelper.Item item) {
                                return item != null && Objects.equals(item.getName(), ((ReqStorageHelper.Item) userObject).getName()) && Objects.equals(item.getType(), ((ReqStorageHelper.Item) userObject).getType());
                            }
                        }).findFirst();
                if (first.isPresent()) {
                    exportObj.getItems().add(first.get());
                }
            }
        }
        return exportObj;
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
        iconLabel = new JLabel(FunctionCallIconProvider.FUNCTION_CALL_ICON);
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
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select a item");
                    return;
                }

                String title = selectedReq.getTitle();
                selectedReq.setTitle(titleTextField.getText());
                try {
                    selectedReq.setArgs(JSON.parseObject(jsonInputField.getText()));
                } catch (Throwable ex) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Input parameter must be json object");
                    return;
                }

                JSONObject meta = new JSONObject();
                switch (selectedItem.getType()) {
                    case function_call -> {
                        try {
                            meta = JSON.parseObject(metaTextPane.getText().trim());
                        } catch (Throwable ex) {
                            TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Meta must be json object");
                            return;
                        }
                    }
                    case flexible_test -> {
                        ReqStorageHelper.FlexibleTestMeta metaObj = selectedItem.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                        metaObj.setCode(metaTextPane.getText().trim());
                        meta = JSON.parseObject(JSON.toJSONString(metaObj, SerializerFeature.WriteMapNullValue));
                    }
                    default -> {

                    }
                }
                ReqStorageHelper.saveAppReq(getToolWindow().getProject(), app, selectedItem.getGroup(), selectedItem.getType(), selectedItem.getName(), meta, title, selectedReq);
                TestkitHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Save success");
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
                TestkitHelper.notify(getToolWindow().getProject(), NotificationType.INFORMATION, "Delete success");
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
//        gbc.insets = JBUI.insets(0, 0, 5, 0);

        contentPanel.add(firstRow, gbc);

        gbc.gridy = 1;
        contentPanel.add(secondRow, gbc);


        // 第二行：下拉框和执行按钮
        inputPanel = buildInputPanel(toolWindow);

        gbc.gridy = 2;
        gbc.weighty = 0;
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
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(1);
        contentPanel.add(outputScrollPane, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInputPanel(TestkitToolWindow window) {
        JPanel panelResults = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelResults.setLayout(gridbag);

        JPanel panelParamsHeadersBody = new JPanel(new GridLayout(1, 2));
        panelParamsHeadersBody.setPreferredSize(new Dimension(0, 300));


        jsonInputField = new LanguageTextField(JsonLanguage.INSTANCE, null, "", false);
        JPanel paramsPanel = createLabelTextFieldPanel(window, "Params", jsonInputField);
        metaTextPane = new JTextPane();
        metaTextPane.setEditable(false);
        JPanel metaPanel = createLabelTextFieldPanel(window, "Meta", metaTextPane);


        panelParamsHeadersBody.add(paramsPanel);
        panelParamsHeadersBody.add(metaPanel);

        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;
        panelResults.add(panelParamsHeadersBody, c);

        actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,2,2));
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


        copyRetButton = new JButton(AllIcons.Actions.Copy);
        copyRetButton.setPreferredSize(new Dimension(32, 32));
        copyRetButton.setToolTipText("Copy result text");
        copyRetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TestkitHelper.copyToClipboard(toolWindow.getProject(), outputTextPane.getText(), null);
            }
        });


        useProxyButton = new JToggleButton(FunctionCallTool.PROXY_ICON, true);
        useProxyButton.setPreferredSize(new Dimension(32, 32));
        useProxyButton.setToolTipText("Use proxy obj call function");
        useProxyButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyButton.isSelected()) {
                    useProxyButton.setIcon(FunctionCallTool.PROXY_ICON);
                    useProxyButton.setToolTipText("Use proxy obj call function");
                } else {
                    useProxyButton.setIcon(FunctionCallTool.PROXY_DISABLE_ICON);
                    useProxyButton.setToolTipText("Use original obj call function");
                }
            }
        });
        executeButton = new JButton(AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute this");
        executeButton.setPreferredSize(new Dimension(32, 32));
        executeButton.addActionListener(e -> executeAction());

        buildControllerButton();
        buildFeignButton();
        buildSpringCacheButton();
        return panelResults;
    }


    private void buildSpringCacheButton() {
        getKeyButton = new JButton(FunctionCallTool.KIcon);
        getKeyButton.setToolTipText("[Spring-cache] Build keys");
        getValButton = new JButton(AllIcons.Actions.Find);
        getValButton.setToolTipText("[Spring-cache] Build keys and get values for every key");
        delValButton = new JButton(AllIcons.Actions.GC);
        delValButton.setToolTipText("[Spring-cache] Build keys and delete these");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(30, 30);
        getKeyButton.setPreferredSize(buttonSize);
        getValButton.setPreferredSize(buttonSize);
        delValButton.setPreferredSize(buttonSize);

        getKeyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.Item item = selectedItem;
                String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
                String jsonInput = jsonInputField.getText();
                if (item == null || selectedInstance == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.CallMethodMeta meta = null;
                try {
                    meta = JSON.parseObject(metaTextPane.getText(), ReqStorageHelper.CallMethodMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.CallMethodMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(getKeyButton, FunctionCallTool.KIcon, visibleApp.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return buildCacheParams("build_cache_key", finalMeta,visibleApp, finalArgs);
                    }
                });
            }
        });

        getValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.Item item = selectedItem;
                String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
                String jsonInput = jsonInputField.getText();
                if (item == null || selectedInstance == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.CallMethodMeta meta = null;
                try {
                    meta = JSON.parseObject(metaTextPane.getText(), ReqStorageHelper.CallMethodMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.CallMethodMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(getValButton, AllIcons.Actions.Find, visibleApp.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return buildCacheParams("get_cache",finalMeta,visibleApp, finalArgs);
                    }
                });
            }
        });

        delValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.Item item = selectedItem;
                String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
                String jsonInput = jsonInputField.getText();
                if (item == null || selectedInstance == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.CallMethodMeta meta = null;
                try {
                    meta = JSON.parseObject(metaTextPane.getText(), ReqStorageHelper.CallMethodMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.CallMethodMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(delValButton, AllIcons.Actions.GC, visibleApp.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return buildCacheParams("delete_cache",finalMeta,visibleApp, finalArgs);
                    }
                });
            }
        });
    }

    private void buildControllerButton() {
        controllerCommandButton = new JButton(FunctionCallTool.CONTROLLER_ICON);
        controllerCommandButton.setToolTipText("[Controller] Generate controller command");
        controllerCommandButton.setPreferredSize(new Dimension(32, 32));
        controllerCommandButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedItem == null || appBox.getSelectedItem() == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select a app and item");
                    return;
                }
                ReqStorageHelper.CallMethodMeta callMethodMeta = selectedItem.metaObj(ReqStorageHelper.CallMethodMeta.class);
                if (callMethodMeta.getSubType() != ReqStorageHelper.SubItemType.controller || callMethodMeta.getHttpMeta() == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Un support subType or meta is null");
                    return;
                }

                String app = (String) appBox.getSelectedItem();
                DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
                SettingsStorageHelper.HttpCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(toolWindow.getProject(), app);
                String script = controllerCommand.getScript();
                List<String> envs = controllerCommand.getEnvs();
                if (CollectionUtils.isNotEmpty(envs) || !Objects.equals(script, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript())) {
                    if (CollectionUtils.isEmpty(envs)) {
                        envs = new ArrayList<>();
                        envs.add(null);
                    }
                    for (String env : envs) {
                        //显示的一个图标加上标题
                        AnAction documentation = new AnAction("Generate with " + app + ":" + env, "Generate with " + app + ":" + env, FunctionCallTool.GENERATE_ICON) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                Application application = ApplicationManager.getApplication();
                                ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleControllerCommand(env, script, callMethodMeta.getHttpMeta());
                                            }
                                        });
                                    }
                                });
                            }
                        };
                        controllerActionGroup.add(documentation); // 将动作添加到动作组中
                    }
                }


                if (controllerActionGroup.getChildrenCount() == 0) {
                    //没有自定义逻辑，则直接处理

                    Application application = ApplicationManager.getApplication();
                    ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing generate function, please wait ...", false) {

                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            application.runReadAction(new Runnable() {
                                @Override
                                public void run() {
                                    handleControllerCommand(null, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), callMethodMeta.getHttpMeta());
                                }
                            });
                        }
                    });

                    return;
                }

                JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("ControllerCommandPopup", controllerActionGroup).getComponent();
                popupMenu.show(controllerCommandButton, 32, 0);
            }
        });
    }


    private void handleControllerCommand(String env, String script, ReqStorageHelper.HttpCommandMeta commandMeta) {
        String jsonParams = jsonInputField.getText();
        JSONObject inputParams = null;
        try {
            inputParams = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            setOutputText("Input parameter must be json object", null);
            return;
        }
        String httpMethod = commandMeta.getHttpMethod();
        String path1 = commandMeta.getPath();
        Map<String, String> aliasmap = commandMeta.getAliasmap();
        List<String> pathKeys = commandMeta.getPathKeys();
        String jsonBodyKey = commandMeta.getJsonBodyKey();
        Map<String, String> headers = commandMeta.getHeaders();
        Map<String, String> headerValues = new HashMap<>();

        String jsonBody = null;
        if (jsonBodyKey != null) {
            if (inputParams.get(jsonBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(inputParams.get(jsonBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            inputParams.remove(jsonBodyKey);
        }

        if (headers != null) {
            for (Map.Entry<String, String> stringEntry : headers.entrySet()) {
                String headerVal = inputParams.getString(stringEntry.getKey());
                inputParams.remove(stringEntry.getKey());
                if (headerVal == null) {
                    continue;
                }
                headerValues.put(stringEntry.getValue(), headerVal);
            }
        }


//               parse是个双层 map 参数
//                我想让双层key展开平铺
        JSONObject flattenedParse = new JSONObject();
        FunctionCallTool.flattenJson(inputParams, flattenedParse);
//                @requestParam Set<String> ids 这种是需要支持的
        Iterator<Map.Entry<String, Object>> iterator = inputParams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() instanceof JSONArray) {
                flattenedParse.put(entry.getKey(), StringUtils.join((JSONArray) entry.getValue(), ","));
                iterator.remove();
            }
        }

        if (!pathKeys.isEmpty()) {
            for (String path : pathKeys) {
                String val = null;
                try {
                    val = URLEncoder.encode(String.valueOf(flattenedParse.get(path)), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                }
                flattenedParse.remove(path);

                if (aliasmap.containsKey(path)) {
                    path = aliasmap.get(path);
                }
                path1 = path1.replace("{" + path + "}", val);
//                        path 支持写类似正则的内容,类似/{userId1}/orders/{orderId:\\d+}
//                        请手动在 path1 中找{+path:的内容再找到下一个}进行补充替换
                // 处理带有正则的路径变量，例如：/{userId1}/orders/{orderId:\\d+}
                Pattern pattern = Pattern.compile("\\{" + Pattern.quote(path) + ":.*?\\}");
                Matcher matcher = pattern.matcher(path1);
                if (matcher.find()) {
                    path1 = matcher.replaceFirst(val);
                }
            }
        }

        Map<String, String> urlParams = new HashMap<>();
        if (!flattenedParse.isEmpty()) {
            com.testkit.util.Container<Boolean> first = new Container<>();
            first.set(true);
            flattenedParse.entrySet().forEach(new Consumer<Map.Entry<String, Object>>() {
                @Override
                public void accept(Map.Entry<String, Object> stringObjectEntry) {
                    String s = aliasmap.get(stringObjectEntry.getKey());
                    if (s != null && StringUtils.isBlank(s)) {
                        return;
                    }
                    s = s == null ? stringObjectEntry.getKey() : s;
                    urlParams.put(s, String.valueOf(stringObjectEntry.getValue()));
                }
            });
        }
        setOutputText("Generate controller command ...", null);
        try {
            String ret = invokeControllerScript(script, env, httpMethod, path1, urlParams, jsonBody, headerValues);
            setOutputText(ret, null);
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            setOutputText("Command generate error, Please use the classes that come with jdk or groovy, do not use classes in your project, " + ex.getClass().getSimpleName() + ", " + ex.getMessage(), null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error," + ex.getClass().getSimpleName() + ", " + ex.getMessage(), null);
        }
    }

    public String invokeControllerScript(String code, String env, String httpMethod, String path, Map<String, String> params, String jsonBody, Map<String, String> headerValues) {
        RuntimeHelper.VisibleApp selectedApp = parseApp((String) visibleAppComboBox.getSelectedItem());
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.getPort(), httpMethod, path, params, headerValues, jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    private void buildFeignButton() {
        feignCommandButton = new JButton(FunctionCallTool.FEIGN_ICON);
        feignCommandButton.setToolTipText("[FeignClient] Generate feign command");
        feignCommandButton.setPreferredSize(new Dimension(32, 32));
        feignCommandButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedItem == null || appBox.getSelectedItem() == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select a app and item");
                    return;
                }
                ReqStorageHelper.CallMethodMeta callMethodMeta = selectedItem.metaObj(ReqStorageHelper.CallMethodMeta.class);
                if (callMethodMeta.getSubType() != ReqStorageHelper.SubItemType.feign_client || callMethodMeta.getHttpMeta() == null) {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Un support subType or meta is null");
                    return;
                }

                String app = (String) appBox.getSelectedItem();
                DefaultActionGroup feignActionGroup = new DefaultActionGroup();
                SettingsStorageHelper.HttpCommand httpCommand = SettingsStorageHelper.getAppFeignCommand(toolWindow.getProject(), app);
                String script = httpCommand.getScript();
                List<String> envs = httpCommand.getEnvs();
                if (CollectionUtils.isNotEmpty(envs) || !Objects.equals(script, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript())) {
                    if (CollectionUtils.isEmpty(envs)) {
                        envs = new ArrayList<>();
                        envs.add(null);
                    }
                    for (String env : envs) {
                        //显示的一个图标加上标题
                        AnAction documentation = new AnAction("Generate with " + app + ":" + env, "Generate with " + app + ":" + env, FunctionCallTool.FEIGN_ICON) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                Application application = ApplicationManager.getApplication();
                                ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleFeignCommand(env, script, callMethodMeta.getHttpMeta());
                                            }
                                        });
                                    }
                                });
                            }
                        };
                        feignActionGroup.add(documentation); // 将动作添加到动作组中
                    }
                }


                if (feignActionGroup.getChildrenCount() == 0) {
                    //没有自定义逻辑，则直接处理

                    Application application = ApplicationManager.getApplication();
                    ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Processing generate function, please wait ...", false) {

                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            application.runReadAction(new Runnable() {
                                @Override
                                public void run() {
                                    handleFeignCommand(null, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript(), callMethodMeta.getHttpMeta());
                                }
                            });
                        }
                    });

                    return;
                }

                JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("FeignCommandPopup", feignActionGroup).getComponent();
                popupMenu.show(controllerCommandButton, 32, 0);
            }
        });
    }


    private void handleFeignCommand(String env, String script, ReqStorageHelper.HttpCommandMeta commandMeta) {
        String jsonParams = jsonInputField.getText();
        JSONObject inputParams = null;
        try {
            inputParams = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            setOutputText("Input parameter must be json object", null);
            return;
        }
        String httpMethod = commandMeta.getHttpMethod();
        String path1 = commandMeta.getPath();
        Map<String, String> aliasmap = commandMeta.getAliasmap();
        List<String> pathKeys = commandMeta.getPathKeys();
        String jsonBodyKey = commandMeta.getJsonBodyKey();
        Map<String, String> headers = commandMeta.getHeaders();

        HashMap<String, String> headerValues = new HashMap<>();
        String jsonBody = null;
        if (jsonBodyKey != null) {
            if (inputParams.get(jsonBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(inputParams.get(jsonBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            inputParams.remove(jsonBodyKey);
        }

        if (headers != null) {
            for (Map.Entry<String, String> stringEntry : headers.entrySet()) {
                String headerVal = inputParams.getString(stringEntry.getKey());
                inputParams.remove(stringEntry.getKey());
                if (headerVal == null) {
                    continue;
                }
                headerValues.put(stringEntry.getValue(), headerVal);
            }
        }


//               parse是个双层 map 参数
//                我想让双层key展开平铺
        JSONObject flattenedParse = new JSONObject();
        FunctionCallTool.flattenJson(inputParams, flattenedParse);
//                @requestParam Set<String> ids 这种是需要支持的
        Iterator<Map.Entry<String, Object>> iterator = inputParams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() instanceof JSONArray) {
                flattenedParse.put(entry.getKey(), StringUtils.join((JSONArray) entry.getValue(), ","));
                iterator.remove();
            }
        }

        if (!pathKeys.isEmpty()) {
            for (String path : pathKeys) {
                String val = null;
                try {
                    val = URLEncoder.encode(String.valueOf(flattenedParse.get(path)), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                }
                flattenedParse.remove(path);

                if (aliasmap.containsKey(path)) {
                    path = aliasmap.get(path);
                }
                path1 = path1.replace("{" + path + "}", val);
//                        path 支持写类似正则的内容,类似/{userId1}/orders/{orderId:\\d+}
//                        请手动在 path1 中找{+path:的内容再找到下一个}进行补充替换
                // 处理带有正则的路径变量，例如：/{userId1}/orders/{orderId:\\d+}
                Pattern pattern = Pattern.compile("\\{" + Pattern.quote(path) + ":.*?\\}");
                Matcher matcher = pattern.matcher(path1);
                if (matcher.find()) {
                    path1 = matcher.replaceFirst(val);
                }
            }
        }

        Map<String, String> urlParams = new HashMap<>();
        if (!flattenedParse.isEmpty()) {
            com.testkit.util.Container<Boolean> first = new Container<>();
            first.set(true);
            flattenedParse.entrySet().forEach(new Consumer<Map.Entry<String, Object>>() {
                @Override
                public void accept(Map.Entry<String, Object> stringObjectEntry) {
                    String s = aliasmap.get(stringObjectEntry.getKey());
                    if (s != null && StringUtils.isBlank(s)) {
                        return;
                    }
                    s = s == null ? stringObjectEntry.getKey() : s;
                    urlParams.put(s, String.valueOf(stringObjectEntry.getValue()));
                }
            });
        }
        setOutputText("Generate FeignClient command ...", null);
        try {
            String ret = invokeFeignScript(script, env, commandMeta.getFeignName(), commandMeta.getFeignUrl(), httpMethod, path1, urlParams, jsonBody, headerValues);
            setOutputText(ret, null);
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            setOutputText("Command generate error, Please use the classes that come with jdk or groovy, do not use classes in your project, " + ex.getClass().getSimpleName() + ", " + ex.getMessage(), null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error," + ex.getClass().getSimpleName() + ", " + ex.getMessage(), null);
        }
    }

    public String invokeFeignScript(String code, String env, String feignName, String feignUrl, String httpMethod, String path, Map<String, String> params, String jsonBody, HashMap<String, String> headerValues) {
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, feignName, feignUrl, httpMethod, path, params,headerValues ,jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    private void refreshVisibleApp() {
        List<RuntimeHelper.VisibleApp> visibleAppList = RuntimeHelper.getVisibleApps(toolWindow.getProject().getName()).stream()
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
        for (RuntimeHelper.VisibleApp visibleApp : visibleAppList) {
            String item = visibleApp.getAppName() + ":" + visibleApp.getPort();
            visibleAppComboBox.addItem(item);
        }

        // 如果之前选中的项还在新列表中，则重新选中它
        if (selectedItem != null && newItems.contains(selectedItem)) {
            visibleAppComboBox.setSelectedItem(selectedItem);
        }
//        visibleAppComboBox.repaint();
        dialog.revalidate();
        dialog.repaint();
    }


    private JPanel createLabelTextFieldPanel(TestkitToolWindow window, String labelText, Component field) {
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
                TestkitHelper.copyToClipboard(window.getProject(), field instanceof TextAccessor ? ((TextAccessor) field).getText() : ((JTextComponent) field).getText(), labelText + " was Copied");
            }
        });


        // 将 JTextComponent 放入 JScrollPane
        JScrollPane scrollPane = new JBScrollPane(field);
        if (field instanceof EditorTextComponent) {
            scrollPane.setBorder(BorderFactory.createTitledBorder("Input"));
            ((EditorTextComponent) field).getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                public void documentChanged(DocumentEvent event) {
                    scrollPane.revalidate();
                    scrollPane.repaint();
                }
            });
        }


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
    public void updateRightPanel() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        Object nowItem = node == null ? null : node.getUserObject();
        if (nowItem == selectedItem) {
            return;
        }
        if (!(nowItem instanceof ReqStorageHelper.Item)) {
            selectedItem = null;
            iconLabel.setIcon(null);
            reqsComboBox.removeAllItems();
            jsonInputField.setText("");
            metaTextPane.setText("");
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
                // 更新标题
                List<ReqStorageHelper.SavedReq> reqs = item.getReqs();
                reqsComboBox.removeAllItems();
                if (reqs != null) {
                    for (ReqStorageHelper.SavedReq req : reqs) {
                        reqsComboBox.addItem(req);
                    }
                }


                // 根据条件添加script输入框
                if (item.getType() == ReqStorageHelper.ItemType.flexible_test) {
                    ReqStorageHelper.FlexibleTestMeta meta = item.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                    metaTextPane.setText(meta.getCode() == null ? "" : meta.getCode());
                    metaTextPane.setEnabled(false);
                } else if (item.getType() == ReqStorageHelper.ItemType.function_call) {
                    metaTextPane.setText(item.getMeta() == null ? "" : JsonUtil.formatObj(item.getMeta()));
                    metaTextPane.setEnabled(false);
                } else {
                    TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Un support type, please contact developer");
                    return;
                }

                actionPanel.removeAll();

                JLabel instanceLabel = new JLabel();
                instanceLabel.setText("RuntimeApp:");
                actionPanel.add(copyRetButton);
                // 创建底部面板使用FlowLayout
                actionPanel.add(instanceLabel);
                actionPanel.add(visibleAppComboBox);
                actionPanel.add(useProxyButton);
                actionPanel.add(executeButton);

                if(item.getType() == ReqStorageHelper.ItemType.function_call && item.metaObj(ReqStorageHelper.CallMethodMeta.class).isSpringCache()){
                    actionPanel.add(getKeyButton);
                    actionPanel.add(getValButton);
                    actionPanel.add(delValButton);
                }

                if (item.fetchSubType() == ReqStorageHelper.SubItemType.controller) {
                    actionPanel.add(controllerCommandButton);
                } else if (item.fetchSubType() == ReqStorageHelper.SubItemType.feign_client) {
                    actionPanel.add(feignCommandButton);
                }
                // 刷新面板
                actionPanel.revalidate();
                actionPanel.repaint();
            }
        });
    }


    // 执行按钮点击事件
    private void executeAction() {
        ReqStorageHelper.Item item = selectedItem;
        String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
        String jsonInput = jsonInputField.getText();
        if (item == null || selectedInstance == null) {
            TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select valid item and runtime app");
            return;
        }
        RuntimeHelper.VisibleApp visibleApp = parseApp(selectedInstance);
        if (visibleApp == null) {
            TestkitHelper.alert(getToolWindow().getProject(), Messages.getErrorIcon(), "Please select runtime app");
            return;
        }

        triggerHttpTask(executeButton, AllIcons.Actions.Execute, visibleApp.getSidePort(), new Supplier<JSONObject>() {
            @Override
            public JSONObject get() {
                if (item.getType() == ReqStorageHelper.ItemType.function_call) {
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
                        meta = JSON.parseObject(metaTextPane.getText(), ReqStorageHelper.CallMethodMeta.class);
                    } catch (Throwable e) {
                        throw new RuntimeException("Meta must be json object");
                    }
                    return buildCallMethodParams(meta, visibleApp, args);
                } else if (item.getType() == ReqStorageHelper.ItemType.flexible_test) {
                    JSONObject args = new JSONObject();
                    if (StringUtils.isNotBlank(jsonInput)) {
                        try {
                            args = JSON.parseObject(jsonInput.trim());
                        } catch (Throwable e) {
                            throw new RuntimeException("Params must be json object");
                        }
                    }
                    ReqStorageHelper.FlexibleTestMeta meta = item.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                    meta.setCode(metaTextPane.getText().trim());
                    return buildFlexibleParams(meta, visibleApp, args);
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

    private RuntimeHelper.VisibleApp parseApp(String selectedItem) {
        if (selectedItem == null) {
            return null;
        }
        String[] split = selectedItem.split(":");
        if (split.length == 2) {
            RuntimeHelper.VisibleApp visibleApp = new RuntimeHelper.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[1]) + 10000);
            return visibleApp;

        } else if (split.length == 3) {
            RuntimeHelper.VisibleApp visibleApp = new RuntimeHelper.VisibleApp();
            visibleApp.setAppName(split[0]);
            visibleApp.setPort(Integer.parseInt(split[1]));
            visibleApp.setSidePort(Integer.parseInt(split[2]));
            return visibleApp;
        }
        return null;
    }

    private JSONObject buildCallMethodParams(ReqStorageHelper.CallMethodMeta meta, RuntimeHelper.VisibleApp visibleApp, JSONObject args) {
        JSONObject params = new JSONObject();
        params.put("typeClass", meta.getTypeClass());
        params.put("beanName", meta.getBeanName());
        params.put("methodName", meta.getMethodName());
        params.put("argTypes", meta.getArgTypes());
        params.put("args", ToolHelper.adapterParams(meta.getArgNames(), args).toJSONString());
        params.put("original", !useProxyButton.isSelected());
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.FUNCTION_CALL.getCode());
        req.put("params", params);

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.getAppScript(toolWindow.getProject(), visibleApp.getAppName()));
        }
        return req;
    }

    private JSONObject buildCacheParams(String cacheAction,ReqStorageHelper.CallMethodMeta meta, RuntimeHelper.VisibleApp visibleApp, JSONObject args) {
        JSONObject params = new JSONObject();
        params.put("typeClass", meta.getTypeClass());
        params.put("beanName", meta.getBeanName());
        params.put("methodName", meta.getMethodName());
        params.put("argTypes", meta.getArgTypes());
        params.put("args", ToolHelper.adapterParams(meta.getArgNames(), args).toJSONString());
        params.put("action", cacheAction);
        JSONObject req = new JSONObject();
        req.put("method", "spring-cache");
        req.put("params", params);

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.getAppScript(toolWindow.getProject(), visibleApp.getAppName()));
        }
        return req;
    }

    private JSONObject buildFlexibleParams(ReqStorageHelper.FlexibleTestMeta meta, RuntimeHelper.VisibleApp visibleApp, JSONObject args) {
        JSONObject params = new JSONObject();
        params.put("code", meta.getCode());
        params.put("methodName", meta.getMethodName());
        params.put("argTypes", meta.getArgTypes());
        params.put("args", ToolHelper.adapterParams(meta.getArgNames(), args).toJSONString());
        JSONObject req = new JSONObject();
        req.put("method", PluginToolEnum.FLEXIBLE_TEST.getCode());
        req.put("params", params);

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(toolWindow.getProject());
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.getAppScript(toolWindow.getProject(), visibleApp.getAppName()));
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
            case function_call:
                return FunctionCallIconProvider.FUNCTION_CALL_ICON;
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

    public TestkitToolWindow getToolWindow() {
        return toolWindow;
    }

    public void setToolWindow(TestkitToolWindow toolWindow) {
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
                setToolTipText(item.getName());

                // 可以添加额外信息（如时间戳等）
//                append("  " + item.getType(),
//                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER | SimpleTextAttributes.STYLE_ITALIC,
//                                GROUP_COLOR));
            }
        }


    }


}
