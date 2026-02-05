package com.testkit.remote_script;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
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
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
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
    private JButton hotDeployButton;
    
    // State
    private String remoteCode = null;
    private boolean isDeploying = false;

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

        // Toolbar on top (Refresh, Copy, Hot Deploy)
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        
        refreshButton = new JButton("Refresh", AllIcons.Actions.Refresh);
        refreshButton.addActionListener(e -> loadRemoteCode());
        toolbarPanel.add(refreshButton);
        
        // 分隔符
        toolbarPanel.add(Box.createHorizontalStrut(20));
        
        hotDeployButton = new JButton("🔥 Hot Deploy");
        hotDeployButton.setToolTipText("Upload local .class file and retransform on remote instance");
        hotDeployButton.addActionListener(e -> confirmAndHotDeploy());
        toolbarPanel.add(hotDeployButton);

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
                    // Check if it's a constructor (method name equals simple class name)
                    String simpleClassName = getSimpleClassName();
                    String actualMethodName = methodName.trim();
                    if (actualMethodName.equals(simpleClassName)) {
                        // Constructor: use <init>
                        actualMethodName = "<init>";
                    }
                    command = "jad " + className.trim() + " " + actualMethodName;
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


    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            codeArea.setText("❌ Error\n\n" + message);
            refreshButton.setEnabled(true);
        });
    }
    
    /**
     * 确认并执行热部署
     */
    private void confirmAndHotDeploy() {
        if (isDeploying) {
            return;
        }
        
        // 先找到 .class 文件
        File classFile = findClassFile();
        if (classFile == null) {
            Messages.showWarningDialog(project,
                    "Cannot find .class file for: " + className + "\n\n" +
                    "Please make sure the project is compiled.",
                    "Hot Deploy");
            return;
        }
        
        // 确认对话框
        String message = "Class: " + className + "\n" +
                "File: " + classFile.getName() + "\n" +
                "Instance: [" + instance.getRemotePartition() + "] " + instance.getRemoteIp() + "\n" +
                "⚠️ JVM Limitation:\n" +
                "  • Only method body changes are supported\n" +
                "  • Adding/removing methods or fields will FAIL\n" +
                "💡 To restore: revert code → rebuild → hot deploy again";
        
        int result = Messages.showYesNoDialog(project, message, "🔥 Hot Deploy",
                "Deploy", "Cancel", Messages.getQuestionIcon());
        
        if (result == Messages.YES) {
            hotDeploy(classFile);
        }
    }
    
    /**
     * 确认并执行回滚
     */
    /**
     * 执行热部署
     */
    private void hotDeploy(File classFile) {
        isDeploying = true;
        hotDeployButton.setEnabled(false);
        
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Hot Deploy: " + getSimpleClassName(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Preparing...");
                    
                    String scriptPath = SettingsStorageHelper.getRemoteScriptPath(project);
                    if (StringUtils.isBlank(scriptPath)) {
                        showDeployResult(false, "Remote script not configured");
                        return;
                    }
                    
                    RemoteScriptExecutor executor = new RemoteScriptExecutor(scriptPath);
                    if (!executor.isValid()) {
                        showDeployResult(false, "Remote script invalid: " + scriptPath);
                        return;
                    }
                    
                    // 远程文件路径：/tmp/ClassName.class
                    String simpleClassName = getSimpleClassName();
                    String remotePath = "/tmp/" + simpleClassName + ".class";
                    String localPath = classFile.getAbsolutePath();
                    
                    // 1. 上传 .class 文件
                    indicator.setText("Uploading .class file...");
                    System.out.println("[HotDeploy] Uploading " + localPath + " to " + remotePath);
                    Map<String, Object> uploadParams = new HashMap<>();
                    uploadParams.put("action", "upload");
                    uploadParams.put("localPath", localPath);
                    uploadParams.put("remotePath", remotePath);
                    
                    executor.sendFileRequest(
                            instance.getAppName(),
                            instance.getRemotePartition(),
                            instance.getRemoteIp(),
                            uploadParams);
                    
                    // 2. 执行 retransform 命令
                    indicator.setText("Retransforming class...");
                    System.out.println("[HotDeploy] Executing retransform " + remotePath);
                    Integer arthasPort = RuntimeHelper.getArthasPort(instance.toConnectionString());
                    if (arthasPort == null || arthasPort <= 0) {
                        showDeployResult(false, "Arthas port not configured");
                        return;
                    }
                    
                    Map<String, Object> arthasParams = new HashMap<>();
                    arthasParams.put("command", "retransform " + remotePath);
                    
                    Object result = executor.sendArthasRequest(
                            instance.getAppName(),
                            instance.getRemotePartition(),
                            instance.getRemoteIp(),
                            arthasPort,
                            arthasParams,
                            RemoteScriptExecutor.REMOTE_ARTHAS_TIMEOUT);
                
                    // 检查结果
                    boolean success = false;
                    String errorMsg = "";
                    
                    if (result instanceof Map) {
                        Map<?, ?> resultMap = (Map<?, ?>) result;
                        success = Boolean.TRUE.equals(resultMap.get("success"));
                        if (!success) {
                            Object msg = resultMap.get("message");
                            errorMsg = msg != null ? msg.toString() : result.toString();
                        }
                    } else {
                        String resultStr = result != null ? result.toString() : "";
                        success = !resultStr.contains("Error") && !resultStr.contains("error");
                        errorMsg = resultStr;
                    }
                    
                    if (success) {
                        showDeployResult(true, "Hot deploy successful!");
                        // 自动刷新反编译代码
                        ApplicationManager.getApplication().invokeLater(RemoteDecodeDialog.this::loadRemoteCode);
                    } else {
                        // 检查是否是 JVM 限制导致的错误
                        if (errorMsg.contains("attempted to add a method") || 
                            errorMsg.contains("attempted to delete a method") ||
                            errorMsg.contains("attempted to change the schema")) {
                            errorMsg += "\n\n⚠️ JVM Limitation:\n" +
                                    "Hot deploy (retransform) can ONLY modify method body.\n" +
                                    "It CANNOT:\n" +
                                    "  • Add/remove methods\n" +
                                    "  • Add/remove fields\n" +
                                    "  • Change method signatures\n" +
                                    "  • Change class hierarchy\n\n" +
                                    "Please restart the application for structural changes.";
                        }
                        showDeployResult(false, errorMsg);
                    }
                
                } catch (Exception e) {
                    showDeployResult(false, "Hot deploy failed: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 显示部署结果
     */
    private void showDeployResult(boolean success, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            isDeploying = false;
            hotDeployButton.setEnabled(true);
            
            if (success) {
                TestkitHelper.notify(project, NotificationType.INFORMATION, "🔥 " + message);
            } else {
                Messages.showErrorDialog(project, message, "Hot Deploy Failed");
            }
        });
    }
    
    /**
     * 查找本地 .class 文件（只在当前类所属的模块中查找）
     */
    private File findClassFile() {
        if (StringUtils.isBlank(className)) {
            return null;
        }
        
        // 将类名转换为路径：com.example.MyClass -> com/example/MyClass.class
        String classPath = className.replace('.', '/') + ".class";
        
        // 通过类名找到 PsiClass，然后获取其所属的 Module
        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.projectScope(project));
        
        if (psiClass == null) {
            System.out.println("[HotDeploy] PsiClass not found for: " + className);
            return null;
        }
        
        Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (module == null) {
            System.out.println("[HotDeploy] Module not found for class: " + className);
            return null;
        }
        
        System.out.println("[HotDeploy] Found module: " + module.getName() + " for class: " + className);
        
        CompilerModuleExtension compilerExtension = CompilerModuleExtension.getInstance(module);
        if (compilerExtension == null) {
            System.out.println("[HotDeploy] CompilerModuleExtension not found for module: " + module.getName());
            return null;
        }
        
        // 检查主输出目录
        VirtualFile outputPath = compilerExtension.getCompilerOutputPath();
        if (outputPath != null) {
            File classFile = new File(outputPath.getPath(), classPath);
            if (classFile.exists()) {
                System.out.println("[HotDeploy] Found class file: " + classFile.getAbsolutePath());
                return classFile;
            }
        }
        
        System.out.println("[HotDeploy] Class file not found in output directory for: " + className);
        return null;
    }

    @Override
    protected Action @NotNull [] createActions() {
        // No buttons needed, user can close with ESC
        return new Action[0];
    }
}
