package com.fling.tools.mybatis_sql;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.tools.ToolHelper;
import com.fling.util.Container;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.sql.psi.SqlLanguage;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Supplier;

public class MybatisSqlTool extends BasePluginTool {

    private JComboBox<ToolHelper.XmlTagAction> actionComboBox;


    private JRadioButton prepareRadioButton;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public MybatisSqlTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }

    @Override
    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionComboBox = addActionComboBox("<html>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<strong>mybatis-sql</strong><br>\n" +
                "<ul>\n" +
                "    <li>mapper的xml文件内 sql标签</li>\n" +
                "    <li>select/insert/update/delete</li>\n" +
                "</ul>\n" +
                "</html>",topPanel, new ActionListener() {

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
                triggerLocalTask(runButton, AllIcons.Actions.Execute, new Supplier<String>() {
                    @Override
                    public String get() {
                        String jsonInput = inputEditorTextField.getDocument().getText();
                        if (jsonInput == null || jsonInput.isBlank()) {
                            return "input parameter is blank";
                        }
                        JSONObject jsonObject;
                        try {
                            jsonObject = JSONObject.parseObject(jsonInput);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            return "input parameter must be json object";
                        }
                        String jsonString = JSONObject.toJSONString(jsonObject, true);
                        inputEditorTextField.setText(jsonString);
                        ToolHelper.XmlTagAction selectedItem = (ToolHelper.XmlTagAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            return "pls select method";
                        }
                        selectedItem.setArgs(jsonString);
                        String xmlContent = selectedItem.getXmlTag().getParent().getContainingFile().getText();
                        String statementId = selectedItem.getXmlTag().getAttributeValue("id");
                        System.err.println("xml: " + xmlContent + ", statementId: " + statementId + ", params:" + jsonObject.toJSONString());
                        try {
                            String sql = MybatisGenerator.generateSql(xmlContent, statementId, prepareRadioButton.isSelected(), jsonObject);
                            if (StringUtils.isBlank(sql)) {
                                return sql;
                            }

                            Container<String> formattedSql = new Container<>();
                            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                                PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText("temp.sql", SqlLanguage.INSTANCE, sql);
                                CodeStyleManager.getInstance(getProject()).reformat(psiFile);
                                Document formattedDocument = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
                                if (formattedDocument != null) {
                                    formattedSql.set(formattedDocument.getText());
                                }
                            });
                            return formattedSql.get();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
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
    public boolean needAppBox() {
        return false;
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
        ToolHelper.XmlTagAction xmlAction = (ToolHelper.XmlTagAction) actionComboBox.getSelectedItem();
        if (xmlAction != null && xmlAction.getXmlTag().equals(xmlTag)) {
            return;
        }

        // 检查下拉框中是否已经包含当前方法的名称
        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            if (actionComboBox.getItemAt(i).toString().equals(ToolHelper.buildXmlTagKey(xmlTag))) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }
        // 如果当前下拉框没有此选项，则新增该选项
        ToolHelper.XmlTagAction item = new ToolHelper.XmlTagAction(xmlTag);
        actionComboBox.addItem(item);
        actionComboBox.setSelectedItem(item);
    }


    private void triggerChangeAction() {
        // 获取当前选中的值
        ToolHelper.XmlTagAction xmlTagAction = (ToolHelper.XmlTagAction) actionComboBox.getSelectedItem();
        if (xmlTagAction == null || xmlTagAction.getXmlTag() == null || !xmlTagAction.getXmlTag().isValid()) {
            inputEditorTextField.setText("{}");
            return;
        }
        if (xmlTagAction.getArgs() != null) {
            inputEditorTextField.setText(xmlTagAction.getArgs());
            return;
        }
        inputEditorTextField.setText("init params ...");
        String xmlContent = xmlTagAction.getXmlTag().getParent().getContainingFile().getText();
        String statementId = xmlTagAction.getXmlTag().getAttributeValue("id");

        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                return MybatisGenerator.buildParamTemplate(xmlContent, statementId);
            }

            @Override
            protected void done() {
                try {
                    JSONObject initParams = get();
                    inputEditorTextField.setText(JSONObject.toJSONString(initParams, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue));
                } catch (Throwable e) {
                    e.printStackTrace();
                    inputEditorTextField.setText("{\"nb\":\"init fail, You're on your own\"}");
                }
            }
        }.execute();

    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox!=null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }


}
