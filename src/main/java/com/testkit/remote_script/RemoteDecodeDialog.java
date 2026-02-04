package com.testkit.remote_script;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.HashMap;
import java.util.Map;

/**
 * Remote Code Dialog - 反编译展示 + 本地对比
 */
public class RemoteDecodeDialog extends DialogWrapper {

    private final Project project;
    private final String className;
    private final String methodName;
    private final RuntimeHelper.VisibleApp instance;

    // UI Components
    private JTextArea codeArea;
    private JButton refreshButton;
    private JButton copyButton;
    
    // State
    private String remoteCode = null;

    public RemoteDecodeDialog(Project project, String className, String methodName,
                              RuntimeHelper.VisibleApp instance) {
        super(project);
        this.project = project;
        this.className = className;
        this.methodName = methodName;
        this.instance = instance;
        
        // Title: Arthas jad - ClassName#MethodName or Arthas jad - ClassName
        String title = StringUtils.isNotBlank(methodName) 
                ? "Arthas jad - " + getSimpleClassName() + "#" + methodName
                : "Arthas jad - " + getSimpleClassName();
        setTitle(title);
        setModal(false);
        init();
        
        // Auto load
        loadRemoteCode();
    }

    private String getSimpleClassName() {
        if (className == null) return "";
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setPreferredSize(new Dimension(800, 600));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: Info panel (Instance + Target)
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Code panel (with toolbar on top)
        JPanel codePanel = createCodePanel();
        mainPanel.add(codePanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Target",
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 5, 2, 10);

        // Row 1: Instance
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Instance:"), gbc);
        gbc.gridx = 1;
        Integer arthasPort = RuntimeHelper.getArthasPort(instance.toConnectionString());
        String instanceInfo = String.format("[%s] %s : %s",
                instance.getRemotePartition(),
                instance.getRemoteIp(),
                arthasPort != null ? arthasPort : "N/A");
        JLabel instanceLabel = new JLabel(instanceInfo);
        instanceLabel.setFont(instanceLabel.getFont().deriveFont(Font.BOLD));
        panel.add(instanceLabel, gbc);

        // Row 2: Class#Method
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Target:"), gbc);
        gbc.gridx = 1;
        String targetInfo = StringUtils.isNotBlank(methodName) 
                ? className + "#" + methodName 
                : className;
        JLabel targetLabel = new JLabel(targetInfo);
        targetLabel.setForeground(new Color(0, 100, 150));
        panel.add(targetLabel, gbc);

        return panel;
    }

    private JPanel createCodePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Decompiled Code",
                TitledBorder.LEFT, TitledBorder.TOP));

        // Toolbar on top (Refresh, Copy, Compare)
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        
        refreshButton = new JButton("Refresh", AllIcons.Actions.Refresh);
        refreshButton.addActionListener(e -> loadRemoteCode());
        toolbarPanel.add(refreshButton);

        copyButton = new JButton("Copy", AllIcons.Actions.Copy);
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyToClipboard());
        toolbarPanel.add(copyButton);

        panel.add(toolbarPanel, BorderLayout.NORTH);

        // Code area
        codeArea = new JTextArea();
        codeArea.setEditable(false);
        codeArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        codeArea.setText("Loading...");
        codeArea.setTabSize(4);

        JBScrollPane scrollPane = new JBScrollPane(codeArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void loadRemoteCode() {
        refreshButton.setEnabled(false);
        codeArea.setText("Loading...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
            if (StringUtils.isBlank(scriptPath)) {
                showError("Remote script not configured");
                return;
            }

            try {
                RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                
                // Validate className
                System.out.println("[RemoteCodeDialog] className = '" + className + "', methodName = '" + methodName + "'");
                if (StringUtils.isBlank(className)) {
                    showError("Class name is empty. className=" + className);
                    return;
                }
                
                // Build jad command
                // jad syntax: jad [--source-only] className [methodName]
                String command;
                if (StringUtils.isNotBlank(methodName)) {
                    // Decompile specific method
                    command = "jad " + className.trim() + " " + methodName.trim();
                } else {
                    // Decompile entire class
                    command = "jad " + className.trim();
                }
                System.out.println("[RemoteCodeDialog] Executing command: " + command);

                // Get arthas port (may be null if not configured)
                Integer arthasPort = RuntimeHelper.getArthasPort(instance.toConnectionString());
                if (arthasPort == null || arthasPort <= 0) {
                    showError("Arthas port not configured for this instance.\n" +
                            "Please ensure loadInstances returns arthasPort field.");
                    return;
                }

                Map<String, Object> params = new HashMap<>();
                params.put("command", command);

                Object result = executor.sendArthasRequest(
                        instance.getAppName(),
                        instance.getRemotePartition(),
                        instance.getRemoteIp(),
                        arthasPort,
                        params,
                        RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT
                );

                String code = extractCode(result);
                remoteCode = code;

                ApplicationManager.getApplication().invokeLater(() -> {
                    codeArea.setText(code);
                    codeArea.setCaretPosition(0);
                    refreshButton.setEnabled(true);
                    copyButton.setEnabled(true);
                });

            } catch (Exception e) {
                showError("Load failed: " + e.getMessage());
            }
        });
    }

    private String extractCode(Object result) {
        if (result == null) {
            return "No result";
        }

        if (result instanceof String) {
            return (String) result;
        }

        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            
            // 尝试获取 data 字段
            Object data = map.get("data");
            if (data != null) {
                return data.toString();
            }
            
            // 检查是否成功
            Object success = map.get("success");
            if (Boolean.FALSE.equals(success)) {
                Object message = map.get("message");
                return "Error: " + (message != null ? message : "Unknown error");
            }

            return result.toString();
        }

        return result.toString();
    }

    private void copyToClipboard() {
        if (remoteCode != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(remoteCode), null);
            TestkitHelper.notify(project, NotificationType.INFORMATION, "Code copied to clipboard");
        }
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            codeArea.setText("❌ Error\n\n" + message);
            refreshButton.setEnabled(true);
        });
    }

    @Override
    protected Action @NotNull [] createActions() {
        // No buttons needed, user can close with ESC
        return new Action[0];
    }
}
