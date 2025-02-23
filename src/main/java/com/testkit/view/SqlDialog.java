package com.testkit.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.sql_review.MysqlWriteUtil;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.IconLoader;
import com.intellij.sql.psi.SqlLanguage;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class SqlDialog extends JDialog {

    public static final Icon SQL_ANALYSIS_ICON = IconLoader.getIcon("/icons/sql-analysis.svg", SqlDialog.class);
    public static final Icon SAFE_EXECUTE_ICON = IconLoader.getIcon("/icons/safe-execute.svg", SqlDialog.class);


    private TestkitToolWindow toolWindow;
    private LanguageTextField inputSqlField;
    private JComboBox<String> dataSourceComboBox;
    private JButton unfoldButton;
    private JPanel actionResults;
    private JPanel panelResults;

    // 动态解析 ResultSet 列和数据
    private static final List<String> explainColumns = List.of("id", "select_type", "table", "type",
            "possible_keys", "key", "key_len",
            "ref", "rows", "filtered", "Extra");


    public SqlDialog(TestkitToolWindow testkitWindow) {
        super((Frame) null, "SQL tool", true);
        this.toolWindow = testkitWindow;
        JPanel panelMain = new JPanel(new GridBagLayout());
        GridBagConstraints c1 = new GridBagConstraints();

        JBScrollPane scrollPane = buildInputPane();
        // 设置 panelCmd
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.NORTH;// 使组件在水平方向和垂直方向都拉伸
        c1.gridwidth = 3;
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 0.3; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
//        c1.gridwidth = 2; // 横跨两列
        panelMain.add(scrollPane, c1);

        JPanel panelAction = buildActionPanel(testkitWindow);
        c1.gridy = 1;
        c1.weightx = 1;
        c1.weighty = 0;
        c1.gridwidth = 2; // 不再跨列
        c1.fill = GridBagConstraints.NONE;
        c1.anchor = GridBagConstraints.WEST;
        panelMain.add(panelAction, c1);

        // 设置 panelResults
        JPanel panelResults = buildResultPanel(testkitWindow);
        c1.weighty = 0.7; // 垂直方向占70%
        c1.gridy = 2;
        c1.gridwidth = 2; // 不再跨列
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.SOUTH;
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

    private JBScrollPane buildInputPane() {
        inputSqlField = new LanguageTextField(SqlLanguage.INSTANCE, toolWindow.getProject(), "", false);
        JBScrollPane scrollPane = new JBScrollPane(inputSqlField);
        scrollPane.setBorder(BorderFactory.createTitledBorder("SQL Input"));

        // 添加文档监听器以更新滚动条
        inputSqlField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent event) {
                scrollPane.revalidate();
                scrollPane.repaint();
            }
        });
        return scrollPane;
    }

    private JPanel buildActionPanel(TestkitToolWindow window) {
        actionResults = new JPanel();
        actionResults.setLayout(new BoxLayout(actionResults, BoxLayout.X_AXIS)); // 顺序排列（水平）

        // 一个Label，代表datasource
        JLabel dataSourceLabel = new JLabel("Datasource:");

        // 一个下拉框
        dataSourceComboBox = new JComboBox<>();

        // 一个按钮
        JButton button = new JButton(SQL_ANALYSIS_ICON);
        button.setToolTipText("Analysis SQL");
        button.setPreferredSize(new Dimension(32, 32));
        button.addActionListener(e -> {
            String sqlText = inputSqlField.getText();
            if (StringUtils.isBlank(sqlText)) {
                setMsg("Please input your SQL");
                return;
            }

            try {
                MysqlUtil.parse(sqlText);
            } catch (Throwable ex) {
                setMsg(ex.getMessage());
                return;
            }

            String selectedData = (String) dataSourceComboBox.getSelectedItem();
            // if (selectedData == null) {
            // setMsg("Please select a datasource, if there is no option, configure the database link in settings - SQL tool");
            // return;
            // }

            Optional<SettingsStorageHelper.DatasourceConfig> first = RuntimeHelper.getValidDatasources(toolWindow.getProject().getName())
                    .stream()
                    .filter(new Predicate<SettingsStorageHelper.DatasourceConfig>() {
                        @Override
                        public boolean test(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                            return Objects.equals(selectedData, datasourceConfig.getName());
                        }
                    })
                    .findFirst();
            // if (first.isEmpty()) {
            // setMsg("Can not find valid datasource");
            // return;
            // }

            // 执行 EXPLAIN 逻辑部分
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Analysis sql, please wait ...", false) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    loadAnalysis(first.orElse(null), sqlText);
                }
            });
        });

        // 回滚按钮
