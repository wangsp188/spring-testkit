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

public class MCPServerDialog extends JDialog {

    public static final Icon Verify_CONFIG_ICON = IconLoader.getIcon("/icons/fire-test.svg", MCPServerDialog.class);


    private TestkitToolWindow toolWindow;
    private LanguageTextField inputConfigField;


    public MCPServerDialog(TestkitToolWindow testkitWindow) {
        super((Frame) null, "MCP Servers", true);
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

        // 一个按钮
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
                    McpAdapter.McpInitRet mcpInitRet = McpAdapter.parseAndBuildMcpClients(finalJsonObject);
                    try {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getInformationIcon(), mcpInitRet.toString().replace("\n","<br>"));
                    } finally {
                        mcpInitRet.close();
                    }
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
                        McpAdapter.McpInitRet mcpInitRet = McpAdapter.parseAndBuildMcpClients(finalJsonObject);

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
                            McpHelper.refreshClients(mcpInitRet.clients());
                            TestkitHelper.notify(toolWindow.getProject(), NotificationType.INFORMATION, "MCP-Servers apply successfully\n"+mcpInitRet);
                        } catch (RuntimeExceptionWithAttachments ex) {
                            ex.printStackTrace();
                            mcpInitRet.close();
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

}
