package com.testkit.listener;

import com.alibaba.fastjson.JSON;
import com.intellij.notification.Notification;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.view.TestkitToolWindow;
import com.testkit.view.TestkitToolWindowFactory;
import com.testkit.util.UpdaterUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.*;
import java.awt.*;

public class TestkitProjectListener implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // ✅ 方案 2：使用 runWhenSmart 替代 smartInvokeLater
        // runWhenSmart 会在所有索引任务完成后才执行，更可靠
        DumbService.getInstance(project).runWhenSmart(() -> {
            try {
//                new Thread(() -> {
//                    while (true) {
//                        CodingGuidelinesHelper.refreshDoc(project);
//                        TestkitHelper.refresh(project);
//                        try {
//                            Thread.sleep(24 * 3600 * 1000);
//                        } catch (InterruptedException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                }).start();

                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Init project apps", false){
                    @Override
                    public void run(@NotNull ProgressIndicator progressIndicator) {
                        // ✅ 方案 1：强制重新扫描，并确保更新缓存
                        Map<String, RuntimeHelper.AppMeta> scannedApps = TestkitHelper.findSpringBootClass(project);
                        List<RuntimeHelper.AppMeta> apps = new ArrayList<>(scannedApps.values());
                        
                        // 关键修复：无论结果如何都更新缓存
                        RuntimeHelper.updateAppMetas(project.getName(), apps);
                        
                        // 如果扫描结果为空，记录日志便于排查
                        if (apps.isEmpty()) {
                            System.err.println("[Testkit] Warning: No @SpringBootApplication found in " + project.getName());
                        }
                        
                        RuntimeHelper.setEnableMapperSql(SettingsStorageHelper.getSqlConfig(project).isEnableMapperSql());
                        System.out.println("Init project line marker,"+project.getName());
                        TestkitHelper.refresh(project);
                        CodingGuidelinesHelper.refreshDoc(project);
                    }
                });

                // Startup: check plugin updates in background
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Check plugin updates", false){
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        UpdaterUtil.LastVersion last = UpdaterUtil.fetchNeedUpdateLastVersion();
                        if (last == null) {
                            return;
                        }
                        String zipPath = last.buildPluginPath();
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(project, last, zipPath);
                            }
                        });
                    }
                });

            } catch (Exception e) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Schedule refresh failed," + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        });
        return null;
    }

    private static void refreshValidDatasources(Project project) {
        try {
            String propertiesStr = SettingsStorageHelper.getSqlConfig(project).getProperties();
            if (StringUtils.isBlank(propertiesStr) || Objects.equals(propertiesStr, SettingsStorageHelper.datasourceTemplateProperties)) {
                return;
            }
            TestkitToolWindow toolWindow = TestkitToolWindowFactory.getToolWindow(project);
            if (toolWindow==null) {
                return;
            }

            List<SettingsStorageHelper.DatasourceConfig> datasources = RuntimeHelper.getValidDatasources(project.getName());

            List<SettingsStorageHelper.DatasourceConfig> sqlConfig = SettingsStorageHelper.SqlConfig.parseDatasources(propertiesStr);

            Set<String> oldValidNames = datasources.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());
            Set<String> configNames = sqlConfig.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());
            //有无效的就重新判断下
            if (oldValidNames.containsAll(configNames)) {
                return;
            }
            Map<String, String> results = new HashMap<>();
            List<SettingsStorageHelper.DatasourceConfig> valids = new ArrayList<>();
            List<String> ddls = new ArrayList<>();
            List<String> writes = new ArrayList<>();
            for (SettingsStorageHelper.DatasourceConfig config : sqlConfig) {
                // 测试连接
                Object result = MysqlUtil.testConnectionAndClose(config);
                if (result instanceof Integer) {
                    valids.add(config);
                    if (Objects.equals(result, 2)) {
                        ddls.add(config.getName());
                    } else if (Objects.equals(result, 1)) {
                        writes.add(config.getName());
                    }
                }
                results.put(config.getName(), !(result instanceof String) ? ("connect success, DDL: " + result) : result.toString());
            }

            Set<String> validNames = valids.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());

            Set<String> oldDdls = new HashSet<>(RuntimeHelper.getDDLDatasources(project.getName()));
            Set<String> oldWrites = new HashSet<>(RuntimeHelper.getWriteDatasources(project.getName()));
            if(oldDdls.containsAll(ddls) && new HashSet<>(ddls).containsAll(oldDdls)
            && new HashSet<>(writes).containsAll(oldWrites) && oldWrites.containsAll(writes)
                    && oldValidNames.containsAll(validNames) && validNames.containsAll(oldValidNames)
            ) {
                return;
            }
            RuntimeHelper.updateValidDatasources(project.getName(), valids, ddls, writes);
            //更新
            toolWindow.refreshSqlDatasources();
            TestkitHelper.notify(project, NotificationType.INFORMATION, "SQL Tool is updated, Connection result is " + JSON.toJSONString(results));
        } catch (Throwable e) {
            System.out.println("datasource更新失败" + e.getMessage());
        }
    }

    /**
     * 显示更新对话框（居中显示）
     */
    private static void showUpdateDialog(Project project, UpdaterUtil.LastVersion lastVersion, String zipPath) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // 标题
        JLabel titleLabel = new JLabel("🎉 New Version Available");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Release Notes 内容
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));

        JLabel versionLabel = new JLabel("Version: " + lastVersion.getVersion());
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 14f));
        contentPanel.add(versionLabel, BorderLayout.NORTH);

        if (lastVersion.getReleaseNotes() != null && !lastVersion.getReleaseNotes().isEmpty()) {
            JTextArea notesArea = new JTextArea(lastVersion.getReleaseNotes());
            notesArea.setEditable(false);
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);
            notesArea.setFont(notesArea.getFont().deriveFont(12f));
            notesArea.setBackground(panel.getBackground());

            JBScrollPane scrollPane = new JBScrollPane(notesArea);
            scrollPane.setPreferredSize(new Dimension(500, 200));
            scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            contentPanel.add(scrollPane, BorderLayout.CENTER);
        }

        panel.add(contentPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton installButton = new JButton("Install Now");
        installButton.setPreferredSize(new Dimension(120, 32));

        JButton openFolderButton = new JButton("Open Folder");
        openFolderButton.setPreferredSize(new Dimension(120, 32));
        openFolderButton.addActionListener(e -> {
            UpdaterUtil.revealFile(zipPath);
        });

        JButton laterButton = new JButton("Later");
        laterButton.setPreferredSize(new Dimension(120, 32));

        buttonPanel.add(installButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(laterButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // 创建居中弹窗
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, installButton)
                .setTitle(TestkitHelper.getPluginName()+" Update")
                .setMovable(true)
                .setResizable(false)
                .setRequestFocus(true)
                .setCancelOnClickOutside(false)
                .setCancelKeyEnabled(true)  // 允许ESC键关闭，这样标题栏会显示关闭按钮
                .setCancelOnWindowDeactivation(false)
                .createPopup();

        // 设置按钮事件（需要在popup创建后）
        installButton.addActionListener(e -> {
            boolean ok = UpdaterUtil.installFromZip(zipPath, project);
            if (ok) {
                popup.cancel();  // 关闭update弹窗
                showRestartNotification(project, lastVersion.getVersion());
            } else {
                TestkitHelper.notify(project, NotificationType.WARNING, "Install failed. Please open folder and install via Plugins > Install from Disk.");
            }
        });

        laterButton.addActionListener(e -> popup.cancel());

        // 居中显示
        popup.showCenteredInCurrentWindow(project);
    }

    /**
     * 显示重启 IDE 的通知（居中弹窗）
     */
    private static void showRestartNotification(Project project, String version) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // 成功消息
        JLabel messageLabel = new JLabel("✅ Plugin v" + version + " installed successfully!");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(messageLabel, BorderLayout.NORTH);

        // 提示信息
        JLabel tipLabel = new JLabel("<html><div style='text-align: center; padding: 10px;'>Please restart IDE to apply changes.</div></html>");
        tipLabel.setFont(tipLabel.getFont().deriveFont(12f));
        panel.add(tipLabel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        JButton restartNowButton = new JButton("Restart Now");
        restartNowButton.setPreferredSize(new Dimension(120, 32));

        JButton restartLaterButton = new JButton("Restart Later");
        restartLaterButton.setPreferredSize(new Dimension(120, 32));

        buttonPanel.add(restartNowButton);
        buttonPanel.add(restartLaterButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // 创建居中弹窗
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, restartNowButton)
                .setTitle(TestkitHelper.getPluginName() + " - Installation Complete")
                .setMovable(true)
                .setResizable(false)
                .setRequestFocus(true)
                .setCancelOnClickOutside(false)
                .setCancelKeyEnabled(true)  // 允许ESC键关闭，这样标题栏会显示关闭按钮
                .setCancelOnWindowDeactivation(false)
                .createPopup();

        restartNowButton.addActionListener(e -> {
            popup.cancel();
            ApplicationManager.getApplication().restart();
        });

        restartLaterButton.addActionListener(e -> popup.cancel());

        // 居中显示
        popup.showCenteredInCurrentWindow(project);
    }
}