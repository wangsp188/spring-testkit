package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.treeStructure.Tree;
import com.testkit.remote_script.RemoteScriptExecutor;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.testkit.remote_script.RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT;

/**
 * Arthas Command Execution Dialog
 */
public class ArthasDialog extends DialogWrapper {

    private final Project project;
    private Tree instanceTree;
    private DefaultTreeModel treeModel;
    private JBTextField commandField;
    private LanguageTextField resultEditor;
    private JButton executeButton;
    
    // State preservation
    private static String lastCommand = "";
    private static String lastSelectedInstance = "";
    private static String lastResult = "";
    
    // Request cancellation - single button toggle (Execute ↔ Suspend)
    private Future<?> currentTask = null;
    private boolean isExecuting = false;
    private volatile boolean isCancelled = false;
    
    // ===== Profiler 相关字段 =====
    private ComboBox<String> durationBox;           // 时长选择: 10s, 30s, 60s, 120s
    private JButton profilerButton;                  // Start/Cancel 按钮
    private JButton openLastButton;                  // 打开上次结果
    private JLabel profilerStatusLabel;              // 状态显示
    private JProgressBar profilerProgressBar;        // 进度条
    
    // Profiler 状态
    private enum ProfilerState { READY, PROFILING, DOWNLOADING }
    private ProfilerState profilerState = ProfilerState.READY;
    private Future<?> profilerTask = null;
    private volatile boolean profilerCancelled = false;
    private String currentProfilerFilePath = null;   // 当前正在采样的文件路径
    
    // Profiler 文件目录（远程和本地使用相同路径）
    private static final String PROFILER_DIR = "/tmp/profiler";

    public ArthasDialog(Project project) {
        super(project);
        this.project = project;
        setTitle("Arthas Diagnostic Tool");
        setModal(false);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setPreferredSize(new Dimension(1000, 600));

        // 左侧：实例列表
        JPanel leftPanel = createInstanceListPanel();
        leftPanel.setPreferredSize(new Dimension(250, 600));
        mainPanel.add(leftPanel, BorderLayout.WEST);

        // 右侧：命令执行面板
        JPanel rightPanel = createCommandPanel();
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * Create instance list panel (two-level tree: appName -> instances)
     */
    private JPanel createInstanceListPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Instances",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));

        // Create tree with root node
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(root);
        instanceTree = new Tree(treeModel);
        instanceTree.setRootVisible(false);
        instanceTree.setShowsRootHandles(true);
        instanceTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        // Custom cell renderer for formatted display
        instanceTree.setCellRenderer(new InstanceTreeCellRenderer());

        JBScrollPane scrollPane = new JBScrollPane(instanceTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Load instance tree
        refreshInstanceList();
        
        // Restore last selected instance
        restoreLastSelection();

        return panel;
    }

    /**
     * Create command execution panel
     */
    private JPanel createCommandPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // ===== Top: Profiler + Command 组合面板 =====
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        // Profiler 面板
        JPanel profilerPanel = createProfilerPanel();
        topPanel.add(profilerPanel, BorderLayout.NORTH);
        
        // Command input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder(">_ Command"));

        commandField = new JBTextField();
        commandField.setToolTipText("Enter Arthas command, e.g.: jad com.example.MyClass");
        commandField.addActionListener(e -> executeCommand()); // Support Enter key
        
        // Restore last command
        commandField.setText(lastCommand);

        // Single button: Execute ↔ Suspend (like function-call)
        executeButton = new JButton(AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute command");
        executeButton.setPreferredSize(new Dimension(32, 32));
        executeButton.addActionListener(e -> handleExecuteOrCancel());

        inputPanel.add(new JLabel("Command:"), BorderLayout.WEST);
        inputPanel.add(commandField, BorderLayout.CENTER);
        inputPanel.add(executeButton, BorderLayout.EAST);

        topPanel.add(inputPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.NORTH);

        // Middle: Result display area
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Result",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));

        String defaultText = "Select an instance and enter command...\n\n" +
                "Common command examples:\n" +
                "  jad com.example.MyClass                - Decompile class\n" +
                "  sc -d com.example.MyClass              - Show class details\n" +
                "  sm com.example.MyClass                 - Show methods of class\n" +
                "  getstatic com.example.App INSTANCE     - Get static field value\n" +
                "  ognl '@com.example.App@field'          - Execute OGNL expression\n" +
                "  logger --name ROOT --level debug       - Change logger level\n" +
                "  classloader -t                         - Show classloader tree\n";
        
        String initialText = StringUtils.isNotBlank(lastResult) ? lastResult : defaultText;
        
        resultEditor = new LanguageTextField(JsonLanguage.INSTANCE, project, initialText, false) {
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
        resultEditor.setOneLineMode(false);
        
        resultPanel.add(resultEditor, BorderLayout.CENTER);

        panel.add(resultPanel, BorderLayout.CENTER);

        // Bottom: Quick command buttons
        JPanel quickCommandPanel = createQuickCommandPanel();
        panel.add(quickCommandPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Create quick command panel
     */
    private JPanel createQuickCommandPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("⌨ Quick Commands"));

        String[] quickCommands = {
                "jad *",
                "thread -n 5",
                "thread -b",
                "jvm",
                "sysprop",
                "sysenv"
        };

        for (String cmd : quickCommands) {
            JButton btn = new JButton(cmd);
            btn.addActionListener(e -> {
                commandField.setText(cmd);
                commandField.requestFocus();
            });
            panel.add(btn);
        }

        return panel;
    }
    
    // ===== Profiler 相关方法 =====
    
    /**
     * Create Profiler panel
     */
    private JPanel createProfilerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 3));
        panel.setBorder(BorderFactory.createTitledBorder("🔥Profiler"));

