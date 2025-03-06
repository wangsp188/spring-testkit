package com.testkit.view;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
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
import com.testkit.sql_review.MysqlVerifyExecuteUtil;
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
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.deparser.StatementDeParser;
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
import java.io.StringWriter;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        super((Frame) null, "SQL Tool", true);
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


        // 一个展开的按钮
        unfoldButton = new JButton(SAFE_EXECUTE_ICON);
//        unfoldButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        unfoldButton.setToolTipText("Verify & Execute");
        unfoldButton.setPreferredSize(new Dimension(32, 32));
        unfoldButton.addActionListener(e -> {
            // 创建表格模型
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"Datasource", "Operate", "SQL", "Verify", "Rollback", "Result"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 1; // 只有Operate列可编辑
                }
            };


            List<String> operateDs = new ArrayList<>();
            List<String> ddlDatasources = RuntimeHelper.getDDLDatasources(toolWindow.getProject().getName());
            List<String> writeDatasources = RuntimeHelper.getWriteDatasources(toolWindow.getProject().getName());
            LinkedHashMap<String, List<String>> url2Names = new LinkedHashMap<>();
            for (SettingsStorageHelper.DatasourceConfig validDatasource : RuntimeHelper.getValidDatasources(toolWindow.getProject().getName())) {
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
                    operateDs.add(ddl);
                    continue;
                }

                String write = findAny(optinals, writeDatasources);
                if (write != null) {
                    operateDs.add(write);
                    continue;
                }
            }
            if (CollectionUtils.isEmpty(operateDs)) {
                setMsg("No Datasource has Write/DDL permission");
                return;
            }


            // 创建表格
            JBTable table = new JBTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            // 在创建表格后添加行过滤器逻辑
            TableRowSorter sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);
            // 设置列宽
            TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(0).setPreferredWidth(150);   // 数据源名称
            columnModel.getColumn(1).setPreferredWidth(80);   // Operate
            columnModel.getColumn(2).setPreferredWidth((panelResults.getWidth() - 237) / 4);
            columnModel.getColumn(3).setPreferredWidth((panelResults.getWidth() - 237) / 4);
            columnModel.getColumn(4).setPreferredWidth((panelResults.getWidth() - 237) / 4);
            columnModel.getColumn(5).setPreferredWidth((panelResults.getWidth() - 237) / 4);
            table.setDefaultRenderer(Object.class, new TextAreaCellRenderer(3, Set.of(2, 3, 4, 5), operateDs.size()));
            // 添加点击复制功能
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow); // 关键转换
                        int col = table.columnAtPoint(e.getPoint());
                        if (col == 2 || col == 3 || col == 4) {
                            Object value = table.getModel().getValueAt(modelRow, col); // 使用模型索引
                            String text = value != null ? value.toString() : "";
                            TestkitHelper.showMessageWithCopy(toolWindow.getProject(), text);
                        }
                    }
                }
            });


            // 自定义操作列渲染
            columnModel.getColumn(1).setCellRenderer(new TableCellRenderer() {

                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus, int row, int column) {
                    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
                    JButton checkBtn = new JButton(AllIcons.Actions.Checked);
//                    checkBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    checkBtn.setToolTipText("Verify this");
                    JButton executeBtn = new JButton(AllIcons.Actions.Execute);
//                    executeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    executeBtn.setToolTipText("Execute this");
                    checkBtn.setPreferredSize(new Dimension(32, 32));
                    executeBtn.setPreferredSize(new Dimension(32, 32));
                    panel.add(checkBtn);
                    panel.add(executeBtn);
                    return panel;
                }
            });

            // 自定义操作列的编辑器
            columnModel.getColumn(1).setCellEditor(new AbstractTableCellEditor() {
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
                                                             boolean isSelected, int viewRow, int column) {
                    int modelRow = table.convertRowIndexToModel(viewRow); // 关键转换

                    return panels.computeIfAbsent(modelRow, new Function<Integer, JPanel>() {
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
                                verifyWrite(table, viewRow);
                            });

                            // 执行按钮事件
                            executeBtn.addActionListener(e -> {
                                executeWrite(table, viewRow);
                            });

                            panel.add(checkBtn);
                            panel.add(executeBtn);
                            return panel;
                        }
                    });
                }

                private void executeWrite(JTable table, int viewRow) {
                    int modelRow = table.convertRowIndexToModel(viewRow); // 关键转换
                    String sqlText = table.getModel().getValueAt(modelRow, 2).toString(); // 使用模型索引
                    if (StringUtils.isBlank(sqlText)) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "SQL is empty");
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
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Only support MYSQL Alter/Drop/CreateIndex/CreateTable/Update");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }
                    String datasourceName = (String) table.getModel().getValueAt(modelRow, 0);
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
                            TableModel tableModel = table.getModel();

                            try (Connection connection = datasource.newConnection()) {
                                tableModel.setValueAt("", modelRow, 5);
                                String verifyDdl = tableModel.getValueAt(modelRow, 3).toString();
                                String rollbackDdl = tableModel.getValueAt(modelRow, 4).toString();
                                boolean needVerifyed = StringUtils.isAnyBlank(verifyDdl, rollbackDdl);
                                if (needVerifyed) {
                                    verifyDdl = null;
                                    rollbackDdl = null;
                                    tableModel.setValueAt("", modelRow, 3);
                                    tableModel.setValueAt("", modelRow, 4);
                                    verifyDdl = MysqlVerifyExecuteUtil.verifyWriteSQL(finalStatement, connection);
                                    tableModel.setValueAt(verifyDdl, modelRow, 3);
                                    try {
                                        rollbackDdl = MysqlVerifyExecuteUtil.rollbackWriteSQL(finalStatement, connection);
                                        tableModel.setValueAt(rollbackDdl, modelRow, 4);
                                    } catch (Throwable ex) {
                                        tableModel.setValueAt("Generate rollback error,\n" + ex.getMessage(), modelRow, 4);
                                    }
                                }

                                String msg = rollbackDdl == null ? ("Are you sure you want to execute this SQL?\n\nDatasource:\n" + datasourceName + "\n\nSQL:\n" + sqlText + "\n\nHere's verify:\n" + verifyDdl + "\n\nGenerate rollback error") : ("Are you sure you want to execute this SQL?\n\nDatasource:\n" + datasourceName + "\n\nSQL:\n" + sqlText + "\n\nHere's verify:\n" + verifyDdl + "\n\nHere's rollback sql:\n" + rollbackDdl);
                                // 添加确认对话框
                                int result = JOptionPane.showConfirmDialog(
                                        SqlDialog.this, // 父组件，可以为 null
                                        msg, // 提示信息
                                        "Confirm Execution", // 对话框标题
                                        JOptionPane.YES_NO_OPTION // 选项类型
                                );

                                if (result != JOptionPane.YES_OPTION) {
                                    // 用户点击了“否”或关闭对话框，不执行 SQL
                                    tableModel.setValueAt("CANCEL", modelRow, 5);
                                    fireEditingStopped(); // 结束编辑状态
                                    return;
                                }
                                MysqlUtil.SqlRet sqlRet = MysqlUtil.executeSQL(sqlText, connection);
                                tableModel.setValueAt(sqlRet.toString(), modelRow, 5);
                            } catch (RuntimeExceptionWithAttachments edt) {
                                tableModel.setValueAt("CANCEL", modelRow, 5);
                            } catch (Throwable ex) {
                                TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Execute error, " + ex.getMessage());
                            } finally {
                                fireEditingStopped(); // 结束编辑状态
                            }
                        }
                    });
                }

                private void verifyWrite(JTable table, int viewRow) {
                    int modelRow = table.convertRowIndexToModel(viewRow); // 关键转换

                    String sqlText = table.getModel().getValueAt(modelRow, 2).toString();
                    if (StringUtils.isBlank(sqlText)) {
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "SQL is empty");
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
                        TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), "Only support MYSQL Alter/Drop/CreateIndex/CreateTable/Update");
                        fireEditingStopped(); // 结束编辑状态
                        return;
                    }

                    String datasourceName = (String) table.getModel().getValueAt(modelRow, 0);

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
                            TableModel tableModel = table.getModel();
                            try (Connection connection = datasource.newConnection()) {
                                tableModel.setValueAt("", modelRow, 3);
                                tableModel.setValueAt("", modelRow, 4);
                                String checkDdl = MysqlVerifyExecuteUtil.verifyWriteSQL(finalStatement, connection);
                                tableModel.setValueAt(checkDdl, modelRow, 3);
                                try {
                                    String rollbackDdl = MysqlVerifyExecuteUtil.rollbackWriteSQL(finalStatement, connection);
                                    tableModel.setValueAt(rollbackDdl, modelRow, 4);
                                } catch (Throwable ex) {
                                    tableModel.setValueAt("Generate rollback error,\n" + ex.getMessage(), modelRow, 4);
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


            // 更新结果面板
            panelResults.removeAll();
            panelResults.setLayout(new BorderLayout());

            JBLabel titleField = new JBLabel("Please verify carefully before executing, Now support MYSQL alter/drop/create/update");
            titleField.setForeground(Color.pink);
            titleField.setFont(new Font("Arial", Font.BOLD, 14));
            titleField.setHorizontalAlignment(SwingConstants.CENTER); // 设置文本居中
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // 垂直布局
            panel.add(titleField);

            JButton refreshSqlsButton = new JButton(AllIcons.Actions.Refresh);
            refreshSqlsButton.setPreferredSize(new Dimension(32, 32));
            refreshSqlsButton.setToolTipText("Parse SQL & Refresh table");
//            refreshSqlsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            refreshSqlsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String text = inputSqlField.getText();
                    List<String> sqls;
                    if (StringUtils.isNotBlank(text)) {
                        try {
                            Statements parses = MysqlUtil.parses(text);
                            sqls = parses.getStatements().stream()
                                    .map(new Function<Statement, String>() {
                                        @Override
                                        public String apply(Statement statement) {
                                            boolean b = statement instanceof Alter
                                                    || statement instanceof Drop
                                                    || statement instanceof CreateIndex
                                                    || statement instanceof CreateTable
                                                    || statement instanceof Update;
                                            if (!b) {
                                                throw new RuntimeException("Only support MYSQL Alter/Drop/CreateIndex/CreateTable/Update");
                                            }
                                            return statement.toString();
                                        }
                                    }).collect(Collectors.toUnmodifiableList());
                        } catch (Exception ex) {
                            TestkitHelper.alert(toolWindow.getProject(), Messages.getErrorIcon(), ex.getMessage());
                            return;
                        }
                    } else {
                        sqls = Collections.emptyList();
                    }
                    refreshExecuteTable(sqls, model, operateDs, table);
                }
            });

            // 第二行：单选按钮组
            JPanel dbBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            dbBoxPanel.add(refreshSqlsButton);

            JLabel dblabel = new JLabel("DB");
            dbBoxPanel.add(dblabel);
            List<JCheckBox> datasourceCheckboxes = new ArrayList<>(); // 存储引用以便后续操作
            // 创建筛选更新方法
            Runnable updateFilter = () -> {
                List<String> selectedDatasources = datasourceCheckboxes.stream()
                        .filter(JCheckBox::isSelected)
                        .map(AbstractButton::getText)
                        .collect(Collectors.toList());

                sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        if (selectedDatasources.isEmpty()) {
                            return false;
                        }
                        String rowDatasource = (String) entry.getModel().getValueAt(entry.getIdentifier(), 0);
                        return selectedDatasources.contains(rowDatasource);
                    }
                });

                table.setDefaultRenderer(Object.class, new TextAreaCellRenderer(3, Set.of(2, 3, 4, 5), selectedDatasources.size()));
                // 在修改数据后触发界面更新
                table.repaint();
                table.revalidate();
            };


            for (String datasource : operateDs) {
                JCheckBox checkBox = new JCheckBox(datasource);
                checkBox.setSelected(true); // 默认未选中
                checkBox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        updateFilter.run();
                    }
                });
                dbBoxPanel.add(checkBox);
                datasourceCheckboxes.add(checkBox); // 保存引用
            }

            panel.add(dbBoxPanel);

            panelResults.add(panel, BorderLayout.NORTH);
            panelResults.add(new JBScrollPane(table), BorderLayout.CENTER);
            panelResults.revalidate();
            panelResults.repaint();

            String text = inputSqlField.getText();
            List<String> sqls;
            if (StringUtils.isNotBlank(text)) {
                try {
                    Statements parses = MysqlUtil.parses(text);
                    String trim = text.trim();
                    if (trim.startsWith(";")) {
                        trim = trim.substring(1);
                    }
                    if (trim.endsWith(";")) {
                        trim = trim.substring(0, trim.length() - 1);
                    }
                    if (trim.split(";").length == parses.getStatements().size()) {
                        sqls = Arrays.asList(trim.split(";"));
                    } else {
                        sqls = parses.getStatements().stream()
                                .map(new Function<Statement, String>() {
                                    @Override
                                    public String apply(Statement statement) {
                                        return statement.toString();
                                    }
                                }).collect(Collectors.toUnmodifiableList());
                    }

                    refreshExecuteTable(sqls, model, operateDs, table);
                } catch (Throwable ex) {
                    TestkitHelper.notify(toolWindow.getProject(), NotificationType.ERROR, "Refresh execution table error<br>you click refresh button try again<br>" + ex.getMessage());
                }
            }
        });

        actionResults.add(dataSourceLabel);
        actionResults.add(dataSourceComboBox);
        actionResults.add(button);
        return actionResults;
    }

    private void refreshExecuteTable(List<String> sqls, DefaultTableModel tableModel, List<String> datasources2, JBTable table) {
        // 模拟刷新
        ApplicationManager.getApplication().invokeLater(() -> {
            // 更新表格数据（假设模型为 DefaultTableModel）
            // 清空现有数据
            tableModel.setRowCount(0);
            // 插入新数据
            for (String sql : sqls) {
                // 填充数据源
                for (String ds : datasources2) {
                    tableModel.addRow(new Object[]{ds, "", sql + ";", "", "", ""});
                }
            }

            table.revalidate();
            table.repaint();
        });
    }


    private List<Map<String, String>> explainSql(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sqlText) {
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

            List<Map<String, String>> explainList = new ArrayList<>();

            while (resultSet.next()) {
                HashMap<String, String> map = new HashMap<>();
                for (int i = 0; i < explainColumns.size(); i++) {
                    String colName = explainColumns.get(i);
                    Integer colIdx = columnIndexMapping.get(colName); // 获取实际列索引
                    String value = (colIdx != null) && resultSet.getObject(colIdx) != null ? String.valueOf(resultSet.getObject(colIdx)) : null;
                    map.put(colName, StringUtils.isBlank(value) ? null : value);
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
        List<Map<String, String>> explains;
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
                List<String[]> resultData = new ArrayList<>();
                for (Map<String, String> explain : explains) {
                    String[] row = new String[explainColumns.size()];
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

                // 自定义渲染器
                explainTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
//                        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        JLabel c = new JLabel(value == null ? "" : value.toString());
                        TableModel tableModel = explainTable.getModel();
                        String select_type = (String) tableModel.getValueAt(row, 1);
                        String type = (String) tableModel.getValueAt(row, 3);
                        String possible_keys = (String) tableModel.getValueAt(row, 4);
                        String ref = (String) tableModel.getValueAt(row, 7);
                        String rows = (String) tableModel.getValueAt(row, 8);
                        String filtered = (String) tableModel.getValueAt(row, 9);
                        String extra = (String) tableModel.getValueAt(row, 10);


                        // Full Table Scan
                        if ("ALL".equalsIgnoreCase(type) && column == 3) {
                            c.setForeground(Color.RED);
                            c.setToolTipText("Full Table Scan<br>The query is performing a full table scan, which can be slow.");
                        } else if (!"ALL".equalsIgnoreCase(type) && (possible_keys == null || possible_keys.equalsIgnoreCase("NULL")) && column == 4) {
                            c.setForeground(Color.RED);
                            c.setToolTipText("Index Not Used<br>No index is being used for this query.");
                        }


                        if (column == 1 && ("DEPENDENT SUBQUERY".equalsIgnoreCase(select_type) || "UNCACHEABLE SUBQUERY".equalsIgnoreCase(select_type))) {
                            c.setForeground(Color.RED);
                            c.setToolTipText("Subquery Performance Issue<br>This is a dependent or uncacheable subquery, which may impact performance.");
                        }

                        // Filesort
                        if (column == 10 && extra != null) {
                            String extroStr = "";
                            if (extra.contains("Using filesort")) {
                                extroStr += "Filesort: MySQL is performing a filesort, which can be slow.<br>";
                            }

                            if (extra.contains("Using temporary")) {
                                extroStr += "Temporary Table: MySQL is using a temporary table, which may impact performance.<br>";
                            }

                            if (extra.contains("Using join buffer (Block Nested Loop)")) {
                                extroStr += "Nested Loop Join: MySQL is using a nested loop join, which may be slow.<br>";
                            }

                            if (!extroStr.isEmpty()) {
                                c.setForeground(Color.RED);
                                c.setToolTipText(extroStr);
                            }
                        }

                        // High Rows Scanned
                        try {
                            int rowsValue = Integer.parseInt(rows);
                            double filteredValue = Double.parseDouble(filtered);
                            if (rowsValue > 10000 && column == 8) {
                                c.setForeground(Color.RED);
                                c.setToolTipText("High Rows Scanned<br>The query is scanning a large number of rows (" + rowsValue + ").");
                            } else if (rowsValue <= 10000 && rowsValue > 1000 && filteredValue > 70 && column == 9) {
                                c.setForeground(Color.RED);
                                c.setToolTipText("High Filtered<br>A high percentage of rows (" + filteredValue + "%) are being filtered.");
                            }
                        } catch (NumberFormatException e) {
                            // 忽略无效的行数
                        }

                        return c;
                    }
                });

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

    public synchronized void refreshDatasources() {
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
        } else if (!ddl && Arrays.asList(actionResults.getComponents()).contains(unfoldButton)) {
            actionResults.remove(unfoldButton);
        }
        dataSourceComboBox.revalidate();
        dataSourceComboBox.repaint();
        actionResults.revalidate();
        actionResults.repaint();
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
