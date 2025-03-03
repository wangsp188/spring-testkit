package com.testkit.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.ui.Messages;
import com.testkit.TestkitHelper;
import com.testkit.tools.BasePluginTool;
import com.testkit.util.JsonUtil;
import com.testkit.util.curl.CurlEntity;
import com.testkit.util.curl.CurlParserUtil;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class CurlDialog extends JDialog {

    public static final Icon ADAPTER_INPUT_ICON = IconLoader.getIcon("/icons/adapter-input.svg", CurlDialog.class);


    private JBTextArea inputTextArea;
    private JTextField urlField, methodField;
    private JTextArea paramsField, headersField, bodyField;

    public CurlDialog(TestkitToolWindow testkitWindow) {
        super((Frame) null, "Curl Parser", true);

        JPanel panelMain = new JPanel(new GridBagLayout());
        GridBagConstraints c1 = new GridBagConstraints();

        // 设置 panelCmd
        JPanel panelCmd = buildCmdPanel(testkitWindow);
        c1.anchor = GridBagConstraints.NORTH;
        c1.fill = GridBagConstraints.BOTH;  // 使组件在水平方向和垂直方向都拉伸
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 0.2; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
        c1.gridwidth = 2; // 横跨两列
        panelMain.add(panelCmd, c1);


        // 设置 panelResults
        JPanel actionPanel = buildActionPanel(testkitWindow);
        c1.gridy = 1;
        c1.weightx = 1;
        c1.weighty = 0;
        c1.gridwidth = 2; // 不再跨列
        c1.fill = GridBagConstraints.NONE;
        c1.anchor = GridBagConstraints.WEST;
        panelMain.add(actionPanel, c1);

        // 设置 panelResults
        JPanel panelResults = buildResultPanel(testkitWindow);
        c1.anchor = GridBagConstraints.CENTER;
        c1.fill = GridBagConstraints.BOTH;
        c1.weighty = 0.8; // 垂直方向占70%
        c1.gridy = 2;
        c1.gridwidth = 2; // 不再跨列
        panelMain.add(panelResults, c1);

        // 设置 closePanel
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        closePanel.add(closeButton);

        c1.fill = GridBagConstraints.NONE;
        c1.anchor = GridBagConstraints.SOUTHEAST; // 将按钮放置在东南角
        c1.weightx = 0;  // 不占据额外空间
        c1.weighty = 0;
        c1.gridx = 1;    // 放在第二列
        c1.gridy = 3;    // 放在第三行
        panelMain.add(closePanel, c1);

        add(panelMain);
        pack();
        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screenSize.width * 0.7), (int) (screenSize.height * 0.7));
        setLocationRelativeTo(null);
    }

    private JPanel buildActionPanel(TestkitToolWindow window) {
        JPanel actionResults = new JPanel();
        actionResults.setLayout(new BoxLayout(actionResults, BoxLayout.X_AXIS)); // 顺序排列（水平）

        JButton parseButton = new JButton("Parse");
        // ... parseButton 的监听器代码保持不变 ...
        parseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = inputTextArea.getText();
                CurlEntity curlEntity = null;
                try {
                    if (StringUtils.isNotBlank(text)) {
                        curlEntity = CurlParserUtil.parse(text.trim());
                    }
                } catch (Throwable ex) {
                    TestkitHelper.alert(window.getProject(), Messages.getErrorIcon(), "Parse curl error,\n" + ex.getMessage());
                    return;
                }
                urlField.setText(curlEntity == null || curlEntity.getUrl() == null ? "" : curlEntity.getUrl());
                methodField.setText(curlEntity == null || curlEntity.getMethod() == null ? "" : curlEntity.getMethod().toString());
                headersField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getHeaders()));
                paramsField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getUrlParams()));
                bodyField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getBody()));
            }
        });

        actionResults.add(parseButton);
        return actionResults;
    }

    private JPanel buildResultPanel(TestkitToolWindow window) {
        JPanel panelResults = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelResults.setLayout(gridbag);

        JPanel panelRequestTypeUrl = buildRetUrlPanel();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        panelResults.add(panelRequestTypeUrl, c);

        JPanel panelParamsHeadersBody = new JPanel(new GridLayout(1, 3));
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 1;
        panelResults.add(panelParamsHeadersBody, c);

        paramsField = new JTextArea();
        paramsField.setEditable(false);
        JPanel panelParams = createLabelTextFieldPanel(window, "Params", paramsField);
        headersField = new JTextArea();
        headersField.setEditable(false);
        JPanel panelHeaders = createLabelTextFieldPanel(window, "Headers", headersField);
        bodyField = new JTextArea();
        bodyField.setEditable(false);
        JPanel panelBody = createLabelTextFieldPanel(window, "Body", bodyField);

        panelParamsHeadersBody.add(panelHeaders);
        panelParamsHeadersBody.add(panelParams);
        panelParamsHeadersBody.add(panelBody);
        return panelResults;
    }

    private @NotNull JPanel buildRetUrlPanel() {
        JPanel panelRequestTypeUrl = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelRequestTypeUrl.setLayout(gridbag);

        methodField = new JTextField(5);
        methodField.setEditable(false);
        urlField = new JTextField(20);
        urlField.setEditable(false);

// Add methodField
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        panelRequestTypeUrl.add(methodField, c);

// Add urlField
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        panelRequestTypeUrl.add(urlField, c);
        return panelRequestTypeUrl;
    }


    private @NotNull JPanel buildCmdPanel(TestkitToolWindow window) {
        JPanel panelCmd = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelCmd.setLayout(gridbag);

//        JLabel curlLabel = new JLabel("Curl:");
        inputTextArea = new JBTextArea(5, 20);
        inputTextArea.getEmptyText().setText("input your curl and click right button to parse");

        // 添加文本框：水平和垂直扩展
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 1; // 占用水平剩余空间
        c.weighty = 1; // 关键！分配垂直剩余空间
        c.gridx = 0;
        c.gridy = 0;
        JScrollPane scrollPane = new JBScrollPane(inputTextArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Curl Input")); // 可选：去除滚动面板边框
        panelCmd.add(scrollPane, c);
        // 可选：移除面板的默认边框
//        panelCmd.setBorder(BorderFactory.createEmptyBorder());
        return panelCmd;
    }

    private JPanel createLabelTextFieldPanel(TestkitToolWindow window, String labelText, JTextArea field) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(labelText);
        JButton copyButton = new JButton(AllIcons.Actions.Copy);
        copyButton.setToolTipText("Copy this");
        Dimension preferredSize = new Dimension(30, 30);
        copyButton.setPreferredSize(preferredSize);
        copyButton.setMaximumSize(preferredSize);
        copyButton.setMinimumSize(preferredSize);
        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TestkitHelper.copyToClipboard(window.getProject(), field.getText(), labelText + " was Copied");
            }
        });


        // 配置 JTextComponent（如 JTextArea）
        field.setLineWrap(false);