//        JButton rollbackButton = new JButton(AllIcons.Actions.Rollback);
//        rollbackButton.setToolTipText("Generate rollback sql");
//        rollbackButton.setPreferredSize(new Dimension(32, 32));
//        rollbackButton.addActionListener(e -> {
//            // 回滚逻辑解析当前sql
////            根据sql
//            rollbackWrite();
//        });


        // 一个展开的按钮
        unfoldButton = new JButton(SAFE_EXECUTE_ICON);
        unfoldButton.setToolTipText("DDL Executor");
        unfoldButton.setPreferredSize(new Dimension(32, 32));
        unfoldButton.addActionListener(e -> {
            // 创建表格模型
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"Datasource", "Verify", "Rollback", "Result", "Operate"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 4; // 只有Operate列可编辑
                }
            };

            List<String> datasources = RuntimeHelper.getDDLDatasources(toolWindow.getProject().getName());
            List<String> datasources2 = RuntimeHelper.getWriteDatasources(toolWindow.getProject().getName());
            if (CollectionUtils.isEmpty(datasources) && CollectionUtils.isEmpty(datasources2)) {
                setMsg("No Datasource has Write/DDL permission");
                return;
            }
            // 填充数据源
            for (String ds : datasources) {
                model.addRow(new Object[]{ds, "", "", "", ""});
            }

            // 创建表格
            JBTable table = new JBTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);

            // 设置列宽
            TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(0).setPreferredWidth(120);   // 数据源名称
            columnModel.getColumn(1).setPreferredWidth((panelResults.getWidth() - 207) / 3);
            columnModel.getColumn(2).setPreferredWidth((panelResults.getWidth() - 207) / 3);
            columnModel.getColumn(3).setPreferredWidth((panelResults.getWidth() - 207) / 3);
            columnModel.getColumn(4).setPreferredWidth(80);   // Operate
            table.setDefaultRenderer(Object.class, new TextAreaCellRenderer(3, Set.of(1, 2, 3)));

            // 添加点击复制功能
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());

                    if (row >= 0 && col >= 0 && (col == 1 || col == 2)) {
                        Object value = table.getValueAt(row, col);
                        String text = value != null ? value.toString() : "";
                        TestkitHelper.copyToClipboard(toolWindow.getProject(), text, null);
                    }
                }
            });


            // 自定义操作列渲染
            columnModel.getColumn(4).setCellRenderer(new TableCellRenderer() {

                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus, int row, int column) {
                    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
                    JButton checkBtn = new JButton(AllIcons.Actions.Checked);
                    checkBtn.setToolTipText("Verify this");
                    JButton executeBtn = new JButton(AllIcons.Actions.Execute);
                    executeBtn.setToolTipText("Execute this");
                    checkBtn.setPreferredSize(new Dimension(32, 32));
                    executeBtn.setPreferredSize(new Dimension(32, 32));
                    panel.add(checkBtn);
                    panel.add(executeBtn);
                    return panel;
                }
            });

            // 自定义操作列的编辑器
            columnModel.getColumn(4).setCellEditor(new AbstractTableCellEditor() {
                private Map<Integer, JPanel> panels = new HashMap<>();

                @Override
                public Object getCellEditorValue() {
                    return ""; // 返回值无关紧要，因为操作直接通过按钮事件处理
                }

                @Override
                public boolean isCellEditable(EventObject e) {
                    return true;
                }

                @Override
                public Component getTableCellEditorComponent(JTable table, Object value,
                                                             boolean isSelected, int row, int column) {
                    return panels.computeIfAbsent(row, new Function<Integer, JPanel>() {
                        @Override
                        public JPanel apply(Integer integer) {
                            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
                            JButton checkBtn;
                            JButton executeBtn;
                            checkBtn = new JButton(AllIcons.Actions.Checked);
                            checkBtn.setToolTipText("Verify this");
                            executeBtn = new JButton(AllIcons.Actions.Execute);
                            executeBtn.setToolTipText("Execute this");
                            checkBtn.setPreferredSize(new Dimension(32, 32));
                            executeBtn.setPreferredSize(new Dimension(32, 32));

                            // 验证按钮事件
                            checkBtn.addActionListener(e -> {
                                verifyWrite(table, integer);
                            });

                            // 执行按钮事件
                            executeBtn.addActionListener(e -> {
                                executeWrite(table, integer);
                            });

                            panel.add(checkBtn);
                            panel.add(executeBtn);
                            return panel;
                        }
                    });
                }

                private void executeWrite(JTable table, int row) {
                    String sqlText = inputSqlField.getText();
                    if (StringUtils.isBlank(sqlText)) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please input your SQL");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    Statement statement = null;
                    try {
                        statement = MysqlUtil.parse(sqlText);
                    } catch (Throwable ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), ex.getMessage());
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    boolean b = statement instanceof Alter
                            || statement instanceof Drop
                            || statement instanceof CreateIndex
                            || statement instanceof CreateTable
                            || statement instanceof Update;
                    if (!b) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Only support Alter or Drop or CreateIndex or CreateTable or Update");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }
                    String datasourceName = (String) table.getModel().getValueAt(row, 0);
                    SettingsStorageHelper.DatasourceConfig datasource = RuntimeHelper.getDatasource(toolWindow.getProject().getName(), datasourceName);
                    if (datasource == null) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Can not find valid datasource");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    // 执行 EXPLAIN 逻辑部分
                    Statement finalStatement = statement;
                    ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Execute SQL, please wait ...", false) {
                        @Override
                        public void run(@NotNull ProgressIndicator progressIndicator) {
                            try (Connection connection = datasource.newConnection()) {
                                String verifyDdl = MysqlWriteUtil.verifyWriteSQL(finalStatement, connection);
                                table.getModel().setValueAt(verifyDdl, row, 1);
                                String rollbackDdl = null;
                                try {
                                    rollbackDdl = MysqlWriteUtil.rollbackWriteSQL(finalStatement, connection);
                                    table.getModel().setValueAt(rollbackDdl, row, 2);
                                } catch (Throwable ex) {
                                    table.getModel().setValueAt("Generate rollback error,\n" + ex.getMessage(), row, 2);
                                }
                                String msg = rollbackDdl == null ? ("Are you sure you want to execute this SQL?\n\nDatasource:\n" + datasourceName + "\n\nSQL:\n" + sqlText + "\n\nHere's verify:\n" + verifyDdl + "\n\nGenerate rollback error") : ("Are you sure you want to execute this SQL?\n\nDatasource:\n" + datasourceName + "\n\nSQL:\n" + sqlText + "\n\nHere's verify:\n" + verifyDdl + "\n\nHere's rollback sql:\n" + rollbackDdl);
                                // 添加确认对话框
                                int result = JOptionPane.showConfirmDialog(
                                        null, // 父组件，可以为 null
                                        msg, // 提示信息
                                        "Confirm Execution", // 对话框标题
                                        JOptionPane.YES_NO_OPTION // 选项类型
                                );

                                if (result != JOptionPane.YES_OPTION) {
                                    // 用户点击了“否”或关闭对话框，不执行 SQL
                                    fireEditingStopped(); // 结束编辑状态
                                    return;
                                }
                                MysqlUtil.SqlRet sqlRet = MysqlUtil.executeSQL(sqlText, connection);
                                table.getModel().setValueAt(sqlRet.toString(), row, 3);
                            } catch (RuntimeExceptionWithAttachments edt) {
                            } catch (Throwable ex) {
                                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Execute error, " + ex.getMessage());
                            } finally {
                                fireEditingStopped(); // 结束编辑状态
                            }
                        }
                    });
                }

                private void verifyWrite(JTable table, int row) {
                    String sqlText = inputSqlField.getText();
                    if (StringUtils.isBlank(sqlText)) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Please input your SQL");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    Statement statement = null;
                    try {
                        statement = MysqlUtil.parse(sqlText);
                    } catch (Throwable ex) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), ex.getMessage());
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    boolean b = statement instanceof Alter
                            || statement instanceof Drop
                            || statement instanceof CreateIndex
                            || statement instanceof CreateTable
                            || statement instanceof Update;
                    if (!b) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Only support Alter or Drop or CreateIndex or CreateTable or Update");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    String datasourceName = (String) table.getModel().getValueAt(row, 0);

                    SettingsStorageHelper.DatasourceConfig datasource = RuntimeHelper.getDatasource(toolWindow.getProject().getName(), datasourceName);
                    if (datasource == null) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Can not find valid datasource");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    // 执行 EXPLAIN 逻辑部分
                    Statement finalStatement = statement;
                    ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Verify SQL, please wait ...", false) {
                        @Override
                        public void run(@NotNull ProgressIndicator progressIndicator) {
                            try (Connection connection = datasource.newConnection()) {
                                String checkDdl = MysqlWriteUtil.verifyWriteSQL(finalStatement, connection);
                                table.getModel().setValueAt(checkDdl, row, 1);
                                try {
                                    String rollbackDdl = MysqlWriteUtil.rollbackWriteSQL(finalStatement, connection);
                                    table.getModel().setValueAt(rollbackDdl, row, 2);
                                } catch (Throwable ex) {
                                    table.getModel().setValueAt("Generate rollback error,\n" + ex.getMessage(), row, 2);
                                }
                            } catch (Throwable ex) {
                                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Verify error, " + ex.getMessage());
                            } finally {
                                fireEditingStopped(); // 结束编辑状态
                            }
                        }
                    });
                }
            });

            // 设置ToolTip
