package com.testkit.sql_review.rule;

import com.intellij.debugger.memory.filtering.FilteringTask;
import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;  
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.select.Join;  
import net.sf.jsqlparser.statement.select.FromItem;  

import java.util.ArrayList;  
import java.util.List;

public class MultipleUpdateDeleteReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        List<Suggest> suggestions = new ArrayList<>();
        Statement stmt = ctx.getStatement();

        if (stmt instanceof Update) {
            handleUpdate((Update) stmt, suggestions);
        } else if (stmt instanceof Delete) {
            handleDelete((Delete) stmt, suggestions);
        }

        return suggestions;
    }

    private void handleUpdate(Update update, List<Suggest> suggestions) {
        // 主表 + FROM/JOIN中的表总数
        int tableCount = 1;  // 主表默认计为1

        // 解析FROM子句和JOIN
        FromItem fromItem = update.getFromItem();
        tableCount += countTablesInFromItem(fromItem);

        List<Join> startJoins = update.getStartJoins();
        if (startJoins != null) {
            for (Join join : startJoins) {
                tableCount += countTablesInFromItem(join.getRightItem());
            }
        }

        // 解析额外JOIN
        List<Join> joins = update.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                tableCount += countTablesInFromItem(join.getRightItem());
            }
        }

        if (tableCount > 1) {
            suggestions.add(createSuggest());
        }
    }

    private void handleDelete(Delete delete, List<Suggest> suggestions) {
        FromItem fromItem = delete.getTable();
        int fromTableCount = countTablesInFromItem(fromItem);

        int joinCount = 0;
        List<Join> joins = delete.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                joinCount += countTablesInFromItem(join.getRightItem());
            }
        }

        int usingTableCount = delete.getUsingList() != null ? delete.getUsingList().size() : 0;

        if (fromTableCount + usingTableCount + joinCount > 1) {
            suggestions.add(createSuggest());
        }
    }

    private Suggest createSuggest() {
        Suggest suggest = new Suggest();
        suggest.setRule(SuggestRule.MULTIPLE_UPDATE_DELETE); // 假设规则已定义
        suggest.setDetail("The use of multiple tables in UPDATE or DELETE operation is not recommended. "
                + "\nConsider breaking down the operation into separate single-table statements or "
                + "\nusing transactions to manage the update/delete across multiple tables safely.");
        return suggest;
    }

    private int countTablesInFromItem(FromItem fromItem) {
        if (fromItem == null) {
            return 0;
        }

        if (fromItem instanceof Table) {
            return 1;
        } else if (fromItem instanceof Join) {
            Join join = (Join) fromItem;
            return countTablesInFromItem(join.getRightItem());
        }
        // 其他未处理的类型（如子查询）返回0
        return 0;
    }



    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new MultipleUpdateDeleteReviewer(), "UPDATE orders   \n" +
                "SET total = 100   \n" +
                "FROM customers   \n" +
                "WHERE orders.cust_id = customers.id ");
        System.out.println(suggests);
    }

}