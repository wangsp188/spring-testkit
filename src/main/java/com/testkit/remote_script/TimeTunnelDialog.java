package com.testkit.remote_script;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.util.RemoteScriptCallUtils;
import com.testkit.view.CurlDialog;
import com.testkit.view.TestkitToolWindow;
import com.testkit.view.TestkitToolWindowFactory;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TimeTunnel Dialog - Multi-instance recording & replay
 * 
 * 优化版布局：左右分割，每个 instance 有独立的状态和操作按钮
 */
public class TimeTunnelDialog extends DialogWrapper {

    public static final Icon CLEAR_ICON = IconLoader.getIcon("/icons/clear.svg", TimeTunnelDialog.class);

    private final Project project;
    private final String className;
    private final String methodName;
    private List<RuntimeHelper.VisibleApp> arthasApps;

    // UI Components - Top
    private ComboBox<String> appBox;
    private ComboBox<String> partitionBox;
    
    // UI Components - Watch Point
    private JBTextField classField;
    private JBTextField methodField;
    private JBTextField conditionField;
    private ComboBox<Integer> maxRecordsBox;
    private ComboBox<Integer> expandLevelBox;
    
    // UI Components - Left Panel (Instance List)
    private JPanel instanceListPanel;
    private Map<String, InstancePanel> instancePanelMap = new LinkedHashMap<>();
    
    // UI Components - Right Panel (Detail)
    private LanguageTextField jsonEditor;
    
    // Current displayed instances
    private List<RuntimeHelper.VisibleApp> currentInstances = new ArrayList<>();

    // Instance states
    private enum InstanceState {
        READY("Ready", new Color(100, 150, 100)),
        RECORDING("Recording", new Color(200, 50, 50)),
        LOADING("Loading...", new Color(0, 100, 200)),
        STOPPING("Stopping...", new Color(150, 100, 50));
        
        final String label;
        final Color color;
        
        InstanceState(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    public TimeTunnelDialog(Project project, String className, String methodName, 
                           List<RuntimeHelper.VisibleApp> arthasApps) {
        super(project);
        this.project = project;
        this.className = className;
        this.methodName = methodName;
        this.arthasApps = arthasApps;
        
        setTitle("Arthas TimeTunnel");
        setModal(false);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setPreferredSize(new Dimension(1100, 650));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: Target selection + Watch point config
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(createTargetPanel(), BorderLayout.NORTH);
        topPanel.add(createWatchPointPanel(), BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Left-Right Split (Instance List | Detail)
        JBSplitter splitter = new JBSplitter(false, 0.55f);
        splitter.setFirstComponent(createLeftPanel());
        splitter.setSecondComponent(createRightPanel());
        mainPanel.add(splitter, BorderLayout.CENTER);

        // Initialize instance list
        initializeInstanceList();

        return mainPanel;
    }

    /**
     * Create target selection panel (App / Partition / Add Connection)
     */
    private JPanel createTargetPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Target",
                TitledBorder.LEFT, TitledBorder.TOP));

        // App selection
        panel.add(new JLabel("App:"));
        Set<String> appNames = arthasApps.stream()
                .map(RuntimeHelper.VisibleApp::getAppName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        appBox = new ComboBox<>(appNames.toArray(new String[0]));
        appBox.setPreferredSize(new Dimension(120, 28));
        appBox.addActionListener(e -> onAppChanged());
        panel.add(appBox);

        // Partition selection
        panel.add(new JLabel("Partition:"));
        partitionBox = new ComboBox<>();
        partitionBox.setPreferredSize(new Dimension(100, 28));
        partitionBox.addActionListener(e -> onPartitionChanged());
        panel.add(partitionBox);

        // Refresh button (直接从 visibleApps 刷新)
        JButton refreshBtn = new JButton(AllIcons.Actions.Refresh);
        refreshBtn.setToolTipText("Refresh instances from visibleApps");
        refreshBtn.setPreferredSize(new Dimension(28, 28));
        refreshBtn.addActionListener(e -> refreshArthasApps());
        panel.add(refreshBtn);

        // Add connection button
        JButton addConnectionBtn = new JButton(TestkitToolWindow.connectionIcon);
        addConnectionBtn.setToolTipText("Add remote connection");
        addConnectionBtn.setPreferredSize(new Dimension(28, 28));
        addConnectionBtn.addActionListener(e -> {
            TestkitToolWindowFactory.showConnectionConfigPopup(project, () -> {
                SwingUtilities.invokeLater(this::refreshArthasApps);
            }, false);
        });
        panel.add(addConnectionBtn);

        // Initialize dropdowns
        onAppChanged();

        return panel;
    }

