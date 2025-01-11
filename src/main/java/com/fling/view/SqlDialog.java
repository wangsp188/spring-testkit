package com.fling.view;

import com.fling.RuntimeHelper;
import com.fling.SettingsStorageHelper;
import com.fling.tools.method_call.MethodCallIconProvider;
import com.fling.util.sql.MysqlUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.sql.psi.SqlLanguage;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class SqlDialog extends JDialog {

    public static final Icon SQL_ANALYSIS_ICON = IconLoader.getIcon("/icons/sql-analysis.svg", MethodCallIconProvider.class);

    private FlingToolWindow toolWindow;
    private LanguageTextField inputSqlField;
    private JComboBox<String> dataSourceComboBox;
    private JPanel panelResults;


    public SqlDialog(FlingToolWindow flingWindow) {
        super((Frame) null, "SQL tool", true);
        this.toolWindow = flingWindow;
        JPanel panelMain = new JPanel(new GridBagLayout());
        GridBagConstraints c1 = new GridBagConstraints();

        // 设置 panelCmd
        JPanel panelCmd = buildCmdPanel(flingWindow);
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.NORTH;// 使组件在水平方向和垂直方向都拉伸
        c1.weightx = 1;   // 水平方向占满
        c1.weighty = 0.2; // 垂直方向占30%
        c1.gridx = 0;
        c1.gridy = 0;
        c1.gridwidth = 2; // 横跨两列
        panelMain.add(panelCmd, c1);

        JPanel panelAction = buildActionPanel(flingWindow);
        c1.gridy = 1;
        c1.weightx = 1;
        c1.weighty = 0;
        c1.gridwidth = 2; // 不再跨列
        c1.fill = GridBagConstraints.NONE;
        c1.anchor = GridBagConstraints.WEST;
        panelMain.add(panelAction, c1);

        // 设置 panelResults
        JPanel panelResults = buildResultPanel(flingWindow);
        c1.weighty = 0.8; // 垂直方向占70%
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

    private JPanel buildActionPanel(FlingToolWindow window) {
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
            String selectedData = (String) dataSourceComboBox.getSelectedItem();
            if (selectedData == null) {
                setMsg("Please select a datasource, if there is no option, configure the database link in settings-SQL tool");
                return;
            }


            Optional<SettingsStorageHelper.DatasourceConfig> first = RuntimeHelper.getValidDatasources(toolWindow.getProject().getName())
                    .stream()
                    .filter(new Predicate<SettingsStorageHelper.DatasourceConfig>() {
                        @Override
                        public boolean test(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                            return selectedData.equals(datasourceConfig.getName());
                        }
                    })
                    .findFirst();
            if (!first.isPresent()) {
                setMsg("Can not find valid datasource");
                return;
            }
            //执行explain，并刷新结果
// 执行 EXPLAIN 逻辑部分
            ProgressManager.getInstance().run(new Task.Backgroundable(toolWindow.getProject(), "Analysis sql, please wait ...", false) {

                                                  @Override
                                                  public void run(@NotNull ProgressIndicator progressIndicator) {
                                                      analysisSql(first.get(), sqlText);
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

    private void analysisSql(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sqlText) {
        try (Connection connection = MysqlUtil.getDatabaseConnection(datasourceConfig);
             PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sqlText);
             ResultSet resultSet = statement.executeQuery()) {

            // 解析结果并刷新表格
            List<Object[]> resultData = new ArrayList<>();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 读取 ResultSet 数据
            while (resultSet.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = resultSet.getObject(i);
                }
                resultData.add(row);
            }

            // 使用加载数据的 API 刷新表格
            SwingUtilities.invokeLater(() -> loadAnalysis(resultData.toArray(new Object[0][])));
        } catch (Throwable ex) {
            setMsg("Error executing EXPLAIN: " + ex.getMessage());
        }
    }

    private JPanel buildResultPanel(FlingToolWindow window) {
        panelResults = new JPanel(new BorderLayout());
        setMsg("");
        return panelResults;
    }

    private void setMsg(String msg){
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


    // 加载数据的 API
    private void loadAnalysis(Object[][] data) {

        // 创建表格结构
        String[] columnNames = {"Id", "Select Type", "Table", "Type", "Possible Keys", "Key", "Key Length", "Ref", "Rows", "Extra"};
        // 使用 JBTable
        JBTable explainTable = new JBTable(new DefaultTableModel(data, columnNames));

        // 表格效果设置
        explainTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS); // 自动调整列宽
        explainTable.setFillsViewportHeight(true); // 填满视图高度
        explainTable.getTableHeader().setReorderingAllowed(false); // 禁止列拖动

        // 自定义渲染器实现自动换行
        explainTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JTextArea textArea = new JTextArea();
                textArea.setText(value == null ? "" : value.toString());
                textArea.setWrapStyleWord(true); // 单词换行
                textArea.setLineWrap(true); // 自动换行
                textArea.setOpaque(true); // 确保颜色背景一致
                textArea.setFont(table.getFont()); // 保持表格的字体一致
                if (isSelected) {
                    textArea.setBackground(table.getSelectionBackground());
                    textArea.setForeground(table.getSelectionForeground());
                } else {
                    textArea.setBackground(table.getBackground());
                    textArea.setForeground(table.getForeground());
                }
                return textArea;
            }
        });

        // 垂直滚动条，禁止横向滚动条
        JBScrollPane scrollPane = new JBScrollPane(explainTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

//
//        DefaultTableModel model = (DefaultTableModel) explainTable.getModel();
//        model.setRowCount(0); // 清空现有数据
//        for (Object[] row : data) {
//            model.addRow(row); // 添加新行
//        }
        panelResults.removeAll();
        panelResults.add(scrollPane, BorderLayout.CENTER);
        panelResults.revalidate();
        panelResults.repaint();
    }



    private @NotNull JPanel buildCmdPanel(FlingToolWindow window) {
        JPanel panelCmd = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panelCmd.setLayout(gridbag);

        JLabel curlLabel = new JLabel("Sql:");
        inputSqlField = new LanguageTextField(SqlLanguage.INSTANCE, toolWindow.getProject(), "", false);
//        inputTextArea.getEmptyText().setText("input your sql");

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
        panelCmd.add(inputSqlField, c);
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
