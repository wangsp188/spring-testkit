package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.SqlTable;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;

public class FullScanReviewer implements Reviewer {

    private static final int VERY_SMALL_TABLE_ROW_COUNT = 1; // 小表行数阈值

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
//          .Online query full table scan is not recommended.
//    Exceptions are:
//    1. very small table
//    2. very low frequency
//    3. the table/result set returned is very small (within 100 records / 100 KB).
//
//match:
//        select 1 from a
//        select 1 from a where b != / <>
//        select 1 from a where b not like
//        select 1 from a where b not in
//        select 1 from a where not exists
//        select 1 from a where b like %a / %a%
//
//        not match:
//        select * from a left join b on (a.id = b.id) and a.c=1
        Statement statement = ctx.getStatement();
        // 检查是否是 SELECT 语句
        if (!(statement instanceof Select)) {
            return null;
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        // 解析查询主体，包括主查询和子查询
        analyzeSelectBody(selectBody, ctx, suggestions);
        return suggestions;
    }

    /**
     * 分析主查询或子查询的 SelectBody，检测是否可能存在全表扫描
     */
    private void analyzeSelectBody(SelectBody selectBody, ReviewCtx ctx, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            FromItem fromItem = plainSelect.getFromItem();

            if (fromItem instanceof SubSelect) {
                // 如果是子查询，递归处理子查询
                SubSelect subSelect = (SubSelect) fromItem;
                PlainSelect subPlainSelect = (PlainSelect) subSelect.getSelectBody();
                // 递归调用当前方法来处理子查询
                analyzeSelectBody(subPlainSelect, ctx,suggestions);
                return;
            }


            // 解析 FROM 表
            String tableName = plainSelect.getFromItem().toString();
            SqlTable table = ctx.getTables().get(tableName);

            // 如果表不存在于上下文中，跳过处理
            if (table == null) {
                return;
            }

            // 如果是小表（行数阈值以下），跳过优化
            if (table.getRowCount() <= VERY_SMALL_TABLE_ROW_COUNT) {
                return;
            }

            // 检查 WHERE 条件中可能的全表扫描模式
            Expression where = plainSelect.getWhere();
            if (where == null || isPotentialFullScanCondition(where.toString().toLowerCase())) {
                Suggest suggest = new Suggest();
                suggest.setRule(SuggestRule.full_scan);
                suggest.setDetail("Potential full table scan detected on table '" + tableName +
                        "' in query: [" + selectBody + "]. \nConsider optimizing the WHERE clause or adding indexes.");
                suggestions.add(suggest);
            }

            // 检查子查询
            List<SubSelect> subSelects = extractSubSelects(plainSelect);
            for (SubSelect subSelect : subSelects) {
                analyzeSelectBody(subSelect.getSelectBody(), ctx, suggestions); // 递归分析子查询
            }
        }
    }

    /**
     * 检查 WHERE 条件是否包含可能导致全表扫描的模式
     */
    private boolean isPotentialFullScanCondition(String whereExpression) {
        return whereExpression.contains("!=") ||         // 不等于操作符
                whereExpression.contains("<>") ||         // 不等于操作符
                whereExpression.contains("not in") ||     // NOT IN
                whereExpression.contains("not exists") || // NOT EXISTS
                whereExpression.contains("like '%") ||    // LIKE '%...'
                whereExpression.endsWith("%'");           // LIKE '...%'
    }

    /**
     * 从查询语句中提取所有子查询
     */
    private List<SubSelect> extractSubSelects(PlainSelect plainSelect) {
        List<SubSelect> subSelects = new ArrayList<>();

        // 处理 WHERE 子句的表达式
        if (plainSelect.getWhere() != null) {
            SubSelectExpressionDeParser expressionDeParser = new SubSelectExpressionDeParser(subSelects);
            plainSelect.getWhere().accept(expressionDeParser);
        }

        // 处理 SELECT 列表
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                selectItem.accept(new SubSelectSelectItemVisitor(subSelects));
            }
        }

        //补全
        // 处理 FROM 子句
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof SubSelect) {
            subSelects.add((SubSelect) fromItem);
        } else {
            // 如果是 Join 的情况，处理每个 Join 的右表
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    FromItem joinItem = join.getRightItem();
                    if (joinItem instanceof SubSelect) {
                        subSelects.add((SubSelect) joinItem);
                    }
                }
            }
        }

        return subSelects;
    }

    /**
     * 自定义 ExpressionDeParser，用于收集 WHERE 子句中存在的子查询
     */
    private static class SubSelectExpressionDeParser extends ExpressionVisitorAdapter {
        private final List<SubSelect> subSelects;

        public SubSelectExpressionDeParser(List<SubSelect> subSelects) {
            this.subSelects = subSelects;
        }

        @Override
        public void visit(SubSelect subSelect) {
            subSelects.add(subSelect);
            super.visit(subSelect);
        }
    }

    /**
     * 自定义 SelectItemVisitor，用于收集 SELECT 列表中存在的子查询
     */
    private static class SubSelectSelectItemVisitor implements SelectItemVisitor {
        private final List<SubSelect> subSelects;

        public SubSelectSelectItemVisitor(List<SubSelect> subSelects) {
            this.subSelects = subSelects;
        }

        @Override
        public void visit(AllColumns allColumns) {
            // 不处理 SELECT * 的情况，因为它不会包含子查询
        }

        @Override
        public void visit(AllTableColumns allTableColumns) {
            // 不处理 SELECT table.* 的情况，因为它不会包含子查询
        }

        @Override
        public void visit(SelectExpressionItem selectExpressionItem) {
            // 检查表达式中是否包含子查询
            if (selectExpressionItem.getExpression() instanceof SubSelect) {
                subSelects.add((SubSelect) selectExpressionItem.getExpression());
            }
        }
    }

    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new FullScanReviewer(), "SELECT * FROM (select * from house where id not in (1,2))");
        System.out.println(suggests);
    }
}