//            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
//                @Override
//                public Component getTableCellRendererComponent(JTable table, Object value,
//                                                               boolean isSelected, boolean hasFocus, int row, int column) {
//                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//                    if (c instanceof JComponent) {
//                        JComponent jc = (JComponent) c;
//                        if (column == 1 || column == 2 || column == 3) {
//                            String str = value != null ? value.toString() : "";
//                            List<String> strs = Arrays.asList(str.replace("\n", "<br>").split("<br>"));
//                            if (strs.size() > 20) {
//                                strs = new ArrayList<>(strs.subList(0, 19));
//                                strs.add("...");
//                            }
//                            jc.setToolTipText(String.join("<br>", strs));
//                        } else {
//                            jc.setToolTipText(null);
//                        }
//                    }
//                    return c;
//                }
//            });

            // 更新结果面板
            panelResults.removeAll();
            panelResults.setLayout(new BorderLayout());
            JBLabel titleField = new JBLabel("Please verify carefully before executing, Now support alter/drop/create/update");
            titleField.setForeground(Color.pink);
            titleField.setFont(new Font("Arial", Font.BOLD, 14));
            titleField.setHorizontalAlignment(SwingConstants.CENTER); // 设置文本居中
            panelResults.add(titleField, BorderLayout.NORTH);
            panelResults.add(new JBScrollPane(table), BorderLayout.CENTER);
            panelResults.revalidate();
            panelResults.repaint();
        });

        actionResults.add(dataSourceLabel);
        actionResults.add(dataSourceComboBox);
