package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.update.Update;

import java.sql.Connection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MysqlVerifyExecuteUtil {

    public static String rollbackWriteSQL(Statement statement, Connection connection) throws RuntimeException {
        if (statement == null) {
            return "Unsupported Insert";
        }
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
        } else if (statement instanceof Delete) {
            return "Unsupported SQL type: " + statement.getClass().getSimpleName();
        } else {
            throw new UnsupportedOperationException("Unsupported SQL type: " + statement.getClass().getSimpleName());
        }
    }


    public static String verifyWriteSQL(Statement statement, Connection connection) {
        if (statement == null) {
            return "Unsupported Insert";
        }
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
        }else if (statement instanceof Delete) {
            return verifyDelete((Delete) statement, connection);
        } else {
            throw new UnsupportedOperationException("Unsupported SQL type: " + statement.getClass().getSimpleName());
        }
    }


    private static String rollbackAlterTable(Alter alter, Connection connection) {
        String tableName = MysqlUtil.remove(alter.getTable().getName());
        List<String> rollbackOps = new ArrayList<>();

        String renameTableName = null;
        Boolean lastCol = null;
        for (AlterExpression expr : alter.getAlterExpressions()) {
            String operation = expr.getOperation().toString().toUpperCase();
            switch (operation) {
                case "ADD":
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 添加列
                        for (AlterExpression.ColumnDataType columnDataType : expr.getColDataTypeList()) {
                            String columnName = MysqlUtil.remove(columnDataType.getColumnName());
                            rollbackOps.add("DROP COLUMN `" + columnName + "`");
                        }
//                        String columnName = expr.getColumnName();
//                        rollbackOps.add("DROP COLUMN " + columnName);
                    } else if (expr.getIndex() != null) {
                        // 添加索引
                        String indexName = MysqlUtil.remove(expr.getIndex().getName());
                        rollbackOps.add("DROP INDEX `" + indexName + "`");
                    } else if (expr.getUkName() != null) {
                        String indexName = MysqlUtil.remove(expr.getUkName());
                        rollbackOps.add("DROP INDEX `" + indexName + "`");
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
                        String columnName = MysqlUtil.remove(expr.getColumnName());
                        String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                        if (columnDefinition != null) {
                            rollbackOps.add("ADD COLUMN `" + columnDefinition + "`");
                        } else {
                            throw new RuntimeException("Drop column: `" + tableName + "`.`" + columnName + "` not exist");
                        }
                    } else if (expr.getIndex() != null) {
                        lastCol = Boolean.FALSE;
                        // 删除索引
                        String indexName = MysqlUtil.remove(expr.getIndex().getName());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition == null) {
                            throw new RuntimeException("Drop index: `" + tableName + "`.`" + indexName + "` not exist");
                        } else {
                            rollbackOps.add("ADD `" + indexDefinition + "`");
                        }
                    } else {
                        lastCol = null;
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported DROP operation in ALTER TABLE: " + expr);
                    }
                    break;
                case "CHANGE":
                    lastCol = null;
                    if (expr.getColDataTypeList() != null && expr.getColumnOldName() != null) {
                        String columnOldName = MysqlUtil.remove(expr.getColumnOldName());
                        String oldColumnDefinition = MysqlUtil.getColumnDefinition(tableName, columnOldName, connection);
                        if (oldColumnDefinition == null) {
                            throw new RuntimeException("Change column: `" + tableName + "`.`" + columnOldName + "` not exist");
                        }
                        if (expr.getColDataTypeList().isEmpty() || expr.getColDataTypeList().size() != 1) {
                            throw new UnsupportedOperationException("Unsupported CHANGE operation in expr: " + expr);
                        }
                        String columnName = MysqlUtil.remove(expr.getColDataTypeList().get(0).getColumnName());
                        rollbackOps.add("CHANGE `" + columnName + "` " + oldColumnDefinition);
                    } else {
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported CHANGE operation in expr: " + expr);
                    }
                    break;
                case "MODIFY":  // 新增MODIFY分支
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 遍历所有被修改的列
                        for (AlterExpression.ColumnDataType columnDataType : expr.getColDataTypeList()) {
                            String columnName = MysqlUtil.remove(columnDataType.getColumnName());
                            String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                            if (columnDefinition == null) {
                                throw new RuntimeException("Modify column: `" + tableName + "`.`" + columnName + "` not exist");
                            }
                            rollbackOps.add("MODIFY COLUMN " + columnDefinition);
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported MODIFY operation in expr: " + expr);
                    }
                    break;
                case "RENAME":
                    lastCol = null;
                    // 处理列重命名
                    if (expr.getColumnName() != null && expr.getColumnOldName() != null) {
                        String oldColumn = MysqlUtil.remove(expr.getColumnOldName());
                        String newColumn = MysqlUtil.remove(expr.getColumnName());
                        // 生成反向操作：RENAME COLUMN new_name TO old_name
                        rollbackOps.add("RENAME COLUMN `" + newColumn + "` TO `" + oldColumn + "`");
                    } else {
                        throw new UnsupportedOperationException("Unsupported RENAME operation in expr: " + expr);
                    }
                    break;
                case "RENAME_TABLE":
                    lastCol = null;
                    renameTableName = MysqlUtil.remove(expr.getNewTableName());
                    // 生成反向操作：RENAME COLUMN new_name TO old_name
                    rollbackOps.add("RENAME TO `" + tableName + "`");
                    break;

                case "UNSPECIFIC":
                    // 处理DROP操作
                    if (lastCol == null) {
                        throw new UnsupportedOperationException("Unsupported UNSPECIFIC operation in ALTER TABLE: " + tableName);
                    }
                    if (lastCol) {
                        // 删除列
                        String columnName = MysqlUtil.remove(expr.getOptionalSpecifier());
                        String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                        if (columnDefinition == null) {
                            throw new RuntimeException("Drop column: `" + tableName + "`.`" + columnName + "` not exist");
                        }
                        rollbackOps.add("ADD COLUMN `" + columnDefinition + "`");
                    } else {
                        // 删除索引
                        String indexName = MysqlUtil.remove(expr.getOptionalSpecifier());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition == null) {
                            throw new RuntimeException("Drop index: `" + tableName + "`.`" + indexName + "` not exist");
                        } else {
                            rollbackOps.add("ADD `" + indexDefinition + "`");
                        }
                    }

                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported ALTER operation: " + operation);
            }
        }
        StringBuilder rollbackDDL = new StringBuilder();
        rollbackDDL.append("ALTER TABLE `").append(renameTableName == null ? tableName : renameTableName).append("` ");
        rollbackDDL.append(String.join(",\n", rollbackOps));
        rollbackDDL.append(";");
        return rollbackDDL.toString();
    }

    private static String verifyAlterTable(Alter alter, Connection connection) {
        Boolean lastCol = null;
        List<String> checks = new ArrayList<>();
        String tableName = MysqlUtil.remove(alter.getTable().getName());

        SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
        if (!tableMeta.judgeIsExists()) {
            return "Alter table: `" + tableName + "` not exist";
        }

        for (AlterExpression expr : alter.getAlterExpressions()) {
            String operation = expr.getOperation().toString().toUpperCase();

            switch (operation) {
                case "ADD":
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 添加列
                        for (AlterExpression.ColumnDataType columnDataType : expr.getColDataTypeList()) {
                            String columnName = MysqlUtil.remove(columnDataType.getColumnName());
                            String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                            if (columnDefinition != null) {
                                checks.add("Add column: `" + tableName + "`.`" + columnName + "`  already exist, " + columnDefinition);
                            } else {
                                checks.add(expr.toString());
                            }
                        }
                    } else if (expr.getIndex() != null) {
                        // 添加索引
                        String indexName = MysqlUtil.remove(expr.getIndex().getName());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition != null) {
                            checks.add("Add index: `" + tableName + "`.`" + indexName + "`  already exist, " + indexDefinition);
                        } else {
                            checks.add(expr.toString());
                        }
                    } else if (expr.getUkName() != null) {
                        String indexName = MysqlUtil.remove(expr.getUkName());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition != null) {
                            checks.add("Add Unique index: `" + tableName + "`.`" + indexName + "`  already exist, " + indexDefinition);
                        } else {
                            checks.add(expr.toString());
                        }
                    } else {
                        // 其他ADD操作，无法处理
                        throw new UnsupportedOperationException("Unsupported ADD operation in expr: " + expr);
                    }
                    break;
                case "DROP":
                    // 处理DROP操作
                    if (expr.getColumnName() != null) {
                        lastCol = Boolean.TRUE;
                        // 删除列
                        String columnName = MysqlUtil.remove(expr.getColumnName());
                        String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                        if (columnDefinition == null) {
                            checks.add("Drop column: `" + tableName + "`.`" + columnName + "` not exist");
                        } else {
                            checks.add("Drop column: " + columnDefinition);
                        }
                    } else if (expr.getIndex() != null) {
                        lastCol = Boolean.FALSE;
                        // 删除索引
                        String indexName = MysqlUtil.remove(expr.getIndex().getName());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition == null) {
                            checks.add("Drop index: `" + tableName + "`.`" + indexName + "` not exist");
                        } else {
                            checks.add("Drop index: " + indexDefinition);
                        }
                    } else {
                        lastCol = null;
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported DROP operation in expr: " + expr);
                    }
                    break;
                case "CHANGE":
                    lastCol = null;
                    if (expr.getColumnOldName() != null && expr.getColDataTypeList() != null) {
                        String columnOldName = MysqlUtil.remove(expr.getColumnOldName());
                        String oldColumnDefinition = MysqlUtil.getColumnDefinition(tableName, columnOldName, connection);
                        if (oldColumnDefinition == null) {
                            checks.add("Change column: `" + tableName + "`.`" + columnOldName + "` not exist");
                            break;
                        }
                        if (expr.getColDataTypeList().isEmpty() || expr.getColDataTypeList().size() != 1) {
                            throw new UnsupportedOperationException("Unsupported CHANGE operation in expr: " + expr);
                        }
                        String columnName = MysqlUtil.remove(expr.getColDataTypeList().get(0).getColumnName());
                        if (!Objects.equals(columnOldName, columnName)) {
                            String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                            if (columnDefinition != null) {
                                checks.add("Change to column: `" + tableName + "`.`" + columnName + "` already exist");
                                break;
                            }
                        }

                        checks.add(expr.toString());
                    } else {
                        // 其他DROP操作，无法处理
                        throw new UnsupportedOperationException("Unsupported CHANGE operation in expr: " + expr);
                    }
                    break;
                case "MODIFY":  // 新增MODIFY分支
                    lastCol = null;
                    if (expr.getColDataTypeList() != null) {
                        // 遍历所有被修改的列
                        for (AlterExpression.ColumnDataType columnDataType : expr.getColDataTypeList()) {
                            String columnName = MysqlUtil.remove(columnDataType.getColumnName());
                            String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                            if (columnDefinition == null) {
                                checks.add("Modify column: `" + tableName + "`.`" + columnName + "` not exist");
                            } else {
                                checks.add(expr.toString());
                            }
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported MODIFY operation in expr: " + expr);
                    }
                    break;
                case "RENAME":
                    lastCol = null;
                    // 处理列重命名
                    if (expr.getColumnName() != null && expr.getColumnOldName() != null) {
                        String oldColumn = MysqlUtil.remove(expr.getColumnOldName());
                        if (MysqlUtil.getColumnDefinition(tableName, oldColumn, connection) == null) {
                            checks.add("Rename column: `" + tableName + "`.`" + oldColumn + "` not exits");
                            break;
                        }
                        String newColumn = MysqlUtil.remove(expr.getColumnName());
                        if (MysqlUtil.getColumnDefinition(tableName, newColumn, connection) != null) {
                            checks.add("Rename to column: `" + tableName + "`.`" + newColumn + "` already exits");
                            break;
                        }
                        checks.add("Rename column: `" + tableName + "`.`" + oldColumn + "` to: `" + newColumn + "`");
                    } else {
                        throw new UnsupportedOperationException("Unsupported RENAME operation in expr: " + expr);
                    }
                    break;
                case "RENAME_TABLE":
                    lastCol = null;
                    String oldTableCreateSQL = MysqlUtil.getTableCreateSQL(connection, tableName);
                    if (oldTableCreateSQL == null) {
                        checks.add("Rename table: `" + tableName + "` not exits");
                        break;
                    }
                    String newTableName = MysqlUtil.remove(expr.getNewTableName());
                    String tableCreateSQL = MysqlUtil.getTableCreateSQL(connection, newTableName);
                    if (tableCreateSQL != null) {
                        checks.add("Rename to table: `" + newTableName + "` already exits");
                    } else {
                        checks.add("Rename table `" + tableName + "` to: `" + newTableName + "`");
                    }
                    break;

                case "UNSPECIFIC":
                    // 处理DROP操作
                    if (lastCol == null) {
                        throw new UnsupportedOperationException("Unsupported UNSPECIFIC operation in expr: " + alter.getTable().getName());
                    }
                    if (lastCol) {
                        // 删除列
                        String columnName = MysqlUtil.remove(expr.getOptionalSpecifier());
                        String columnDefinition = MysqlUtil.getColumnDefinition(tableName, columnName, connection);
                        if (columnDefinition == null) {
                            checks.add("Drop column: `" + tableName + "`.`" + columnName + "` not exist");
                        } else {
                            checks.add("Drop column: " + columnDefinition);
                        }
                    } else {
                        // 删除索引
                        String indexName = MysqlUtil.remove(expr.getOptionalSpecifier());
                        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
                        if (indexDefinition == null) {
                            checks.add("Drop index: `" + tableName + "`.`" + indexName + "` not exist");
                        } else {
                            checks.add("Drop index: " + indexDefinition);
                        }
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ALTER operation: " + operation);
            }
        }
        return String.join("\n", checks);
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
            SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
            if (!tableMeta.judgeIsExists()) {
                return MessageFormat.format("Drop table: `{0}` not exist", tableName);
            }
            return MessageFormat.format("Drop table: `{0}`, row:{1}, column:{2}, index:{3}", tableName, tableMeta.getRowCount(), tableMeta.getFields().size(), tableMeta.getIndices().size());
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            String tableName = MysqlUtil.remove(drop.getParameters().get(1));
            String indexName = MysqlUtil.remove(drop.getName().getName());
            String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
            if (indexDefinition == null) {
                return "Drop index: `" + tableName + "`.`" + indexName + "` not exist";
            } else {
                return "Drop index: `" + tableName + "`." + indexDefinition;
            }
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
            String tableCreateSQL = MysqlUtil.getTableCreateSQL(connection, tableName);
            if (tableCreateSQL == null) {
                throw new RuntimeException("`" + tableName + "` not exits");
            }
            return tableCreateSQL;
        } else if (type.equalsIgnoreCase("INDEX")) {
            //补全alter drop index的逻辑
            // 处理 DROP INDEX 的情况
            String indexName = MysqlUtil.remove(drop.getName().getName());
            String tableName = MysqlUtil.remove(drop.getParameters().get(1));
            // 获取索引的创建语句
            String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
            if (indexDefinition == null) {
                throw new RuntimeException("`" + tableName + "`.`" + indexName + "` not exits");
            }
            return "ALTER TABLE `" + tableName + "` ADD " + indexDefinition;
        }
        throw new UnsupportedOperationException("Unsupported DDL statement: " + drop.getClass().getSimpleName());
    }

    /**
     * 生成 CREATE TABLE 语句的回滚 DDL（即 DROP TABLE）
     *
     * @param createTable 要回滚的 CreateTable 语句
     * @return 回滚的 DDL 语句
     */
    private static String rollbackCreateTable(CreateTable createTable) {
        String tableName = createTable.getTable().getName();
        return "DROP TABLE `" + MysqlUtil.remove(tableName) + "`;";
    }

    private static String rollbackCreateIndex(CreateIndex createIndex) {
        // 生成回滚的DROP INDEX语句
        // 例如：原语句是 "CREATE INDEX index_name ON table_name(column_name)"
        // 回滚语句可能是 "DROP INDEX index_name ON table_name"
        String indexName = MysqlUtil.remove(createIndex.getIndex().getName());
        String tableName = MysqlUtil.remove(createIndex.getTable().getName());
        return "DROP INDEX `" + indexName + "` ON `" + tableName + "`;";
    }

    /**
     * 生成 CREATE TABLE 语句的回滚 DDL（即 DROP TABLE）
     *
     * @param createTable 要回滚的 CreateTable 语句
     * @param connection
     * @return 回滚的 DDL 语句
     */
    private static String verifyCreateTable(CreateTable createTable, Connection connection) {
        String tableName = MysqlUtil.remove(createTable.getTable().getName());
        SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
        if (tableMeta.judgeIsExists()) {
            return MessageFormat.format("Create table: `{0}` already exist, row:{1}, column:{2}, index:{3}", tableName, tableMeta.getRowCount(), tableMeta.getFields().size(), tableMeta.getIndices().size());
        }
        return "Create table: `" + tableName + "`";
    }

    private static String verifyCreateIndex(CreateIndex createIndex, Connection connection) {
        // 生成回滚的DROP INDEX语句
        // 例如：原语句是 "CREATE INDEX index_name ON table_name(column_name)"
        // 回滚语句可能是 "DROP INDEX index_name ON table_name"
        String tableName = MysqlUtil.remove(createIndex.getTable().getName());
        SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
        if (!tableMeta.judgeIsExists()) {
            return "Create index table: `" + tableName + "` not exist";
        }

        String indexName = MysqlUtil.remove(createIndex.getIndex().getName());
        String indexDefinition = MysqlUtil.getIndexDefinition(tableName, indexName, connection);
        if (indexDefinition != null) {
            return "Add index: `" + tableName + "`.`" + indexName + "`  already exist, " + indexDefinition;
        }
        return "Add index: `" + tableName + "`.`" + indexName + "`";
    }

    private static String verifyUpdate(Update update, Connection connection) {
        String countSql = generateCountSql(update);
        MysqlUtil.SqlRet sqlRet = MysqlUtil.executeSQL(countSql, connection,true);
        if (!sqlRet.isSuccess()) {
            return "Failed to prediction the affected row," + sqlRet.getRet();
        }
        String tableName = MysqlUtil.remove(update.getTable().getName());
        int allCount = MysqlUtil.getTableRowCountByShowTableStatus(connection, tableName);
        return "Expected affected row:" + sqlRet.getRet() + ",\n`" + tableName + "`‘s total count(estimate):" + allCount;
    }

    private static String verifyDelete(Delete delete, Connection connection) {
        String countSql = generateCountSql(delete);
        MysqlUtil.SqlRet sqlRet = MysqlUtil.executeSQL(countSql, connection,true);
        if (!sqlRet.isSuccess()) {
            return "Failed to prediction the affected row," + sqlRet.getRet();
        }
        String tableName = MysqlUtil.remove(delete.getTable().getName());
        int allCount = MysqlUtil.getTableRowCountByShowTableStatus(connection, tableName);
        return "Expected affected row:" + sqlRet.getRet() + ",\n`" + tableName + "`‘s total count(estimate):" + allCount;
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

    public static String generateCountSql(Delete delete) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ");

        // 1. 添加主表
        countSql.append(delete.getTable());

        // 3. 添加 WHERE 条件
        if (delete.getWhere() != null) {
            countSql.append(" WHERE ").append(delete.getWhere().toString());
        }

        // 处理 LIMIT（MySQL 特性）
        if (delete.getLimit() != null) {
            countSql.append(" LIMIT ").append(delete.getLimit().getRowCount());
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


    public static void main(String[] args) {
        Statement statement = MysqlUtil.parse("CREATE TABLE `house` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT,\n" +
                "  `name` varchar(512) DEFAULT NULL,\n" +
                "  `price` int DEFAULT NULL,\n" +
                "  `type` varchar(64) DEFAULT NULL,\n" +
                "  `location` varchar(512) DEFAULT NULL,\n" +
                "  `data` varchar(1024) DEFAULT NULL,\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `x1` (`name`,`price`) COMMENT '111',\n" +
                "  KEY `x12` (`name`)\n" +
                ") ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
        datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        datasourceConfig.setUsername("root");
        datasourceConfig.setPassword("wsd123==");
        datasourceConfig.setName("111");

        MysqlVerifyExecuteUtil.verifyCreateTable((CreateTable) statement, datasourceConfig.newConnection());
    }

}