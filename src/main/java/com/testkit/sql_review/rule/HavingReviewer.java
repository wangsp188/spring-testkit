package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

public class HavingReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        "不建议使用 HAVING 子句",
//			Content:  `将查询的 HAVING 子句改写为 WHERE 中的查询条件，可以在查询处理期间使用索引。`,
//			Case:     "SELECT s.c_id,count(s.c_id) FROM s where c = test GROUP BY s.c_id HAVING s.c_id <> '1660' AND s.c_id <> '2' order by s.c_id",
        Statement statement = ctx.getStatement();
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            Set<String> aggregateAliases = extractAggregateAliases(plainSelect);

            Expression havingExpression = plainSelect.getHaving();
            if (havingExpression != null) {
                if (canMoveToWhere(havingExpression,aggregateAliases)) {
                    Suggest suggest = new Suggest();
                    suggest.setRule(SuggestRule.HAVING_TO_WHERE);
                    suggest.setDetail("The query uses a HAVING clause that could be replaced with a WHERE clause to improve performance by leveraging indexes. \nConsider moving the condition `"
                            + havingExpression + "` to the WHERE clause.");
                    suggestions.add(suggest);
                }
            }
        }

        return suggestions;
    }

    private Set<String> extractAggregateAliases(PlainSelect plainSelect) {
        Set<String> aggregateAliases = new HashSet<>();
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        for (SelectItem item : selectItems) {
            if (item instanceof SelectExpressionItem) {
                SelectExpressionItem selectExpressionItem = (SelectExpressionItem) item;
                Expression expression = selectExpressionItem.getExpression();
                Alias alias = selectExpressionItem.getAlias();

                if (expression instanceof Function && alias != null) {
                    aggregateAliases.add(alias.getName());
                }
            }
        }
        return aggregateAliases;
    }

    private boolean canMoveToWhere(Expression expression, Set<String> aggregateAliases) {
        if (expression instanceof Column) {
            Column column = (Column) expression;
            return !aggregateAliases.contains(column.getColumnName());
        }

        if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            return canMoveToWhere(binaryExpression.getLeftExpression(), aggregateAliases) &&
                    canMoveToWhere(binaryExpression.getRightExpression(), aggregateAliases);
        }

        return true;
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new HavingReviewer(), "select id,name,count(*) con1 from house where id not in (1,2) group by id,name having con12 >1");
        System.out.println(suggests);
    }
}