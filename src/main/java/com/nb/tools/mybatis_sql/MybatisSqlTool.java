package com.nb.tools.mybatis_sql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlTag;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.util.HttpUtil;
import com.nb.view.PluginToolWindow;
import com.nb.view.VisibleApp;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.apache.ibatis.scripting.xmltags.ForEachSqlNode.ITEM_PREFIX;

public class MybatisSqlTool extends BasePluginTool implements ActionTool {

    private JComboBox<XmlTagAction> actionComboBox;


    private JRadioButton prepareRadioButton;

    private XmlTagAction lastXmlTagAction;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public MybatisSqlTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }

    @Override
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionComboBox = new ComboBox<>();
        actionComboBox.setPreferredSize(new Dimension(280, 32));
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
                handleAction("mybatis-sql");
            }
        });


        topPanel.add(runButton);

        // Add the radio button
        prepareRadioButton = new JRadioButton();
        prepareRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (prepareRadioButton.isSelected()) {
                    prepareRadioButton.setText("Final");
                    prepareRadioButton.setToolTipText("build final sql");
                } else {
                    prepareRadioButton.setText("Prepared");
                    prepareRadioButton.setToolTipText("build PreparedSql");
                }
            }
        });
        prepareRadioButton.setSelected(true);
        topPanel.add(prepareRadioButton);
        return topPanel;
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
        if (selectedItem==null) {
            outputTextArea.setText("未选中节点，请先选中");
            return;
        }
        outputTextArea.setText("......");

        try {
            String xmlContent = selectedItem.getXmlTag().getParent().getContainingFile().getText();
            String statementId = selectedItem.getXmlTag().getAttributeValue("id");

            String sql = generateSql(xmlContent, statementId, !prepareRadioButton.isSelected(),jsonObject);
            outputTextArea.setText(sql);
        } catch (Throwable ex) {
            outputTextArea.setText("Error: " + getStackTrace(ex.getCause()));
        }
    }


    @Override
    public void onSwitchAction(PsiElement psiElement) {
        if(!(psiElement instanceof XmlTag)) {
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

    public static String generateSql(String xmlContent, String statementId, boolean prepared,JSONObject parameters) {
        Configuration configuration = new Configuration();
        XPathParser parser = new XPathParser(xmlContent, false, configuration.getVariables(), new XMLMapperEntityResolver());
        XNode mapperNode = parser.evalNode("/mapper");

        // Parse <sql> tags and store them in a map
        Map<String, XNode> sqlFragments = new HashMap<>();
        for (XNode node : mapperNode.getChildren()) {
            if ("sql".equals(node.getName())) {
                String id = node.getStringAttribute("id");
                sqlFragments.put(id, node);
            }
        }

        // Find the specified SQL statement
        XNode statementNode = null;
        for (XNode node : mapperNode.getChildren()) {
            if (statementId.equals(node.getStringAttribute("id"))) {
                statementNode = node;
                break;
            }
        }

        if (statementNode == null) {
            throw new IllegalArgumentException("Statement not found: " + statementId);
        }

        // Handle <include> tags
        statementNode = applyIncludes(statementNode, sqlFragments);

        // Create SQL source and generate SQL
        String sqlText = statementNode.getStringBody().trim();
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(configuration, sqlText, JSONObject.class);
        BoundSql boundSql = sqlSource.getBoundSql(parameters);

        String preparedSQL = boundSql.getSql();
        if(prepared){
            return preparedSQL;
        }

        Splitter splitter = Splitter.on("?");
        Iterable<String> splitIterable = splitter.split(preparedSQL);
        java.util.List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        for (String str : splitIterable) {
            if (StringUtils.isBlank(str)) {
                continue;
            }
            stringBuilder.append(str);
            ParameterMapping paramObj = parameterMappings.get(i);
            String fieldName = paramObj.getProperty();
            Object value;
            // 这里 ITEM_PREFIX 见处理<foreach>的 org.apache.ibatis.scripting.xmltags.ForEachSqlNode.ITEM_PREFIX
            if (fieldName.startsWith(ITEM_PREFIX)) {
                fieldName = fieldName.substring(7);
                int indexIndex = fieldName.lastIndexOf('_');
                int valueIndex = NumberUtils.toInt(fieldName.substring(indexIndex + 1));
                fieldName = fieldName.substring(0, indexIndex);
                JSONArray listValue = parameters.getJSONArray(fieldName);
                value = listValue.get(valueIndex);
            } else {
                value = parameters.get(fieldName);
            }
            stringBuilder.append(value);
            i++;
        }
        return stringBuilder.toString();
    }

    /**
     * Recursively applies includes, replacing <include refid="..."> tags with their corresponding SQL fragments.
     */
    private static XNode applyIncludes(XNode node, Map<String, XNode> sqlFragments) {
        for (XNode child : node.getChildren()) {
            if ("include".equals(child.getName())) {
                String refid = child.getStringAttribute("refid");
                XNode fragment = sqlFragments.get(refid);
                if (fragment == null) {
                    throw new IllegalArgumentException("SQL fragment not found: " + refid);
                }

                // Replace the include node with the fragment nodes
                for (XNode fragmentChild : fragment.getChildren()) {
                    node.getChildren().add(fragmentChild);
                }

                // Remove the processed include node
                node.getChildren().remove(child);
            } else {
                // Recursively process child nodes
                applyIncludes(child, sqlFragments);
            }
        }
        return node;
    }

    private void triggerChangeAction() {
        // 获取当前选中的值
        XmlTagAction xmlTagAction = (XmlTagAction) actionComboBox.getSelectedItem();
        if (xmlTagAction == null) {
            inputEditorTextField.setText("[]");
            return;
        }
        if(lastXmlTagAction!=null && xmlTagAction==lastXmlTagAction){
            return;
        }
        inputEditorTextField.setText("{}");
        lastXmlTagAction = xmlTagAction;
    }


}
