package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.drop.Drop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MysqlDDLUtil {

    public static String rollbackDdl(Statement statement, Connection connection) {
        if (statement instanceof Alter) {
            return rollbackAlterTable((Alter) statement, connection);
        } else if (statement instanceof Drop) {
            return rollbackDrop((Drop) statement, connection);
        } else if (statement instanceof CreateTable) {
            return rollbackCreateTable((CreateTable) statement);
        } else if (statement instanceof CreateIndex) {
            return rollbackCreateIndex((CreateIndex) statement);
        } else {
            throw new UnsupportedOperationException("Unsupported DDL statement: " + statement.getClass().getSimpleName());
        }
    }


    public static String checkDdl(Statement statement, Connection connection) {
        if (statement instanceof Alter) {
            return checkAlterTable((Alter) statement, connection);
        } else if (statement instanceof Drop) {
            return checkDrop((Drop) statement, connection);
        } else if (statement instanceof CreateTable) {
            return checkCreateTable((CreateTable) statement,connection);
        } else if (statement instanceof CreateIndex) {
            return checkCreateIndex((CreateIndex) statement,connection);
        } else {
            throw new UnsupportedOperationException("Unsupported DDL statement: " + statement.getClass().getSimpleName());
        }
    }




    private static String rollbackAlterTable(Alter alter, Connection connection) {
        StringBuilder rollbackDDL = new StringBuilder();
        rollbackDDL.append("ALTER TABLE ").append(alter.getTable().getName()).append(" ");

        List<String> rollbackOps = new ArrayList<>();

        Boolean lastCol = null;
        for (AlterExpression expr : alter.getAlterExpressions()) {
            String operation = expr.getOperation().toString().toUpperCase();

            switch (operation) {
                case "ADD":
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 添加列
                        String columnName = expr.getColumnName();
                        rollbackOps.add("DROP COLUMN " + columnName);
                    } else if (expr.getIndex() != null) {
                        // 添加索引
                        String indexName = expr.getIndex().getName();
                        rollbackOps.add("DROP INDEX " + indexName);
                    } else {
                        // 其他ADD操作，无法处理
                        throw new UnsupportedOperationException("Unsupported ADD operation in ALTER TABLE: " + expr);
                    }
                    break;
                case "DROP":
                    // 处理DROP操作
                    try {
                        if (expr.getColumnName() != null) {
                            lastCol = Boolean.TRUE;
                            // 删除列
                            String columnName = expr.getColumnName();
                            String columnDefinition = getColumnDefinition(alter.getTable().getName(), columnName, connection);
                            if (columnDefinition != null) {
                                rollbackOps.add("ADD COLUMN " + columnDefinition);
                            } else {
                                throw new UnsupportedOperationException("Cannot rollback DROP COLUMN - Column definition not found: " + columnName);
                            }
                        } else if (expr.getIndex() != null) {
                            lastCol = Boolean.FALSE;
                            // 删除索引
                            String indexName = expr.getIndex().getName();
                            String indexDefinition = getIndexDefinition(alter.getTable().getName(), indexName, connection);
                            if (indexDefinition != null) {
                                rollbackOps.add("ADD " + indexDefinition);
                            } else {
                                throw new UnsupportedOperationException("Cannot rollback DROP INDEX - Index definition not found: " + indexName);
                            }
                        } else {
                            lastCol = null;
                            // 其他DROP操作，无法处理
                            throw new UnsupportedOperationException("Unsupported DROP operation in ALTER TABLE: " + expr);
                        }
                    } catch (SQLException e) {
                        throw new UnsupportedOperationException("Failed to retrieve column or index definition for rollback", e);
                    }
                    break;

                case "UNSPECIFIC":
                    // 处理DROP操作
                    try {
                        if (lastCol == null) {
                            throw new UnsupportedOperationException("Unsupported UNSPECIFIC operation in ALTER TABLE: " + alter.getTable().getName());
                        }
                        if (lastCol) {
                            // 删除列
                            String columnName = expr.getOptionalSpecifier();
                            String columnDefinition = getColumnDefinition(alter.getTable().getName(), columnName, connection);
                            if (columnDefinition != null) {
                                rollbackOps.add("ADD COLUMN " + columnDefinition);
                            } else {
                                throw new UnsupportedOperationException("Cannot rollback DROP COLUMN - Column definition not found: " + columnName);
                            }
                        } else {
                            // 删除索引
                            String indexName = expr.getOptionalSpecifier();
                            String indexDefinition = getIndexDefinition(alter.getTable().getName(), indexName, connection);
                            if (indexDefinition != null) {
                                rollbackOps.add("ADD " + indexDefinition);
                            } else {
                                throw new UnsupportedOperationException("Cannot rollback DROP INDEX - Index definition not found: " + indexName);
                            }
                        }
                    } catch (SQLException e) {
                        throw new UnsupportedOperationException("Failed to retrieve column or index definition for rollback", e);
                    }

                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported ALTER operation: " + operation);
            }
        }

        rollbackDDL.append(String.join(", ", rollbackOps));
        rollbackDDL.append(";");
        return rollbackDDL.toString();
    }

    private static String checkAlterTable(Alter alter, Connection connection) {
        return getTableCreateSQL(connection,alter.getTable().getName());
    }

    private static String getColumnDefinition(String tableName, String columnName, Connection connection) throws SQLException {
        String query = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                StringBuilder definition = new StringBuilder();
                definition.append(rs.getString("COLUMN_NAME")).append(" ");
                definition.append(rs.getString("COLUMN_TYPE")).append(" ");
                if ("NO".equals(rs.getString("IS_NULLABLE"))) {
                    definition.append("NOT NULL ");
                }
                String defaultValue = rs.getString("COLUMN_DEFAULT");
                if (defaultValue != null) {
                    definition.append("DEFAULT '").append(defaultValue).append("' ");
                }
                String extra = rs.getString("EXTRA");
                if (extra != null && !extra.isEmpty()) {
                    definition.append(extra).append(" ");
                }
                return definition.toString().trim();
            }
        }
        return null;
    }

    private static String getIndexDefinition(String tableName, String indexName, Connection connection) throws SQLException {
        String query = "SELECT INDEX_NAME, COLUMN_NAME, INDEX_TYPE, NON_UNIQUE " +
                "FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, indexName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                StringBuilder definition = new StringBuilder();
                definition.append("INDEX ").append(rs.getString("INDEX_NAME")).append(" ");
                definition.append("ON ").append(tableName).append(" (");
                definition.append(rs.getString("COLUMN_NAME")).append(")");
                if ("1".equals(rs.getString("NON_UNIQUE"))) {
                    definition.append(" NON_UNIQUE");
                }
                return definition.toString();
            }
        }
        return null;
    }


    /**
     * 生成 DROP 语句的回滚 DDL（仅回滚表结构）
     *
     * @param drop       要回滚的 Drop 语句
     * @param connection 数据库连接
     * @return 回滚的 DDL 语句
     */
    public static String checkDrop(Drop drop, Connection connection) {
        // 获取 Drop 的类型（表、索引等）
        String type = drop.getType();
        // 仅处理 DROP TABLE 的情况
        String tableName = null;
        if (type.equalsIgnoreCase("TABLE")) {
            tableName = remove(drop.getName().getName());
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            tableName = remove(drop.getParameters().get(1));
        } else {
            throw new UnsupportedOperationException("Unsupported DDL statement: " + drop.getClass().getSimpleName());
        }
        return getTableCreateSQL(connection, tableName);
    }

    /**
     * 生成 DROP 语句的回滚 DDL（仅回滚表结构）
     *
     * @param drop       要回滚的 Drop 语句
     * @param connection 数据库连接
     * @return 回滚的 DDL 语句
     */
    public static String rollbackDrop(Drop drop, Connection connection) {
        StringBuilder rollbackDDL = new StringBuilder();

        // 获取 Drop 的类型（表、索引等）
        String type = drop.getType();
        // 仅处理 DROP TABLE 的情况
        if (type.equalsIgnoreCase("TABLE")) {
            String tableName = remove(drop.getName().getName());
            // 获取表的创建语句
            String createTableSQL = getTableCreateSQL(connection, tableName);
            if (createTableSQL != null) {
                rollbackDDL.append(createTableSQL).append(";\n");
            }
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            String indexName = remove(drop.getName().getName());
            String tableName = remove(drop.getParameters().get(1));
            // 获取索引的创建语句
            String createIndexSQL = getIndexCreateSQL(connection, tableName, indexName);
            if (createIndexSQL != null) {
                rollbackDDL.append(createIndexSQL).append(";\n");
            }
        } else {
            throw new UnsupportedOperationException("Unsupported DDL statement: " + drop.getClass().getSimpleName());
        }
        return rollbackDDL.toString();
    }

    /**
     * 从数据库中获取表的创建 SQL
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 表的创建 SQL，如果失败则返回 null
     */
    private static String getTableCreateSQL(Connection connection, String tableName) throws RuntimeException {
        String sql = "SHOW CREATE TABLE " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("Create Table");
            }
        } catch (Throwable e) {
            throw new RuntimeException("-- Failed to get create statement for table: " + tableName + ", " + e.getMessage());
        }
        return null;
    }


    /**
     * 从数据库中获取索引的创建 SQL
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @param indexName  索引名
     * @return 索引的创建 SQL，如果失败则返回 null
     */
    private static String getIndexCreateSQL(Connection connection, String tableName, String indexName) throws RuntimeException {
        String sql = "SHOW INDEX FROM " + tableName + " WHERE Key_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, indexName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // 构建创建索引的 SQL
                    StringBuilder createIndexSQL = new StringBuilder();
                    createIndexSQL.append("CREATE INDEX ").append(indexName).append(" ON ").append(tableName).append(" (");
                    // 获取索引的列名
                    createIndexSQL.append(rs.getString("Column_name")).append(")");
                    return createIndexSQL.toString();
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("-- Failed to get create statement for index: " + indexName + ", " + e.getMessage());
        }
        return null;
    }

    /**
     * 生成 CREATE TABLE 语句的回滚 DDL（即 DROP TABLE）
     *
     * @param createTable 要回滚的 CreateTable 语句
     * @return 回滚的 DDL 语句
     */
    private static String rollbackCreateTable(CreateTable createTable) {
        String tableName = createTable.getTable().getName();
        return "DROP TABLE " + tableName + ";";
    }

    private static String rollbackCreateIndex(CreateIndex createIndex) {
        // 生成回滚的DROP INDEX语句
        // 例如：原语句是 "CREATE INDEX index_name ON table_name(column_name)"
        // 回滚语句可能是 "DROP INDEX index_name ON table_name"
        String indexName = createIndex.getIndex().getName();
        String tableName = createIndex.getTable().getName();
        return "DROP INDEX " + indexName + " ON " + tableName + ";";
    }

    /**
     * 生成 CREATE TABLE 语句的回滚 DDL（即 DROP TABLE）
     *
     * @param createTable 要回滚的 CreateTable 语句
     * @param connection
     * @return 回滚的 DDL 语句
     */
    private static String checkCreateTable(CreateTable createTable, Connection connection) {
        String tableName = createTable.getTable().getName();
        return getTableCreateSQL(connection, tableName);
    }

    private static String checkCreateIndex(CreateIndex createIndex, Connection connection) {
        // 生成回滚的DROP INDEX语句
        // 例如：原语句是 "CREATE INDEX index_name ON table_name(column_name)"
        // 回滚语句可能是 "DROP INDEX index_name ON table_name"
        String indexName = createIndex.getIndex().getName();
        String tableName = createIndex.getTable().getName();
        return getTableCreateSQL(connection, tableName);
    }


    private static String remove(String xx) {
        if (xx.startsWith("`") && xx.endsWith("`")) {
            return xx.substring(1, xx.length() - 1);
        }
        return xx;
    }



    public static String executeDDl(String sql, Connection connection) {
        // 校验 SQL 合法性
        if (sql == null || sql.trim().isEmpty()) {
            return "Error: SQL statement is empty or null.";
        }

        long startTime = System.currentTimeMillis();
        String result;

        try (java.sql.Statement statement = connection.createStatement()) {
            // 执行 DDL
            boolean isResultSet = statement.execute(sql);
            long endTime = System.currentTimeMillis();

            // 构造成功结果
            result = String.format(
                    "Execute Success!\n" +
                            "Time: %dms\n" +
                            "Type: DDL\n" +
                            "Result: %s",
                    endTime - startTime,
                    isResultSet ? "Returned a ResultSet" : "No ResultSet (likely DDL)"
            );

        } catch (SQLException e) {
            // 构造错误结果
            long endTime = System.currentTimeMillis();
            result = String.format(
                    "Execute Failed!\n" +
                            "Time: %dms\n" +
                            "Error: %s\n" +
                            "SQLState: %s\n" +
                            "Vendor Code: %d",
                    endTime - startTime,
                    e.getMessage(),
                    e.getSQLState(),
                    e.getErrorCode()
            );

            // 可选：打印详细日志（生产环境建议用 Logger）
            e.printStackTrace();
        }
        return result;
    }

    public static void main(String[] args) throws JSQLParserException, SQLException {
        // 测试示例
        String ddl1 = "CREATE TABLE my_table (id INT, name VARCHAR(255));";
        String ddl2 = "drop index PRIMARY on house;";
        String ddl3 = "ALTER TABLE house drop COLUMN  name,age;";
        String ddl4 = "CREATE INDEX idx_name ON my_table(name);";

        Statement stmt1 = CCJSqlParserUtil.parse(ddl1);
        Statement stmt2 = CCJSqlParserUtil.parse(ddl2);
        Statement stmt3 = CCJSqlParserUtil.parse(ddl3);
        Statement stmt4 = CCJSqlParserUtil.parse(ddl4);
        SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
        datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        datasourceConfig.setUsername("root");
        datasourceConfig.setPassword("wsd123==");
        datasourceConfig.setName("111");
        Connection databaseConnection = MysqlUtil.getDatabaseConnection(datasourceConfig);
        String rollbackDdl = rollbackDdl(stmt3, databaseConnection);
        System.out.println(rollbackDdl);
    }
}