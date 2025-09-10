package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.awt.RelativePoint;
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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
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

public class TestkitStoreWindow {

    public static final Icon UNKNOWN_ICON = IconLoader.getIcon("/icons/unknown.svg", TestkitStoreWindow.class);

    public static final Icon STORE_ICON = IconLoader.getIcon("/icons/testkit-store.svg", TestkitStoreWindow.class);



    private Project project;
    private ToolWindow window;

    private JPanel windowContent;

    private JComboBox<String> appBox;


    private JLabel iconLabel;


    private JPanel inputPanel;
    private JPanel actionPanel;

    private JComboBox<String> visibleAppComboBox;
    private JButton copyRetButton;
    private JButton quickSaveParamsButton;
    private JToggleButton useProxyButton;
    private JButton executeButton;
    private JButton controllerCommandButton;

    private JButton feignCommandButton;

    private JButton getKeyButton;
    private JButton getValButton;
    private JButton delValButton;

    private JComboBox<ReqStorageHelper.SavedReq> reqsComboBox;

    private LanguageTextField jsonInputField;

    private JTextPane outputTextPane;

    private ReqStorageHelper.Item selectedItem;

    // 新增：用于替代左侧树的下拉选择
    private JComboBox<String> groupBox;
    private JComboBox<ReqStorageHelper.Item> itemBox;
    private volatile boolean suppressComboEvents = false;

    protected Set<String> cancelReqs = new HashSet<>(128);
    private String lastReqId;


    public TestkitStoreWindow(Project project, ToolWindow window) {
        this.window = window;
        this.project = project;
        init();
    }

    public JPanel getContent() {
        return windowContent;
    }

