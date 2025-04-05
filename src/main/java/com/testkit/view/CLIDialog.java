package com.testkit.view;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CLIDialog extends JDialog {
    private static final Icon downloadIcon = IconLoader.getIcon("/icons/download.svg", CLIDialog.class);


    private TestkitToolWindow toolWindow;

    public CLIDialog(TestkitToolWindow toolWindow) {
        super((Frame) null, "Testkit CLI", true);
        this.toolWindow = toolWindow;
        initComponents();
        pack();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.add(TestkitToolWindow.createTips("Testkit-CLI is a command-line version of the testkit plugin\nSupport for dynamic attach runtime JVM\nThe ability to provide quick testing without intruding into the release process"));
        // 第一部分: 下载按钮和文本
        JPanel downloadPanel = createDownloadPanel();
        mainPanel.add(downloadPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(createCommandGeneratorPanel());

        setContentPane(mainPanel);
    }

    private JPanel createCommandGeneratorPanel() {
//        //我现在想给个生成启动命令的小工具，这些xxx的是需要用户填写的东西，有默认值
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Quick connection command"));

        panel.add(TestkitToolWindow.createTips("After downloading the cli, you can launch the tool directly using java -jar. \nThe following parameters are intended only to shorten the boot process of connecting to the jvm"));
        panel.add(Box.createVerticalStrut(15));

        SettingsStorageHelper.CliConfig cliConfig = SettingsStorageHelper.getCliConfig(toolWindow.getProject());
        // 下载选项
        JPanel downloadOptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox downloadCheckBox = new JCheckBox("Download first");
        downloadOptionPanel.add(downloadCheckBox);

        // 下载URL输入框
        JPanel urlPanel = new JPanel(new BorderLayout());
        JLabel wget = new JLabel("<html> ,<code style='color:#e83e8c'> wget </code></html>");
        //我想文本设置为代码风格
        urlPanel.add(wget, BorderLayout.WEST);
        JBTextField urlField = new JBTextField(cliConfig.getDownloadUrl());
        urlPanel.add(urlField, BorderLayout.CENTER);
        panel.add(urlPanel);
        urlPanel.setVisible(false);
        downloadOptionPanel.add(urlPanel);
        panel.add(downloadOptionPanel);

        // 设置下载选项的可见性联动
        downloadCheckBox.addActionListener(e -> {
            urlPanel.setVisible(downloadCheckBox.isSelected());
            pack(); // 重新调整窗口大小
        });
        if (cliConfig.isDownloadFirst()) {
            downloadCheckBox.setSelected(true);
            urlPanel.setVisible(true);
        }

        // 端口参数
        JPanel portPanel = new JPanel(new BorderLayout());
        portPanel.setToolTipText("Port number of the external service");
        portPanel.add(new JLabel("<html><code style='color:#e83e8c'>java -Dtestkit.cli.port=</code></html>"), BorderLayout.WEST);
        JBTextField portField = new JBTextField(String.valueOf(cliConfig.getPort()),5);
        portPanel.add(portField, BorderLayout.CENTER);
        panel.add(portPanel);

        // 上下文参数
        JPanel ctxPanel = new JPanel(new BorderLayout());
        ctxPanel.setToolTipText("A static variable in the target jvm that points to ApplicationContext, like com.xx.classname #feildName");
        ctxPanel.add(new JLabel("<html><code style='color:#e83e8c'>-Dtestkit.cli.ctx=</code></html>"), BorderLayout.WEST);
        JBTextField ctxField = new JBTextField("");
        ctxPanel.add(ctxField, BorderLayout.CENTER);
        panel.add(ctxPanel);

        // 环境键参数
        JPanel envKeyPanel = new JPanel(new BorderLayout());
        envKeyPanel.setToolTipText("The property key for the deployment environment of the target jvm");
        envKeyPanel.add(new JLabel("<html><code style='color:#e83e8c'>-Dtestkit.cli.env-key=</code></html>"), BorderLayout.WEST);
        JBTextField envKeyField = new JBTextField(cliConfig.getEnvKey());
        envKeyPanel.add(envKeyField, BorderLayout.CENTER);
        panel.add(envKeyPanel);

        // JAR文件路径
        JPanel jarPanel = new JPanel(new BorderLayout());
        jarPanel.add(new JLabel("<html><code style='color:#e83e8c'>-jar testkit-cli-1.0.jar</code></html>"), BorderLayout.WEST);
        panel.add(jarPanel);

        // 生成按钮和结果显示
        JPanel buttonAndResultPanel = new JPanel(new BorderLayout());
        JButton generateButton = new JButton("Generate Command");
        buttonAndResultPanel.add(generateButton, BorderLayout.WEST);
        panel.add(buttonAndResultPanel);

        // 添加生成按钮的动作
        generateButton.addActionListener(e -> {
            if (downloadCheckBox.isSelected() && urlField.getText().isEmpty()) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "pls set download url");
                return;
            }

            StringBuilder command = new StringBuilder("");
            String jarName = "testkit-cli-1.0.jar";
            String downUrl = urlField.getText().trim();
            if (downloadCheckBox.isSelected()) {
                command.append("if [ ! -f \"" + jarName + "\" ]; then\n" +
                        "    echo \"" + jarName + " not found. Downloading...\"\n" +
                        "    wget \"" + downUrl + "\" -O \"" + jarName + "\"\n" +
                        "    \n" +
                        "    if [ $? -ne 0 ]; then\n" +
                        "        echo \"Download failed.\"\n" +
                        "        exit 1\n" +
                        "    fi\n" +
                        "    echo \"Download completed.\"\n" +
                        "fi\n");
            }
            command.append("java ");
            String portText = portField.getText();
            try {
                Integer port = Integer.parseInt(portText.trim());
                if (port < 1 && port > 99999) {
                    throw new IllegalArgumentException("port must be port");
                }
            } catch (Throwable ex) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "must be available port ");
                return;
            }
            if (!portText.isEmpty()) {
                command.append("-Dtestkit.cli.port=").append(portText.trim()).append(" ");
            }

            String ctxFieldText = ctxField.getText();
            if (!ctxFieldText.trim().isEmpty()) {
                if (ctxFieldText.trim().split("#").length != 2) {
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "ctx must like com.xx.className#feildName");
                    return;
                }
                command.append("-Dtestkit.cli.ctx=").append(ctxFieldText.trim()).append(" ");
            }

            String envKeyText = envKeyField.getText().trim();
            if (!envKeyText.isEmpty()) {
                command.append("-Dtestkit.cli.env-key=").append(envKeyText).append(" ");
            }
            command.append("-jar testkit-cli-1.0.jar");

            SettingsStorageHelper.CliConfig config = new SettingsStorageHelper.CliConfig();
            config.setDownloadFirst(downloadCheckBox.isSelected());
            config.setDownloadUrl(urlField.getText());
            config.setPort(Integer.parseInt(portText.trim()));
            config.setCtx(ctxFieldText);
            config.setEnvKey(envKeyText);
            SettingsStorageHelper.saveCliConfig(toolWindow.getProject(), config);
            TestkitHelper.copyToClipboard(toolWindow.getProject(), command.toString(), "Command generated & saved!");
        });

        return panel;
    }

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Download CLI"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton downloadButton = new JButton(downloadIcon);
        downloadButton.setPreferredSize(new Dimension(32, 32));
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //弹出一个选项框，一个是本地下载，另一个是跳转到对应连接
                //新增drop down
                DefaultActionGroup copyGroup = new DefaultActionGroup();
                //显示的一个图标加上标题
                AnAction copyDirect = new AnAction("Download from local plugin", "Download from local plugin", AllIcons.Modules.SourceRoot) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        //新增一个本地下载功能，用户选择目录将指定文件下载到目标目录
                        String relativeJarPath = "testkit-cli-1.0.jar";
                        downloadFile(relativeJarPath);
                    }
                };
                copyGroup.add(copyDirect); // 将动作添加到动作组中
                String url = null;
                if(url!=null){
                    //显示的一个图标加上标题
                    AnAction browserDirect = new AnAction("Open browser download Latest", "Open browser download Latest", TestkitToolWindow.BROWSER_ICON) {
                        @Override
                        public void actionPerformed(AnActionEvent e) {
                            //使用默认浏览器打开
                            try {
                                URI uri = new URI(url);
                                Desktop.getDesktop().browse(uri);
                            } catch (Exception e2) {
                                TestkitHelper.notify(toolWindow.getProject(), NotificationType.ERROR, "Open browse fail<br>You can Manual opening<br>" + url);
                            }
                        }
                    };
                    copyGroup.add(browserDirect); // 将动作添加到动作组中
                }

                JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("CopyFunctionCallPopup", copyGroup).getComponent();
                popupMenu.show(downloadButton, 32, 0);
            }
        });
        buttonPanel.add(downloadButton);
        buttonPanel.add(new JLabel("Download the tool, speed up up up"));
        panel.add(buttonPanel);
        return panel;
    }

    private void downloadFile(String name) {
        // 假设有一个本地文件需要下载
        File sourceFile = new File(PathManager.getPluginsPath() + File.separator + TestkitHelper.PLUGIN_ID + File.separator + "lib" + File.separator + name);
        if (!sourceFile.exists()) {
            TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Can not find " + name + "\npls contact with author");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Download Testkit-CLI");

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDirectory = fileChooser.getSelectedFile();
            File targetFile = new File(selectedDirectory, name);

            try {
                Path source = Paths.get(sourceFile.getAbsolutePath());
                Path target = Paths.get(targetFile.getAbsolutePath());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "The file has been successfully downloaded: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "File download failure\n" + e.getMessage());
            }
        }
    }

}