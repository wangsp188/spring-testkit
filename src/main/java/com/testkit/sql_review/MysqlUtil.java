package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MysqlUtil {


    public static net.sf.jsqlparser.statement.Statement parse(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("sql is null");
        }
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Throwable ex) {
            // 错误处理
            throw new RuntimeException("Sorry, i can't parse the sql:\n" + sql + ",\n" + ex.getMessage());
        }
    }

    public static Object testConnectionAndClose(SettingsStorageHelper.DatasourceConfig datasource) {
        if (datasource == null) {
            return "datasource is null";
        }

        Connection connection = null;
        try {
            // 尝试从 DataSource 获取连接
            connection = getDatabaseConnection(datasource);

            // 检查 DDL 权限
            return checkWritePermission(connection);
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

    /**
     * 检查当前连接是否具有 DDL 权限
     * 0:read
     * 1:write
     * 2:ddl
     *
     * @param connection 数据库连接
     * @return true 表示有 DDL 权限，false 表示没有
     * @throws Exception 如果查询权限时发生异常
     */
    private static Integer checkWritePermission(Connection connection) throws Exception {
        int ret = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW GRANTS FOR CURRENT_USER();")) {

            // 检查权限字符串是否包含 DDL 权限
            while (rs.next()) {
                String grant = rs.getString(1);
                if (grant == null) {
                    continue;
                }
                // 判断权限字符串中是否包含 DDL 权限
                if (grant.contains("ALL PRIVILEGES") ||
                        grant.contains("ALTER") ||
                        grant.contains("DROP")
                ) {
                    return 2;
                } else if (grant.contains("UPDATE")) {
                    ret = 1;
                }
            }
        }
        return ret;
    }

    public static Connection getDatabaseConnection(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
        if (datasourceConfig == null) {
            throw new IllegalArgumentException("Datasource config cannot be null");
        }
        // 加载驱动（如果未自动注册）
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = datasourceConfig.getUrl();
            if (!url.contains("?")) {
                url += "?connectTimeout=5000&socketTimeout=30000";
            } else {
                if (!url.contains("connectTimeout=")) {
                    url += "&connectTimeout=5000";
                }

                if (!url.contains("socketTimeout=")) {
                    url += "&socketTimeout=30000";
                }
            }

            // 直接通过 DriverManager 获取连接
            return DriverManager.getConnection(
                    url,
                    datasourceConfig.getUsername(),
                    datasourceConfig.getPassword()
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to connect", e);
        }
    }


    /**
     * 根据表名获取表的元数据信息
     *
     * @param tableName 表名
     * @return SqlTable 对象，包含字段信息和索引信息
     * @throws Exception 异常
     */
    public static SqlTable getTableMeta(Connection connection, String tableName) throws Exception {
        tableName = remove(tableName);
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
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 表的估算行数，失败时返回 -1
     */
    public static int getTableRowCountByShowTableStatus(Connection connection, String tableName) {
        tableName = remove(tableName);
        String sql = "SHOW TABLE STATUS where `name` = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setQueryTimeout(30);
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

    public static String remove(String xx) {
        if (xx.startsWith("`") && xx.endsWith("`")) {
            return xx.substring(1, xx.length() - 1);
        }
        return xx;
    }

    public static SqlRet executeSQL(String sql, Connection connection) {
        if (sql == null || connection == null) {
            return SqlRet.buildFail("sql is null");
        }
        boolean autoCommit = false;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            return SqlRet.buildFail("setAutoCommit(false) error: " + e.getMessage());
        }

        long startTime = System.currentTimeMillis();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            // 提交定时取消任务
            ScheduledFuture<?> cancelTask = executor.schedule(() -> {
                try {
                    if (!statement.isClosed()) {
                        statement.cancel(); // 在超时后取消执行
                    }
                } catch (SQLException ex) {
                    System.err.println("Cancel failed: " + ex.getMessage());
                }
            }, 32, TimeUnit.SECONDS);

            try {
                boolean isResultSet = statement.execute(sql);
                connection.commit();
                long endTime = System.currentTimeMillis();
                //取最前面的
                // 取消定时任务（如果尚未执行）
                cancelTask.cancel(true);

                //select语句，返回最近的
                //update语句，返回受影响的行
                //insert语句，返回受影响的行
                //alter,ddl语句返回null
                Object ret = null;
                if (isResultSet) {
                    ResultSet resultSet = statement.getResultSet();
                    if (sql.replace(" ", "").toLowerCase().startsWith("selectcount(")) {
                        //如果是select count 则返回第一行的第一列的结果
                        resultSet.next();
                        ret = resultSet.getObject(1);
                    } else {
                        //返回行数
                        ret = "return row" + getResultSetRowCount(resultSet);
                    }
                } else {
                    ret = statement.getUpdateCount() >= 0 ? ("affect row:" + statement.getUpdateCount()) : null;
                }

                return SqlRet.buildSuccess(ret);
            } catch (SQLException e) {
                // 判断是否为取消导致的超时
                boolean isCancelled = e.getSQLState() != null && e.getSQLState().equals("HY008");
                String errorMsg = isCancelled ? "timeout" : e.getMessage();

                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    errorMsg = ex.getMessage();
                }

                long endTime = System.currentTimeMillis();
                return SqlRet.buildFail("SQLState: " + e.getSQLState() + "\nVendor Code: " + e.getErrorCode() + "\n" + errorMsg);
            } finally {
                executor.shutdownNow(); // 确保关闭线程池
            }
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            return SqlRet.buildFail("SQLState: " + e.getSQLState() + "\nVendor Code: " + e.getErrorCode() + "\n" + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (Throwable e) {
            }
        }
    }

    private static int getResultSetRowCount(ResultSet rs) throws SQLException {
        int rowCount = 0;
        rs.last(); // 移动到最后一行
        rowCount = rs.getRow(); // 获取当前行号
        rs.beforeFirst(); // 将游标重置到第一行之前，以便后续处理
        return rowCount;
    }


    public static class SqlRet {
        private boolean success;
        private Object ret;


        public static SqlRet buildSuccess(Object ret) {
            SqlRet sqlRet = new SqlRet();
            sqlRet.setSuccess(true);
            sqlRet.setRet(ret);
            return sqlRet;
        }

        public static SqlRet buildFail(String message) {
            SqlRet sqlRet = new SqlRet();
            sqlRet.setSuccess(false);
            sqlRet.setRet(message);
            return sqlRet;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Object getRet() {
            return ret;
        }

        public void setRet(Object ret) {
            this.ret = ret;
        }

        @Override
        public String toString() {
            return (success ? "SUCCESS" : "FAIL")
                    + ",\n" + ret;
        }
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
