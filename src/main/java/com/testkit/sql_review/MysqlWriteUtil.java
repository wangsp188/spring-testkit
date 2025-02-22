package com.testkit.sql_review;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.update.Update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class MysqlWriteUtil {

    public static String rollbackWriteSQL(Statement statement, Connection connection) throws RuntimeException {
        if (statement instanceof Alter) {
            return rollbackAlterTable((Alter) statement, connection);
        } else if (statement instanceof Drop) {
            return rollbackDrop((Drop) statement, connection);
        } else if (statement instanceof CreateTable) {
            return rollbackCreateTable((CreateTable) statement);
        } else if (statement instanceof CreateIndex) {
            return rollbackCreateIndex((CreateIndex) statement);
        } else if (statement instanceof Update) {
            return "Unsupported SQL type: " + statement.getClass().getSimpleName();
        } else {
            throw new UnsupportedOperationException("Unsupported SQL type: " + statement.getClass().getSimpleName());
        }
    }


    public static String verifyWriteSQL(Statement statement, Connection connection) {
        if (statement instanceof Alter) {
            return verifyAlterTable((Alter) statement, connection);
        } else if (statement instanceof Drop) {
            return verifyDrop((Drop) statement, connection);
        } else if (statement instanceof CreateTable) {
            return verifyCreateTable((CreateTable) statement, connection);
        } else if (statement instanceof CreateIndex) {
            return verifyCreateIndex((CreateIndex) statement, connection);
        } else if (statement instanceof Update) {
            return verifyUpdate((Update) statement, connection);
        } else {
            throw new UnsupportedOperationException("Unsupported SQL type: " + statement.getClass().getSimpleName());
        }
    }


    private static String rollbackAlterTable(Alter alter, Connection connection) {
        StringBuilder rollbackDDL = new StringBuilder();
        rollbackDDL.append("ALTER TABLE ").append(alter.getTable().getName()).append(" ");

        List<String> rollbackOps = new ArrayList<>();

        Boolean lastCol = null;
        for (AlterExpression expr : alter.getAlterExpressions()) {
            String operation = expr.getOperation().toString().toUpperCase();
            String tableName = alter.getTable().getName();
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
                    if (expr.getColumnName() != null) {
                        lastCol = Boolean.TRUE;
                        // 删除列
                        String columnName = expr.getColumnName();
                        String columnDefinition = null;
                        try {
                            columnDefinition = getColumnDefinition(tableName, columnName, connection);
                        } catch (SQLException e) {
                            throw new RuntimeException("Column definition not found: " + tableName + "." + columnName + " " + e.getMessage());
                        }
                        if (columnDefinition != null) {
                            rollbackOps.add("ADD COLUMN " + columnDefinition);
                        } else {
                            throw new RuntimeException("Column definition not found: " + tableName + "." + columnName + "  not exist");
                        }
                    } else if (expr.getIndex() != null) {
                        lastCol = Boolean.FALSE;
                        // 删除索引
                        String indexName = expr.getIndex().getName();
                        String indexDefinition = null;
                        try {
                            indexDefinition = getIndexDefinition(tableName, indexName, connection);
                        } catch (SQLException e) {
                            throw new RuntimeException("Index definition not found: " + tableName + "." + indexName + " " + e.getMessage());
                        }
                        if (indexDefinition != null) {
                            rollbackOps.add("ADD " + indexDefinition);
                        } else {
                            throw new RuntimeException("Index definition not found: " + tableName + "." + indexName + "  not exist");
                        }
                    } else {
                        lastCol = null;
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported DROP operation in ALTER TABLE: " + expr);
                    }
                    break;

                case "UNSPECIFIC":
                    // 处理DROP操作
                    if (lastCol == null) {
                        throw new UnsupportedOperationException("Unsupported UNSPECIFIC operation in ALTER TABLE: " + tableName);
                    }
                    if (lastCol) {
                        // 删除列
                        String columnName = expr.getOptionalSpecifier();
                        String columnDefinition = null;
                        try {
                            columnDefinition = getColumnDefinition(tableName, columnName, connection);
                        } catch (SQLException e) {
                            throw new RuntimeException("Column definition not found: " + tableName + "." + columnName + " " + e.getMessage());
                        }
                        if (columnDefinition != null) {
                            rollbackOps.add("ADD COLUMN " + columnDefinition);
                        } else {
                            throw new RuntimeException("Column definition not found: " + tableName + "." + columnName + "  not exist");
                        }
                    } else {
                        // 删除索引
                        String indexName = expr.getOptionalSpecifier();
                        String indexDefinition = null;
                        try {
                            indexDefinition = getIndexDefinition(tableName, indexName, connection);
                        } catch (SQLException e) {
                            throw new RuntimeException("Index definition not found: " + tableName + "." + indexName + " " + e.getMessage());
                        }
                        if (indexDefinition != null) {
                            rollbackOps.add("ADD " + indexDefinition);
                        } else {
                            throw new RuntimeException("Index definition not found: " + tableName + "." + indexName + "  not exist");
                        }
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

    private static String verifyAlterTable(Alter alter, Connection connection) {
        Boolean lastCol = null;
        List<String> checks = new ArrayList<>();
        String tableName = alter.getTable().getName();
        for (AlterExpression expr : alter.getAlterExpressions()) {
            String operation = expr.getOperation().toString().toUpperCase();

            switch (operation) {
                case "ADD":
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 添加列
                        String columnName = expr.getColumnName();
                        checks.add(descColumnDefinition(tableName, columnName, connection));
                    } else if (expr.getIndex() != null) {
                        // 添加索引
                        String indexName = expr.getIndex().getName();
                        checks.add(descIndexDefinition(tableName, indexName, connection));
                    } else {
                        // 其他ADD操作，无法处理
                        throw new UnsupportedOperationException("Unsupported ADD operation in ALTER TABLE: " + expr);
                    }
                    break;
                case "DROP":
                    // 处理DROP操作
                    if (expr.getColumnName() != null) {
                        lastCol = Boolean.TRUE;
                        // 删除列
                        String columnName = expr.getColumnName();
                        checks.add(descColumnDefinition(tableName, columnName, connection));
                    } else if (expr.getIndex() != null) {
                        lastCol = Boolean.FALSE;
                        // 删除索引
                        String indexName = expr.getIndex().getName();
                        checks.add(descIndexDefinition(alter.getTable().getName(), indexName, connection));
                    } else {
                        lastCol = null;
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported DROP operation in ALTER TABLE: " + expr);
                    }
                    break;

                case "UNSPECIFIC":
                    // 处理DROP操作
                    if (lastCol == null) {
                        throw new UnsupportedOperationException("Unsupported UNSPECIFIC operation in ALTER TABLE: " + alter.getTable().getName());
                    }
                    if (lastCol) {
                        // 删除列
                        String columnName = expr.getOptionalSpecifier();
                        checks.add(descColumnDefinition(alter.getTable().getName(), columnName, connection));
                    } else {
                        // 删除索引
                        String indexName = expr.getOptionalSpecifier();
                        checks.add(descIndexDefinition(alter.getTable().getName(), indexName, connection));
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ALTER operation: " + operation);
            }
        }
        return String.join(";<br>", checks);
    }


    private static String descColumnDefinition(String tableName, String columnName, Connection connection) {
        try {
            String columnDefinition = getColumnDefinition(tableName, columnName, connection);
            if (columnDefinition == null) {
                return "-- column " + tableName + "." + columnName + " not exits";
            } else {
                return "-- column " + tableName + "." + columnName + " : " + columnDefinition;
            }
        } catch (SQLException e) {
            return "-- column: " + tableName + "." + columnName + ", " + e.getMessage();
        }
    }

    private static String getColumnDefinition(String tableName, String columnName, Connection connection) throws SQLException {
        String query = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setQueryTimeout(30);
            stmt.setString(1, MysqlUtil.remove(tableName));
            stmt.setString(2, MysqlUtil.remove(columnName));
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
            return null;
        }
    }


    private static String descIndexDefinition(String tableName, String indexName, Connection connection) {
        try {
            String indexDefinition = getIndexDefinition(tableName, indexName, connection);
            if (indexDefinition == null) {
                return "-- index: " + tableName + "." + indexName + " not exits";
            } else {
                return "-- index: " + tableName + "." + indexName + " : " + indexDefinition;
            }
        } catch (SQLException e) {
            return "-- index: " + tableName + "." + indexName + ", " + e.getMessage();
        }
    }

    private static String getIndexDefinition(String tableName, String indexName, Connection connection) throws SQLException {
        String query = "SELECT INDEX_NAME, COLUMN_NAME, INDEX_TYPE, NON_UNIQUE " +
                "FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setQueryTimeout(30);
            stmt.setString(1, MysqlUtil.remove(tableName));
            stmt.setString(2, MysqlUtil.remove(indexName));
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
            return null;
        }
    }


    /**
     * 生成 DROP 语句的回滚 DDL（仅回滚表结构）
     *
     * @param drop       要回滚的 Drop 语句
     * @param connection 数据库连接
     * @return 回滚的 DDL 语句
     */
    public static String verifyDrop(Drop drop, Connection connection) {
        // 获取 Drop 的类型（表、索引等）
        String type = drop.getType();
        // 仅处理 DROP TABLE 的情况
        if (type.equalsIgnoreCase("TABLE")) {
            String tableName = MysqlUtil.remove(drop.getName().getName());
            try {
                SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
                if (tableMeta.judgeIsExists()) {
                    return MessageFormat.format("-- table: {0}, row:{1}, column:{2}, index:{3}", tableName, tableMeta.getRowCount(), tableMeta.getFields().size(), tableMeta.getIndices().size());
                }
                return "-- table: " + tableName + " not exits";
            } catch (Throwable e) {
                return "-- table: " + tableName + ", " + e.getMessage();
            }
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            String tableName = MysqlUtil.remove(drop.getParameters().get(1));
            String indexName = MysqlUtil.remove(drop.getName().getName());
            return descIndexDefinition(tableName, indexName, connection);
        }
        throw new UnsupportedOperationException("Unsupported DDL statement: " + drop.getClass().getSimpleName());
    }

    /**
     * 生成 DROP 语句的回滚 DDL（仅回滚表结构）
     *
     * @param drop       要回滚的 Drop 语句
     * @param connection 数据库连接
     * @return 回滚的 DDL 语句
     */
    public static String rollbackDrop(Drop drop, Connection connection) {
        // 获取 Drop 的类型（表、索引等）
        String type = drop.getType();
        // 仅处理 DROP TABLE 的情况
        if (type.equalsIgnoreCase("TABLE")) {
            String tableName = MysqlUtil.remove(drop.getName().getName());
            // 获取表的创建语句
            try {
                String tableCreateSQL = getTableCreateSQL(connection, tableName);
                if (tableCreateSQL == null) {
                    throw new RuntimeException(tableName + " not exits");
                }
                return tableCreateSQL;
            } catch (SQLException e) {
                throw new RuntimeException("table: " + tableName + ", " + e.getMessage());
            }
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            String indexName = MysqlUtil.remove(drop.getName().getName());
            String tableName = MysqlUtil.remove(drop.getParameters().get(1));
            // 获取索引的创建语句
            try {
                String indexDefinition = getIndexDefinition(tableName, indexName, connection);
                if (indexDefinition == null) {
                    throw new RuntimeException(tableName + "." + indexName + " not exits");
                }
                return indexDefinition;
            } catch (SQLException e) {
                throw new RuntimeException("index: " + tableName + "." + indexName + ", " + e.getMessage());
            }
        }
        throw new UnsupportedOperationException("Unsupported DDL statement: " + drop.getClass().getSimpleName());
    }

    /**
     * 从数据库中获取表的创建 SQL
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 表的创建 SQL，如果失败则返回 null
     */
    private static String getTableCreateSQL(Connection connection, String tableName) throws SQLException {
        String sql = "SHOW CREATE TABLE " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(sql);) {
            stmt.setQueryTimeout(30);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("Create Table");
            }
            return null;
        }
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
    private static String verifyCreateTable(CreateTable createTable, Connection connection) {
        String tableName = createTable.getTable().getName();
        try {
            SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
            if (tableMeta.judgeIsExists()) {
                return MessageFormat.format("-- table: {0}, row:{1}, column:{2}, index:{3}", tableName, tableMeta.getRowCount(), tableMeta.getFields().size(), tableMeta.getIndices().size());
            }
            return "-- table: " + tableName + " not exits";
        } catch (Throwable e) {
            return "-- table: " + tableName + ", " + e.getMessage();
        }
    }

    private static String verifyCreateIndex(CreateIndex createIndex, Connection connection) {
        // 生成回滚的DROP INDEX语句
        // 例如：原语句是 "CREATE INDEX index_name ON table_name(column_name)"
        // 回滚语句可能是 "DROP INDEX index_name ON table_name"
        String indexName = createIndex.getIndex().getName();
        String tableName = createIndex.getTable().getName();
        return descIndexDefinition(tableName, indexName, connection);
    }

    private static String verifyUpdate(Update update, Connection connection) {
        String countSql = generateCountSql(update);
        MysqlUtil.SqlRet sqlRet = MysqlUtil.executeSQL(countSql, connection);
        String tableName = update.getTable().getName();
        int allCount = MysqlUtil.getTableRowCountByShowTableStatus(connection, tableName);
        return "Expected affected row:" + sqlRet.getRet() + ",\n" + tableName + "‘s total count(estimate):" + allCount;
    }


    public static String generateCountSql(Update update) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ");

        // 1. 添加主表
        countSql.append(update.getTable());

        // 2. 处理 startJoins
        if (update.getStartJoins() != null) {
            for (Join join : update.getStartJoins()) {
                // 判断是否为隐式逗号分隔（旧式 JOIN）
                if (isImplicitCommaJoin(join)) {
                    // 隐式逗号分隔：添加 ", table2"
                    countSql.append(", ").append(join.getRightItem());
                } else {
                    // 显式 JOIN：保留 "INNER JOIN table2 ON ..."
                    countSql.append(" ").append(join);
                }
            }
        }

        // 3. 添加 WHERE 条件
        if (update.getWhere() != null) {
            countSql.append(" WHERE ").append(update.getWhere().toString());
        }

        // 处理 LIMIT（MySQL 特性）
        if (update.getLimit() != null) {
            countSql.append(" LIMIT ").append(update.getLimit().getRowCount());
        }

        return countSql.toString();
    }

    // 判断是否为隐式逗号分隔的 JOIN
    private static boolean isImplicitCommaJoin(Join join) {
        // 隐式逗号分隔的 JOIN 没有 JOIN 类型（如 INNER/LEFT）且无 ON 条件
        return join.isSimple() &&
                (join.getOnExpressions() == null || join.getOnExpressions().isEmpty()) &&
                !join.isLeft() &&
                !join.isRight() &&
                !join.isInner();
    }


    public static void main(String[] args) throws JSQLParserException, SQLException {
        // 测试示例
//        String ddl1 = "CREATE TABLE my_table (id INT, name VARCHAR(255));";
//        String ddl2 = "drop index PRIMARY on house;";
//        String ddl3 = "ALTER TABLE house drop COLUMN  name,age;";
//        String ddl4 = "CREATE INDEX idx_name ON my_table(name);";
//
//        Statement stmt1 = CCJSqlParserUtil.parse(ddl1);
//        Statement stmt2 = CCJSqlParserUtil.parse(ddl2);
//        Statement stmt3 = CCJSqlParserUtil.parse(ddl3);
//        Statement stmt4 = CCJSqlParserUtil.parse(ddl4);
//        SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
//        datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
//        datasourceConfig.setUsername("root");
//        datasourceConfig.setPassword("wsd123==");
//        datasourceConfig.setName("111");
//        Connection databaseConnection = MysqlUtil.getDatabaseConnection(datasourceConfig);
//        String rollbackDdl = rollbackDdl(stmt3, databaseConnection);
//        System.out.println(rollbackDdl);
    }
}