package com.fling.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fling.FlingHelper;
import com.fling.util.JsonUtil;
import com.fling.util.curl.CurlEntity;
import com.fling.util.curl.CurlParserUtil;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Map;

public class CurlDialog extends JDialog {

    public static final Icon PARSE_CURL_ICON = IconLoader.getIcon("/icons/curl.svg", CurlDialog.class);
    public static final Icon ADAPTER_INPUT_ICON = IconLoader.getIcon("/icons/adapter-input.svg", CurlDialog.class);


    private JTextArea inputTextArea;
    private JTextField urlField, methodField;
    private JTextArea paramsField, headersField, bodyField;

    public CurlDialog(FlingToolWindow flingWindow) {
        super((Frame) null, "Curl Parser", true);

        JPanel panelMain = new JPanel(new GridBagLayout());
        GridBagConstraints c1 = new GridBagConstraints();

        // 设置 panelCmd
        JPanel panelCmd = buildCmdPanel(flingWindow);
        c1.fill = GridBagConstraints.BOTH;  // 使组件在水平方向和垂直方向都拉伸
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 0.2; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
        c1.gridwidth = 2; // 横跨两列
        panelMain.add(panelCmd, c1);

        // 设置 panelResults
        JPanel panelResults = buildResultPanel(flingWindow);
        c1.weighty = 0.8; // 垂直方向占70%
        c1.gridy = 1;
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
        c1.gridy = 2;    // 放在第三行
        panelMain.add(closePanel, c1);

        add(panelMain);
        pack();
        // 设置对话框的大小与显示位置
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screenSize.width*0.7), (int) (screenSize.height*0.7));
        setLocationRelativeTo(null);
    }

    private JPanel buildResultPanel(FlingToolWindow window) {
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

    private @NotNull JPanel buildCmdPanel(FlingToolWindow window) {
        JPanel panelCmd = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelCmd.setLayout(gridbag);

        JLabel curlLabel = new JLabel("Curl:");
        inputTextArea = new JTextArea(5, 20);

        addPlaceholderToTextArea(inputTextArea, "input your curl and click right button to parse");
        JButton parseButton = new JButton(PARSE_CURL_ICON);
        Dimension preferredSize = new Dimension(30, 30);
        parseButton.setPreferredSize(preferredSize);
//        parseButton.setMaximumSize(preferredSize);
//        parseButton.setMinimumSize(preferredSize);
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
                    FlingHelper.notify(window.getProject(), NotificationType.ERROR, "parse curl error");
                }
                urlField.setText(curlEntity == null || curlEntity.getUrl() == null ? "" : curlEntity.getUrl());
                methodField.setText(curlEntity == null || curlEntity.getMethod() == null ? "" : curlEntity.getMethod().toString());
                headersField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getHeaders()));
                paramsField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getUrlParams()));
                bodyField.setText(curlEntity == null ? "" : JsonUtil.formatObj(curlEntity.getBody()));
            }
        });

// Add label
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        panelCmd.add(curlLabel, c);

// Add textarea
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        panelCmd.add(new JScrollPane(inputTextArea), c);

// Add button
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 2;
        c.gridy = 0;
        panelCmd.add(parseButton, c);
        return panelCmd;
    }

    private static void addPlaceholderToTextArea(JTextArea textArea, String placeholder) {
        // Set initial placeholder text
        textArea.setText(placeholder);
        textArea.setForeground(Color.GRAY);

        Color old = textArea.getForeground();

        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                // Clear the placeholder text when the text area gains focus
                if (textArea.getText().equals(placeholder)) {
                    textArea.setText("");
                    textArea.setForeground(old);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Restore the placeholder text if the text area is empty
                if (textArea.getText().isEmpty()) {
                    textArea.setText(placeholder);
                    textArea.setForeground(Color.GRAY);
                }
            }
        });
    }

    private JPanel createLabelTextFieldPanel(FlingToolWindow window, String labelText, JTextArea field) {
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
                FlingHelper.copyToClipboard(window.getProject(), field.getText(), labelText + " was Copied");
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
                                FlingHelper.notify(window.getProject(), NotificationType.ERROR, "the " + labelText + " is not json");
                                return;
                            }


                            BasePluginTool nowTool = window.getNowTool();
                            if (nowTool == null) {
                                FlingHelper.notify(window.getProject(), NotificationType.WARNING, "not find selected tool");
                                return;
                            }
                            String templateText = nowTool.inputEditorTextField.getText();
                            JSONObject template;
                            try {
                                template = JSON.parseObject(templateText);
                            } catch (Exception ex) {
                                FlingHelper.notify(window.getProject(), NotificationType.ERROR, "input is not json, pls check");
                                return;
                            }

                            if (template == null) {
                                FlingHelper.notify(window.getProject(), NotificationType.WARNING, "input is empty, pls init first");
                                return;
                            }
                            updateTemplate(template, parse);
                            nowTool.inputEditorTextField.setText(JsonUtil.formatObj(template));
                            FlingHelper.notify(window.getProject(), NotificationType.INFORMATION, "use " + labelText + " adapter to input params success");
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
