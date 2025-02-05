package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FieldOperationReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        Field operations are not recommended.
//          Example: a + 1 > 2 => a > 2 - 1
//
//  Consider simplifying your expressions by moving constants out of comparisons.
//
//  No improper field operations detected, query is optimized.

        Statement statement = ctx.getStatement();

        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        // 解析主查询和子查询
        analyzeSelectBody(selectBody, suggestions);
        return suggestions;
    }

    private void analyzeSelectBody(SelectBody selectBody, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 检查 FROM 子句的子查询
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem instanceof SubSelect) {
                analyzeSelectBody(((SubSelect) fromItem).getSelectBody(), suggestions);
            }

            // 检查 JOIN 条件和 JOIN 子句中的子查询
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    if (join.getRightItem() instanceof SubSelect) {
                        analyzeSelectBody(((SubSelect) join.getRightItem()).getSelectBody(), suggestions);
                    }
                    if (join.getOnExpression() != null) {
                        analyzeExpression(join.getOnExpression(), suggestions);
                    }
                }
            }

            // 检查 WHERE 条件中的字段操作
            Expression where = plainSelect.getWhere();
            if (where != null) {
                analyzeExpression(where, suggestions);
            }

            // 检查 SELECT 列表中的子查询
            analyzeSubSelects(plainSelect, suggestions);
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOpList = (SetOperationList) selectBody;
            for (SelectBody subSelectBody : setOpList.getSelects()) {
                analyzeSelectBody(subSelectBody, suggestions);
            }
        }
    }

    private void analyzeExpression(Expression expression, List<Suggest> suggestions) {
        if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;

            if (isFieldOperation(binaryExpression)) {
                Suggest suggest = new Suggest();
                suggest.setRule(SuggestRule.field_operation);
                suggest.setDetail("Field operation detected: [" + binaryExpression +
                        "]. Consider simplifying the expression by removing operations on fields. For example, 'a + 1 > 2' can be rewritten as 'a > 2 - 1'.");
                suggestions.add(suggest);
            }

            analyzeExpression(binaryExpression.getLeftExpression(), suggestions);
            analyzeExpression(binaryExpression.getRightExpression(), suggestions);
        }
    }

    private boolean isFieldOperation(BinaryExpression expression) {
        return (expression instanceof Addition ||
                expression instanceof Subtraction ||
                expression instanceof Multiplication ||
                expression instanceof Division);
    }

    private void analyzeSubSelects(PlainSelect plainSelect, List<Suggest> suggestions) {
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                if (selectItem instanceof SelectExpressionItem) {
                    SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                    Expression expression = selectExpressionItem.getExpression();
                    if (expression instanceof SubSelect) {
                        analyzeSelectBody(((SubSelect) expression).getSelectBody(), suggestions);
                    }
                }
            }
        }

        if (plainSelect.getWhere() != null) {
            plainSelect.getWhere().accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(SubSelect subSelect) {
                    analyzeSelectBody(subSelect.getSelectBody(), suggestions);
                }
            });
        }
    }

    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new FieldOperationReviewer(), "SELECT * FROM (SELECT * FROM house WHERE xx+1>2 LIMIT 10000,1000) t JOIN (SELECT id FROM other_table WHERE b-3<5) o ON t.id = o.id");
        System.out.println(suggests);
    }
}