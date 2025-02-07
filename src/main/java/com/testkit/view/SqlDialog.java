package com.testkit.view;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.sql.psi.SqlLanguage;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

public class SqlDialog extends JDialog {

    public static final Icon SQL_ANALYSIS_ICON = IconLoader.getIcon("/icons/sql-analysis.svg", FunctionCallIconProvider.class);

    private TestkitToolWindow toolWindow;
    private LanguageTextField inputSqlField;
    private JComboBox<String> dataSourceComboBox;
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

        // 设置 panelCmd
        JPanel panelCmd = buildCmdPanel(testkitWindow);
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.NORTH;// 使组件在水平方向和垂直方向都拉伸
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 0.3; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
        c1.gridwidth = 2; // 横跨两列
        panelMain.add(panelCmd, c1);

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

    private JPanel buildActionPanel(TestkitToolWindow window) {
        JPanel panelResults = new JPanel();
        panelResults.setLayout(new BoxLayout(panelResults, BoxLayout.X_AXIS)); // 顺序排列（水平）

        // 一个Label，代表datasource
        JLabel dataSourceLabel = new JLabel("Datasource:");

        // 一个下拉框
        dataSourceComboBox = new ComboBox<>();

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
                CCJSqlParserUtil.parse(sqlText);
            } catch (JSQLParserException ex) {
                setMsg("Parse SQL error, " + ex.getMessage());
                return;
            }

            String selectedData = (String) dataSourceComboBox.getSelectedItem();
//            if (selectedData == null) {
//                setMsg("Please select a datasource, if there is no option, configure the database link in settings-SQL tool");
//                return;
//            }


            Optional<SettingsStorageHelper.DatasourceConfig> first = RuntimeHelper.getValidDatasources(toolWindow.getProject().getName())
                    .stream()
                    .filter(new Predicate<SettingsStorageHelper.DatasourceConfig>() {
                        @Override
                        public boolean test(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                            return Objects.equals(selectedData, datasourceConfig.getName());
                        }
                    })
                    .findFirst();
//            if (first.isEmpty()) {
//                setMsg("Can not find valid datasource");
//                return;
//            }
            //执行explain，并刷新结果
// 执行 EXPLAIN 逻辑部分
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Analysis sql, please wait ...", false) {

                                                  @Override
                                                  public void run(@NotNull ProgressIndicator progressIndicator) {
                                                      loadAnalysis(first.orElse(null), sqlText);
                                                      // 结果数据传递到表格加载方法
//                                                      SwingUtilities.invokeLater(() -> loadAnalysis(first.orElse(null), sqlText));
                                                  }
                                              }
            );


        });

        // 添加组件到panel
        panelResults.add(dataSourceLabel);
        panelResults.add(Box.createHorizontalStrut(10)); // 添加间隔
        panelResults.add(dataSourceComboBox);
        panelResults.add(Box.createHorizontalStrut(10)); // 添加间隔
        panelResults.add(button);

        return panelResults;
    }


    private List<Map<String, Object>> explainSql(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sqlText) {
        if (datasourceConfig == null) {
            return new ArrayList<>();
        }
        try (Connection connection = MysqlUtil.getDatabaseConnection(datasourceConfig);
             PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sqlText);
             ResultSet resultSet = statement.executeQuery()) {

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

                // 将表格和标题添加到垂直容器
                verticalPanel.add(titleLabel,gbc);
                gbc.gridy++;
                verticalPanel.add(new JBScrollPane(explainTable),gbc);
            }
        } catch (Throwable ex) {
            JLabel titleLabel = new JLabel("EXPLAIN", SwingConstants.LEFT);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
            //优化字体
            verticalPanel.add(titleLabel,gbc);

            JLabel titleLabel1 = new JLabel(ex.getMessage(), SwingConstants.LEFT);
            titleLabel1.setForeground(Color.pink);
            //优化字体
            gbc.gridy++;
            verticalPanel.add(titleLabel1,gbc);
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
            JBTable suggestTable = new JBTable(suggestModel) {
                @Override
                public String getToolTipText(MouseEvent e) {
                    // 获取鼠标悬停的单元格位置
                    int row = rowAtPoint(e.getPoint());
                    int column = columnAtPoint(e.getPoint());

                    // 确保行列位置有效
                    if (row != -1 && column == 2) {
                        Object value = getValueAt(row, column); // 获取单元格内容
                        if (value != null) {
                            return value.toString().replace("\n", "<br>"); // 返回内容作为 ToolTip
                        }
                    }
                    return null; // 如果没有内容，则不显示 ToolTip
                }
            };
            suggestTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            suggestTable.setFillsViewportHeight(true);
            suggestTable.getTableHeader().setReorderingAllowed(false);

            // 设置列宽nm
            TableColumnModel columnModel = suggestTable.getColumnModel();
            columnModel.getColumn(0).setPreferredWidth(80); // 第一列固定宽度
            columnModel.getColumn(1).setPreferredWidth(160); // 第二列固定宽度
            columnModel.getColumn(2).setPreferredWidth((panelResults.getWidth() - 250));

            gbc.gridy++;
            // 将建议部分添加到垂直容器
            verticalPanel.add(suggestTitleLabel,gbc);
            gbc.gridy++;
            verticalPanel.add(new JBScrollPane(suggestTable),gbc);
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
        JTextArea jsonInput = new JTextArea();
        jsonInput.setText(msg);
        jsonInput.setLineWrap(true);
        jsonInput.setWrapStyleWord(true);
        JBScrollPane scrollPane = new JBScrollPane(jsonInput);
        panelResults.add(scrollPane, BorderLayout.CENTER);
        panelResults.revalidate();
        panelResults.repaint();
    }


    private @NotNull JPanel buildCmdPanel(TestkitToolWindow window) {
        JPanel panelCmd = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelCmd.setLayout(gridbag);

        JLabel curlLabel = new JLabel("Sql:");
        inputSqlField = new LanguageTextField(SqlLanguage.INSTANCE, toolWindow.getProject(), "", false);
//        inputTextArea.getEmptyText().setText("input your sql");
// 添加一个 JScrollPane 包装 inputSqlField
        JBScrollPane scrollPane = new JBScrollPane(inputSqlField);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Add label
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        panelCmd.add(curlLabel, c);

        // Add textarea
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 1;
        c.gridy = 0;
        panelCmd.add(scrollPane, c);
        return panelCmd;
    }

    public void refreshDatasources() {
        String selectedData = (String) dataSourceComboBox.getSelectedItem();
        dataSourceComboBox.removeAllItems();
        List<SettingsStorageHelper.DatasourceConfig> validDatasources = RuntimeHelper.getValidDatasources(toolWindow.getProject().getName());
        for (SettingsStorageHelper.DatasourceConfig validDatasource : validDatasources) {
            dataSourceComboBox.addItem(validDatasource.getName());
        }
        if (selectedData != null) {
            dataSourceComboBox.setSelectedItem(selectedData);
        }
    }
}
