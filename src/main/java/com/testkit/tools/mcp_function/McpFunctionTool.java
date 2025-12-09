package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.testkit.TestkitHelper;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.ToolHelper;
import com.testkit.util.JsonUtil;
import com.testkit.view.TestkitToolWindow;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class McpFunctionTool extends BasePluginTool {

    public static final Icon MCP_FUNCTION_ICON = IconLoader.getIcon("/icons/mcp.svg", McpFunctionTool.class);

    private JComboBox<ToolHelper.McpFunctionAction> actionComboBox;

    private JPanel formPanel;
    private JScrollPane formScrollPane;
    private final Map<String, JComponent> inputComponents = new LinkedHashMap<>();


    {
        this.tool = PluginToolEnum.MCP_FUNCTION;
    }

    public McpFunctionTool(TestkitToolWindow testkitToolWindow) {
        super(testkitToolWindow);
    }

    @Override
    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(MCP_FUNCTION_ICON, MCP_FUNCTION_ICON, "<strong>mcp-function</strong><br>\n" +
                "<ul>\n" +
                "    <li>anywhere</li>\n" +
                "    <li>anytime</li>\n" +
                "</ul>", topPanel, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.gridx = 4;  // 在 toolSwitchButton 之后
        gbc.gridy = 0;

        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("Execute this");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerLocalTask(runButton, AllIcons.Actions.Execute, "Execute mcp tool", new Supplier<String>() {
                    @Override
                    public String get() {
                        ToolHelper.McpFunctionAction selectedItem = (ToolHelper.McpFunctionAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            return "please select mcp function";
                        }
                        JSONObject args = new JSONObject();
                        String err = validateAndBuild(args);
                        if (err != null) {
                            return err;
                        }
                        selectedItem.setArgs(JsonUtil.formatObj(args));
                        McpServerDefinition.McpFunctionDefinition functionDefinition = selectedItem.getFunctionDefinition();
                        ToolExecutionResult ret = McpHelper.callTool(functionDefinition.getServerKey(), functionDefinition.getName(), args);
                        if (ret == null) {
                            return "null";
                        }
                        if (ret.isError()) {
                            return StringUtils.isBlank(ret.resultText())?"":ret.resultText();
                        }
                        Object result = ret.result();
                        if (result == null) {
                            if (StringUtils.isNotBlank(ret.resultText())) {
                                return ret.resultText();
                            }
                            return "null";
                        }

                        if(result instanceof String){
                            return result.toString();
                        }

                        String jsonString = JSONObject.toJSONString(result, SerializerFeature.WriteMapNullValue);
                        try {
                            Object parse = JSONObject.parse(jsonString);
                            if (!(parse instanceof JSONObject)) {
                                return jsonString;
                            }
                            JSONObject object = (JSONObject) parse;
                            if(object.getBooleanValue("success") && object.containsKey("data")){
                                Object data = object.get("data");
                                if(data instanceof JSONArray || data instanceof JSONObject){
                                    return JSON.toJSONString(data, SerializerFeature.WriteMapNullValue);
                                }
                                return String.valueOf(data);
                            }else if(object.containsKey("desc") && Objects.equals(object.getBoolean("success"),false)){
                                return object.getString("desc");
                            }
                            return jsonString;
                        } catch (Throwable ex) {
                            return jsonString;
                        }
                    }
                });
            }
        });

        gbc.gridx = 5;
        // 将 runButton 添加到容器面板（如果支持拦截器）
        addToInterceptorContainer(runButton, gbc);
        // 如果不支持拦截器，则添加到原面板
        if (interceptorContainerPanel == null) {
            topPanel.add(runButton, gbc);
        }

        return topPanel;
    }


    @Override
    protected JComponent createInputContent(JPanel inputPanel) {
        jsonInputField.setVisible(false);

        formPanel = new JPanel(new GridBagLayout());
        formScrollPane = new JBScrollPane(formPanel);
        formScrollPane.setBorder(BorderFactory.createTitledBorder("Input"));
        return formScrollPane;
    }

    @Override
    public boolean needAppBox() {
        return false;
    }

    @Override
    protected boolean canStore() {
        return false;
    }

    @Override
    public void onSwitchAction(PsiElement psiElement) {
        //psiElement 恒为null
        SwingUtilities.invokeLater(() -> {
            actionComboBox.removeAllItems();
            List<McpServerDefinition.McpFunctionDefinition> definitions = McpHelper.fetchDefinitions();
            for (McpServerDefinition.McpFunctionDefinition definition : definitions) {
                ToolHelper.McpFunctionAction item = new ToolHelper.McpFunctionAction(definition);
                actionComboBox.addItem(item);
            }
        });
    }


    private void triggerChangeAction() {
        // 获取当前选中的值
        ToolHelper.McpFunctionAction functionAction = (ToolHelper.McpFunctionAction) actionComboBox.getSelectedItem();
        if (functionAction == null || functionAction.getFunctionDefinition() == null || CollectionUtils.isEmpty(functionAction.getFunctionDefinition().getArgSchemas())) {
            renderForm(null, new JSONObject());
            return;
        }
        McpServerDefinition.McpFunctionDefinition functionDefinition = functionAction.getFunctionDefinition();
        JSONObject paramTemplate = functionDefinition.buildParamTemplate();
        validateAndBuild(paramTemplate);
        try {
            JSONObject jsonObject = JSONObject.parseObject(functionAction.getArgs());
            ToolHelper.migration(jsonObject, paramTemplate);
        } catch (Throwable e) {
        }
        renderForm(functionDefinition, paramTemplate);
    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox != null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }


    private void renderForm(McpServerDefinition.McpFunctionDefinition functionDefinition, JSONObject values) {
        if (formPanel == null) {
            return;
        }
        formPanel.removeAll();
        inputComponents.clear();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        List<McpServerDefinition.ArgSchema> schemas = functionDefinition == null ? Collections.emptyList() : functionDefinition.getArgSchemas();
        for (McpServerDefinition.ArgSchema schema : schemas) {
            String name = schema.name();
            String type = schema.type();
            boolean required = schema.required();
            String description = schema.description();

            JPanel row = new JPanel(new GridBagLayout());
            GridBagConstraints rbc = new GridBagConstraints();
            rbc.insets = JBUI.insets(2);
            rbc.fill = GridBagConstraints.HORIZONTAL;
            rbc.weightx = 0.0;
            rbc.gridx = 0;
            rbc.gridy = 0;

            boolean hiddenPrefix = Objects.equals(type, McpServerDefinition.ArgType.ENUM.getCode())
                    || Objects.equals(type, McpServerDefinition.ArgType.STRING.getCode())
                    || (Objects.equals(type, McpServerDefinition.ArgType.ARRAY.getCode()) && schema.typeExtension()!=null)
                    ;
            JLabel label = new JLabel((required ? "*" : "") + name + (hiddenPrefix ?"":" (" + type + ")"));
            if (description != null && !description.isEmpty()) {
                label.setToolTipText(description);
            }
            row.add(label, rbc);

            rbc.gridx = 1;
            rbc.weightx = 1.0;

            Object valueObj = values == null ? null : values.get(name);
            JComponent input = buildInputComponent(schema, valueObj);
            row.add(input, rbc);

            formPanel.add(row, gbc);
            gbc.gridy++;

            inputComponents.put(name, input);
        }

        // 占位撑开
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = gbc.gridy;
        filler.weightx = 1.0;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createGlue(), filler);

        formPanel.revalidate();
        formPanel.repaint();

    }

    private JComponent buildInputComponent(McpServerDefinition.ArgSchema schema, Object valueObj) {
        String type = schema.type();
        String name = schema.name();
        if (Objects.equals(type, McpServerDefinition.ArgType.BOOLEAN.getCode())) {
            ComboBox<String> comboBox = new ComboBox<>(new String[]{"true", "false"});
            String v = String.valueOf(Boolean.TRUE.equals(valueObj));
            if (valueObj instanceof Boolean) {
                v = Boolean.TRUE.equals(valueObj) ? "true" : "false";
            }
            comboBox.setSelectedItem(v);
            return comboBox;
        }
        if (Objects.equals(type, McpServerDefinition.ArgType.ENUM.getCode())) {
            java.util.List<?> enums = (java.util.List<?>) schema.typeExtension();
            String[] options = enums == null ? new String[]{} : enums.stream().map(String::valueOf).toArray(String[]::new);
            ComboBox<String> comboBox = new ComboBox<>(options);
            if (valueObj != null) {
                comboBox.setSelectedItem(String.valueOf(valueObj));
            }
            return comboBox;
        }
        // ARRAY 类型且有 typeExtension（可选列表）：使用多选 CheckBox
        if (Objects.equals(type, McpServerDefinition.ArgType.ARRAY.getCode())) {
            java.util.List<?> enums = (java.util.List<?>) schema.typeExtension();
            if (enums != null && !enums.isEmpty()) {
                JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
                Set<String> selectedSet = new HashSet<>();
                if (valueObj instanceof java.util.List) {
                    for (Object v : (java.util.List<?>) valueObj) {
                        selectedSet.add(String.valueOf(v));
                    }
                }
                for (Object enumVal : enums) {
                    String optionStr = String.valueOf(enumVal);
                    JCheckBox checkBox = new JCheckBox(optionStr);
                    checkBox.setSelected(selectedSet.contains(optionStr));
                    checkBoxPanel.add(checkBox);
                }
                JBScrollPane scrollPane = new JBScrollPane(checkBoxPanel);
                scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, 60));
                return scrollPane;
            }
        }
        if (Objects.equals(type, McpServerDefinition.ArgType.STRING.getCode())) {
            // 当属性名包含 sql 时，使用 SQL 语法高亮的输入框
            if (name != null && name.toLowerCase().contains("sql")) {
                Language language = com.intellij.openapi.fileTypes.PlainTextLanguage.INSTANCE;
                try {
                    language = (Language) Class.forName("com.intellij.sql.psi.SqlLanguage").getDeclaredField("INSTANCE").get(null);
                } catch (Throwable e) {
                    // SQL 插件未安装，fallback 到纯文本
                }
                LanguageTextField sqlField = new LanguageTextField(language, getProject(), valueObj == null ? "" : String.valueOf(valueObj), false);
                JBScrollPane scrollPane = new JBScrollPane(sqlField);
                // 设置为 5 行高度
                scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, 100));
                return scrollPane;
            }
            return new JBTextField(valueObj == null ? "" : String.valueOf(valueObj));
        }
        if (Objects.equals(type, McpServerDefinition.ArgType.NUMBER.getCode())
                || Objects.equals(type, McpServerDefinition.ArgType.INTEGER.getCode())) {
            JBTextField textField = new JBTextField(valueObj == null ? "" : String.valueOf(valueObj));
            return textField;
        }
        // 复杂类型使用 JSON 文本框
        LanguageTextField area = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        String text;
        try {
            text = valueObj == null ? "" : JsonUtil.formatObj(valueObj);
        } catch (Throwable ignore) {
            text = valueObj == null ? "" : String.valueOf(valueObj);
        }
        area.setText(text);
        return new JBScrollPane(area);
    }

    private String validateAndBuild(JSONObject out) {
        ToolHelper.McpFunctionAction functionAction = (ToolHelper.McpFunctionAction) actionComboBox.getSelectedItem();
        if (functionAction == null || functionAction.getFunctionDefinition() == null) {
            return "please select mcp function";
        }
        McpServerDefinition.McpFunctionDefinition functionDefinition = functionAction.getFunctionDefinition();
        List<McpServerDefinition.ArgSchema> schemas = functionDefinition.getArgSchemas();
        if (CollectionUtils.isEmpty(schemas)) {
            return null;
        }
        java.util.List<String> errors = new ArrayList<>();
        for (McpServerDefinition.ArgSchema schema : schemas) {
            String name = schema.name();
            String type = schema.type();
            boolean required = schema.required();
            JComponent comp = inputComponents.get(name);
            Object val = null;
            try {
                if (comp instanceof ComboBox) {
                    Object sel = ((ComboBox<?>) comp).getSelectedItem();
                    if (Objects.equals(type, McpServerDefinition.ArgType.BOOLEAN.getCode())) {
                        val = "true".equals(String.valueOf(sel));
                    } else {
                        val = sel == null ? null : String.valueOf(sel);
                    }
                } else if (comp instanceof JBTextField) {
                    String txt = ((JBTextField) comp).getText();
                    if (Objects.equals(type, McpServerDefinition.ArgType.STRING.getCode())) {
                        val = txt;
                    } else if (Objects.equals(type, McpServerDefinition.ArgType.INTEGER.getCode())) {
                        if (txt == null || txt.isBlank()) {
                            val = null;
                        } else {
                            try {
                                val = Integer.valueOf(txt.trim());
                            } catch (Throwable e) {
                                errors.add("'" + name + "' must be an integer.");
                            }
                        }
                    } else if (Objects.equals(type, McpServerDefinition.ArgType.NUMBER.getCode())) {
                        if (txt == null || txt.isBlank()) {
                            val = null;
                        } else {
                            try {
                                val = Double.valueOf(txt.trim());
                            } catch (Throwable e) {
                                errors.add("'" + name + "' must be a number.");
                            }
                        }
                    }
                } else if (comp instanceof JScrollPane) {
                    Component view = ((JScrollPane) comp).getViewport().getView();
                    if (view instanceof JPanel) {
                        // ARRAY 类型多选 CheckBox 面板
                        JPanel checkBoxPanel = (JPanel) view;
                        ArrayList<String> selectedValues = new ArrayList<>();
                        for (Component c : checkBoxPanel.getComponents()) {
                            if (c instanceof JCheckBox && ((JCheckBox) c).isSelected()) {
                                selectedValues.add(((JCheckBox) c).getText());
                            }
                        }
                        val = selectedValues;
                    } else if (view instanceof LanguageTextField) {
                        String txt = ((LanguageTextField) view).getText();
                        if (txt == null || txt.isBlank()) {
                            val = null;
                        } else {
                            // STRING 类型的 SQL 输入框：直接取文本
                            if (Objects.equals(type, McpServerDefinition.ArgType.STRING.getCode())) {
                                val = txt;
                            } else {
                                // 复杂类型：需要解析 JSON
                                try {
                                    val = com.alibaba.fastjson.JSON.parse(txt);
                                } catch (Throwable e) {
                                    errors.add("'" + name + "' must be valid JSON.");
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignore) {
            }

            if (required && (val == null || (val instanceof String && ((String) val).isBlank()))) {
                errors.add("Required argument '" + name + "' is missing.");
            }
            out.put(name, val);
        }
        if (!errors.isEmpty()) {
            return String.join("\n", errors);
        }
        return null;
    }

    @Override
    protected void handleCopyInput(com.intellij.openapi.actionSystem.AnActionEvent e) {
        JSONObject out = new JSONObject();
        String err = validateAndBuild(out);
        if (err != null) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, err);
            return;
        }
        TestkitHelper.copyToClipboard(getProject(), JsonUtil.formatObj(out), "Input was copied");
    }

}