        // 上排：火焰图图标 + Duration + Start + View
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        // 添加火焰图图标
        JLabel flameIcon = new JLabel(AllIcons.Actions.ProfileCPU);
        flameIcon.setToolTipText("CPU Profiler - Generate flame graph");
        controlPanel.add(flameIcon);

        controlPanel.add(new JLabel("Duration:"));
        durationBox = new ComboBox<>(new String[]{"10s", "30s", "60s", "120s","300s"});
        durationBox.setSelectedItem("60s");
        durationBox.setPreferredSize(new Dimension(70, 26));
        controlPanel.add(durationBox);
        
        profilerButton = new JButton("Start", AllIcons.Actions.Execute);
        profilerButton.setToolTipText("Start CPU profiling");
        profilerButton.addActionListener(e -> handleProfilerAction());
        controlPanel.add(profilerButton);
        
        openLastButton = new JButton("View", AllIcons.Actions.Show);
        openLastButton.setToolTipText("Open the latest flame graph for selected pod");
        openLastButton.setEnabled(false);
        openLastButton.addActionListener(e -> openLastFlameGraph());
        controlPanel.add(openLastButton);
        
        panel.add(controlPanel, BorderLayout.NORTH);

        // 下排：Status + Progress
        JPanel statusPanel = new JPanel(new BorderLayout(5, 2));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        
        profilerStatusLabel = new JLabel("Status: Ready");
        profilerStatusLabel.setFont(profilerStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        profilerStatusLabel.setForeground(Color.GRAY);
        statusPanel.add(profilerStatusLabel, BorderLayout.NORTH);
        
        profilerProgressBar = new JProgressBar(0, 100);
        profilerProgressBar.setVisible(false);
        profilerProgressBar.setStringPainted(true);
        profilerProgressBar.setPreferredSize(new Dimension(100, 16));
        statusPanel.add(profilerProgressBar, BorderLayout.CENTER);
        
        panel.add(statusPanel, BorderLayout.CENTER);

        return panel;
    }
    
