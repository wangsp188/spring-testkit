package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;

import java.sql.*;
import java.util.ArrayList;

public class MysqlUtil {


    public static String testConnectionAndClose(SettingsStorageHelper.DatasourceConfig datasource) {
        if (datasource==null) {
            return "datasource is null";
        }
        Connection connection = null;
        try {
            // 尝试从 DataSource 获取连接
            connection = getDatabaseConnection(datasource);
            // 如果获取成功，返回 null 表示连接成功
            return null;
        } catch (Exception e) {
            // 捕获异常并返回异常信息
            return e.getMessage();
        } finally {
            // 确保连接在使用后关闭，避免资源泄漏
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // 忽略关闭连接的异常
                }
            }
        }
    }

    public static Connection getDatabaseConnection(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
        if (datasourceConfig == null) {
            throw new IllegalArgumentException("Datasource config cannot be null");
        }
        // 加载驱动（如果未自动注册）
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 直接通过 DriverManager 获取连接
            return DriverManager.getConnection(
                    datasourceConfig.getUrl(),
                    datasourceConfig.getUsername(),
                    datasourceConfig.getPassword()
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to connect", e);
        }
    }


    /**
     * 根据表名获取表的元数据信息
     * @param tableName 表名
     * @return SqlTable 对象，包含字段信息和索引信息
     * @throws Exception 异常
     */
    public static SqlTable getTableMeta(Connection connection, String tableName) throws Exception {
        SqlTable table = new SqlTable(tableName);

        // 获取数据库元数据
        DatabaseMetaData metaData = connection.getMetaData();

        // 获取表字段信息
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME"); // 字段名
                String columnType = columns.getString("TYPE_NAME");   // 数据类型
                int columnSize = columns.getInt("COLUMN_SIZE");       // 字段长度

                // 添加字段到表
                table.addField(columnName, columnType, columnSize);
            }
        }

        // 获取表索引信息
        try (ResultSet indices = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indices.next()) {
                // 索引名
                String indexName = indices.getString("INDEX_NAME");

                // 如果索引名为空，跳过（一般是 PRIMARY KEY 可能没有 INDEX_NAME）
                if (indexName == null) {
                    continue;
                }

                // 获取索引列名
                String columnName = indices.getString("COLUMN_NAME");
                if (columnName != null) {
                    // 查找当前索引是否已经在对象中
                    SqlTable.Index index = table.getIndices().stream()
                            .filter(i -> i.getName().equals(indexName))
                            .findFirst()
                            .orElse(null);

                    if (index == null) {
                        // 如果索引不存在，则创建并添加
                        index = new SqlTable.Index(indexName, new ArrayList<>());
                        table.getIndices().add(index);
                    }

                    // 将列名加入索引
                    index.getColumnNames().add(columnName);
                }
            }
        }

        // 获取表的近似行数（使用 SHOW TABLE STATUS）
        int approximateRowCount = getTableRowCountByShowTableStatus(connection, tableName);
        table.setRowCount(approximateRowCount);

        return table;
    }

    /**
     * 使用 SHOW TABLE STATUS 获取表的近似行数
     * @param connection 数据库连接
     * @param tableName 表名
     * @return 表的估算行数，失败时返回 -1
     */
    private static int getTableRowCountByShowTableStatus(Connection connection, String tableName) {
        String sql = "SHOW TABLE STATUS where `name` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tableName); // 使用表名匹配
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Rows"); // 获取 Rows 列的值
                }
            }
        } catch (SQLException e) {
            System.err.println("获取表的近似行数失败: " + e.getMessage());
        }
        return -1; // 如果获取失败，返回 -1（表示未知）
    }


    public static void main(String[] args) throws Exception {
        SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
        datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        datasourceConfig.setUsername("root");
        datasourceConfig.setPassword("wsd123==");
        datasourceConfig.setName("111");


        SqlTable house = getTableMeta(datasourceConfig.newConnection(), "house1");
        System.out.println(house);
    }
}
