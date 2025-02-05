package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LimitNoneOrderReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        Summary:  "未使用 ORDER BY 的 LIMIT 查询",
//			Content:  `没有 ORDER BY 的 LIMIT 会导致非确定性的结果，这取决于查询执行计划。`,
//			Case:     "select col1,col2 from tbl where name=xx limit 10",
        Statement statement = ctx.getStatement();

        // 仅处理 SELECT 类型的语句
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;
        SelectBody selectBody = selectStatement.getSelectBody();

        // 递归解析 SELECT Body
        analyzeSelectBody(selectBody, suggestions);

        return suggestions;
    }

    /**
     * 分析 SELECT Body，支持 PlainSelect 和子查询
     */
    private void analyzeSelectBody(SelectBody selectBody, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 检查当前查询是否包含 LIMIT
            Limit limit = plainSelect.getLimit();
            if (limit != null) {
                List<OrderByElement> orderByElements = plainSelect.getOrderByElements();

                if (orderByElements == null || orderByElements.isEmpty()) {
                    // 如果没有 ORDER BY，则记录建议
                    Suggest suggest = new Suggest();
                    suggest.setRule(SuggestRule.limit_no_order);
                    suggest.setDetail("The query uses LIMIT without ORDER BY. This can result in non-deterministic results depending on the query execution plan. Consider adding an ORDER BY clause.");
                    suggestions.add(suggest);
                }
            }

            // 检查子查询，例如 WHERE 子句中的子查询
            Expression where = plainSelect.getWhere();
            if (where instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) where;
                analyzeSelectBody(subSelect.getSelectBody(), suggestions);
            }

            // 检查 FROM 子句中的子查询
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) fromItem;
                analyzeSelectBody(subSelect.getSelectBody(), suggestions);
            }

            // 检查 JOIN 子句中的子查询
            List<Join> joins = plainSelect.getJoins();
            if (joins != null) {
                for (Join join : joins) {
                    FromItem joinItem = join.getRightItem();
                    if (joinItem instanceof SubSelect) {
                        SubSelect subSelect = (SubSelect) joinItem;
                        analyzeSelectBody(subSelect.getSelectBody(), suggestions);
                    }
                }
            }
        } else if (selectBody instanceof SetOperationList) {
            // 处理 UNION、INTERSECT、EXCEPT 等操作
            SetOperationList setOperationList = (SetOperationList) selectBody;
            List<SelectBody> selectBodies = setOperationList.getSelects();
            for (SelectBody body : selectBodies) {
                analyzeSelectBody(body, suggestions);
            }
        } else if (selectBody instanceof WithItem) {
            // 处理 WITH 子句
            WithItem withItem = (WithItem) selectBody;
            analyzeSelectBody(withItem.getSubSelect().getSelectBody(), suggestions);
        }
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new LimitNoneOrderReviewer(), "select * from (select * from house where price = '23' order by null limit 100)");
        System.out.println(suggests);
    }
}