    /**
     * Handle profiler button action (Start or Cancel)
     */
    private void handleProfilerAction() {
        if (profilerState == ProfilerState.READY) {
            startProfiler();
        } else {
            cancelProfiler();
        }
    }
    
    /**
     * Start profiler
     */
    private void startProfiler() {
        RuntimeHelper.VisibleApp selectedApp = getSelectedInstance();
        if (selectedApp == null) {
            profilerStatusLabel.setText("Status: ❌ Please select an instance");
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            return;
        }

        String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
        if (StringUtils.isBlank(scriptPath)) {
            profilerStatusLabel.setText("Status: ❌ Remote script not configured");
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            return;
        }

        // 解析 duration
        String durationStr = (String) durationBox.getSelectedItem();
        int duration = Integer.parseInt(durationStr.replace("s", ""));
        
        // 生成文件路径
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String instanceId = selectedApp.getRemoteIp();  // pod name
        String fileName = instanceId + "_" + timestamp + ".html";
        String remoteFilePath = "/tmp/" + fileName;           // 远程直接放 /tmp/
        currentProfilerFilePath = PROFILER_DIR + "/" + fileName;  // 本地放 /tmp/profiler/
        
        // 确保本地目录存在
        new File(PROFILER_DIR).mkdirs();

        // 切换状态
        setProfilerState(ProfilerState.PROFILING);
        profilerCancelled = false;
        
        profilerTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                if (!executor.isValid()) {
                    showProfilerError("Remote script invalid: " + scriptPath);
                    return;
                }

                // 0. 先停止可能正在运行的 profiler（忽略错误）
                try {
                    Map<String, Object> stopParams = new HashMap<>();
                    stopParams.put("command", "profiler stop");
                    executor.sendArthasRequest(
                        selectedApp.getAppName(),
                        selectedApp.getRemotePartition(),
                        selectedApp.getRemoteIp(),
                        RuntimeHelper.getArthasPort(selectedApp.toConnectionString()),
                        stopParams, REMOTE_ARTHAS_TIMEOUT);
                } catch (Exception ignored) {
                    // 忽略 stop 失败（可能本来就没有运行）
                }
                
                // 1. 执行 profiler start（文件直接写到远程 /tmp/）
                String command = String.format(
                    "profiler start --duration %d --file %s --format html", 
                    duration, remoteFilePath);
                
                Map<String, Object> params = new HashMap<>();
                params.put("command", command);
                
                executor.sendArthasRequest(
                    selectedApp.getAppName(),
                    selectedApp.getRemotePartition(),
                    selectedApp.getRemoteIp(),
                    RuntimeHelper.getArthasPort(selectedApp.toConnectionString()),
                    params, REMOTE_ARTHAS_TIMEOUT);

                // 2. 倒计时等待
                for (int i = 0; i <= duration; i++) {
                    if (profilerCancelled || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    int progress = (i * 100) / duration;
                    int elapsed = i;
                    SwingUtilities.invokeLater(() -> {
                        profilerProgressBar.setValue(progress);
                        profilerStatusLabel.setText(
                            String.format("Status: ⏳ Profiling... %ds / %ds", elapsed, duration));
                        profilerStatusLabel.setForeground(new Color(100, 150, 200));
                    });
                    Thread.sleep(1000);
                }
                
                if (profilerCancelled) return;
                
                // 3. 额外等待几秒确保文件生成
                SwingUtilities.invokeLater(() -> {
                    profilerStatusLabel.setText("Status: ⏳ Waiting for file generation...");
                });
                Thread.sleep(3000);
                
                if (profilerCancelled) return;
                
                // 4. 下载文件
                SwingUtilities.invokeLater(() -> {
                    setProfilerState(ProfilerState.DOWNLOADING);
                    profilerStatusLabel.setText("Status: ⏳ Downloading ...");
                });
                
                Map<String, Object> fileParams = new HashMap<>();
                fileParams.put("action", "download");
                fileParams.put("remotePath", remoteFilePath);          // 远程 /tmp/xxx.html
                fileParams.put("localPath", currentProfilerFilePath);  // 本地 /tmp/profiler/xxx.html
                
                executor.sendFileRequest(
                    selectedApp.getAppName(),
                    selectedApp.getRemotePartition(),
                    selectedApp.getRemoteIp(),
                    fileParams);
                
                // 5. 完成
                SwingUtilities.invokeLater(() -> {
                    setProfilerState(ProfilerState.READY);
                    profilerStatusLabel.setText("Status: ✅ Done! Click 'View' to open");
                    profilerStatusLabel.setForeground(new Color(100, 150, 100));
                    openLastButton.setEnabled(true);
                    highlightButton(openLastButton);
                });
                
            } catch (Exception e) {
                if (!profilerCancelled && !Thread.currentThread().isInterrupted()) {
                    showProfilerError("Profiler failed: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Cancel profiler
     */
    private void cancelProfiler() {
        profilerCancelled = true;
        if (profilerTask != null && !profilerTask.isDone()) {
            profilerTask.cancel(true);
            profilerTask = null;
        }
        
        // 发送 profiler stop 命令到远程
        RuntimeHelper.VisibleApp selectedApp = getSelectedInstance();
        if (selectedApp != null) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    RemoteScriptExecutor executor = new RemoteScriptExecutor(
                        SettingsStorageHelper.getRemoteScriptPath(project));
                    if (executor.isValid()) {
                        Map<String, Object> stopParams = new HashMap<>();
                        stopParams.put("command", "profiler stop");
                        executor.sendArthasRequest(
                            selectedApp.getAppName(),
                            selectedApp.getRemotePartition(),
                            selectedApp.getRemoteIp(),
                            RuntimeHelper.getArthasPort(selectedApp.toConnectionString()),
                            stopParams, REMOTE_ARTHAS_TIMEOUT);
                    }
                } catch (Exception ignored) {
                    // 忽略 stop 失败
                }
            });
        }
        
        setProfilerState(ProfilerState.READY);
        profilerStatusLabel.setText("Status: ⚠️ Cancelled");
        profilerStatusLabel.setForeground(new Color(200, 150, 50));
    }
    
