package com.nb.tools.mybatis_sql;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.sql.psi.SqlLanguage;
import com.nb.tools.ActionTool;
import com.nb.tools.BasePluginTool;
import com.nb.tools.PluginToolEnum;
import com.nb.view.PluginToolWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.concurrent.atomic.AtomicReference;

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

//    @Override
//    protected JPanel createOutputPanel() {
//        JPanel bottomPanel = new JPanel(new BorderLayout());
//        outputTextArea = new LanguageTextField(SqlLanguage.INSTANCE, getProject(), "", false);
//        bottomPanel.add(new JBScrollPane(outputTextArea), BorderLayout.CENTER);
//        return bottomPanel;
//    }

    private void handleAction(String action) {
        String jsonInput = inputEditorTextField.getDocument().getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            outputTextPane.setText("input parameter is blank");
            return;
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonInput);
        } catch (Exception ex) {
            outputTextPane.setText("input parameter must be json array");
            return;
        }


        inputEditorTextField.setText(JSONObject.toJSONString(jsonObject, true));
        XmlTagAction selectedItem = (XmlTagAction) actionComboBox.getSelectedItem();
        if (selectedItem == null) {
            outputTextPane.setText("pls select tag");
            return;
        }
        String xmlContent = selectedItem.getXmlTag().getParent().getContainingFile().getText();
        String statementId = selectedItem.getXmlTag().getAttributeValue("id");
        outputTextPane.setText("wait...");
//        new SwingWorker<String, Void>() {
//
//            @Override
//            protected String doInBackground() throws Exception {
//                System.err.println("xml: " + xmlContent + ", statementId: " + statementId + ", params:" + jsonObject.toJSONString());
//                return MybatisGenerator.generateSql(xmlContent, statementId, prepareRadioButton.isSelected(), jsonObject);
//            }
//
//            @Override
//            protected void done() {
//                try {
//                    String sql = get();
//                    if (sql == null) {
//                        SwingUtilities.invokeLater(() -> outputTextPane.setText("No SQL was generated."));
//                        return;
//                    }
//
//                    Project project = getProject();
//                    AtomicReference<String> format = new AtomicReference<>(sql);
//                    // Execute the write action directly on the right thread
//                    WriteCommandAction.runWriteCommandAction(project, () -> {
//                        ApplicationManager.getApplication().runReadAction(() -> {
//                            PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("temp.sql", SqlLanguage.INSTANCE, sql);
//                            CodeStyleManager.getInstance(project).reformat(psiFile);
//                            Document formattedDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile);
//                            if (formattedDocument != null) {
//                                format.set(formattedDocument.getText());
//                            }
//                        });
//                    });
//
//                    // Update UI outside of the write action
//                    SwingUtilities.invokeLater(() -> outputTextPane.setText(format.get()));
//                } catch (Exception ex) {
//                    SwingUtilities.invokeLater(() -> outputTextPane.setText(getStackTrace(ex)));
//                }
//            }
//        }.execute();
        new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {
                System.err.println("xml: " + xmlContent + ", statementId: " + statementId + ", params:" + jsonObject.toJSONString());
                return MybatisGenerator.generateSql(xmlContent, statementId, prepareRadioButton.isSelected(), jsonObject);
            }

            @Override
            protected void done() {
                try {
                    String sql = get();
                    if (sql == null) {
                        SwingUtilities.invokeLater(() -> outputTextPane.setText("No SQL was generated."));
                        return;
                    }

                    Project project = getProject();
                    AtomicReference<String> format = new AtomicReference<>(sql);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 进行写操作
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("temp.sql", SqlLanguage.INSTANCE, sql);
                            CodeStyleManager.getInstance(project).reformat(psiFile);
                            Document formattedDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                            if (formattedDocument != null) {
                                format.set(formattedDocument.getText());
                            }
                        });

                        // 在写操作完成后更新 UI
                        SwingUtilities.invokeLater(() -> outputTextPane.setText(format.get()));
                    }, ModalityState.defaultModalityState());

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> outputTextPane.setText(getStackTrace(ex)));
                }
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


}
