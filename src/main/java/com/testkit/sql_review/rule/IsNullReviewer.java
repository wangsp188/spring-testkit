package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IsNullReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        Use IS NULL to determine whether it is a NULL value
//          A direct comparison of NULL to any value is NULL.
//           1) The return result of NULL<>NULL is NULL, not false.
//           2) The return result of NULL=NULL is NULL, not true.
//           3) The return result of NULL<>1 is NULL, not true.
//
//  Detected comparison with NULL using =, !=, or <>. " "Use 'IS NULL' or 'IS NOT NULL' for correct NULL checks.
        Statement statement = ctx.getStatement();

        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            Expression where = plainSelect.getWhere();
            if (where != null) {
                analyzeExpression(where, suggestions);
            }
        }
        return suggestions;
    }

    private void analyzeExpression(Expression expression, List<Suggest> suggestions) {
        if (expression instanceof EqualsTo || expression instanceof NotEqualsTo) {
            EqualsTo equalsTo = (EqualsTo) expression;
            Expression leftExpr = equalsTo.getLeftExpression();
            Expression rightExpr = equalsTo.getRightExpression();

            if (isNullValue(leftExpr) || isNullValue(rightExpr)) {
                Suggest suggest = new Suggest();
                suggest.setRule(SuggestRule.NULL_COMPARISON);
                suggest.setDetail("Detected comparison with NULL using " +
                        (expression instanceof EqualsTo ? "=" : "<>") +
                        ". \nUse 'IS NULL' or 'IS NOT NULL' for correct NULL checks. " +
                        "\nDirect comparison against NULL always returns NULL.");
                suggestions.add(suggest);
            }
        } else if (expression instanceof AndExpression) {
            AndExpression andExpr = (AndExpression) expression;
            analyzeExpression(andExpr.getLeftExpression(), suggestions);
            analyzeExpression(andExpr.getRightExpression(), suggestions);
        } else if (expression instanceof OrExpression) {
            OrExpression orExpr = (OrExpression) expression;
            analyzeExpression(orExpr.getLeftExpression(), suggestions);
            analyzeExpression(orExpr.getRightExpression(), suggestions);
        }
        // 可以在此添加对其他类型表达式的处理
    }

    private boolean isNullValue(Expression expression) {
        return expression instanceof NullValue;
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new IsNullReviewer(), "select * from house where price = '23' and null = null");
        System.out.println(suggests);
    }
}