    /**
     * Set profiler state and update UI
     */
    private void setProfilerState(ProfilerState state) {
        this.profilerState = state;
        SwingUtilities.invokeLater(() -> {
            switch (state) {
                case READY:
                    profilerButton.setText("Start");
                    profilerButton.setIcon(AllIcons.Actions.Execute);
                    profilerButton.setToolTipText("Start CPU profiling");
                    profilerProgressBar.setVisible(false);
                    durationBox.setEnabled(true);
                    break;
                case PROFILING:
                    profilerButton.setText("Cancel");
                    profilerButton.setIcon(AllIcons.Actions.Suspend);
                    profilerButton.setToolTipText("Cancel profiling");
                    profilerProgressBar.setVisible(true);
                    profilerProgressBar.setValue(0);
                    durationBox.setEnabled(false);
                    break;
                case DOWNLOADING:
                    profilerButton.setText("Cancel");
                    profilerButton.setIcon(AllIcons.Actions.Suspend);
                    profilerButton.setToolTipText("Cancel download");
                    profilerProgressBar.setVisible(false);
                    break;
            }
        });
    }
    
    /**
     * Show profiler error
     */
    private void showProfilerError(String message) {
        SwingUtilities.invokeLater(() -> {
            setProfilerState(ProfilerState.READY);
            profilerStatusLabel.setText("Status: ❌ " + message);
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            TestkitHelper.notify(project, NotificationType.ERROR, message);
        });
    }
    
    /**
     * Highlight button temporarily
     */
    private void highlightButton(JButton button) {
        Color original = button.getBackground();
        button.setBackground(new Color(100, 200, 100));
        
        Timer timer = new Timer(2000, e -> button.setBackground(original));
        timer.setRepeats(false);
        timer.start();
    }
    
