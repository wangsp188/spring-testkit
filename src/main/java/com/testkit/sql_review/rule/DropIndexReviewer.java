package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.drop.Drop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DropIndexReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        List<Suggest> suggestions = new ArrayList<>();
        Statement stmt = ctx.getStatement();
        
        if (!(stmt instanceof Drop) && !(stmt instanceof Alter)) {
            return suggestions;
        }
        String tableName = null; // 获取表名
        String indexName = null;
        if(stmt instanceof Drop) {
            Drop dropStmt = (Drop) stmt;
            if (!"INDEX".equalsIgnoreCase(dropStmt.getType())) {
                return suggestions; // 非删除索引操作直接返回
            }

            if (dropStmt.getParameters() != null && "on".equalsIgnoreCase(dropStmt.getParameters().get(0))) {
                tableName = dropStmt.getParameters().get(1);
            }

            indexName = dropStmt.getName().getFullyQualifiedName().replaceFirst(".*\\.", ""); // 提取索引名
        }else{
            tableName = ((Alter) stmt).getTable().getName();

            indexName = ((Alter) stmt).getAlterExpressions().stream()
                    .filter(expr -> {
                        return expr.getOperation() == AlterOperation.DROP && expr.getIndex()!=null;
                    }).map(new Function<AlterExpression, String>() {
                        @Override
                        public String apply(AlterExpression alterExpression) {
                            return alterExpression.getIndex().getName();
                        }
                    }).findFirst().orElse(null);
        }

        if(tableName == null || indexName == null) {
            return suggestions;
        }

        // 构建提示信息
        String msg = String.format(
                "To delete index %s.%s, you are advised to use the performance mode to verify the index usage:" +
                        "%n1. Enable monitoring: UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME LIKE 'events_statements%%';" + // 注意：%% 表示一个 % 字符
                        "%n2. Observe service traffic for a period of time (at least 24 hours recommended)" +
                        "%n3. Query usage statistics:" +
                        "%n   SELECT * FROM performance_schema.table_io_waits_summary_by_index_usage" +
                        "%n   WHERE OBJECT_SCHEMA = '%s' AND OBJECT_NAME = '%s' AND INDEX_NAME = '%s';",
                tableName, indexName,
                ctx.getDatabase(), // 假设上下文能获取数据库名
                tableName,
                indexName
        );

        suggestions.add(Suggest.build(SuggestRule.INDEX_DROP_VERIFY, msg));
        return suggestions;
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new DropIndexReviewer(), "alter table house drop index inxd");
        System.out.println(suggests);
    }
}