    /**
     * Create watch point configuration panel
     */
    private JPanel createWatchPointPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Watch Point",
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 1: Class & Method
        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Class:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5;
        classField = new JBTextField(className);
        classField.setEditable(false);
        classField.setBackground(new Color(245, 245, 245));
        inputPanel.add(classField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Method:"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5;
        methodField = new JBTextField(methodName);
        methodField.setEditable(false);
        methodField.setBackground(new Color(245, 245, 245));
        inputPanel.add(methodField, gbc);

        // Row 2: Condition
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Condition:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        conditionField = new JBTextField();
        conditionField.getEmptyText().setText("Optional OGNL, e.g.: params[0].type=='VOICE'");
        inputPanel.add(conditionField, gbc);

        // Row 3: Max Records, Expand Level
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Max Records:"), gbc);
        gbc.gridx = 1;
        maxRecordsBox = new ComboBox<>(new Integer[]{10, 20, 50, 100});
        maxRecordsBox.setSelectedItem(10);
        maxRecordsBox.setPreferredSize(new Dimension(80, 28));
        inputPanel.add(maxRecordsBox, gbc);

        gbc.gridx = 2;
        inputPanel.add(new JLabel("Expand Level:"), gbc);
        gbc.gridx = 3;
        expandLevelBox = new ComboBox<>(new Integer[]{1, 2, 3, 4});
        expandLevelBox.setSelectedItem(2);
        expandLevelBox.setPreferredSize(new Dimension(60, 28));
        inputPanel.add(expandLevelBox, gbc);

        panel.add(inputPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Create left panel (Instance List with Records)
     */
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Instances & Records",
                TitledBorder.LEFT, TitledBorder.TOP));

        // Toolbar
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        
        JButton startAllBtn = new JButton("Start All", AllIcons.Actions.Execute);
        startAllBtn.setToolTipText("Start recording on all instances");
        startAllBtn.addActionListener(e -> startAllRecording());
        toolbarPanel.add(startAllBtn);
        
        JButton stopAllBtn = new JButton("Stop All", AllIcons.Actions.Suspend);
        stopAllBtn.setToolTipText("Stop recording on all instances");
        stopAllBtn.addActionListener(e -> stopAllRecording());
        toolbarPanel.add(stopAllBtn);
        
        panel.add(toolbarPanel, BorderLayout.NORTH);

        // Instance list panel (scrollable)
        instanceListPanel = new JPanel();
        instanceListPanel.setLayout(new BoxLayout(instanceListPanel, BoxLayout.Y_AXIS));
        
        JBScrollPane scrollPane = new JBScrollPane(instanceListPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // Usage guide shown in Detail panel when no record is selected
    private static final String USAGE_GUIDE = """
            TimeTunnel - Record & Replay Method Invocations
            ================================================
            
            Workflow:
              1. ▶ Start recording on instance
              2. Trigger your request
              3. 🔍 Load records
              4. Click record to view detail / ▶ to replay
            
            Condition examples (OGNL):
              #cost>1000                      // execution time > 1000ms
              params[0]=='test'               // first param equals 'test'
              params[0].userId>1000           // param field value
              params[0].name.contains('abc')  // param contains string
              returnObj!=null                 // return value not null
              returnObj.success==false        // return field value
              returnObj.size()>0              // return collection not empty
              throwExp!=null                  // has exception
            
            Tips:
              • Stop recording when done (saves JVM memory)
              • Replay re-executes with original params (may have side effects)
            """;

    /**
     * Create right panel (Detail / Replay Result) - JSON editor with syntax highlighting
     */
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Detail (JSON)",
                TitledBorder.LEFT, TitledBorder.TOP));