    /**
     * Open last generated flame graph
     */
    private void openLastFlameGraph() {
        // 获取当前选中的 pod
        RuntimeHelper.VisibleApp selectedApp = getSelectedInstance();
        if (selectedApp == null) {
            profilerStatusLabel.setText("Status: ⚠️ Please select an instance first");
            profilerStatusLabel.setForeground(new Color(200, 150, 50));
            return;
        }
        
        String podName = selectedApp.getRemoteIp();
        
        // 在目录中查找该 pod 的最新火焰图
        File profilerDir = new File(PROFILER_DIR);
        if (!profilerDir.exists() || !profilerDir.isDirectory()) {
            profilerStatusLabel.setText("Status: ❌ No flame graph found for " + podName);
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            return;
        }
        
        // 过滤出该 pod 的火焰图文件，按修改时间倒序
        File[] matchedFiles = profilerDir.listFiles((dir, name) -> 
                name.startsWith(podName + "_") && name.endsWith(".html"));
        
        if (matchedFiles == null || matchedFiles.length == 0) {
            profilerStatusLabel.setText("Status: ❌ No flame graph found for " + podName);
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            return;
        }
        
        // 按修改时间倒序排序，取最新的
        File latestFile = Arrays.stream(matchedFiles)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
        
        if (latestFile == null || !latestFile.exists()) {
            profilerStatusLabel.setText("Status: ❌ No flame graph found for " + podName);
            profilerStatusLabel.setForeground(new Color(200, 100, 100));
            return;
        }
        
        openFlameGraph(latestFile.getAbsolutePath());
    }
    
    /**
     * Open flame graph in a new window
     */
    private void openFlameGraph(String filePath) {
        try {
            String html = Files.readString(Path.of(filePath));
            
            // 创建新窗口展示火焰图
            JFrame frame = new JFrame("🔥 Flame Graph - " + new File(filePath).getName());
            frame.setSize(1200, 800);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            
            JBCefBrowser browser = new JBCefBrowser();
            browser.loadHTML(html);
            frame.add(browser.getComponent());
            
            frame.setVisible(true);
            
            profilerStatusLabel.setText("Status: ✅ Opened: " + new File(filePath).getName());
            profilerStatusLabel.setForeground(new Color(100, 150, 100));
            
        } catch (Exception e) {
            // 备选：用系统浏览器打开
            try {
                Desktop.getDesktop().browse(new File(filePath).toURI());
                profilerStatusLabel.setText("Status: ✅ Opened in browser");
                profilerStatusLabel.setForeground(new Color(100, 150, 100));
            } catch (Exception ex) {
                profilerStatusLabel.setText("Status: ❌ Failed to open: " + e.getMessage());
                profilerStatusLabel.setForeground(new Color(200, 100, 100));
            }
        }
    }

    /**
     * Refresh instance list (build two-level tree)
     */
    private void refreshInstanceList() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        // Get all Arthas-enabled instances
        List<RuntimeHelper.VisibleApp> visibleApps = RuntimeHelper.getVisibleApps(project.getName());
        List<RuntimeHelper.VisibleApp> arthasEnabledApps = visibleApps.stream()
                .filter(app -> app.isRemoteInstance() && RuntimeHelper.isArthasEnabled(app.toConnectionString()))
                .collect(Collectors.toList());

        if (arthasEnabledApps.isEmpty()) {
            if (resultEditor != null) {
                resultEditor.setText("⚠️ No Arthas-enabled instances found\n\n" +
                        "Please ensure:\n" +
                        "1. Remote Script is configured\n" +
                        "2. loadInstances returns arthasPort field\n" +
                        "3. At least one instance has non-empty arthasPort");
            }
            treeModel.reload();
            return;
        }

