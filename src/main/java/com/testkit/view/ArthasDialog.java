package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.testkit.remote_script.RemoteScriptExecutor;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Arthas Command Execution Dialog
 */
public class ArthasDialog extends DialogWrapper {

    private final Project project;
    private Tree instanceTree;
    private DefaultTreeModel treeModel;
    private JBTextField commandField;
    private JTextArea resultArea;
    private JButton executeButton;
    
    // State preservation
    private static String lastCommand = "";
    private static String lastSelectedInstance = "";
    private static String lastResult = "";
    
    // Request cancellation - single button toggle (Execute ↔ Suspend)
    private Future<?> currentTask = null;
    private boolean isExecuting = false;
    private volatile boolean isCancelled = false;

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

        // Top: Command input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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

        panel.add(inputPanel, BorderLayout.NORTH);

        // Middle: Result display area
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Result",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // Restore last result or show default help text
        if (StringUtils.isNotBlank(lastResult)) {
            resultArea.setText(lastResult);
        } else {
            resultArea.setText("Select an instance and enter command...\n\n" +
                    "Common command examples:\n" +
                    "  jad com.example.MyClass              - Decompile class\n" +
                    "  tt -t com.example.Controller method  - Start TimeTunnel recording\n" +
                    "  tt -l                                - List TimeTunnel records\n" +
                    "  tt -i 1000                           - View record details\n" +
                    "  trace com.example.Service method -n 5 - Trace method calls\n" +
                    "  ognl '@com.example.App@field'        - Execute OGNL expression\n");
        }

        JBScrollPane scrollPane = new JBScrollPane(resultArea);
        resultPanel.add(scrollPane, BorderLayout.CENTER);

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
        panel.setBorder(BorderFactory.createTitledBorder("Quick Commands"));

        String[] quickCommands = {
                "jad *",
                "thread",
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
            if (resultArea != null) {
                resultArea.setText("⚠️ No Arthas-enabled instances found\n\n" +
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
            resultArea.setText("❌ Error: Please select an instance first");
            return;
        }

        String command = commandField.getText().trim();
        if (StringUtils.isBlank(command)) {
            resultArea.setText("❌ Error: Please enter a command");
            return;
        }

        String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
        if (StringUtils.isBlank(scriptPath)) {
            resultArea.setText("❌ Error: Remote script not configured");
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
        resultArea.setText("⏳ Executing command: " + command + "\n\nPlease wait...");

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
                        appName, partition, ip, arthasPort, params, 30
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
                        resultArea.setText("⚠️ Command cancelled");
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
        resultArea.setText("⚠️ Command cancelled");
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
            
            StringBuilder output = new StringBuilder();
            
            if (jsonResult != null && jsonResult.containsKey("success")) {
                // Parsed response format: {success, data, message}
                boolean success = jsonResult.getBooleanValue("success");
                String message = jsonResult.getString("message");
                Object data = jsonResult.get("data");
                
                // Simplified format
                if (success) {
                    output.append("✅ Command: ").append(command).append("\n");
                } else {
                    output.append("❌ Command: ").append(command).append("\n");
                }
                
                output.append("Instance: ").append(selectedApp.toConnectionString()).append("\n");
                
                if (StringUtils.isNotBlank(message)) {
                    output.append("Message: ").append(message).append("\n");
                }
                
                // Only show Result section if data exists and success is true
                if (success && data != null) {
                    output.append("\nResult:\n");
                    output.append("----------------------------------------\n");
                    
                    if (data instanceof String) {
                        output.append(data);
                    } else {
                        output.append(JSON.toJSONString(data, true));
                    }
                    
                    output.append("\n----------------------------------------\n");
                }
            } else {
                // Raw response (no standard format)
                output.append("✅ Command: ").append(command).append("\n");
                output.append("Instance: ").append(selectedApp.toConnectionString()).append("\n\n");
                output.append("Result:\n");
                output.append("----------------------------------------\n");
                
                String resultStr = result != null ? result.toString() : "null";
                output.append(resultStr);
                
                output.append("\n----------------------------------------\n");
            }
            
            String resultText = output.toString();
            resultArea.setText(resultText);
            lastResult = resultText; // Save for next open
            
        } catch (Exception e) {
            // Fallback to simple display
            String resultText = "✅ Command: " + command + "\n" +
                    "Instance: " + selectedApp.toConnectionString() + "\n\n" +
                    "Result:\n" +
                    "----------------------------------------\n" +
                    (result != null ? result.toString() : "null") + "\n" +
                    "----------------------------------------\n";
            resultArea.setText(resultText);
            lastResult = resultText; // Save for next open
        }
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            resultArea.setText("❌ Error\n\n" + message);
            resetExecuteButton();
            TestkitHelper.notify(project, NotificationType.ERROR, message);
        });
    }

    @Override
    protected Action @Nullable [] createActions() {
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
                    // Instance node: show [partition] ip
                    String displayText = String.format("[%s] %s",
                            app.getRemotePartition(),
                            app.getRemoteIp());
                    setText(displayText);
                    setIcon(null);  // No icon for instance nodes
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