    private void init() {
        // 定义主面板
        windowContent = new JPanel(new BorderLayout());

        // 1. 创建顶部参数面板（增加水平滚动以避免按钮被挤没）
        JPanel topPanel = createTopPanel();
        JBScrollPane topScrollPane = new JBScrollPane(topPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        topScrollPane.setBorder(JBUI.Borders.empty());
        topScrollPane.setPreferredSize(new Dimension(0, Math.max(36, topPanel.getPreferredSize().height + 4)));
        windowContent.add(topScrollPane, BorderLayout.NORTH);

        // 仅使用右侧详情面板
        JPanel rightPanel = createRightPanel();
        JBScrollPane rightScrollPane = new JBScrollPane(rightPanel);
        rightScrollPane.setBorder(JBUI.Borders.empty());
        windowContent.add(rightScrollPane, BorderLayout.CENTER);

    }


    public void refreshTree() {
        SwingUtilities.invokeLater(() -> {
            // 保存当前下拉选择
            String selectedGroup = groupBox == null ? null : (String) groupBox.getSelectedItem();
            String selectedItemNameAndType = null;
            if (itemBox != null && itemBox.getSelectedItem() instanceof ReqStorageHelper.Item) {
                ReqStorageHelper.Item it = (ReqStorageHelper.Item) itemBox.getSelectedItem();
                selectedItemNameAndType = it.getName() + "|" + it.getType();
            }

            String app = (String) appBox.getSelectedItem();
            List<ReqStorageHelper.GroupItems> groups = app == null ? null : ReqStorageHelper.getAppReqs(project, app);
            // 重建顶部下拉框
            rebuildCombos(groups, selectedGroup, selectedItemNameAndType);

            // 更新右侧面板
            ReqStorageHelper.Item now = itemBox == null ? null : (ReqStorageHelper.Item) itemBox.getSelectedItem();
            updateRightPanelByItem(now);
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


    public void show() {
        if (appBox.getItemCount() > 0) {
            String selectedItem = (String) appBox.getSelectedItem();
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(project.getName());
            String selectedApp = visibleApp == null ? null : visibleApp.getAppName();
            List<String> projectAppList = RuntimeHelper.getAppMetas(project.getName()).stream().map(new Function<RuntimeHelper.AppMeta, String>() {
                @Override
                public String apply(RuntimeHelper.AppMeta appMeta) {
                    return appMeta.getApp();
                }
            }).collect(Collectors.toCollection(ArrayList::new));
            if (selectedApp != null && projectAppList.contains(selectedApp) && !Objects.equals(selectedItem, selectedApp)) {
                appBox.setSelectedItem(selectedApp);
            }
        }
        window.setAvailable(true, new Runnable() {
            @Override
            public void run() {
                window.show();
            }
        });
    }



    // 创建顶部参数面板
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 第一行：App + 操作按钮
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row1.setBorder(JBUI.Borders.empty(0, 6, 0, 0));
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
        row1.add(appLabel);
        row1.add(appBox);

//        JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
//        refreshButton.setPreferredSize(new Dimension(32, 32));
//        refreshButton.setToolTipText("Reload store");
//        refreshButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                refreshTree();
//                TestkitHelper.notify(project, NotificationType.INFORMATION, "Reload success");
//            }
//        });
//        row1.add(refreshButton);

        JButton importButton = new JButton(SettingsDialog.IMPORT_ICON);
        importButton.setPreferredSize(new Dimension(32, 32));
        importButton.setToolTipText("Import/Overwrite item to the selected app");
        importButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                if (app == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a app");
                    return;
                }
                JDialog dialog = new JDialog();
                dialog.setTitle("Import/Update item to the selected app");
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                JLabel instructionLabel = new JLabel("<html>Paste the data you want to import here<br>Usually json content exported from other device or project</html>");
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                JTextArea jsonInput = new JTextArea();
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);

                JButton importConfirmButton = new JButton("Import");
                importConfirmButton.addActionListener(e1 -> {
                    ReqStorageHelper.GroupItems groupItems = null;
                    try {
                        groupItems = JSON.parseObject(jsonInput.getText().trim(), ReqStorageHelper.GroupItems.class);
                    } catch (Exception ex) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Import item must be group items, " + ex.getMessage());
                        return;
                    }
                    try {
                        ReqStorageHelper.saveAppGroupItems(project, app, groupItems);
                        refreshTree();
                        dialog.dispose();
                        TestkitHelper.notify(project, NotificationType.INFORMATION, "Import successfully");
                    } catch (Exception ex) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Import error," + ex.getMessage());
                    }
                });

                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(importConfirmButton, BorderLayout.SOUTH);

                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.getRootPane().registerKeyboardAction(
                        e2 -> dialog.dispose(),
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                        JComponent.WHEN_IN_FOCUSED_WINDOW
                );

                dialog.setVisible(true);
            }
        });
        row1.add(importButton);

        JButton exportButton = new JButton(SettingsDialog.EXPORT_ICON);
        exportButton.setPreferredSize(new Dimension(32, 32));
        exportButton.setToolTipText("Export the selected group or item");
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                if (app == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a app");
                    return;
                }

                String group = groupBox == null ? null : (String) groupBox.getSelectedItem();
                ReqStorageHelper.Item selected = (itemBox != null && itemBox.getSelectedItem() instanceof ReqStorageHelper.Item)
                        ? (ReqStorageHelper.Item) itemBox.getSelectedItem() : null;
                if (group == null && selected == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a group or Item");
                    return;
                }

                // 构建可选导出范围
                java.util.List<String> scopeList = new ArrayList<>();
                if (group != null) {
                    scopeList.add("Group");
                }
                if (selected != null) {
                    scopeList.add("Item");
                }


                // 创建单选面板
                JPanel radioPanel = new JPanel(new GridLayout(0, 1)); // 垂直排列
                ButtonGroup buttonGroup = new ButtonGroup();
                JRadioButton group1 = new JRadioButton("Group");
                group1.setSelected(true);
                buttonGroup.add(group1);
                radioPanel.add(group1);
                JRadioButton group2 = new JRadioButton("Item");
                buttonGroup.add(group2);
                radioPanel.add(group2);

                // 弹窗设置
                int result = JOptionPane.showConfirmDialog(
                        exportButton,
                        radioPanel,
                        "Choose export scope",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

                if (result != JOptionPane.OK_OPTION) {
                    return;
                }

                String targetGroup = null;
                ReqStorageHelper.Item targetItem = null;
                if (group1.isSelected()) {
                    if (group == null) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a group first");
                        return;
                    }
                    targetGroup = group;
                } else  {
                    if (selected == null) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a item first");
                        return;
                    }
                    targetItem = selected;
                }

                ReqStorageHelper.GroupItems exportObj = exportReqs(app, targetGroup == null ? (targetItem == null ? group : targetItem.getGroup()) : targetGroup, targetItem);

                JDialog dialog = new JDialog();
                dialog.setTitle("Export the selected group or item");
                dialog.setModal(true);
                dialog.setSize(500, 400);
                dialog.setLocationRelativeTo(null);

                String title = targetItem == null ? String.valueOf((targetGroup == null ? (group == null ? "" : group) : targetGroup)) : (targetItem.getGroup() + "." + targetItem.getName());
                JLabel instructionLabel = new JLabel("<html>Current export scope: " + title + "<br><br>The exported content is already below<br/>You can copy it and import it on another device or project</html>");
                instructionLabel.setForeground(new Color(0x72A96B));
                instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
                instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                JTextArea jsonInput = new JTextArea();
                jsonInput.setEditable(false);
                jsonInput.setLineWrap(true);
                jsonInput.setWrapStyleWord(true);
                JScrollPane scrollPane = new JScrollPane(jsonInput);
                jsonInput.setText(JsonUtil.formatObj(exportObj));
                JButton copyConfirmButton = new JButton("Copy");
                copyConfirmButton.addActionListener(e1 -> {
                    TestkitHelper.copyToClipboard(project, jsonInput.getText(), (exportObj == null || exportObj.getItems() == null ? 0 : exportObj.getItems().size()) + " item have been copied");
                    dialog.dispose();
                });

                dialog.setLayout(new BorderLayout());
                dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                dialog.add(instructionLabel, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(copyConfirmButton, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
        });
        row1.add(exportButton);

        panel.add(row1, BorderLayout.NORTH);

        // 第二行：Group + Item 选择（自适应宽度）
        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setBorder(JBUI.Borders.empty(0, 6, 0, 0));
        GridBagConstraints r2 = new GridBagConstraints();
        r2.insets = JBUI.insets(0, 0, 0, 6);

        groupBox = new JComboBox<>();
