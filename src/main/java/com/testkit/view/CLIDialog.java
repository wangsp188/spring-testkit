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
    public static final Icon downloadIcon = IconLoader.getIcon("/icons/download.svg", CLIDialog.class);


    private TestkitToolWindow toolWindow;

    public CLIDialog(TestkitToolWindow toolWindow) {
        super((Frame) null, "Testkit-CLI", true);
        this.toolWindow = toolWindow;
        initComponents();
        pack();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(Box.createVerticalStrut(5));
        // 第一部分: 下载按钮和文本
        JPanel downloadPanel = createDownloadPanel();
        mainPanel.add(downloadPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(createCommandGeneratorPanel());
        mainPanel.add(Box.createVerticalStrut(15));
        setContentPane(mainPanel);
    }



    private JPanel createCommandGeneratorPanel() {
//        //我现在想给个生成启动命令的小工具，这些xxx的是需要用户填写的东西，有默认值
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Shortening the boot process"));

        SettingsStorageHelper.CliConfig cliConfig = SettingsStorageHelper.getCliConfig(toolWindow.getProject());
        // 下载选项
        JPanel downloadOptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox downloadCheckBox = new JCheckBox("Download on demand");
        downloadCheckBox.setToolTipText("Upload testkit-cli to a file server and download it on demand at the time of use for a fast experience");
        downloadOptionPanel.add(downloadCheckBox);
        // 下载URL输入框
        JPanel urlPanel = new JPanel(new BorderLayout());
        JLabel curlLabel = new JLabel("<html> ,<code style='color:#e83e8c'> curl </code></html>");
        //我想文本设置为代码风格
        urlPanel.add(curlLabel, BorderLayout.WEST);
        JBTextField urlField = new JBTextField(cliConfig.getDownloadUrl(),30);
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
        portPanel.setToolTipText("Port number of the side service");
        portPanel.add(new JLabel("<html><code style='color:#e83e8c'>java -Dtestkit.cli.port=</code></html>"), BorderLayout.WEST);
        JBTextField portField = new JBTextField(cliConfig.getPort()==null?"10168":String.valueOf(cliConfig.getPort()));
        portField.getEmptyText().setText("Port number of the side service");

        portPanel.add(portField, BorderLayout.CENTER);
        panel.add(portPanel);

        // 上下文参数
        JPanel ctxPanel = new JPanel(new BorderLayout());
        ctxPanel.setToolTipText("A static variable in the target jvm that points to ApplicationContext, like com.xx.classname#feildName");
        ctxPanel.add(new JLabel("<html><code style='color:#e83e8c'>-Dtestkit.cli.ctx=</code></html>"), BorderLayout.WEST);
        JBTextField ctxField = new JBTextField(cliConfig.getCtx() == null ? "" : cliConfig.getCtx());
        ctxField.getEmptyText().setText("like com.hook.SpringContextUtil#context points to ApplicationContext");
        ctxPanel.add(ctxField, BorderLayout.CENTER);
        panel.add(ctxPanel);

        // 环境键参数
        JPanel envKeyPanel = new JPanel(new BorderLayout());
        envKeyPanel.setToolTipText("The property key for the deployment environment of the target jvm");
        envKeyPanel.add(new JLabel("<html><code style='color:#e83e8c'>-Dtestkit.cli.env-key=</code></html>"), BorderLayout.WEST);
        JBTextField envKeyField = new JBTextField(cliConfig.getEnvKey()==null?"spring.profiles.active":cliConfig.getEnvKey());
        envKeyField.getEmptyText().setText("like spring.profiles.active means the deployment environment of the target jvm");
        envKeyPanel.add(envKeyField, BorderLayout.CENTER);
        panel.add(envKeyPanel);

        // JAR文件路径
        JPanel jarPanel = new JPanel(new BorderLayout());
        jarPanel.add(new JLabel("<html><code style='color:#e83e8c'>-jar testkit-cli-1.0.jar</code></html>"), BorderLayout.WEST);
        panel.add(jarPanel);

        panel.add(Box.createVerticalStrut(15));
        // 生成按钮和结果显示
        JPanel buttonAndResultPanel = new JPanel(new BorderLayout());
        JButton generateButton = new JButton("Save & Copy Command");
        buttonAndResultPanel.add(generateButton, BorderLayout.WEST);
        panel.add(buttonAndResultPanel);

        // 添加生成按钮的动作
        generateButton.addActionListener(e -> {
            if (downloadCheckBox.isSelected() && urlField.getText().isBlank()) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "pls set download url");
                return;
            }
            Integer port = null;
            String portText = portField.getText();
            try {
                port = Integer.parseInt(portText.trim());
                if (port < 1 && port > 99999) {
                    throw new IllegalArgumentException("port must be port");
                }
            } catch (Throwable ex) {
                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "must be available port ");
                return;
            }

            String ctxFieldText = ctxField.getText();
            if (!ctxFieldText.trim().isEmpty()) {
                if (ctxFieldText.trim().split("#").length != 2) {
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "ctx must like com.xx.className#feildName");
                    return;
                }
            }

            SettingsStorageHelper.CliConfig config = new SettingsStorageHelper.CliConfig();
            config.setDownloadFirst(downloadCheckBox.isSelected());
            config.setDownloadUrl(urlField.getText().isBlank()?null:urlField.getText().trim());
            config.setPort(port);
            config.setCtx(ctxFieldText.trim().isBlank()?null:ctxFieldText.trim());
            config.setEnvKey(envKeyField.getText().trim().isBlank()?null:envKeyField.getText().trim());
            SettingsStorageHelper.saveCliConfig(toolWindow.getProject(), config);
            TestkitHelper.copyToClipboard(toolWindow.getProject(), config.buildCommand(), "Command generated & saved!");
        });

        return panel;
    }

    private static JPanel createCMDPanel() {
//        //我现在想给个生成启动命令的小工具，这些xxx的是需要用户填写的东西，有默认值
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createCmd("hi", "Gets the current connection information"));
        panel.add(createCmd("stop", "Stop the Testkit-Server"));
        panel.add(createCmd("view", "View config, eg: view server.port  or view test.ServiceA#field1"));
        panel.add(createCmd("function-call", "Equivalent to function-call of plugin"));
        panel.add(createCmd("flexible-test", "Equivalent to flexible-test of plugin"));
        return panel;
    }


    public static JDialog createCMDDialog(){
        JDialog jDialog = new JDialog((Frame) null, "Testkit-CLI CMD list", true);
        jDialog.setContentPane(createCMDPanel());
        jDialog.pack();
        jDialog.setLocationRelativeTo(null);
        return jDialog;
    }


    private static JPanel createCmd(String cmd,String desc){
        JPanel hi = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel comp = new JLabel(cmd);
        comp.setPreferredSize(new Dimension(80, 32));
        hi.add(comp);

        JLabel comp2 = new JLabel(desc);
        comp2.setFont(new Font("Arial", Font.BOLD, 13));
        hi.add(comp2);
        return hi;
    }

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
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
                AnAction copyDirect = new AnAction("Download to Desktop", "Download to Desktop", AllIcons.Modules.SourceRoot) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        //新增一个本地下载功能，用户选择目录将指定文件下载到目标目录
                        String relativeJarPath = "testkit-cli-1.0.jar";
                        downloadFile(relativeJarPath);
                    }
                };
                copyGroup.add(copyDirect); // 将动作添加到动作组中
                String url = "https://raw.githubusercontent.com/wangsp188/spring-testkit/refs/heads/master/how-to-use/testkit-cli-1.0.jar";
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
        buttonPanel.add(new JLabel("Quick start is Downloading and execute "));
        JLabel comp = new JLabel("'java -jar testkit-cli-1.0.jar'");
        comp.setForeground(new Color(0x72A96B));
        comp.setFont(new Font("Arial", Font.BOLD, 13));
        buttonPanel.add(comp);
        panel.add(buttonPanel);
        return panel;
    }

    private void downloadFile(String name) {
        // 1. 构建源文件路径（使用Path更安全）
        Path sourcePath = Paths.get(
                PathManager.getPluginsPath(),
                TestkitHelper.PLUGIN_ID,
                "lib",
                name
        );
        // 检查源文件是否存在
        if (!Files.exists(sourcePath)) {
            TestkitHelper.alert(
                    toolWindow.getProject(),
                    Messages.getErrorIcon(),
                    "Can not find " + name + "\nPls contact with author"
            );
            return;
        }

        //获取当前桌面目录，并判断是否存在name的file，如果存在则弹出提示是否覆盖
        //将sourceFile copy到桌面
        // 2. 获取桌面路径（跨平台兼容）
        Path desktopPath = Paths.get(
                System.getProperty("user.home"),
                "Desktop"
        );

        // 3. 构建目标文件路径
        Path targetPath = desktopPath.resolve(name);

        try {
            // 4. 检查目标文件是否存在
            if (Files.exists(targetPath)) {
                // 弹窗确认覆盖（使用IDE的DialogWrapper）
                int result = Messages.showYesNoDialog(
                        toolWindow.getProject(),
                        "File \"" + name + "\" already exists on Desktop. Override?",
                        "File Exists",
                        Messages.getQuestionIcon()
                );

                if (result != Messages.YES) {
                    return; // 用户取消操作
                }
            }

            // 5. 执行复制（自动覆盖）
            Files.copy(
                    sourcePath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            // 6. 提示成功
            TestkitHelper.alert(
                    toolWindow.getProject(),
                    Messages.getInformationIcon(),
                    "File saved to Desktop: " + targetPath
            );

        } catch (Throwable e) {
            // 处理IO异常（如权限不足、磁盘空间不足等）
            TestkitHelper.alert(
                    toolWindow.getProject(),
                    Messages.getErrorIcon(),
                    "Failed to save file: " + e.getMessage()
            );
        }
    }

}