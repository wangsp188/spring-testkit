package com.fling.tools.mapper_sql;

import com.alibaba.fastjson.JSONObject;
import com.fling.tools.ToolHelper;
import com.fling.tools.method_call.MethodCallIconProvider;
import com.fling.util.Container;
import com.fling.util.JsonUtil;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.sql.psi.SqlLanguage;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Supplier;

public class MapperSqlTool extends BasePluginTool {

    public static final Icon FLING_SQL_DISABLE_ICON = IconLoader.getIcon("/icons/fling-sql-disable.svg", MapperSqlIconProvider.class);


    public static final Icon REPLACE_DISABLE_ICON = IconLoader.getIcon("/icons/replace-disable.svg", MethodCallIconProvider.class);
    public static final Icon REPLACE_ICON = IconLoader.getIcon("/icons/replace.svg", MethodCallIconProvider.class);


    private JComboBox<ToolHelper.XmlTagAction> actionComboBox;


    private JToggleButton replaceParamsButton;

    {
        this.tool = PluginToolEnum.FLEXIBLE_TEST;
    }

    public MapperSqlTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
    }

    @Override
    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(FLING_SQL_DISABLE_ICON,FLING_SQL_DISABLE_ICON, "<strong>mapper-sql</strong><br>\n" +
                "<ul>\n" +
                "    <li>mapper的xml文件内 sql标签</li>\n" +
                "    <li>select/insert/update/delete</li>\n" +
                "</ul>",topPanel, new ActionListener() {

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
        // Add the radio button
        replaceParamsButton = new JToggleButton(REPLACE_DISABLE_ICON,false);
        replaceParamsButton.setPreferredSize(new Dimension(32,32));
        replaceParamsButton.setToolTipText("Build preparedSql");
        replaceParamsButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (replaceParamsButton.isSelected()) {
                    replaceParamsButton.setIcon(REPLACE_ICON);
                    replaceParamsButton.setToolTipText("Build finalSql");

                } else {
                    replaceParamsButton.setIcon(REPLACE_DISABLE_ICON);
                    replaceParamsButton.setToolTipText("Build preparedSql");
                }
            }
        });
        topPanel.add(replaceParamsButton,gbc);

        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("Build sql");
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
                        ToolHelper.XmlTagAction selectedItem = (ToolHelper.XmlTagAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            return "please select tag";
                        }
                        selectedItem.setArgs(jsonInput);
                        String xmlContent = selectedItem.getXmlTag().getParent().getContainingFile().getText();
                        String statementId = selectedItem.getXmlTag().getAttributeValue("id");
                        System.err.println("xml: " + xmlContent + ", statementId: " + statementId + ", params:" + jsonObject.toJSONString());
                        try {
                            String sql = MapperGenerator.generateSql(xmlContent, statementId, !replaceParamsButton.isSelected(), jsonObject);
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

        gbc.gridx = 4;
        topPanel.add(runButton,gbc);

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
        String oldText = inputEditorTextField.getText();
        inputEditorTextField.setText("init params ...");
        String xmlContent = xmlTagAction.getXmlTag().getParent().getContainingFile().getText();
        String statementId = xmlTagAction.getXmlTag().getAttributeValue("id");

        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                return MapperGenerator.buildParamTemplate(xmlContent, statementId);
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
                    inputEditorTextField.setText(JsonUtil.formatObj(initParams));
                } catch (Throwable e) {
                    e.printStackTrace();
                    inputEditorTextField.setText("{\"Fling\":\"init fail, You're on your own\"}");
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
