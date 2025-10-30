package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.testkit.TestkitHelper;
import com.testkit.view.TestkitToolWindow;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class MCPServerDialog extends JDialog {

    public static final Icon CURSOR_ICON = IconLoader.getIcon("/icons/cursor.png", MCPServerDialog.class);
    public static final Icon Verify_CONFIG_ICON = IconLoader.getIcon("/icons/fire-test.svg", MCPServerDialog.class);


    private TestkitToolWindow toolWindow;
    private LanguageTextField inputConfigField;


    public MCPServerDialog(TestkitToolWindow testkitWindow) {
        super((Frame) null,  "MCP Servers is all you need", true);
        this.toolWindow = testkitWindow;
        JPanel panelMain = new JPanel(new GridBagLayout());
        GridBagConstraints c1 = new GridBagConstraints();

        JBScrollPane scrollPane = buildInputPane();
        // 设置 panelCmd
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.NORTH;// 使组件在水平方向和垂直方向都拉伸
        c1.gridwidth = 3;
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 1; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
//        c1.gridwidth = 2; // 横跨两列
        panelMain.add(scrollPane, c1);

        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        //Function As A Service is all you need

        // Import 按钮
        JButton importButton = new JButton(CURSOR_ICON);
        importButton.setToolTipText("Import MCP-Servers from Cursor");
        importButton.setPreferredSize(new Dimension(30, 30));
        importButton.addActionListener(e -> {
            // 后台任务导入配置
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Importing from Cursor...", false) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    try {
                        // 读取 Cursor 配置
                        JSONObject cursorConfig = McpHelper.readCursorMcpConfig();
                        JSONObject cursorServers = cursorConfig.getJSONObject("mcpServers");
                        
                        if (cursorServers == null || cursorServers.isEmpty()) {
                            TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "No MCP servers found in Cursor configuration");
                            return;
                        }

                        // 获取当前配置
                        String currentText = inputConfigField.getText();
                        JSONObject currentConfig = null;
                        try {
                            currentConfig = StringUtils.isBlank(currentText) ? new JSONObject() : JSON.parseObject(currentText);
                        } catch (Exception ex) {
                            TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "Parse mcp.json fail, please modify and retry");
                            return;
                        }
                        JSONObject currentServers = currentConfig.getJSONObject("mcpServers");
                        if (currentServers == null) {
                            currentServers = new JSONObject();
                        }

                        // 去重并找出新的 servers
                        Map<String, JSONObject> newServers = findNewServers(cursorServers, currentServers);

                        if (newServers.isEmpty()) {
                            TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), "No new servers to import, all servers already exist");
                            return;
                        }

                        // 弹出多选框让用户选择
                        JSONObject finalCurrentServers = currentServers;
                        JSONObject finalCurrentConfig = currentConfig;
                        SwingUtilities.invokeLater(() -> {
                            java.util.List<String> selectedServers = showServerSelectionDialog(newServers);
                            if (selectedServers != null && !selectedServers.isEmpty()) {
                                // 合并配置
                                for (String serverName : selectedServers) {
                                    finalCurrentServers.put(serverName, newServers.get(serverName));
                                }
                                finalCurrentConfig.put("mcpServers", finalCurrentServers);
                                // 更新输入框
                                inputConfigField.setText(JSON.toJSONString(finalCurrentConfig, SerializerFeature.PrettyFormat));
                                TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "Imported successfully, " + selectedServers);
                            }
                        });

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Failed to import," + ex.getMessage());
                    }
                }
            });
        });
        closePanel.add(importButton);

        // Verify 按钮
        JButton verifyButton = new JButton(Verify_CONFIG_ICON);
        verifyButton.setToolTipText("Verify MCP-Servers");
        verifyButton.setPreferredSize(new Dimension(32, 32));
        verifyButton.addActionListener(e -> {
            String configText = inputConfigField.getText();
            if (StringUtils.isBlank(configText)) {
                TestkitHelper.alert(toolWindow.getProject(),Messages.getErrorIcon(),"Please input your mcp.json");
                return;
            }

            JSONObject jsonObject = null;
            try {
                jsonObject = JSON.parseObject(configText);
            } catch (Throwable ex) {
                TestkitHelper.alert(toolWindow.getProject(),Messages.getErrorIcon(),"Parse mcp.json fail,"+ex.getMessage());
                return;
            }
            JSONObject finalJsonObject = jsonObject;
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Verify MCP-Servers, please wait ...", false) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    McpAdapter.McpInitRet mcpInitRet = McpAdapter.parseMcpServers(finalJsonObject);
                    TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), mcpInitRet.toString().replace("\n","<br>"));
                }
            });
        });
        closePanel.add(verifyButton);


        // 设置 closePanel
        JButton applyButton = new JButton("Apply");
        applyButton.setToolTipText("Initialize&Apply mcp.json");
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String configText = inputConfigField.getText();
                if (StringUtils.isBlank(configText)) {
                    TestkitHelper.alert(toolWindow.getProject(),Messages.getErrorIcon(),"Please input your mcp.json");
                    return;
                }

                JSONObject jsonObject = null;
                try {
                    jsonObject = JSON.parseObject(configText);
                } catch (Throwable ex) {
                    TestkitHelper.alert(toolWindow.getProject(),Messages.getErrorIcon(),"Parse mcp.json fail,"+ex.getMessage());
                    return;
                }

                JSONObject finalJsonObject = jsonObject;
                ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Initialize MCP-Servers, please wait ...", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator progressIndicator) {
                        //更新配置
                        McpAdapter.McpInitRet mcpInitRet = McpAdapter.parseMcpServers(finalJsonObject);
                        try {
                            // 添加确认对话框
                            int result = JOptionPane.showConfirmDialog(
                                    MCPServerDialog.this, // 父组件，可以为 null
                                    "Are you sure to apply this MCP-Servers?\n"+mcpInitRet, // 提示信息
                                    "Confirm Apply", // 对话框标题
                                    JOptionPane.YES_NO_OPTION // 选项类型
                            );

                            if (result != JOptionPane.YES_OPTION) {
                                // 用户点击了“否”或关闭对话框，不执行 SQL
                                return;
                            }

                            //保存文件
                            McpHelper.saveMcpJson(finalJsonObject);
                            McpHelper.refreshServers(mcpInitRet.servers());
                            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "MCP-Servers apply successfully\n"+mcpInitRet);
                        } catch (RuntimeExceptionWithAttachments ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }
        });
        closePanel.add(applyButton);


        c1.fill = GridBagConstraints.NONE;
        c1.anchor = GridBagConstraints.SOUTHEAST; // 将按钮放置在东南角
        c1.weightx = 0;  // 不占据额外空间
        c1.weighty = 0;
        c1.gridx = 1;    // 放在第二列
        c1.gridy = 3;    // 放在第三行
        panelMain.add(closePanel, c1);

        add(panelMain);
        pack();
        resizeDialog();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public void refreshMcpJson(){
        inputConfigField.setText(JSON.toJSONString(McpHelper.fetchMcpJson(), SerializerFeature.PrettyFormat));
    }

    public void resizeDialog() {
        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screenSize.width * 0.7), (int) (screenSize.height * 0.7));
        setLocationRelativeTo(null);
    }

    private JBScrollPane buildInputPane() {
        Language language = JsonLanguage.INSTANCE;
        inputConfigField = new LanguageTextField(language, toolWindow.getProject(), "", false);
        JBScrollPane scrollPane = new JBScrollPane(inputConfigField);
        scrollPane.setBorder(BorderFactory.createTitledBorder("mcp.json"));

        // 添加文档监听器以更新滚动条
        inputConfigField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent event) {
                scrollPane.revalidate();
                scrollPane.repaint();
            }
        });
        return scrollPane;
    }

    /**
     * 找出新的 servers（去重逻辑）
     */
    private Map<String, JSONObject> findNewServers(JSONObject cursorServers, JSONObject currentServers) {
        Map<String, JSONObject> newServers = new LinkedHashMap<>();
        
        for (String serverName : cursorServers.keySet()) {
            JSONObject cursorServer = cursorServers.getJSONObject(serverName);
            if (cursorServer == null) {
                continue;
            }
            
            boolean isDuplicate = false;
            
            // 遍历当前已有的 servers 检查是否重复
            for (String existingServerName : currentServers.keySet()) {
                JSONObject existingServer = currentServers.getJSONObject(existingServerName);
                if (existingServer == null) {
                    continue;
                }
                
                if (isServerDuplicate(cursorServer, existingServer)) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                newServers.put(serverName, cursorServer);
            }
        }
        
        return newServers;
    }

    /**
     * 判断两个 server 配置是否重复
     */
    private boolean isServerDuplicate(JSONObject server1, JSONObject server2) {
        // 优先判断是否有 command 属性
        String command1 = server1.getString("command");
        String command2 = server2.getString("command");
        
        // 如果两个都有 command 属性，则按照 command + args 判断
        if (command1 != null && command2 != null) {
            // 截取最后一个斜杠后的内容进行匹配，以处理绝对路径和相对路径的情况
            // 例如：uv 和 /Users/dexwang/.local/bin/uv 应该被认为是相同的
            String normalizedCommand1 = normalizeCommand(command1);
            String normalizedCommand2 = normalizeCommand(command2);
            if (!normalizedCommand1.equals(normalizedCommand2)) {
                return false;
            }
            
            // 比较 args
            Object args1Obj = server1.get("args");
            Object args2Obj = server2.get("args");
            
            String args1Str = args1Obj != null ? JSON.toJSONString(args1Obj) : "";
            String args2Str = args2Obj != null ? JSON.toJSONString(args2Obj) : "";
            
            return args1Str.equals(args2Str);
        }
        
        // 如果没有 command，则判断是否有 type 属性
        String type1 = server1.getString("type");
        String type2 = server2.getString("type");
        
        if (type1 != null && type2 != null) {
            if (!type1.equalsIgnoreCase(type2)) {
                return false;
            }
            
            // 比较 url
            String url1 = server1.getString("url");
            String url2 = server2.getString("url");
            
            return url1 != null && url1.equals(url2);
        }
        
        return false;
    }

    /**
     * 规范化 command，截取最后一个斜杠后的内容
     * 例如：/Users/dexwang/.local/bin/uv -> uv
     *      uv -> uv
     */
    private String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        int lastSlashIndex = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        if (lastSlashIndex >= 0 && lastSlashIndex < command.length() - 1) {
            return command.substring(lastSlashIndex + 1);
        }
        return command;
    }

    /**
     * 显示 server 选择对话框（多选框）
     * @return 用户选择的 server 名称列表，如果取消则返回 null
     */
    private java.util.List<String> showServerSelectionDialog(Map<String, JSONObject> servers) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        Map<String, JCheckBox> checkBoxMap = new java.util.LinkedHashMap<>();
        
        panel.add(new JLabel("Select servers to import:"));
        panel.add(Box.createVerticalStrut(10));
        
        for (Map.Entry<String, JSONObject> entry : servers.entrySet()) {
            String serverName = entry.getKey();
            JCheckBox checkBox = new JCheckBox(serverName);
            checkBox.setSelected(false); // 默认不选中
            checkBoxMap.put(serverName, checkBox);
            panel.add(checkBox);
        }
        
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        int result = JOptionPane.showConfirmDialog(
                this,
                scrollPane,
                "Select Servers to Import",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            java.util.List<String> selectedServers = new java.util.ArrayList<>();
            for (Map.Entry<String, JCheckBox> entry : checkBoxMap.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selectedServers.add(entry.getKey());
                }
            }
            return selectedServers;
        }
        
        return null;
    }

}
