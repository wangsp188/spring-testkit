package com.testkit.tools.mcp_function;

import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.JBUI;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.ToolHelper;
import com.testkit.util.JsonUtil;
import com.testkit.view.TestkitToolWindow;
import org.apache.commons.collections.CollectionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Supplier;

public class McpFunctionTool extends BasePluginTool {

    public static final Icon MCP_FUNCTION_ICON = IconLoader.getIcon("/icons/mcp-disable.svg", McpFunctionTool.class);

    private JComboBox<ToolHelper.McpFunctionAction> actionComboBox;


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
        gbc.gridx = 3;
        gbc.gridy = 0;

        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("Execute this");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerLocalTask(runButton, AllIcons.Actions.Execute, "Execute MCP function", new Supplier<String>() {
                    @Override
                    public String get() {
                        String jsonInput = jsonInputField.getDocument().getText();
                        if (jsonInput == null || jsonInput.isBlank()) {
                            return "input parameter is blank";
                        }
                        JSONObject jsonObject;
                        try {
                            jsonObject = JSONObject.parseObject(jsonInput);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            return "input parameter must be json object, " + ex.getMessage();
                        }
                        ToolHelper.McpFunctionAction selectedItem = (ToolHelper.McpFunctionAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            return "please select mcp function";
                        }
                        selectedItem.setArgs(jsonInput);
                        McpFunctionDefinition functionDefinition = selectedItem.getFunctionDefinition();
                        return McpHelper.callTool(functionDefinition.getServerKey(), functionDefinition.getName(), jsonObject);
                    }
                });
            }
        });

        gbc.gridx = 4;
        topPanel.add(runButton, gbc);

        return topPanel;
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
            List<McpFunctionDefinition> definitions = McpHelper.fetchDefinitions();
            for (McpFunctionDefinition definition : definitions) {
                ToolHelper.McpFunctionAction item = new ToolHelper.McpFunctionAction(definition);
                actionComboBox.addItem(item);
            }
        });
    }


    private void triggerChangeAction() {
        // 获取当前选中的值
        ToolHelper.McpFunctionAction functionAction = (ToolHelper.McpFunctionAction) actionComboBox.getSelectedItem();
        if (functionAction == null || functionAction.getFunctionDefinition() == null || CollectionUtils.isEmpty(functionAction.getFunctionDefinition().getArgSchemas())) {
            jsonInputField.setText("{}");
            return;
        }
        McpFunctionDefinition functionDefinition = functionAction.getFunctionDefinition();
        String oldText = jsonInputField.getText();
        jsonInputField.setText("init params ...");
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                return functionDefinition.buildParamTemplate();
            }

            @Override
            protected void done() {
                try {
                    JSONObject initParams = get();
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(oldText.trim());
                        ToolHelper.migration(jsonObject, initParams);
                    } catch (Throwable e) {
                    }
                    jsonInputField.setText(JsonUtil.formatObj(initParams));
                } catch (Throwable e) {
                    e.printStackTrace();
                    jsonInputField.setText("init fail, " + e.getMessage());
                }
            }
        }.execute();

    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox != null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }


}