        // Group by appName
        Map<String, List<RuntimeHelper.VisibleApp>> groupedApps = arthasEnabledApps.stream()
                .collect(Collectors.groupingBy(RuntimeHelper.VisibleApp::getAppName));

        // Build tree structure
        for (Map.Entry<String, List<RuntimeHelper.VisibleApp>> entry : groupedApps.entrySet()) {
            String appName = entry.getKey();
            List<RuntimeHelper.VisibleApp> instances = entry.getValue();

            DefaultMutableTreeNode appNode = new DefaultMutableTreeNode(appName);
            root.add(appNode);

            for (RuntimeHelper.VisibleApp instance : instances) {
                DefaultMutableTreeNode instanceNode = new DefaultMutableTreeNode(instance);
                appNode.add(instanceNode);
            }
        }

        treeModel.reload();

        // Expand all nodes
        for (int i = 0; i < instanceTree.getRowCount(); i++) {
            instanceTree.expandRow(i);
        }

        // Select first instance by default
        if (root.getChildCount() > 0) {
            DefaultMutableTreeNode firstApp = (DefaultMutableTreeNode) root.getChildAt(0);
            if (firstApp.getChildCount() > 0) {
                DefaultMutableTreeNode firstInstance = (DefaultMutableTreeNode) firstApp.getChildAt(0);
                TreePath path = new TreePath(treeModel.getPathToRoot(firstInstance));
                instanceTree.setSelectionPath(path);
            }
        }
    }
    
    /**
     * Restore last selected instance
     */
    private void restoreLastSelection() {
        if (StringUtils.isBlank(lastSelectedInstance)) {
            return;
        }
        
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode appNode = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < appNode.getChildCount(); j++) {
                DefaultMutableTreeNode instanceNode = (DefaultMutableTreeNode) appNode.getChildAt(j);
                Object userObject = instanceNode.getUserObject();
                if (userObject instanceof RuntimeHelper.VisibleApp) {
                    RuntimeHelper.VisibleApp app = (RuntimeHelper.VisibleApp) userObject;
                    if (app.toConnectionString().equals(lastSelectedInstance)) {
                        TreePath path = new TreePath(treeModel.getPathToRoot(instanceNode));
                        instanceTree.setSelectionPath(path);
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * Get selected instance from tree
     */
    private RuntimeHelper.VisibleApp getSelectedInstance() {
        TreePath selectionPath = instanceTree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        Object userObject = node.getUserObject();
        
        if (userObject instanceof RuntimeHelper.VisibleApp) {
            return (RuntimeHelper.VisibleApp) userObject;
        }
        
        return null;
    }

    /**
     * Handle execute or cancel based on current state (single button toggle)
     */
    private void handleExecuteOrCancel() {
        // If currently executing, cancel it
        if (isExecuting) {
            cancelCommand();
            return;
        }
        
        // Otherwise, execute command
        executeCommand();
    }
    
    /**
     * Execute command
     */
    private void executeCommand() {
        RuntimeHelper.VisibleApp selectedApp = getSelectedInstance();
        if (selectedApp == null) {
            resultEditor.setText("❌ Error: Please select an instance first");
            return;
        }

        String command = commandField.getText().trim();
        if (StringUtils.isBlank(command)) {
            resultEditor.setText("❌ Error: Please enter a command");
            return;
        }

        String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
        if (StringUtils.isBlank(scriptPath)) {
            resultEditor.setText("❌ Error: Remote script not configured");
            return;
        }
        
        // Save state
        lastCommand = command;
        lastSelectedInstance = selectedApp.toConnectionString();

        // Switch to Suspend icon (executing state)
        isExecuting = true;
        isCancelled = false;
        executeButton.setIcon(AllIcons.Actions.Suspend);
        executeButton.setToolTipText("Cancel command");
        resultEditor.setText("⏳ Executing command: " + command + "\n\nPlease wait...");

        // Execute in background
        currentTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                if (!executor.isValid()) {
                    showError("Remote script invalid: " + scriptPath);
                    return;
                }

                String appName = selectedApp.getAppName();
                String partition = selectedApp.getRemotePartition();
                String ip = selectedApp.getRemoteIp();
                Integer arthasPort = RuntimeHelper.getArthasPort(selectedApp.toConnectionString());

                // Build request parameters
                Map<String, Object> params = new HashMap<>();
                params.put("command", command);

                Object result = executor.sendArthasRequest(
                        appName, partition, ip, arthasPort, params, REMOTE_ARTHAS_TIMEOUT
                );

                // Display result
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (isExecuting) { // Only update if not cancelled
                        displayResult(command, selectedApp, result);
                    }
                    resetExecuteButton();
                });

            } catch (Exception e) {
                // Check if cancelled by user (either thread interrupted or isCancelled flag)
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        resultEditor.setText("⚠️ Command cancelled");
                        resetExecuteButton();
                    });
                } else {
                    showError("Command execution failed: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Cancel current executing command
     */
    private void cancelCommand() {
        isCancelled = true;  // Set flag first
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
            currentTask = null;
        }
        resultEditor.setText("⚠️ Command cancelled");
        resetExecuteButton();
    }
    
    /**
     * Reset execute button to normal state
     */
    private void resetExecuteButton() {
        isExecuting = false;
        currentTask = null;
        executeButton.setIcon(AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute command");
    }
    
    /**
     * Display result with proper formatting (parse success/data/message)
     */
    private void displayResult(String command, RuntimeHelper.VisibleApp selectedApp, Object result) {
        try {
            // Try to parse as JSON response
            JSONObject jsonResult = null;
            if (result instanceof Map) {
                jsonResult = new JSONObject((Map) result);
            } else if (result instanceof String) {
                try {
                    jsonResult = JSON.parseObject((String) result);
                } catch (Exception e) {
                    // Not JSON, display as plain text
                }
            }
            
            String resultText;
            
            if (jsonResult != null && jsonResult.containsKey("success")) {
                // Parsed response format: {success, data, message}
                boolean success = jsonResult.getBooleanValue("success");
                String message = jsonResult.getString("message");
                Object data = jsonResult.get("data");
                
                if (success && data != null) {
                    // 成功：直接显示 data
                    if (data instanceof String) {
                        resultText = (String) data;
                    } else {
                        resultText = JSON.toJSONString(data, true);
                    }
                } else if (!success && StringUtils.isNotBlank(message)) {
                    // 失败：显示 message
                    resultText = "❌ " + message;
                } else {
                    // 其他情况：显示完整 JSON
                    resultText = JSON.toJSONString(jsonResult, true);
                }
            } else {
                // Raw response (no standard format)
                resultText = result != null ? result.toString() : "null";
            }
            
            resultEditor.setText(resultText);
            lastResult = resultText;
            
        } catch (Exception e) {
            // Fallback to simple display
            String resultText = result != null ? result.toString() : "null";
            resultEditor.setText(resultText);
            lastResult = resultText;
        }
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            resultEditor.setText("❌ Error\n\n" + message);
            resetExecuteButton();
            TestkitHelper.notify(project, NotificationType.ERROR, message);
        });
    }

    @Override
    protected Action [] createActions() {
        // No buttons (ESC to close)
        return new Action[]{};
    }
    
    /**
     * Custom tree cell renderer for instance display
     */
    private static class InstanceTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                
                if (userObject instanceof RuntimeHelper.VisibleApp app) {
                    // Instance node: show [partition] ip with server icon
                    String displayText = String.format("[%s] %s",
                            app.getRemotePartition(),
                            app.getRemoteIp());
                    setText(displayText);
                    setIcon(AllIcons.Webreferences.Server);  // Server/machine icon for instance nodes
                } else if (userObject instanceof String appName) {
                    // App name node
                    setText(appName);
                    setIcon(AllIcons.Nodes.Module);
                }
            }
            
            return this;
        }
    }
}
