package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectFieldReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //        Using 'SELECT *' can lead to unnecessary data retrieval and potentially impact query performance. "
//"Consider specifying only the necessary columns explicitly to optimize your query.
        Statement statement = ctx.getStatement();

        // 仅处理 SELECT 语句
        if (!(statement instanceof Select)) {
            return Collections.emptyList();
        }

        List<Suggest> suggestions = new ArrayList<>();
        Select selectStatement = (Select) statement;

        // 处理 SELECT 查询的主体
        SelectBody selectBody = selectStatement.getSelectBody();
        analyzeSelectBody(selectBody, suggestions);

        return suggestions;
    }

    /**
     * 分析 Select 查询的主体，检测是否使用了 SELECT *
     *
     * @param selectBody   SELECT 查询主体
     * @param suggestions  建议集合，用于存储优化建议
     */
    private void analyzeSelectBody(SelectBody selectBody, List<Suggest> suggestions) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 检查是否使用了 SELECT *
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            for (SelectItem item : selectItems) {
                if (item instanceof AllColumns) { // 检测是否使用了 *
                    Suggest suggest = new Suggest();
                    suggest.setRule(SuggestRule.select_all_columns_rule);
                    suggest.setDetail("Using 'SELECT *' can lead to unnecessary data retrieval and potentially impact query performance. Consider specifying only the necessary columns explicitly to optimize your query.");
                    suggestions.add(suggest);
                }
            }

            // 检查子查询
            FromItem fromItem = plainSelect.getFromItem();
            analyzeFromItem(fromItem, suggestions);

            // 检查 JOIN 子查询
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    analyzeFromItem(join.getRightItem(), suggestions);
                }
            }
        } else if (selectBody instanceof SetOperationList) { // 处理 UNION 等查询
            SetOperationList setOperationList = (SetOperationList) selectBody;
            for (SelectBody body : setOperationList.getSelects()) {
                analyzeSelectBody(body, suggestions);
            }
        } else if (selectBody instanceof WithItem) { // 处理 WITH 查询
            WithItem withItem = (WithItem) selectBody;
            analyzeSelectBody(withItem.getSubSelect().getSelectBody(), suggestions);
        }
    }

    /**
     * 分析 FROM 项中的子查询
     *
     * @param fromItem     FROM 子查询或表
     * @param suggestions  建议集合，用于存储优化建议
     */
    private void analyzeFromItem(FromItem fromItem, List<Suggest> suggestions) {
        if (fromItem instanceof SubSelect) { // 处理子查询
            SubSelect subSelect = (SubSelect) fromItem;
            analyzeSelectBody(subSelect.getSelectBody(), suggestions);
        }
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new SelectFieldReviewer(), "select id from ( select * from house where price = '23' and null = null)");
        System.out.println(suggests);
    }
}