//        actionResults.add(rollbackButton);
        actionResults.add(button);
        return actionResults;
    }

//    private void rollbackWrite() {
//        String sqlText = inputSqlField.getText();
//        if (StringUtils.isBlank(sqlText)) {
//            setMsg("Please input your SQL");
//            return;
//        }
//
//        Statement statement = null;
//        try {
//            statement = CCJSqlParserUtil.parse(sqlText);
//        } catch (JSQLParserException ex) {
//            setMsg("Parse SQL error, " + ex.getMessage());
//            return;
//        }
//
//        boolean b = statement instanceof Alter
//                || statement instanceof Drop
//                || statement instanceof CreateIndex
//                || statement instanceof CreateTable
//                || statement instanceof Update;
//        if (!b) {
//            setMsg("Only support Alter or Drop or CreateIndex or CreateTable or Update");
//            return;
//        }
//
//        if (dataSourceComboBox.getSelectedItem() == null) {
//            setMsg("Please select a datasource, if there is no option, configure the database link in settings - SQL tool");
//            return;
//        }
//
//        SettingsStorageHelper.DatasourceConfig datasource = RuntimeHelper.getDatasource(toolWindow.getProject().getName(), dataSourceComboBox.getSelectedItem().toString());
//        if (datasource == null) {
//            setMsg("Can not find valid datasource");
//            return;
//        }
//
//        // 执行 EXPLAIN 逻辑部分
//        Statement finalStatement = statement;
//        ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Generate rollback sql, please wait ...", false) {
//            @Override
//            public void run(@NotNull ProgressIndicator progressIndicator) {
//                try (Connection connection = datasource.newConnection()) {
//                    String rollbackDdl = MysqlWriteUtil.rollbackWriteSQL(finalStatement, connection);
//                    setMsg("Here's rollback sql, verify it carefully !!!\n" + rollbackDdl);
//                } catch (Throwable ex) {
//                    setMsg("Generate error\n" + ex.getMessage());
//                }
//            }
//        });
//    }


    private List<Map<String, Object>> explainSql(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sqlText) {
        if (datasourceConfig == null) {
            return new ArrayList<>();
        }

        Statement jstatement = MysqlUtil.parse(sqlText);
        if (!(jstatement instanceof Select) && !(jstatement instanceof Update) && !(jstatement instanceof Delete)) {
            return new ArrayList<>();
        }

        try (Connection connection = MysqlUtil.getDatabaseConnection(datasourceConfig);
             PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sqlText)) {
            statement.setQueryTimeout(30);
            ResultSet resultSet = statement.executeQuery();
            // 动态解析 ResultSet 列和数据
            Map<String, Integer> columnIndexMapping = new HashMap<>(); // 存放列名 -> 索引
            ResultSetMetaData metaData = resultSet.getMetaData();

            // 获取返回的列信息，动态映射到列索引
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i).toLowerCase(); // 转为小写以匹配
                if (explainColumns.contains(columnName)) {
                    columnIndexMapping.put(columnName, i); // 保存列名与索引映射
                }
            }

            // 检查是否所有所需列都存在
            if (columnIndexMapping.isEmpty()) {
                throw new RuntimeException("The result set does not contain the required EXPLAIN columns!");
            }

            List<Map<String, Object>> explainList = new ArrayList<>();

            while (resultSet.next()) {
                HashMap<String, Object> map = new HashMap<>();
                for (int i = 0; i < explainColumns.size(); i++) {
                    String colName = explainColumns.get(i);
                    Integer colIdx = columnIndexMapping.get(colName); // 获取实际列索引
                    map.put(colName, (colIdx != null) ? resultSet.getObject(colIdx) : null);
                }
                explainList.add(map); // 添加行
            }
            return explainList;
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void loadAnalysis(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sqlText) {
        // 使用 BorderLayout 管理组件布局
        panelResults.removeAll(); // 清空现有组件
        panelResults.setLayout(new BorderLayout());
        // 使用垂直布局容器承载所有内容（EXPLAIN 和 SQL Review 建议）
        JPanel verticalPanel = new JPanel();
//        verticalPanel.setLayout(new BoxLayout(verticalPanel, BoxLayout.Y_AXIS));
        verticalPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER; // 居中对齐
        gbc.fill = GridBagConstraints.HORIZONTAL; // 水平填充

        JLabel goodLuckLabel = new JLabel("Good luck, Analysis support select/update/delete/drop/create", SwingConstants.CENTER);
        goodLuckLabel.setForeground(Color.decode("#72a96b")); // 设置字体颜色为红色
        goodLuckLabel.setFont(new Font("Arial", Font.BOLD, 14));
        verticalPanel.add(goodLuckLabel, gbc);

        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST; // 左对齐
        gbc.fill = GridBagConstraints.HORIZONTAL; // 水平填充
        gbc.weightx = 1; // 水平权重
        gbc.insets = JBUI.emptyInsets(); // 设置边距
        // 提取数据
        List<Map<String, Object>> explains;
        try {
            explains = explainSql(datasourceConfig, sqlText);
            // 1. EXPLAIN 结果展示
            if (CollectionUtils.isNotEmpty(explains)) {
                // 创建标题 JLabel
                JLabel titleLabel = new JLabel("EXPLAIN", SwingConstants.LEFT);
                titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
                titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

                // 表格数据转换
                List<Object[]> resultData = new ArrayList<>();
                for (Map<String, Object> explain : explains) {
                    Object[] row = new Object[explainColumns.size()];
                    for (int i = 0; i < explainColumns.size(); i++) {
                        row[i] = explain.get(explainColumns.get(i));
                    }
                    resultData.add(row);
                }

                // 创建表格
                JBTable explainTable = new JBTable(new DefaultTableModel(resultData.toArray(new Object[0][]), explainColumns.toArray(new String[0])) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false; // 禁止所有单元格编辑
                    }
                });
                explainTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                explainTable.setFillsViewportHeight(true);
                explainTable.getTableHeader().setReorderingAllowed(false);
                explainTable.setDefaultRenderer(Object.class, new TextAreaCellRenderer(1, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));


                // 将表格和标题添加到垂直容器
                verticalPanel.add(titleLabel, gbc);
                gbc.gridy++;
                verticalPanel.add(new JBScrollPane(explainTable), gbc);
            }
        } catch (Throwable ex) {
            JLabel titleLabel = new JLabel("EXPLAIN", SwingConstants.LEFT);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
            //优化字体
            verticalPanel.add(titleLabel, gbc);

            JLabel titleLabel1 = new JLabel(ex.getMessage(), SwingConstants.LEFT);
            titleLabel1.setForeground(Color.pink);
            //优化字体
            gbc.gridy++;
            verticalPanel.add(titleLabel1, gbc);
        }

        // 2. SQL Review 建议展示
        List<Suggest> suggests = SqlReviewer.reviewMysql(datasourceConfig, sqlText);
        if (CollectionUtils.isNotEmpty(suggests)) {
            // 创建建议标题
            JLabel suggestTitleLabel = new JLabel("Suggests", SwingConstants.LEFT);
            suggestTitleLabel.setFont(new Font("Arial", Font.BOLD, 14));
            suggestTitleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

            // 表格列名
            String[] suggestColumns = {"Level", "Title", "Detail"};

            // 转换建议数据
            DefaultTableModel suggestModel = new DefaultTableModel(suggestColumns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false; // 禁止所有单元格编辑
                }


            };
            for (Suggest suggest : suggests) {
                suggestModel.addRow(new Object[]{
                        suggest.getRule().getLevel().name(),
                        suggest.getRule().getTitle(),
                        suggest.getDetail()
                });
            }

            // 创建建议表格
            JBTable suggestTable = new JBTable(suggestModel);
            suggestTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            suggestTable.setFillsViewportHeight(true);
            suggestTable.getTableHeader().setReorderingAllowed(false);

            suggestTable.setDefaultRenderer(Object.class, new TextAreaCellRenderer(3, Set.of(2)));

            // 设置列宽nm
            TableColumnModel columnModel = suggestTable.getColumnModel();
            columnModel.getColumn(0).setPreferredWidth(80); // 第一列固定宽度
            columnModel.getColumn(1).setPreferredWidth(160); // 第二列固定宽度
            columnModel.getColumn(2).setPreferredWidth((panelResults.getWidth() - 250));

            gbc.gridy++;
            // 将建议部分添加到垂直容器
            verticalPanel.add(suggestTitleLabel, gbc);
            gbc.gridy++;
            verticalPanel.add(new JBScrollPane(suggestTable), gbc);
        }

        // 将垂直容器包裹在滚动面板中（支持整体滚动）
        JBScrollPane mainScrollPane = new JBScrollPane(verticalPanel);
        panelResults.add(mainScrollPane, BorderLayout.NORTH);
        panelResults.revalidate();
        panelResults.repaint();
    }


    private JPanel buildResultPanel(TestkitToolWindow window) {
        panelResults = new JPanel(new BorderLayout());
        setMsg("");
        return panelResults;
    }

    private void setMsg(String msg) {
        panelResults.removeAll();
        JTextArea msgArea = new JTextArea();
        msgArea.setText(msg);
        msgArea.setLineWrap(true);
        msgArea.setEditable(false);
        msgArea.setWrapStyleWord(true);
        JBScrollPane scrollPane = new JBScrollPane(msgArea);
        panelResults.add(scrollPane, BorderLayout.CENTER);
        panelResults.revalidate();
        panelResults.repaint();
    }

    public void refreshDatasources() {
        String selectedData = (String) dataSourceComboBox.getSelectedItem();
        dataSourceComboBox.removeAllItems();
        List<SettingsStorageHelper.DatasourceConfig> validDatasources = RuntimeHelper.getValidDatasources(toolWindow.getProject().getName());
        List<String> ddlDatasources = RuntimeHelper.getDDLDatasources(toolWindow.getProject().getName());
        List<String> writeDatasources = RuntimeHelper.getWriteDatasources(toolWindow.getProject().getName());
        LinkedHashMap<String, List<String>> url2Names = new LinkedHashMap<>();
        for (SettingsStorageHelper.DatasourceConfig validDatasource : validDatasources) {
            String url = validDatasource.getUrl();
            url = url.split("\\?")[0];
            url2Names.computeIfAbsent(url, new Function<String, List<String>>() {
                @Override
                public List<String> apply(String s) {
                    return new ArrayList<>();
                }
            }).add(validDatasource.getName());
        }

        for (Map.Entry<String, List<String>> listEntry : url2Names.entrySet()) {
            List<String> optinals = new ArrayList<>(listEntry.getValue());
            String ddl = findAny(optinals, ddlDatasources);
            if (ddl != null) {
                dataSourceComboBox.addItem(ddl);
                continue;
            }

            String write = findAny(optinals, writeDatasources);
            if (write != null) {
                dataSourceComboBox.addItem(write);
                continue;
            }

            dataSourceComboBox.addItem(optinals.get(0));
        }

        if (selectedData != null) {
            dataSourceComboBox.setSelectedItem(selectedData);
        }

        boolean ddl = CollectionUtils.isNotEmpty(ddlDatasources);
        if (ddl && !Arrays.asList(actionResults.getComponents()).contains(unfoldButton)) {
            actionResults.add(unfoldButton);
            actionResults.revalidate();
            actionResults.repaint();
        } else if (!ddl && Arrays.asList(actionResults.getComponents()).contains(unfoldButton)) {
            actionResults.remove(unfoldButton);
            actionResults.revalidate();
            actionResults.repaint();
        }
    }


    private static String findAny(List<String> sources, List<String> search) {
        if (sources == null || search == null) {
            return null;
        }
        for (String source : sources) {
            if (search.contains(source)) {
                return source;
            }
        }
        return null;
    }

}