        // Create JSON editor with syntax highlighting
        jsonEditor = new LanguageTextField(JsonLanguage.INSTANCE, project, USAGE_GUIDE, false) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                editor.setVerticalScrollbarVisible(true);
                editor.setHorizontalScrollbarVisible(true);
                editor.getSettings().setUseSoftWraps(true);
                editor.getSettings().setLineNumbersShown(true);
                editor.getSettings().setFoldingOutlineShown(true);
                return editor;
            }
        };
        jsonEditor.setOneLineMode(false);
        
        panel.add(jsonEditor, BorderLayout.CENTER);

        return panel;
    }

    // ==================== Event Handlers ====================

    private void onAppChanged() {
        String selectedApp = (String) appBox.getSelectedItem();
        if (selectedApp == null) return;

        Set<String> partitions = arthasApps.stream()
                .filter(app -> selectedApp.equals(app.getAppName()))
                .map(RuntimeHelper.VisibleApp::getRemotePartition)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        partitionBox.removeAllItems();
        partitions.forEach(partitionBox::addItem);
    }

    private void onPartitionChanged() {
        String selectedApp = (String) appBox.getSelectedItem();
        String selectedPartition = (String) partitionBox.getSelectedItem();
        
        if (selectedApp == null || selectedPartition == null) {
            // No app/partition selected, clear instances and show hint
            currentInstances = new ArrayList<>();
        } else {
            // Update currentInstances
            currentInstances = arthasApps.stream()
                    .filter(app -> selectedApp.equals(app.getAppName()) 
                            && selectedPartition.equals(app.getRemotePartition()))
                    .collect(Collectors.toList());
        }
        
        // Rebuild instance panels (will show hint if empty)
        rebuildInstancePanels();
    }

    private void initializeInstanceList() {
        onPartitionChanged();
    }

    /**
     * Refresh arthas apps list
     */
    private void refreshArthasApps() {
        arthasApps = ArthasLineMarkerProvider.getRemoteScriptApps(project.getName());
        
        String selectedApp = (String) appBox.getSelectedItem();
        appBox.removeAllItems();
        Set<String> appNames = arthasApps.stream()
                .map(RuntimeHelper.VisibleApp::getAppName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        appNames.forEach(appBox::addItem);
        
        if (selectedApp != null && appNames.contains(selectedApp)) {
            appBox.setSelectedItem(selectedApp);
        }
        
        onAppChanged();
        onPartitionChanged();
    }

    // Empty state hint for instance list
    private static final String NO_INSTANCE_HINT = 
            "<html><center>No instances available<br><br>Click 📎 button to add</center></html>";

    /**
     * Rebuild instance panels (增量更新，保留已有 panel 的 state)
     */
    private void rebuildInstancePanels() {
        if (instanceListPanel == null) {
            return;
        }
        
        // 保存旧的 panel map，用于复用
        Map<String, InstancePanel> oldPanelMap = new LinkedHashMap<>(instancePanelMap);
        
        // 清空 UI 但复用已有的 panel
        instanceListPanel.removeAll();
        instancePanelMap.clear();
        
        if (currentInstances.isEmpty()) {
            // Show empty state hint - use wrapper panel for centering
            JPanel hintWrapper = new JPanel(new GridBagLayout());
            hintWrapper.setOpaque(false);
            JLabel hintLabel = new JLabel(NO_INSTANCE_HINT);
            hintLabel.setForeground(Color.GRAY);
            hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
            hintWrapper.add(hintLabel);
            instanceListPanel.add(hintWrapper);
        } else {
            for (RuntimeHelper.VisibleApp inst : currentInstances) {
                String connStr = inst.toConnectionString();
                // 复用已有的 panel（保留 state），或创建新的
                InstancePanel panel = oldPanelMap.get(connStr);
                if (panel == null) {
                    panel = new InstancePanel(inst);
                }
                instancePanelMap.put(connStr, panel);
                instanceListPanel.add(panel);
                instanceListPanel.add(Box.createVerticalStrut(5));
            }
        }
        
        // Add glue at the end to push panels to top
        instanceListPanel.add(Box.createVerticalGlue());
        
        instanceListPanel.revalidate();
        instanceListPanel.repaint();
    }

    // ==================== Batch Operations ====================

    private void startAllRecording() {
        for (InstancePanel panel : instancePanelMap.values()) {
            if (panel.state == InstanceState.READY) {
                panel.startRecording();
            }
        }
    }

    private void stopAllRecording() {
        for (InstancePanel panel : instancePanelMap.values()) {
            if (panel.state == InstanceState.RECORDING) {
                panel.stopRecording();
            }
        }
    }

    // ==================== Helper Methods ====================

    private String buildTtCommand() {
        String cls = classField.getText().trim();
        String method = methodField.getText().trim();
        String condition = conditionField.getText().trim();
        int maxRecords = (Integer) maxRecordsBox.getSelectedItem();

        StringBuilder command = new StringBuilder();
        command.append("tt -t ").append(cls).append(" ").append(method);
        command.append(" -n ").append(maxRecords);
        if (StringUtils.isNotBlank(condition)) {
            command.append(" '").append(condition).append("'");
        }
        return command.toString();
    }

    private void showDetail(String text) {
        jsonEditor.setText(text);
    }

    private void formatAndShowDetail(TtRecord record, String instanceIp, Object result) {
        String data = "";
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            Object dataObj = map.get("data");
            if (dataObj != null) {
                data = JSON.toJSONString(dataObj, true);
            } else {
                data = JSON.toJSONString(result, true);
            }
        } else if (result != null) {
            data = result.toString();
        }
        showDetail(data);
    }

    private void formatAndShowReplayResult(TtRecord record, String instanceIp, Object result) {
        String data = "";
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            Object dataObj = map.get("data");
            if (dataObj != null) {
                data = JSON.toJSONString(dataObj, true);
            } else {
                data = JSON.toJSONString(result, true);
            }
        } else if (result != null) {
            data = result.toString();
        }
        showDetail(data);
    }

    @Override
    protected Action [] createActions() {
        // Remove OK button
        return new Action[0];
    }

    @Override
    protected void doOKAction() {
        // Stop all recording before closing
        stopAllRecording();
        super.doOKAction();
    }

    // ==================== Inner Class: InstancePanel ====================

    /**
     * Panel for a single instance with controls and records
     */
    private class InstancePanel extends JPanel {
        
        private final RuntimeHelper.VisibleApp instance;
        private InstanceState state = InstanceState.READY;
        private List<TtRecord> records = new ArrayList<>();
        private boolean expanded = true;  // Default expanded
        
        // UI Components
        private JLabel statusLabel;
        private JButton actionBtn;
        private JButton loadBtn;
        private JButton clearBtn;
        private JButton expandBtn;
        private JPanel recordsPanel;

        InstancePanel(RuntimeHelper.VisibleApp instance) {
            this.instance = instance;
            // 从 ConnectionMeta 恢复状态
            String cachedState = RuntimeHelper.getTtState(instance.toConnectionString(), getMethodKey());
            if (cachedState != null) {
                try {
                    this.state = InstanceState.valueOf(cachedState);
                } catch (IllegalArgumentException ignored) {
                    // 无效状态，使用默认值
                }
            }
            initUI();
        }
        
        private String getMethodKey() {
            return className + "#" + methodName;
        }

        private void initUI() {
            setLayout(new BorderLayout(2, 2));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 180)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            // Header: [▼] IP [Status] [✕] ... [Actions...]
            JPanel headerPanel = new JPanel();
            headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
            headerPanel.setOpaque(false);

            // Expand/Collapse button (icon)
            expandBtn = createIconButton(AllIcons.General.ArrowDown, "Collapse records");
            expandBtn.addActionListener(e -> toggleExpand());
            headerPanel.add(expandBtn);

            // IP Label
            JLabel ipLabel = new JLabel(instance.getRemoteIp());
            ipLabel.setFont(ipLabel.getFont().deriveFont(Font.BOLD, 12f));
            ipLabel.setIcon(AllIcons.Webreferences.Server);
            headerPanel.add(ipLabel);

            headerPanel.add(Box.createHorizontalStrut(4));

            // Status
            statusLabel = new JLabel("[" + state.label + "]");
            statusLabel.setForeground(state.color);
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));
            headerPanel.add(statusLabel);

            headerPanel.add(Box.createHorizontalGlue());

            // Action buttons (icons only)
            actionBtn = createIconButton(AllIcons.Actions.Execute, "Start recording");
            actionBtn.addActionListener(e -> onActionClick());
            headerPanel.add(actionBtn);

            loadBtn = createIconButton(AllIcons.Actions.Search, "Load records (tt -l)");
            loadBtn.addActionListener(e -> loadRecords());
            headerPanel.add(loadBtn);

            clearBtn = createIconButton(CLEAR_ICON, "Clear records");
            clearBtn.addActionListener(e -> clearRecords());
            headerPanel.add(clearBtn);

            add(headerPanel, BorderLayout.NORTH);

            // Records list (tree-like)
            recordsPanel = new JPanel();
            recordsPanel.setLayout(new BoxLayout(recordsPanel, BoxLayout.Y_AXIS));
            recordsPanel.setOpaque(false);
            recordsPanel.setBorder(BorderFactory.createEmptyBorder(2, 20, 0, 0));
            
            updateRecordsPanel();
            add(recordsPanel, BorderLayout.CENTER);
            
            // 同步按钮状态（从缓存恢复时需要）
            syncButtonState();
        }
        
        private void syncButtonState() {
            switch (state) {
                case READY:
                    actionBtn.setIcon(AllIcons.Actions.Execute);
                    actionBtn.setToolTipText("Start recording");
                    actionBtn.setEnabled(true);
                    loadBtn.setEnabled(true);
                    break;
                case RECORDING:
                    actionBtn.setIcon(AllIcons.Actions.Suspend);
                    actionBtn.setToolTipText("Stop recording");
                    actionBtn.setEnabled(true);
                    loadBtn.setEnabled(true);
                    break;
                case LOADING:
                    actionBtn.setEnabled(false);
                    loadBtn.setEnabled(false);
                    break;
                case STOPPING:
                    actionBtn.setEnabled(false);
                    loadBtn.setEnabled(true);
                    break;
            }
        }

        private JButton createIconButton(Icon icon, String tooltip) {
            JButton btn = new JButton(icon);
            btn.setToolTipText(tooltip);
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setBorder(BorderFactory.createEmptyBorder());
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Force fixed size
            Dimension size = new Dimension(18, 18);
            btn.setPreferredSize(size);
            btn.setMinimumSize(size);
            btn.setMaximumSize(size);
            return btn;
        }

        private void toggleExpand() {
            expanded = !expanded;
            expandBtn.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
            expandBtn.setToolTipText(expanded ? "Collapse records" : "Expand records");
            recordsPanel.setVisible(expanded);
            
            // Trigger layout update
            revalidate();
            repaint();
            
            // Update parent scroll pane
            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
        }

        private void updateState(InstanceState newState) {
            this.state = newState;
            statusLabel.setText("[" + newState.label + "]");
            statusLabel.setForeground(newState.color);
            
            // 保存状态到 ConnectionMeta（只缓存 READY 和 RECORDING 状态，中间状态不缓存）
            if (newState == InstanceState.READY || newState == InstanceState.RECORDING) {
                RuntimeHelper.setTtState(instance.toConnectionString(), getMethodKey(), newState.name());
            }
            
            // Update action button
            switch (newState) {
                case READY:
                    actionBtn.setIcon(AllIcons.Actions.Execute);
                    actionBtn.setToolTipText("Start recording");
                    actionBtn.setEnabled(true);
                    loadBtn.setEnabled(true);
                    break;
                case RECORDING:
                    actionBtn.setIcon(AllIcons.Actions.Suspend);
                    actionBtn.setToolTipText("Stop recording");
                    actionBtn.setEnabled(true);
                    loadBtn.setEnabled(true);
                    break;
                case LOADING:
                    actionBtn.setEnabled(false);
                    loadBtn.setEnabled(false);
                    break;
                case STOPPING:
                    actionBtn.setEnabled(false);
                    loadBtn.setEnabled(true);
                    break;
            }
        }

        private void onActionClick() {
            if (state == InstanceState.READY) {
                startRecording();
            } else if (state == InstanceState.RECORDING) {
                stopRecording();
            }
        }

        void startRecording() {
            String command = buildTtCommand();
            updateState(InstanceState.LOADING);
            
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Start Recording: " + instance.getRemoteIp(), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Starting tt recording...");
                    
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isBlank(scriptPath)) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateState(InstanceState.READY);
                            TestkitHelper.notify(project, NotificationType.ERROR, "Remote script not configured");
                        });
                        return;
                    }

                    try {
                        RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                        String connStr = instance.toConnectionString();
                        Integer arthasPort = RuntimeHelper.getArthasPort(connStr);
                        
                        if (arthasPort == null) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                updateState(InstanceState.READY);
                                TestkitHelper.notify(project, NotificationType.ERROR, 
                                        "Arthas port not found for " + instance.getRemoteIp());
                            });
                            return;
                        }

                        Map<String, Object> params = new HashMap<>();
                        params.put("command", command);
                        executor.sendArthasRequest(
                                instance.getAppName(),
                                instance.getRemotePartition(),
                                instance.getRemoteIp(),
                                arthasPort,
                                params,
                                RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                        );

                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateState(InstanceState.RECORDING);
                        });

                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateState(InstanceState.READY);
                            TestkitHelper.notify(project, NotificationType.ERROR, 
                                    "Start recording failed on " + instance.getRemoteIp() + ": " + e.getMessage());
                        });
                    }
                }
            });
        }

        void stopRecording() {
            updateState(InstanceState.STOPPING);
            
            // 构建 reset 命令：reset className methodName
            String cls = classField.getText().trim();
            String method = methodField.getText().trim();
            String resetCommand = "reset " + cls + " " + method;
            
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Stop Recording: " + instance.getRemoteIp(), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Stopping tt recording...");
                    
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isBlank(scriptPath)) {
                        ApplicationManager.getApplication().invokeLater(() -> updateState(InstanceState.READY));
                        return;
                    }

                    try {
                        RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                        Integer arthasPort = RuntimeHelper.getArthasPort(instance.toConnectionString());
                        
                        if (arthasPort != null) {
                            Map<String, Object> params = new HashMap<>();
                            params.put("command", resetCommand);
                            executor.sendArthasRequest(
                                    instance.getAppName(),
                                    instance.getRemotePartition(),
                                    instance.getRemoteIp(),
                                    arthasPort,
                                    params,
                                    RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                            );
                        }

                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateState(InstanceState.READY);
                        });

                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateState(InstanceState.READY);
                        });
                    }
                }
            });
        }

        void loadRecords() {
            loadBtn.setIcon(AllIcons.Process.Step_1);
            loadBtn.setEnabled(false);
            
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Load Records: " + instance.getRemoteIp(), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Loading tt records...");
                    
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isBlank(scriptPath)) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            loadBtn.setIcon(AllIcons.Actions.Refresh);
                            loadBtn.setEnabled(true);
                            TestkitHelper.notify(project, NotificationType.ERROR, "Remote script not configured");
                        });
                        return;
                    }

                    try {
                        RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                        String connStr = instance.toConnectionString();
                        Integer arthasPort = RuntimeHelper.getArthasPort(connStr);
                        
                        if (arthasPort == null) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                loadBtn.setIcon(AllIcons.Actions.Refresh);
                                loadBtn.setEnabled(true);
                                TestkitHelper.notify(project, NotificationType.ERROR, 
                                        "Arthas port not found for " + instance.getRemoteIp());
                            });
                            return;
                        }

                        Map<String, Object> params = new HashMap<>();
                        // Note: tt -l doesn't support -n, it lists all records in memory
                        // The -n param only works with tt -t (start recording)
                        params.put("command", "tt -l");
                        
                        Object result = executor.sendArthasRequest(
                                instance.getAppName(),
                                instance.getRemotePartition(),
                                instance.getRemoteIp(),
                                arthasPort,
                                params,
                                RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                        );
                        
                        List<TtRecord> newRecords = TtRecord.parse(result, instance.getRemoteIp());
                        newRecords.sort(Comparator.comparing(TtRecord::getTimestamp).reversed());

                        ApplicationManager.getApplication().invokeLater(() -> {
                            records = newRecords;
                            updateRecordsPanel();
                            loadBtn.setIcon(AllIcons.Actions.Checked);
                            loadBtn.setToolTipText("Loaded " + records.size() + " records");
                            loadBtn.setEnabled(true);
                            
                            // Reset icon after 2 seconds
                            Timer timer = new Timer(2000, e -> {
                                loadBtn.setIcon(AllIcons.Actions.Refresh);
                                loadBtn.setToolTipText("Load records (tt -l)");
                            });
                            timer.setRepeats(false);
                            timer.start();
                        });

                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            loadBtn.setIcon(AllIcons.General.Error);
                            loadBtn.setToolTipText("Load failed: " + e.getMessage());
                            loadBtn.setEnabled(true);
                            
                            // Notify user
                            TestkitHelper.notify(project, NotificationType.ERROR, 
                                    "Load records failed on " + instance.getRemoteIp() + ": " + e.getMessage());
                            
                            Timer timer = new Timer(2000, ev -> {
                                loadBtn.setIcon(AllIcons.Actions.Refresh);
                                loadBtn.setToolTipText("Load records (tt -l)");
                            });
                            timer.setRepeats(false);
                            timer.start();
                        });
                    }
                }
            });
        }

        void clearRecords() {
            // Confirm before clear
            int confirm = JOptionPane.showConfirmDialog(
                    TimeTunnelDialog.this.getContentPane(),
                    "Clear all records on " + instance.getRemoteIp() + "?\nThis will execute 'tt --delete-all' on remote server.",
                    "Confirm Clear",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            
            clearBtn.setEnabled(false);
            clearBtn.setToolTipText("Clearing...");
            
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Clear Records: " + instance.getRemoteIp(), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Clearing tt records...");
                    
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isBlank(scriptPath)) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            clearBtn.setEnabled(true);
                            clearBtn.setToolTipText("Clear records");
                            TestkitHelper.notify(project, NotificationType.ERROR, "Remote script not configured");
                        });
                        return;
                    }

                    try {
                        RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                        Integer arthasPort = RuntimeHelper.getArthasPort(instance.toConnectionString());
                        if (arthasPort == null) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                clearBtn.setEnabled(true);
                                clearBtn.setToolTipText("Clear records");
                                TestkitHelper.notify(project, NotificationType.ERROR, 
                                        "Arthas port not found for " + instance.getRemoteIp());
                            });
                            return;
                        }

                        Map<String, Object> params = new HashMap<>();
                        params.put("command", "tt --delete-all");
                        
                        executor.sendArthasRequest(
                                instance.getAppName(),
                                instance.getRemotePartition(),
                                instance.getRemoteIp(),
                                arthasPort,
                                params,
                                RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                        );

                        ApplicationManager.getApplication().invokeLater(() -> {
                            records.clear();
                            updateRecordsPanel();
                            clearBtn.setIcon(AllIcons.Actions.Checked);
                            clearBtn.setToolTipText("Cleared");
                            clearBtn.setEnabled(true);
                            
                            // Reset icon after 2 seconds
                            Timer timer = new Timer(2000, e -> {
                                clearBtn.setIcon(CLEAR_ICON);
                                clearBtn.setToolTipText("Clear records");
                            });
                            timer.setRepeats(false);
                            timer.start();
                        });

                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            clearBtn.setIcon(AllIcons.General.Error);
                            clearBtn.setToolTipText("Clear failed: " + e.getMessage());
                            clearBtn.setEnabled(true);
                            
                            TestkitHelper.notify(project, NotificationType.ERROR, 
                                    "Clear records failed on " + instance.getRemoteIp() + ": " + e.getMessage());
                            
                            Timer timer = new Timer(2000, ev -> {
                                clearBtn.setIcon(CLEAR_ICON);
                                clearBtn.setToolTipText("Clear records");
                            });
                            timer.setRepeats(false);
                            timer.start();
                        });
                    }
                }
            });
        }

        private void updateRecordsPanel() {
            recordsPanel.removeAll();
            
            if (records.isEmpty()) {
                JLabel emptyLabel = new JLabel("└─ (no records)");
                emptyLabel.setForeground(Color.GRAY);
                emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 11f));
                recordsPanel.add(emptyLabel);
            } else {
                for (int i = 0; i < records.size(); i++) {
                    TtRecord record = records.get(i);
                    boolean isLast = (i == records.size() - 1);
                    JPanel recordRow = createRecordRow(record, isLast);
                    recordsPanel.add(recordRow);
                }
            }
            
            recordsPanel.revalidate();
            recordsPanel.repaint();
            
            // Adjust panel height
            revalidate();
            repaint();
        }

        private JPanel createRecordRow(TtRecord record, boolean isLast) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Tree branch symbol
            String branch = isLast ? "└─" : "├─";
            JLabel branchLabel = new JLabel(branch);
            branchLabel.setForeground(Color.GRAY);
            branchLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
            row.add(branchLabel);
            
            // Result icon
            String resultIcon = record.isSuccess() ? "✅" : "❌";
            
            // Format time: extract HH:mm:ss.SSS from full timestamp
            String time = record.getTime();
            if (time != null && time.length() > 12) {
                // Keep only HH:mm:ss.SSS (first 12 chars of time part)
                int dotIndex = time.indexOf('.');
                if (dotIndex > 0 && dotIndex + 4 <= time.length()) {
                    time = time.substring(0, dotIndex + 4); // HH:mm:ss.SSS
                }
            }
            
            // Record info (compact): #index HH:mm:ss.SSS cost icon
            String text = String.format("#%s %s %s %s", 
                    record.getIndex(), 
                    time, 
                    record.getCost(),
                    resultIcon);
            
            JLabel label = new JLabel(text);
            label.setFont(label.getFont().deriveFont(11f));
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            // Click to view detail
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1) {
                        viewRecordDetail(record);
                    }
                }
                
                @Override
                public void mouseEntered(MouseEvent e) {
                    label.setForeground(new Color(0, 100, 200));
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    label.setForeground(UIManager.getColor("Label.foreground"));
                }
            });
            row.add(label);
            
            // Replay button (icon style)
            JButton replayBtn = createIconButton(AllIcons.Actions.Execute, "Replay this request");
            replayBtn.addActionListener(e -> replayRecord(record));
            row.add(replayBtn);
            
            return row;
        }

        private void viewRecordDetail(TtRecord record) {
            showDetail("Loading details for #" + record.getIndex() + "...");
            int expandLevel = (Integer) expandLevelBox.getSelectedItem();

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                if (StringUtils.isBlank(scriptPath)) {
                    ApplicationManager.getApplication().invokeLater(() -> 
                            showDetail("Error: Remote script not configured"));
                    return;
                }

                try {
                    RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                    Map<String, Object> params = new HashMap<>();
                    params.put("command", "tt -i " + record.getIndex() + " -x " + expandLevel);
                    
                    Object result = executor.sendArthasRequest(
                            instance.getAppName(),
                            instance.getRemotePartition(),
                            instance.getRemoteIp(),
                            RuntimeHelper.getArthasPort(instance.toConnectionString()),
                            params,
                            RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                    );

                    ApplicationManager.getApplication().invokeLater(() -> 
                            formatAndShowDetail(record, instance.getRemoteIp(), result));

                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> 
                            showDetail("Error: Failed to get details - " + e.getMessage()));
                }
            });
        }

        private void replayRecord(TtRecord record) {
            // Confirm before replay
            int confirm = JOptionPane.showConfirmDialog(
                    TimeTunnelDialog.this.getContentPane(),
                    "Replay request #" + record.getIndex() + " on " + instance.getRemoteIp() + "?",
                    "Confirm Replay",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            
            showDetail("Replaying #" + record.getIndex() + " on " + instance.getRemoteIp() + "...");
            int expandLevel = (Integer) expandLevelBox.getSelectedItem();

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                if (StringUtils.isBlank(scriptPath)) {
                    ApplicationManager.getApplication().invokeLater(() -> 
                            showDetail("Error: Remote script not configured"));
                    return;
                }

                try {
                    RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                    Map<String, Object> params = new HashMap<>();
                    params.put("command", "tt -i " + record.getIndex() + " -p -x " + expandLevel + " --replay-times 1 --replay-interval 0");
                    
                    Object result = executor.sendArthasRequest(
                            instance.getAppName(),
                            instance.getRemotePartition(),
                            instance.getRemoteIp(),
                            RuntimeHelper.getArthasPort(instance.toConnectionString()),
                            params,
                            RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                    );

                    ApplicationManager.getApplication().invokeLater(() -> 
                            formatAndShowReplayResult(record, instance.getRemoteIp(), result));

                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> 
                            showDetail("Error: Replay failed - " + e.getMessage()));
                }
            });
        }
    }
}
