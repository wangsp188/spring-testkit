package com.nb.tools.mybatis_sql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.sql.dialects.dateTime.SqlDtLanguage;
import com.intellij.sql.dialects.mysql.MysqlBaseMetaLanguage;
import com.intellij.sql.psi.SqlLanguage;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.view.PluginToolWindow;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Map;

import static org.apache.ibatis.scripting.xmltags.ForEachSqlNode.ITEM_PREFIX;

public class MybatisSqlTool extends BasePluginTool implements ActionTool {

    private JComboBox<XmlTagAction> actionComboBox;


    private JRadioButton prepareRadioButton;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public MybatisSqlTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }

    @Override
    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel visibleAppLabel = new JLabel("action:");
        topPanel.add(visibleAppLabel);
        actionComboBox = buildActionComboBox();
        // Populate methodComboBox with method names
        topPanel.add(actionComboBox);

        actionComboBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                triggerChangeAction();
            }
        });


        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("build sql");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleAction(PluginToolEnum.MYBATIS_SQL.getCode());
            }
        });


        topPanel.add(runButton);

        // Add the radio button
        prepareRadioButton = new JRadioButton();
        prepareRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (prepareRadioButton.isSelected()) {
                    prepareRadioButton.setText("Prepared");
                    prepareRadioButton.setToolTipText("build PreparedSql");
                } else {
                    prepareRadioButton.setText("Final");
                    prepareRadioButton.setToolTipText("build FinalSql");
                }
            }
        });
        prepareRadioButton.setSelected(true);
        topPanel.add(prepareRadioButton);
        return topPanel;
    }

    @Override
    protected JPanel createOutputPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        outputTextArea = new LanguageTextField(SqlLanguage.INSTANCE, getProject(), "", false);
        bottomPanel.add(new JBScrollPane(outputTextArea), BorderLayout.CENTER);
        return bottomPanel;
    }

    private void handleAction(String action) {
        String jsonInput = inputEditorTextField.getDocument().getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            outputTextArea.setText("参数框不可为空");
            return;
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonInput);
        } catch (Exception ex) {
            outputTextArea.setText("参数必须是json数组");
            return;
        }

        inputEditorTextField.setText(JSONObject.toJSONString(jsonObject, true));
        XmlTagAction selectedItem = (XmlTagAction) actionComboBox.getSelectedItem();
        if (selectedItem == null) {
            outputTextArea.setText("未选中节点，请先选中");
            return;
        }
        String xmlContent = selectedItem.getXmlTag().getParent().getContainingFile().getText();
        String statementId = selectedItem.getXmlTag().getAttributeValue("id");
        outputTextArea.setText("wait...");
        new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {
                return SqlGenerator.generateSql(xmlContent, statementId, prepareRadioButton.isSelected(), jsonObject);
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    try {
                        String sql = get();
                        if (sql == null) {
                            return;
                        }
                        outputTextArea.setText(sql);
                    } catch (Throwable ex) {
                        outputTextArea.setText(getStackTrace(ex));
                    }
                });
            }
        }.execute();

    }


    @Override
    public void onSwitchAction(PsiElement psiElement) {
        if (!(psiElement instanceof XmlTag)) {
            Messages.showMessageDialog(getProject(),
                    "un support element",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        XmlTag xmlTag = (XmlTag) psiElement;
        XmlTagAction xmlAction = (XmlTagAction) actionComboBox.getSelectedItem();
        if (xmlAction != null && xmlAction.getXmlTag().equals(xmlTag)) {
            return;
        }

        // 检查下拉框中是否已经包含当前方法的名称
        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            if (actionComboBox.getItemAt(i).toString().equals(buildXmlTagKey(xmlTag))) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }
        // 如果当前下拉框没有此选项，则新增该选项
        XmlTagAction item = new XmlTagAction(xmlTag);
        actionComboBox.addItem(item);
        actionComboBox.setSelectedItem(item);
    }


    private void triggerChangeAction() {
        // 获取当前选中的值
        XmlTagAction xmlTagAction = (XmlTagAction) actionComboBox.getSelectedItem();
        if (xmlTagAction == null) {
            inputEditorTextField.setText("[]");
            return;
        }
        inputEditorTextField.setText("{}");
    }


}
