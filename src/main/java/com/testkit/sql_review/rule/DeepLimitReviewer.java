package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeepLimitReviewer implements Reviewer {

    // Define the threshold for a large OFFSET value
    private static final long OFFSET_THRESHOLD = 1000;

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        Statement statement = ctx.getStatement();

        // Only process SELECT statements
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;

        // Recursively check the SELECT query body
        checkSelectBody(selectStatement.getSelectBody(), suggestions);

        return suggestions;
    }

    private void checkSelectBody(SelectBody selectBody, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Check if LIMIT clause is present
            Limit limit = plainSelect.getLimit();
            if (limit != null && limit.getOffset() != null) {
                long offsetValue = getOffsetValue(limit);

                if (offsetValue > OFFSET_THRESHOLD) {
                    Suggest suggest = new Suggest();
                    suggest.setRule(SuggestRule.deep_limit_rule);
                    suggest.setDetail("The query uses LIMIT with OFFSET, which can lead to performance issues for large datasets.\n"
                            + "Consider replacing OFFSET with an index-based pagination approach for better performance.\n"
                            + "Example:\n"
                            + "- Instead of: SELECT c1, c2 FROM tbl ORDER BY id LIMIT 10 OFFSET " + offsetValue + "\n"
                            + "- Use: SELECT c1, c2 FROM tbl WHERE id > ? ORDER BY id LIMIT 10;");
                    suggestions.add(suggest);
                }
            }

            // Check for subqueries in FROM clause
            if (plainSelect.getFromItem() instanceof SubSelect) {
                checkSelectBody(((SubSelect) plainSelect.getFromItem()).getSelectBody(), suggestions);
            }

            // Check for subqueries in JOINs
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    if (join.getRightItem() instanceof SubSelect) {
                        checkSelectBody(((SubSelect) join.getRightItem()).getSelectBody(), suggestions);
                    }
                }
            }
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOpList = (SetOperationList) selectBody;
            for (SelectBody subSelectBody : setOpList.getSelects()) {
                checkSelectBody(subSelectBody, suggestions);
            }
        }
    }

    private long getOffsetValue(Limit limit) {
        if (limit.getOffset() instanceof LongValue) {

            return ((LongValue) limit.getOffset()).getValue();
        }
        return 0;
    }

    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new DeepLimitReviewer(), "select * from (select * from house limit 10000,1000)");
        System.out.println(suggests);
    }
}