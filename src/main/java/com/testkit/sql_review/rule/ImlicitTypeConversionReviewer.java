package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.SqlTable;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImlicitTypeConversionReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {

        //        Detect potential implicit type conversions on indexed columns in query conditions
//          due to comparison or arithmetic operations with different types, which may degrade index efficiency.
//        The following indexed columns may be involved in implicit type conversions due to comparison or arithmetic operations:\n{issue_list}\nReview these to ensure optimal index usage.
        Statement statement = ctx.getStatement();

        // 仅处理 SELECT 语句
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        // 解析主查询和子查询
        analyzeSelectBody(selectBody, ctx, suggestions);
        return suggestions;
    }

    /**
     * 分析 SelectBody 主体，查找可能的隐式类型转换
     */
    private void analyzeSelectBody(SelectBody selectBody, ReviewCtx ctx, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 检查 WHERE 条件中的字段操作
            Expression where = plainSelect.getWhere();
            if (where != null) {
                analyzeExpression(where, ctx, suggestions);
            }

            // 检查子查询
            analyzeSubSelects(plainSelect, ctx, suggestions);
        }
    }

    /**
     * 深入分析表达式，检测隐式类型转换
     */
    private void analyzeExpression(Expression expression, ReviewCtx ctx, List<Suggest> suggestions) {
        if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;

            // 递归检查子表达式（左、右操作数）
            analyzeExpression(binaryExpression.getLeftExpression(), ctx, suggestions);
            analyzeExpression(binaryExpression.getRightExpression(), ctx, suggestions);

            // 如果是比较操作，检查是否可能有隐式类型转换
            if (isComparisonOperator(binaryExpression)) {
                detectImplicitConversion(binaryExpression, ctx, suggestions);
            }
        }
    }

    private void detectImplicitConversion(BinaryExpression binaryExpression, ReviewCtx ctx, List<Suggest> suggestions) {
        Expression left = binaryExpression.getLeftExpression();
        Expression right = binaryExpression.getRightExpression();

        // 左侧是否是列
        SqlTable.SqlColumn column = getColumnIfExists(left, ctx);
        if (column != null) {
            // 检查右侧表达式，并判断是否发生了隐式类型转换
            if (!isTypeCompatible(column, right)) {
                Suggest suggest = new Suggest();
                suggest.setRule(SuggestRule.implicit_type_conversion);
                suggest.setDetail("Implicit type conversion detected for indexed column '" + column.getName() +
                        "' in expression: [" + binaryExpression + "]. Review to ensure optimal index usage.");
                suggestions.add(suggest);
            }
        }
    }

    /**
     * 获取字段信息，如果表达式为列（包括别名前缀等），则返回对应的 `SqlColumn`；否则返回 null。
     */
    private SqlTable.SqlColumn getColumnIfExists(Expression expression, ReviewCtx ctx) {
        if (expression == null || ctx == null) {
            return null;
        }

        String expressionString = expression.toString();
        String tableName = null;
        String columnName = null;

        // 检查表达式是否具有表名或别名前缀，例如 `t1.id`
        if (expressionString.contains(".")) {
            String[] parts = expressionString.split("\\.");
            if (parts.length == 2) {
                tableName = parts[0].trim();   // 表名或别名
                columnName = parts[1].trim(); // 列名
            }
        } else {
            columnName = expressionString.trim(); // 没有表名直接是字段名
        }

        if (tableName != null) {
            // 根据表名查找对应的 `SqlTable`，并查找列是否存在
            SqlTable table = ctx.findTable(tableName);
            if (table != null) {
                return table.findColumn(columnName);
            }
        } else {
            // 如果没有表名，遍历所有表查找列（可能是单表查询的情况）
            for (SqlTable table : ctx.getTables().values()) {
                SqlTable.SqlColumn column = table.findColumn(columnName);
                if (column != null) {
                    return column;
                }
            }
        }

        return null; // 列不存在
    }

    /**
     * 检查字段类型与表达式的类型是否兼容，判断是否可能发生隐式类型转换。
     */
    private boolean isTypeCompatible(SqlTable.SqlColumn column, Expression rightExpression) {
        // 获取字段的数据类型
        String columnType = column.getType().toLowerCase();

        // 推测表达式的类型
        String rightType = guessExpressionType(rightExpression);

        // 示例规则：简单匹配字段类型和表达式类型
        if ((columnType.contains("int") || columnType.contains("decimal")) && rightType.equals("string")) {
            return false; // 数字字段与字符串比较不兼容
        }
        if (columnType.contains("date") && rightType.equals("string")) {
            return false; // 日期字段与字符串比较可能导致隐式转换
        }

        return true; // 其他情况认为兼容
    }

    /**
     * 猜测表达式的类型，用于判断是否发生了隐式类型转换。
     */
    private String guessExpressionType(Expression expression) {
        if (expression == null) {
            return "unknown";
        }

        // 简单模拟几种类型推导
        String value = expression.toString().toLowerCase();
        if (value.startsWith("'") && value.endsWith("'")) {
            return "string"; // 字符串类型
        }
        if (value.matches("^\\d+(\\.\\d+)?$")) {
            return "number"; // 数字类型
        }
        if (expression instanceof Function) {
            return "function"; // 函数类型
        }

        return "unknown";
    }


    /**
     * 是否为比较运算符
     */
    private boolean isComparisonOperator(BinaryExpression expression) {
        return expression instanceof EqualsTo ||
                expression instanceof GreaterThan ||
                expression instanceof MinorThan;
    }

    /**
     * 检查并分析子查询
     */
    private void analyzeSubSelects(PlainSelect plainSelect, ReviewCtx ctx, List<Suggest> suggestions) {
        // 检查 SELECT 列表中的子查询
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                if (selectItem instanceof SelectExpressionItem) {
                    SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                    Expression expression = selectExpressionItem.getExpression();
                    if (expression instanceof SubSelect) {
                        SubSelect subSelect = (SubSelect) expression;
                        analyzeSelectBody(subSelect.getSelectBody(), ctx, suggestions);
                    }
                }
            }
        }

        // 检查 WHERE 子句中的子查询（递归处理）
        if (plainSelect.getWhere() != null) {
            plainSelect.getWhere().accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(SubSelect subSelect) {
                    analyzeSelectBody(subSelect.getSelectBody(), ctx, suggestions);
                }
            });
        }
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new ImlicitTypeConversionReviewer(), "select * from house where price = '23'");
        System.out.println(suggests);
    }
}