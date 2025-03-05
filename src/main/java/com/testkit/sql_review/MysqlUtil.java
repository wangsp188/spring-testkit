package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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

    public static Statements parses(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("sql is null");
        }
        try {
            return CCJSqlParserUtil.parseStatements(sql);
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
            if (connection.getCatalog() == null) {
                throw new IllegalArgumentException("schema must be specified");
            }
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
    public static SqlTable getTableMeta(Connection connection, String tableName) {
        tableName = remove(tableName);
        SqlTable table = new SqlTable(tableName);

        try {
            // 获取数据库元数据
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            // 获取表字段信息
            try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME"); // 字段名
                    String columnType = columns.getString("TYPE_NAME");   // 数据类型
                    int columnSize = columns.getInt("COLUMN_SIZE");       // 字段长度

                    // 添加字段到表
                    table.addField(columnName, columnType, columnSize);
                }
            }

            // 获取表索引信息
            try (ResultSet indices = metaData.getIndexInfo(catalog, schema, tableName, false, false)) {
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
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        }
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
        String sql = null; // 明确指定 Schema
        try {
            sql = "SHOW TABLE STATUS FROM `" + connection.getCatalog() + "` WHERE `name` = ?";
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setQueryTimeout(30);
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Rows");
                }
            }
        } catch (SQLException e) {
            System.err.println("获取表的近似行数失败: " + e.getMessage());
        }
        return -1;
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
                    ret = statement.getUpdateCount() > 0 ? ("affect row:" + statement.getUpdateCount()) : null;
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

    public static String getColumnDefinition(String tableName, String columnName, Connection connection) {
        // SQL 查询包括 COLUMN_COMMENT 以获取列的注释
        String query = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setQueryTimeout(30); // 设置查询超时时间为30秒
            stmt.setString(1, remove(tableName)); // 设置表名参数
            stmt.setString(2, remove(columnName)); // 设置列名参数
            ResultSet rs = stmt.executeQuery(); // 执行查询
            if (rs.next()) {
                StringBuilder definition = new StringBuilder();
                // 添加列名
                definition.append("`").append(rs.getString("COLUMN_NAME")).append("` ");
                // 添加列类型
                definition.append(rs.getString("COLUMN_TYPE")).append(" ");
                // 添加是否可为空
                if ("NO".equals(rs.getString("IS_NULLABLE"))) {
                    definition.append("NOT NULL ");
                }
                // 添加默认值
                String defaultValue = rs.getString("COLUMN_DEFAULT");
                if (defaultValue != null) {
                    definition.append("DEFAULT '").append(defaultValue).append("' ");
                }
                // 添加额外信息（如自动递增等）
                String extra = rs.getString("EXTRA");
                if (extra != null && !extra.isEmpty()) {
                    definition.append(extra).append(" ");
                }
                // 添加列注释
                String comment = rs.getString("COLUMN_COMMENT");
                if (comment != null && !comment.isEmpty()) {
                    definition.append("COMMENT '").append(comment).append("' ");
                }
                // 返回生成的列定义字符串，并去除末尾的空格
                return definition.toString().trim();
            }
            // 如果没有找到对应的列，返回 null
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static String getIndexDefinition(String tableName, String indexName, Connection connection) {
        // SQL 查询包括 INDEX_COMMENT 以获取索引的注释
        String query = "SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, INDEX_TYPE, " +
                "       NON_UNIQUE, SUB_PART, COLLATION, INDEX_COMMENT " +
                "FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "  AND TABLE_NAME = ? " +
                "  AND INDEX_NAME = ? " +
                "ORDER BY SEQ_IN_INDEX"; // 确保列顺序正确

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, remove(tableName)); // 设置表名参数
            stmt.setString(2, remove(indexName)); // 设置索引名参数
            ResultSet rs = stmt.executeQuery(); // 执行查询

            if (!rs.isBeforeFirst()) {
                return null; // 没有找到索引
            }

            StringBuilder definition = new StringBuilder();
            List<String> columns = new ArrayList<>();

            // 处理索引类型修饰符
            String oriType = null;
            String indexType = "";
            String indexComment = null;
            if (rs.next()) {
                oriType = rs.getString("INDEX_TYPE").toUpperCase();
                indexComment = rs.getString("INDEX_COMMENT");
                switch (oriType) {
                    case "FULLTEXT":
                        indexType = "FULLTEXT ";
                        break;
                    case "SPATIAL":
                        indexType = "SPATIAL ";
                        break;
                }

                // 处理UNIQUE需要放在类型之后（因为FULLTEXT/SPATIAL不能是UNIQUE）
                if ("0".equals(rs.getString("NON_UNIQUE"))) {
                    indexType = "UNIQUE " + indexType;
                }

                // 处理第一个列
                columns.add(buildColumnExpression(rs));
            }

            // 收集剩余列
            while (rs.next()) {
                columns.add(buildColumnExpression(rs));
            }

            definition.append(indexType)
                    .append("INDEX `").append(indexName).append("` (")
                    .append(String.join(", ", columns))
                    .append(")");

            // 处理索引类型声明（如BTREE）
            if (!"BTREE".equalsIgnoreCase(oriType)) {
                definition.append(" USING ").append(oriType);
            }

            // 添加索引注释
            if (indexComment != null && !indexComment.isEmpty()) {
                definition.append(" COMMENT '").append(indexComment).append("'");
            }

            return definition.toString();
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // 构建单个列的表达式
    private static String buildColumnExpression(ResultSet rs) throws SQLException {
        String column = "`" + rs.getString("COLUMN_NAME") + "`";

        // 处理前缀长度
        String subPart = rs.getString("SUB_PART");
        if (subPart != null) {
            column += "(" + subPart + ")";
        }

        // 处理排序方式 (A=asc, D=desc)
        String collation = rs.getString("COLLATION");
        if ("D".equalsIgnoreCase(collation)) {
            column += " DESC";
        }

        return column;
    }

    /**
     * 从数据库中获取表的创建 SQL
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 表的创建 SQL，如果失败则返回 null
     */
    public static String getTableCreateSQL(Connection connection, String tableName) {
        String sql = "SHOW CREATE TABLE " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setQueryTimeout(30);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("Create Table");
            }
            return null;
        } catch (SQLException e) {
            if ("42000".equals(e.getSQLState())) {
                return null;
            }
            throw new RuntimeException(e.getMessage(), e);
        }
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

        getTableCreateSQL(datasourceConfig.newConnection(), "11");
//        SqlTable house = getTableMeta(datasourceConfig.newConnection(), "house1");
//        System.out.println(house);
    }
}
