package com.testkit.sql_review;

import com.testkit.SettingsStorageHelper;
import com.testkit.sql_review.rule.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.sql.Connection;
import java.util.*;

public class SqlReviewer {

    private static Map<SqlType, List<Reviewer>> reviewRules = new HashMap<SqlType, List<Reviewer>>();

    static {
        reviewRules.put(SqlType.select, Arrays.asList(
                new DeepLimitReviewer()
                , new FieldOperationReviewer()
                , new FullScanReviewer()
                , new HavingReviewer()
                , new ImlicitTypeConversionReviewer()
                , new IsNullReviewer()
                , new LimitNoneOrderReviewer()
                , new MultiJoinReviewer()
                , new SelectFieldReviewer()
        ));
    }


    public static List<Suggest> testReviewer(Reviewer reviewer, String sql) {
        try {
            SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
            datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
            datasourceConfig.setUsername("root");
            datasourceConfig.setPassword("wsd123==");
            datasourceConfig.setName("111");
            // 1. 使用 JSQLParser 解析 SQL
            Statement statement = CCJSqlParserUtil.parse(sql);
            // 2. 提取 SQL 中列出表名
            Set<String> tableNames = extractTableNames(statement);

            // 3. 获取表的字段和索引元数据
            Map<String, SqlTable> tableMetaMap = new HashMap<>();

            Connection connection = datasourceConfig.newConnection();
            for (String tableName : tableNames) {
                SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
                tableMetaMap.put(tableName, tableMeta);
            }
            connection.close();
            ReviewCtx reviewCtx = new ReviewCtx();
            reviewCtx.setSql(sql);
            reviewCtx.setTables(tableMetaMap);
            reviewCtx.setStatement(statement);
            return reviewer.suggest(reviewCtx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    // 主方法：检查 SQL 并返回建议
    public static List<Suggest> reviewMysql(SettingsStorageHelper.DatasourceConfig datasourceConfig, String sql) {
        List<Suggest> suggestions = new ArrayList<>();
        try {
            // 1. 使用 JSQLParser 解析 SQL  
            Statement statement = CCJSqlParserUtil.parse(sql);
            // 2. 提取 SQL 中列出表名
            Set<String> tableNames = extractTableNames(statement);

            // 3. 获取表的字段和索引元数据  
            Map<String, SqlTable> tableMetaMap = new HashMap<>();

            if (datasourceConfig != null) {
                Connection connection = datasourceConfig.newConnection();
                for (String tableName : tableNames) {
                    SqlTable tableMeta = MysqlUtil.getTableMeta(connection, tableName);
                    tableMetaMap.put(tableName, tableMeta);
                }
                connection.close();
            }

            ReviewCtx reviewCtx = new ReviewCtx();
            reviewCtx.setSql(sql);
            reviewCtx.setTables(tableMetaMap);
            reviewCtx.setStatement(statement);

            List<Reviewer> rules = null;

            if (statement instanceof Select) {
                rules = reviewRules.get(SqlType.select);
            }

            if (rules != null) {
                for (Reviewer rule : rules) {
                    List<Suggest> suggest = rule.suggest(reviewCtx);
                    if (suggest == null) {
                        continue;
                    }
                    for (Suggest suggest1 : suggest) {
                        if (suggest1 == null || suggest1.getRule() == null) {
                            continue;
                        }
                        suggestions.add(suggest1);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return suggestions;
    }

    /**
     * 提取指定 SQL 语句中的所有表名
     *
     * @param statement JSQLParser 解析的 Statement 对象
     * @return 包含表名的集合
     */
    public static Set<String> extractTableNames(Statement statement) {
        Set<String> tableNames = new HashSet<>();

        if (statement instanceof Select) {
            // 处理 SELECT 查询
            Select select = (Select) statement;
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            tableNames.addAll(extractTableNames(plainSelect));

        } else if (statement instanceof Update) {
            // 处理 UPDATE 查询
            Update update = (Update) statement;

            // 获取表名
            tableNames.add(update.getTable().getName());

            // 检查 WHERE 子句中的表
            Expression where = update.getWhere();
            if (where != null) {
                extractSubqueries(where, tableNames);
            }

            // 检查 UPDATE SET 中的子查询
            List<Expression> setExpressions = update.getExpressions();
            if (setExpressions != null) {
                for (Expression expr : setExpressions) {
                    extractSubqueries(expr, tableNames);
                }
            }

        } else if (statement instanceof Delete) {
            // 处理 DELETE 查询
            Delete delete = (Delete) statement;

            // 获取表名
            tableNames.add(delete.getTable().getName());

            // 检查 WHERE 子句中的表
            Expression where = delete.getWhere();
            if (where != null) {
                extractSubqueries(where, tableNames);
            }

        } else if (statement instanceof CreateIndex) {
            // 处理索引创建
            CreateIndex createIndex = (CreateIndex) statement;
            tableNames.add(createIndex.getTable().getName());

        } else if (statement instanceof Drop) {
            // 处理索引删除
            Drop drop = (Drop) statement;
            if (drop.getType().equalsIgnoreCase("INDEX")) {
                tableNames.add(drop.getName().getName());  // 索引所在的表
            }

        } else if (statement instanceof Alter) {
            // 处理字段更改（新增、删除、修改）
            Alter alter = (Alter) statement;
            tableNames.add(alter.getTable().getName());
        } else if (statement instanceof Insert) {
            // 处理 INSERT 查询（可选）
            // 可扩展实现...

        }
        return tableNames;
    }

    private static Set<String> extractTableNames(PlainSelect plainSelect) {
        Set<String> tableNames = new HashSet<>();
        FromItem fromItem = plainSelect.getFromItem();

        // 分析主查询的表
        if (fromItem instanceof Table) {
            tableNames.add(((Table) fromItem).getName());
        } else if (fromItem instanceof SubSelect) {
            // 如果是子查询，递归查找子查询的表名
            tableNames.addAll(extractTableNames((PlainSelect) ((SubSelect) fromItem).getSelectBody()));
        }

        // 分析 JOIN 的表
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                FromItem joinItem = join.getRightItem();

                if (joinItem instanceof Table) {
                    tableNames.add(((Table) joinItem).getName());
                } else if (joinItem instanceof SubSelect) {
                    tableNames.addAll(extractTableNames((PlainSelect) ((SubSelect) joinItem).getSelectBody()));
                }
            }
        }

        return tableNames;
    }

    /**
     * 辅助方法：从表达式中提取子查询中的表名
     */
    private static void extractSubqueries(Expression expr, Set<String> tableNames) {
        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(SubSelect subSelect) {
                PlainSelect subBody = (PlainSelect) subSelect.getSelectBody();
                tableNames.addAll(extractTableNames(subBody));
            }
        });
    }


    public static enum SqlType {
        select
    }


    public static void main(String[] args) {
        SettingsStorageHelper.DatasourceConfig datasourceConfig = new SettingsStorageHelper.DatasourceConfig();
        datasourceConfig.setUrl("jdbc:mysql:///test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        datasourceConfig.setUsername("root");
        datasourceConfig.setPassword("wsd123==");
        datasourceConfig.setName("111");
        List<Suggest> suggests = SqlReviewer.reviewMysql(datasourceConfig, "select * from house");
        System.out.println(suggests);
    }
}