//        ((JTextArea) field).setLineWrap(true); // 启用自动换行
        field.setWrapStyleWord(false); // 按单词换行

        // 将 JTextComponent 放入 JScrollPane
        JScrollPane scrollPane = new JBScrollPane(field);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); // 允许横向滚动

        // 顶部面板
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        northPanel.add(label);
        northPanel.add(copyButton);
        if ("Params".equals(labelText) || "Body".equals(labelText)) {
            JButton adapterButton = new JButton(ADAPTER_INPUT_ICON);
            adapterButton.setToolTipText("use " + labelText + " adapter to input params");
            adapterButton.setPreferredSize(preferredSize);
            adapterButton.setMaximumSize(preferredSize);
            adapterButton.setMinimumSize(preferredSize);
            northPanel.add(adapterButton);
            adapterButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            if (StringUtils.isBlank(field.getText())) {
                                return;
                            }
                            Map<String, String> parse;
                            try {
                                parse = JSON.parseObject(field.getText(), new TypeReference<Map<String, String>>() {
                                });
                            } catch (Exception ex) {
                                TestkitHelper.notify(window.getProject(), NotificationType.ERROR, "the " + labelText + " is not json");
                                return;
                            }


                            BasePluginTool nowTool = window.getNowTool();
                            if (nowTool == null) {
                                TestkitHelper.notify(window.getProject(), NotificationType.WARNING, "not find selected tool");
                                return;
                            }
                            String templateText = nowTool.getInputEditorTextField().getText();
                            JSONObject template;
                            try {
                                template = JSON.parseObject(templateText);
                            } catch (Exception ex) {
                                TestkitHelper.notify(window.getProject(), NotificationType.ERROR, "input is not json, pls check");
                                return;
                            }

                            if (template == null) {
                                TestkitHelper.notify(window.getProject(), NotificationType.WARNING, "input is empty, pls init first");
                                return;
                            }
                            updateTemplate(template, parse);
                            nowTool.getInputEditorTextField().setText(JsonUtil.formatObj(template));
                            TestkitHelper.notify(window.getProject(), NotificationType.INFORMATION, "use " + labelText + " adapter to input params success");
                        }
                    });

                }
            });
        }

        // 添加到主面板
        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }


    public static void updateTemplate(JSONObject template, Map<String, String> params) {
        if (template == null || template.isEmpty()) {
            return;
        }
        for (String key : params.keySet()) {
            updateJsonObject(template, key, params.get(key));
        }
    }

    private static void updateJsonObject(Object json, String keyToUpdate, String newValue) {
        if (json instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) json;
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                if (value instanceof JSONObject) {
                    // 递归处理嵌套的 JSONObject
                    updateJsonObject(value, keyToUpdate, newValue);
                } else if (value instanceof JSONArray) {
                    // 处理 JSONArray
                    updateJsonObject(value, keyToUpdate, newValue);
                } else if (key.equals(keyToUpdate) && value == null) {
                    // 如果键匹配，则更新值
                    jsonObject.put(key, newValue);
                }
            }
        } else if (json instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) json;
            for (int i = 0; i < jsonArray.size(); i++) {
                // 对数组中的每个元素递归处理
                updateJsonObject(jsonArray.get(i), keyToUpdate, newValue);
            }
        }
    }
}
