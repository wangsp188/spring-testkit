package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiJoinReviewer implements Reviewer {

    // Recommended maximum number of joins
    private static final int RECOMMENDED_JOIN_LIMIT = 3;

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        The number of association tables is not recommended to exceed 5
//        The query involves {join_count} tables in JOIN operations, exceeding the recommended limit of 3.\n"
//                "Consider the following optimizations:\n"
//                "- Break the query into smaller, simpler queries and use application-side processing to combine results.\n"
//                "- Review the schema design; denormalization or indexed views might reduce the need for complex joins.\n"
//                "- Ensure all joined columns are properly indexed for involved tables.\n"
//                "- If applicable, consider using materialized views or caching strategies for frequently accessed subsets of data.
        Statement statement = ctx.getStatement();

        // Only process SELECT statements
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;

        // Analyze the SELECT Body, including subqueries
        analyzeSelectBody(selectStatement.getSelectBody(), suggestions);

        return suggestions;
    }

    private void analyzeSelectBody(SelectBody selectBody, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Check the number of JOINs in the query
            List<Join> joins = plainSelect.getJoins();
            if (joins != null && joins.size()+1 > RECOMMENDED_JOIN_LIMIT) {
                int joinCount = joins.size();

                // Generate optimization suggestions
                Suggest suggest = new Suggest();
                suggest.setRule(SuggestRule.multi_join_rule);
                suggest.setDetail("The query involves " + (joinCount+1) + " tables in JOIN operations, exceeding the recommended limit of " + RECOMMENDED_JOIN_LIMIT + ".\n"
                        + "Consider the following optimizations:\n"
                        + "- Break the query into smaller, simpler queries and use application-side processing to combine results.\n"
                        + "- Review the schema design; denormalization or indexed views might reduce the need for complex joins.\n"
                        + "- Ensure all joined columns are properly indexed for involved tables.\n"
                        + "- If applicable, consider using materialized views or caching strategies for frequently accessed subsets of data.");
                suggestions.add(suggest);
            }

            // Check for subqueries in WHERE, SELECT, and other clauses
            if (plainSelect.getWhere() != null) {
                analyzeExpression(plainSelect.getWhere(), suggestions);
            }
            if (plainSelect.getSelectItems() != null) {
                for (SelectItem item : plainSelect.getSelectItems()) {
                    if (item instanceof SelectExpressionItem) {
                        analyzeExpression(((SelectExpressionItem) item).getExpression(), suggestions);
                    }
                }
            }
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) selectBody;
            for (SelectBody body : setOperationList.getSelects()) {
                analyzeSelectBody(body, suggestions);
            }
        }
    }

    private void analyzeExpression(Expression expression, List<Suggest> suggestions) {
        if (expression instanceof SubSelect) {
            // Handle subquery
            SubSelect subSelect = (SubSelect) expression;
            analyzeSelectBody(subSelect.getSelectBody(), suggestions);
        } else if (expression instanceof BinaryExpression) {
            // Recursively analyze left and right expressions
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            analyzeExpression(binaryExpression.getLeftExpression(), suggestions);
            analyzeExpression(binaryExpression.getRightExpression(), suggestions);
        } else if (expression instanceof InExpression) {
            // Handle IN expressions that may contain subqueries
            InExpression inExpression = (InExpression) expression;
            if (inExpression.getRightItemsList() instanceof SubSelect) {
                analyzeSelectBody(((SubSelect) inExpression.getRightItemsList()).getSelectBody(), suggestions);
            }
        } else if (expression instanceof ExistsExpression) {
            // Handle EXISTS expressions that may contain subqueries
            ExistsExpression existsExpression = (ExistsExpression) expression;
            if (existsExpression.getRightExpression() instanceof SubSelect) {
                analyzeSelectBody(((SubSelect) existsExpression.getRightExpression()).getSelectBody(), suggestions);
            }
        }
        // Add more cases if needed to handle other types of expressions
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new MultiJoinReviewer(), "select * from house join h2 join h3 join h4");
        System.out.println(suggests);
    }
}