//        groupBox.setPrototypeDisplayValue("XXXXXXXXXXXX");
        groupBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (suppressComboEvents) {
                    return;
                }
                onGroupSelected();
            }
        });
        JLabel groupLabel = new JLabel("Group:");
        groupLabel.setLabelFor(groupBox);

        r2.gridx = 0; r2.gridy = 0; r2.weightx = 0; r2.fill = GridBagConstraints.NONE;
        row2.add(groupLabel, r2);
        r2.gridx = 1; r2.gridy = 0; r2.weightx = 0.35; r2.fill = GridBagConstraints.HORIZONTAL;
        row2.add(groupBox, r2);

        itemBox = new JComboBox<>();
        itemBox.setRenderer(new ListCellRenderer<ReqStorageHelper.Item>() {
            private final DefaultListCellRenderer delegate = new DefaultListCellRenderer();
            @Override
            public Component getListCellRendererComponent(JList<? extends ReqStorageHelper.Item> list, ReqStorageHelper.Item value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = value == null ? "" : value.getName();
                return delegate.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        itemBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (suppressComboEvents) {
                    itemBox.setToolTipText("");
                    return;
                }
                onItemSelected();
            }
        });
        JLabel itemLabel = new JLabel("Item:");
        itemLabel.setLabelFor(itemBox);

        r2.gridx = 2; r2.gridy = 0; r2.weightx = 0; r2.fill = GridBagConstraints.NONE;
        row2.add(itemLabel, r2);
        r2.gridx = 3; r2.gridy = 0; r2.weightx = 0.65; r2.fill = GridBagConstraints.HORIZONTAL;
        row2.add(itemBox, r2);

        panel.add(row2, BorderLayout.CENTER);

        return panel;
    }

    private ReqStorageHelper.GroupItems exportReqs(String app, String groupName, ReqStorageHelper.Item itemOrNull) {
        ReqStorageHelper.GroupItems exportObj = null;
        List<ReqStorageHelper.GroupItems> groups = ReqStorageHelper.getAppReqs(project, app);
        if (itemOrNull == null) {
            Optional<ReqStorageHelper.GroupItems> groupItems = Optional.ofNullable(groups)
                    .orElse(new ArrayList<>())
                    .stream().filter(new Predicate<ReqStorageHelper.GroupItems>() {
                        @Override
                        public boolean test(ReqStorageHelper.GroupItems groupItems) {
                            return groupItems != null && Objects.equals(groupName, groupItems.getGroup());
                        }
                    }).findFirst();
            exportObj = groupItems.orElseGet(new Supplier<ReqStorageHelper.GroupItems>() {
                @Override
                public ReqStorageHelper.GroupItems get() {
                    ReqStorageHelper.GroupItems items = new ReqStorageHelper.GroupItems();
                    items.setGroup(groupName);
                    return items;
                }
            });
        } else {
            Optional<ReqStorageHelper.GroupItems> groupItems = Optional.ofNullable(groups)
                    .orElse(new ArrayList<>())
                    .stream().filter(new Predicate<ReqStorageHelper.GroupItems>() {
                        @Override
                        public boolean test(ReqStorageHelper.GroupItems groupItems) {
                            return groupItems != null && Objects.equals(itemOrNull.getGroup(), groupItems.getGroup());
                        }
                    }).findFirst();
            exportObj = new ReqStorageHelper.GroupItems();
            exportObj.setGroup(itemOrNull.getGroup());
            if (groupItems.isPresent()) {
                Optional<ReqStorageHelper.Item> first = Optional.ofNullable(groupItems.get().getItems())
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(new Predicate<ReqStorageHelper.Item>() {
                            @Override
                            public boolean test(ReqStorageHelper.Item item) {
                                return item != null && Objects.equals(item.getName(), itemOrNull.getName()) && Objects.equals(item.getType(), itemOrNull.getType());
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
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        JButton delButton = new JButton(AllIcons.Actions.GC);
        delButton.setPreferredSize(new Dimension(32, 32));
        delButton.setMargin(JBUI.insets(0));
        delButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) appBox.getSelectedItem();
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem == null) {
                    return;
                }
                ReqStorageHelper.delAppReq(project, app, selectedItem.getGroup(), selectedItem.getType(), selectedItem.getName(), selectedReq.getTitle());
                TestkitHelper.notify(project, NotificationType.INFORMATION, "Delete success");
                refreshTree();
            }
        });

        buttonPanel.add(delButton);
        firstRow.add(buttonPanel, BorderLayout.EAST);

        reqsComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReqStorageHelper.SavedReq selectedReq = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
                if (selectedReq == null || selectedItem == null) {
                    jsonInputField.setText("");
                    return;
                }
                ReqStorageHelper.ItemType type = selectedItem.getType();
                if (type == null) {
                    jsonInputField.setText("");
                    return;
                }
                jsonInputField.setText(selectedReq.getArgs() == null ? "{}" : JSON.toJSONString(selectedReq.getArgs(), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
//        gbc.insets = JBUI.insets(0, 0, 5, 0);

        contentPanel.add(firstRow, gbc);


        // 第二行：下拉框和执行按钮
        inputPanel = buildInputPanel();

        gbc.gridy = 1;
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
        gbc.gridy = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = JBUI.insets(1);
        contentPanel.add(outputScrollPane, gbc);

        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInputPanel() {
        JPanel panelResults = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelResults.setLayout(gridbag);

        JPanel panelParamsHeadersBody = new JPanel(new GridLayout(1, 2));
        panelParamsHeadersBody.setPreferredSize(new Dimension(0, 300));

        
        jsonInputField = new LanguageTextField(JsonLanguage.INSTANCE, null, "", false);
        JPanel paramsPanel = createLabelTextFieldPanel("Params", jsonInputField);
//        metaTextPane = new JTextPane();
//        metaTextPane.setEditable(false);
//        JPanel metaPanel = createLabelTextFieldPanel("Meta", metaTextPane);


        panelParamsHeadersBody.add(paramsPanel);
//        panelParamsHeadersBody.add(metaPanel);

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
        copyRetButton.setToolTipText("Copy output");
        copyRetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TestkitHelper.copyToClipboard(project, outputTextPane.getText(), null);
            }
        });

        // 保存参数按钮（放在 copy 按钮旁）
        quickSaveParamsButton = new JButton(AllIcons.Actions.MenuSaveall);
        quickSaveParamsButton.setPreferredSize(new Dimension(32, 32));
        quickSaveParamsButton.setToolTipText("Save current params to store");
        quickSaveParamsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showQuickSavePopup();
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
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.FunctionCallMeta meta = null;
                try {
                    meta = JSON.parseObject(iconLabel.getToolTipText(), ReqStorageHelper.FunctionCallMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.FunctionCallMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(getKeyButton, FunctionCallTool.KIcon, visibleApp.getTestkitPort(), new Supplier<JSONObject>() {
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
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.FunctionCallMeta meta = null;
                try {
                    meta = JSON.parseObject(iconLabel.getToolTipText(), ReqStorageHelper.FunctionCallMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.FunctionCallMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(getValButton, AllIcons.Actions.Find, visibleApp.getTestkitPort(), new Supplier<JSONObject>() {
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
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select valid item and runtime app");
                    return;
                }
                RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(selectedInstance);
                if (visibleApp == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select runtime app");
                    return;
                }
                if (item.getType() != ReqStorageHelper.ItemType.function_call) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Spring cache only support function-call");
                    return;
                }
                JSONObject args = new JSONObject();
                if (StringUtils.isNotBlank(jsonInput)) {
                    try {
                        args = JSON.parseObject(jsonInput.trim());
                    } catch (Throwable e1) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Params must be json object");
                        return;
                    }
                }
                ReqStorageHelper.FunctionCallMeta meta = null;
                try {
                    meta = JSON.parseObject(iconLabel.getToolTipText(), ReqStorageHelper.FunctionCallMeta.class);
                } catch (Throwable e2) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Meta must be json object");
                    return;
                }
                ReqStorageHelper.FunctionCallMeta finalMeta = meta;
                JSONObject finalArgs = args;
                triggerHttpTask(delValButton, AllIcons.Actions.GC, visibleApp.getTestkitPort(), new Supplier<JSONObject>() {
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
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a app and item");
                    return;
                }
                ReqStorageHelper.FunctionCallMeta functionCallMeta = selectedItem.metaObj(ReqStorageHelper.FunctionCallMeta.class);
                if (functionCallMeta.getSubType() != ReqStorageHelper.SubItemType.controller || functionCallMeta.getHttpMeta() == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Un support subType or meta is null");
                    return;
                }

                String app = (String) appBox.getSelectedItem();
                DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
                SettingsStorageHelper.HttpCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(project, app);
                String script = controllerCommand.getScript();
                List<String> envs = controllerCommand.getEnvs();
                if (CollectionUtils.isNotEmpty(envs) || !Objects.equals(script, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript())) {
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
                                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleControllerCommand(env, script, functionCallMeta.getHttpMeta());
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
                    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing generate function, please wait ...", false) {

                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            application.runReadAction(new Runnable() {
                                @Override
                                public void run() {
                                    handleControllerCommand(null, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), functionCallMeta.getHttpMeta());
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
            setOutputText("Command generate error\nPlease use the classes that come with jdk or groovy, do not use classes in your project\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage(), null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage(), null);
        }
    }

    public String invokeControllerScript(String code, String env, String httpMethod, String path, Map<String, String> params, String jsonBody, Map<String, String> headerValues) {
        RuntimeHelper.VisibleApp selectedApp = RuntimeHelper.parseApp((String) visibleAppComboBox.getSelectedItem());
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.buildWebPort(), httpMethod, path, params, headerValues, jsonBody});
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
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select a app and item");
                    return;
                }
                ReqStorageHelper.FunctionCallMeta functionCallMeta = selectedItem.metaObj(ReqStorageHelper.FunctionCallMeta.class);
                if (functionCallMeta.getSubType() != ReqStorageHelper.SubItemType.feign_client || functionCallMeta.getHttpMeta() == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Un support subType or meta is null");
                    return;
                }

                String app = (String) appBox.getSelectedItem();
                DefaultActionGroup feignActionGroup = new DefaultActionGroup();
                SettingsStorageHelper.HttpCommand httpCommand = SettingsStorageHelper.getAppFeignCommand(project, app);
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
                                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleFeignCommand(env, script, functionCallMeta.getHttpMeta());
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
                    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing generate function, please wait ...", false) {

                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            application.runReadAction(new Runnable() {
                                @Override
                                public void run() {
                                    handleFeignCommand(null, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript(), functionCallMeta.getHttpMeta());
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
            setOutputText("Command generate error\nPlease use the classes that come with jdk or groovy, do not use classes in your project\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage(), null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage(), null);
        }
    }

    public String invokeFeignScript(String code, String env, String feignName, String feignUrl, String httpMethod, String path, Map<String, String> params, String jsonBody, HashMap<String, String> headerValues) {
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, feignName, feignUrl, httpMethod, path, params,headerValues ,jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    private void refreshVisibleApp() {
        List<RuntimeHelper.VisibleApp> visibleAppList = RuntimeHelper.getVisibleApps(project.getName()).stream()
                .filter(visibleApp -> Objects.equals(appBox.getSelectedItem(), visibleApp.getAppName()))
                .toList();

        // 获取当前列表的内容，用于比较是否有变化
        List<String> currentItems = new ArrayList<>();
        for (int i = 0; i < visibleAppComboBox.getItemCount(); i++) {
            currentItems.add(visibleAppComboBox.getItemAt(i));
        }

        // 构建新列表
        List<String> newItems = visibleAppList.stream()
                .map(app -> app.getAppName() +":"+app.getIp()+ ":" + app.getTestkitPort())
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
            String item = visibleApp.getAppName() +":"+visibleApp.getIp()+ ":" + visibleApp.getTestkitPort();
            visibleAppComboBox.addItem(item);
        }

        // 如果之前选中的项还在新列表中，则重新选中它
        if (selectedItem != null && newItems.contains(selectedItem)) {
            visibleAppComboBox.setSelectedItem(selectedItem);
        }
//        visibleAppComboBox.repaint();
        windowContent.revalidate();
        windowContent.repaint();
    }


    private JPanel createLabelTextFieldPanel(String labelText, Component field) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(labelText);

        Dimension preferredSize = new Dimension(30, 30);


        JButton copyButton = new JButton(AllIcons.Actions.Copy);
        copyButton.setToolTipText("Copy this");
        copyButton.setPreferredSize(preferredSize);
        copyButton.setMaximumSize(preferredSize);
        copyButton.setMinimumSize(preferredSize);
        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TestkitHelper.copyToClipboard(project, field instanceof TextAccessor ? ((TextAccessor) field).getText() : ((JTextComponent) field).getText(), labelText + " was Copied");
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
        Object nowItem = (itemBox != null && itemBox.getSelectedItem() instanceof ReqStorageHelper.Item)
                ? (ReqStorageHelper.Item) itemBox.getSelectedItem() : null;
        if (nowItem == selectedItem) {
            return;
        }
        if (!(nowItem instanceof ReqStorageHelper.Item)) {
            selectedItem = null;
            iconLabel.setIcon(null);
            reqsComboBox.removeAllItems();
            jsonInputField.setText("");
            iconLabel.setToolTipText("");
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
                    iconLabel.setToolTipText(meta.getCode() == null ? "" : meta.getCode());
                } else if (item.getType() == ReqStorageHelper.ItemType.function_call) {
                    iconLabel.setToolTipText(item.getMeta() == null ? "" : JsonUtil.formatObj(item.getMeta()));
                } else {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Un support type, please contact developer");
                    return;
                }

                actionPanel.removeAll();

                JLabel instanceLabel = new JLabel();
                instanceLabel.setText("RuntimeApp:");
                instanceLabel.setToolTipText("The list of currently connected apps");
                actionPanel.add(copyRetButton);
                actionPanel.add(quickSaveParamsButton);
                // 创建底部面板使用FlowLayout
                actionPanel.add(instanceLabel);
                actionPanel.add(visibleAppComboBox);
                actionPanel.add(useProxyButton);
                actionPanel.add(executeButton);

                if(item.getType() == ReqStorageHelper.ItemType.function_call && item.metaObj(ReqStorageHelper.FunctionCallMeta.class).isSpringCache()){
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

    // 供刷新后直接根据 item 更新右侧区域
    private void updateRightPanelByItem(ReqStorageHelper.Item item) {
        suppressComboEvents = true;
        try {
            if (itemBox != null && item != null) {
                for (int i = 0; i < itemBox.getItemCount(); i++) {
                    if (Objects.equals(itemBox.getItemAt(i), item)) {
                        itemBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
            updateRightPanel();
        } finally {
            suppressComboEvents = false;
        }
    }

    private void showQuickSavePopup() {
        java.util.List<String> apps = RuntimeHelper.getAppMetas(project.getName()).stream().map(new Function<RuntimeHelper.AppMeta, String>() {
            @Override
            public String apply(RuntimeHelper.AppMeta appMeta) {
                return appMeta.getApp();
            }
        }).collect(Collectors.toCollection(ArrayList::new));

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);

        String[] options = apps.toArray(new String[apps.size()]);
        ComboBox<String> comboBox = new ComboBox<>(options);
        if (appBox != null && appBox.getSelectedItem() instanceof String) {
            comboBox.setSelectedItem((String) appBox.getSelectedItem());
        }

        JBTextField groupField = new JBTextField("default");
        groupField.getEmptyText().setText("Which group to save to");
        groupField.setEditable(true);
        groupField.setEnabled(true);
        groupField.setFocusable(true);
        if (groupBox != null && groupBox.getSelectedItem() instanceof String) {
            groupField.setText((String) groupBox.getSelectedItem());
        }
        JBTextField titleField = new JBTextField("default");
        titleField.getEmptyText().setText("Give this parameter a name");
        titleField.setEditable(true);
        titleField.setEnabled(true);
        titleField.setFocusable(true);
        if (reqsComboBox != null && reqsComboBox.getSelectedItem() instanceof ReqStorageHelper.SavedReq) {
            ReqStorageHelper.SavedReq sel = (ReqStorageHelper.SavedReq) reqsComboBox.getSelectedItem();
            if (sel != null && StringUtils.isNotBlank(sel.getTitle())) {
                titleField.setText(sel.getTitle());
            }
        }

        JButton submitButton = new JButton("Save");

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel appLabel = new JLabel("App:");
        appLabel.setLabelFor(comboBox);
        appLabel.setToolTipText("Which app domain you want to save to");
        panel.add(appLabel, gbc);
        gbc.gridx = 1; panel.add(comboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel groupLabel = new JLabel("Group:");
        groupLabel.setToolTipText("Grouping functions");
        panel.add(groupLabel, gbc);
        gbc.gridx = 1; panel.add(groupField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel titleLabel = new JLabel("Title:");
        titleLabel.setToolTipText("Multiple params can be stored in a function");
        panel.add(titleLabel, gbc);
        gbc.gridx = 1; panel.add(titleField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(submitButton, gbc);

        JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, titleField)
                .setRequestFocus(true)
                .setFocusable(true)
                .setTitle("Save this to store")
                .setMovable(true)
                .setResizable(false)
                .createPopup();

        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String app = (String) comboBox.getSelectedItem();
                String group = groupField.getText();
                String title = titleField.getText();
                if (StringUtils.isBlank(app) || StringUtils.isBlank(group) || StringUtils.isBlank(title)) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "App/Group/Title must not be blank");
                    return;
                }
                if (selectedItem == null) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select an item first");
                    return;
                }
                ReqStorageHelper.SavedReq req = new ReqStorageHelper.SavedReq();
                req.setTitle(title);
                try {
                    JSONObject args = StringUtils.isBlank(jsonInputField.getText()) ? new JSONObject() : JSON.parseObject(jsonInputField.getText());
                    req.setArgs(args);
                } catch (Throwable ex) {
                    TestkitHelper.alert(project, Messages.getErrorIcon(), "Params must be json object");
                    return;
                }

                JSONObject meta = new JSONObject();
                if (selectedItem.getType() == ReqStorageHelper.ItemType.function_call) {
                    try {
                        meta = JSON.parseObject(iconLabel.getToolTipText().trim());
                    } catch (Throwable ex) {
                        TestkitHelper.alert(project, Messages.getErrorIcon(), "Meta must be json object");
                        return;
                    }
                } else if (selectedItem.getType() == ReqStorageHelper.ItemType.flexible_test) {
                    ReqStorageHelper.FlexibleTestMeta metaObj = selectedItem.metaObj(ReqStorageHelper.FlexibleTestMeta.class);
                    metaObj.setCode(iconLabel.getToolTipText().trim());
                    meta = JSON.parseObject(JSON.toJSONString(metaObj, SerializerFeature.WriteMapNullValue));
                }

                ReqStorageHelper.saveAppReq(project, app, group, selectedItem.getType(), selectedItem.getName(), meta, title, req);
                TestkitHelper.notify(project, NotificationType.INFORMATION, "Save success");
                popup.closeOk(null);
                refreshTree();
            }
        });
        popup.show(new RelativePoint(jsonInputField, new Point(0, 0)));
    }


    // 执行按钮点击事件
    private void executeAction() {
        ReqStorageHelper.Item item = selectedItem;
        String selectedInstance = (String) visibleAppComboBox.getSelectedItem();
        String jsonInput = jsonInputField.getText();
        if (item == null || selectedInstance == null) {
            TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select valid item and runtime app");
            return;
        }
        RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.parseApp(selectedInstance);
        if (visibleApp == null) {
            TestkitHelper.alert(project, Messages.getErrorIcon(), "Please select runtime app");
            return;
        }

        triggerHttpTask(executeButton, AllIcons.Actions.Execute, visibleApp.getTestkitPort(), new Supplier<JSONObject>() {
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
                    ReqStorageHelper.FunctionCallMeta meta = null;
                    try {
                        meta = JSON.parseObject(iconLabel.getToolTipText(), ReqStorageHelper.FunctionCallMeta.class);
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
                    meta.setCode(iconLabel.getToolTipText().trim());
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

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing req, please wait ...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                // 发起任务请求，获取请求ID
                JSONObject response = null;
                try {
                    response = HttpUtil.sendPost("http://localhost:" + sidePort + "/", submit.get(), JSONObject.class,5,30);
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
                                setOutputText("req is error\nresult is null", null);
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

    private JSONObject buildCallMethodParams(ReqStorageHelper.FunctionCallMeta meta, RuntimeHelper.VisibleApp visibleApp, JSONObject args) {
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

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(project);
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.encodeInterceptor(project, visibleApp.getAppName()));
        }
        return req;
    }

    private JSONObject buildCacheParams(String cacheAction, ReqStorageHelper.FunctionCallMeta meta, RuntimeHelper.VisibleApp visibleApp, JSONObject args) {
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

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(project);
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.encodeInterceptor(project, visibleApp.getAppName()));
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

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(project);
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (meta.isUseInterceptor()) {
            req.put("interceptor", SettingsStorageHelper.encodeInterceptor(project, visibleApp.getAppName()));
        }
        return req;
    }

    private void setOutputText(String text, List<Map<String, String>> profile) {
        outputTextPane.setText(text);
    }


    private void rebuildCombos(List<ReqStorageHelper.GroupItems> groups, String selectedGroup, String selectedItemNameAndType) {
        suppressComboEvents = true;
        try {
            groupBox.removeAllItems();
            itemBox.removeAllItems();
            if (CollectionUtils.isEmpty(groups)) {
                return;
            }
            Set<String> groupNames = new LinkedHashSet<>();
            for (ReqStorageHelper.GroupItems g : groups) {
                if (g != null && StringUtils.isNotBlank(g.getGroup())) {
                    groupNames.add(g.getGroup());
                }
            }
            for (String name : groupNames) {
                groupBox.addItem(name);
            }
            if (selectedGroup != null && groupNames.contains(selectedGroup)) {
                groupBox.setSelectedItem(selectedGroup);
            }

            // 填充 itemBox
            String groupToFill = (String) groupBox.getSelectedItem();
            if (groupToFill != null) {
                for (ReqStorageHelper.GroupItems g : groups) {
                    if (g != null && Objects.equals(groupToFill, g.getGroup()) && CollectionUtils.isNotEmpty(g.getItems())) {
                        for (ReqStorageHelper.Item it : g.getItems()) {
                            itemBox.addItem(it);
                        }
                        break;
                    }
                }
            }

            if (selectedItemNameAndType != null) {
                for (int i = 0; i < itemBox.getItemCount(); i++) {
                    ReqStorageHelper.Item it = itemBox.getItemAt(i);
                    String key = it.getName() + "|" + it.getType();
                    if (selectedItemNameAndType.equals(key)) {
                        itemBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            suppressComboEvents = false;
        }
    }

    private void onGroupSelected() {
        String group = (String) groupBox.getSelectedItem();
        if (group == null) {
            itemBox.setToolTipText("");
            return;
        }

        // 重建 item 列表（以当前组为准）
        String app = (String) appBox.getSelectedItem();
        List<ReqStorageHelper.GroupItems> groups = app == null ? null : ReqStorageHelper.getAppReqs(project, app);
        rebuildCombos(groups, group, null);
    }

    private void onItemSelected() {
        ReqStorageHelper.Item item = (ReqStorageHelper.Item) itemBox.getSelectedItem();
        String group = (String) groupBox.getSelectedItem();
        if (group == null) {
            itemBox.setToolTipText("");
            return;
        }
        itemBox.setToolTipText(item == null ? "" : item.getName());
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
