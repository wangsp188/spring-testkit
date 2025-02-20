package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.SqlTable;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AlterTableReviewer implements Reviewer {

    private static final long LARGE_TABLE_ROWS_THRESHOLD = 1_000_000L; // 大表阈值（100万行）

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        List<Suggest> suggestions = new ArrayList<>();
        Statement stmt = ctx.getStatement();

        if (!(stmt instanceof Alter)) {
            return suggestions;
        }

        Alter alter = (Alter) stmt;
        String tableName = alter.getTable().getName();
        SqlTable table = ctx.findTable(tableName); // 从上下文获取表元数据
        if (table == null) {
            return suggestions;
        }

        // 1. 检查字段类型修改风险
        checkColumnModifyRisk(alter, table).ifPresent(suggestions::add);

        // 2. 检查大表新增字段风险
        checkAddColumnForLargeTable(alter, table).ifPresent(suggestions::add);

        // 3. 检查删除字段前的依赖
        checkDropColumnDependency(alter, table).ifPresent(suggestions::add);


        //4新增外建提示禁止
        checkForeignKeyOperation(alter, table).ifPresent(suggestions::add);


        return suggestions;
    }

    // 规则1: 修改字段类型风险提醒
    private Optional<Suggest> checkColumnModifyRisk(Alter alter, SqlTable table) {
        return alter.getAlterExpressions().stream()
                .filter(expr -> expr.getOperation() == AlterOperation.MODIFY) // 识别修改操作
                .findFirst()
                .flatMap(modifiedColumn -> {
                    // 从参数列表中提取 ColumnDefinition
                    List<AlterExpression.ColumnDataType> colDataTypeList = modifiedColumn.getColDataTypeList();
                    if (colDataTypeList == null) {
                        return null;
                    }
                    return colDataTypeList.stream()
                            .map(new Function<AlterExpression.ColumnDataType, Suggest>() {
                                @Override
                                public Suggest apply(AlterExpression.ColumnDataType columnDataType) {
                                    String columnName = columnDataType.getColumnName();
                                    String colDataType = columnDataType.getColDataType().getDataType();
                                    //修改字段类型
                                    SqlTable.SqlColumn oldType = table.findColumn(columnName); // 原字段类型
                                    if (oldType == null) {
                                        return null;
                                    }
                                    if (!"VARCHAR".equals(oldType.getType()) && colDataType.equalsIgnoreCase(oldType.getType())) {
                                        return null;
                                    }
                                    String msg = String.format("Table %s modified field %s type (%s → %s) may trigger a full table rebuild,\nwhich is performed after impact assessment",
                                            table.getTableName(), columnName, oldType, colDataType);
                                    return Suggest.build(SuggestRule.COLUMN_MODIFY_RISK, msg);
                                }
                            })
                            .filter(Objects::nonNull)
                            .findFirst();
                });
    }

    // 规则2: 大表新增字段警告
    private Optional<Suggest> checkAddColumnForLargeTable(Alter alter, SqlTable table) {
        if (table == null || table.getRowCount() < LARGE_TABLE_ROWS_THRESHOLD) {
            return Optional.empty();
        }

        boolean hasAddColumn = alter.getAlterExpressions().stream()
                .anyMatch(expr -> {
                    return expr.getOperation() == AlterOperation.ADD && expr.getColDataTypeList()!=null;
                }); // 识别新增字段操作

        if (hasAddColumn) {
            String msg = String.format("A new field in the large table %s (current row %d) may lock the table.\nYou are advised to use pt-online-schema-change",
                    table.getTableName(), table.getRowCount());
            return Optional.of(Suggest.build(SuggestRule.LARGE_TABLE_ADD_COLUMN, msg));
        }
        return Optional.empty();
    }

    // 规则3: 删除字段前的索引/约束依赖检查
    private Optional<Suggest> checkDropColumnDependency(Alter alter, SqlTable table) {
        List<String> dropColumns = alter.getAlterExpressions().stream()
                .filter(expr -> expr.getOperation() == AlterOperation.DROP) // 识别删除字段操作
                .map(expr -> expr.getColumnName())
                .collect(Collectors.toList());

        if (dropColumns.isEmpty() || table == null) {
            return Optional.empty();
        }

        // 检查字段是否被索引引用
        List<String> affectedIndexes = table.getIndices().stream()
                .filter(index -> index.getColumnNames().stream().anyMatch(new Predicate<String>() {
                    @Override
                    public boolean test(String s) {
                        return dropColumns.contains(s.toLowerCase()) || dropColumns.contains(s.toUpperCase());
                    }
                }))
                .map(SqlTable.Index::getName)
                .collect(Collectors.toList());

        if (!affectedIndexes.isEmpty()) {
            String msg = String.format("Table %s Delete the associated index [%s] before deleting Field %s",
                    table.getTableName(), String.join(",", dropColumns), String.join(",", affectedIndexes));
            return Optional.of(Suggest.build(SuggestRule.DROP_COLUMN_DEPENDENCY, msg));
        }
        return Optional.empty();
    }

    private Optional<Suggest> checkForeignKeyOperation(Alter alter, SqlTable table) {
        // 检查是否添加了外键
        boolean hasAddForeignKey = alter.getAlterExpressions().stream()
                .anyMatch(expr -> expr.getOperation() == AlterOperation.ADD && expr.getIndex() != null && expr.getIndex() instanceof ForeignKeyIndex);

        if (hasAddForeignKey) {
            String msg = String.format("Adding a foreign key to table %s is not recommended. " +
                            "Consider creating an index first to avoid performance issues or data integrity risks.",
                    table.getTableName());
            return Optional.of(Suggest.build(SuggestRule.AVOID_FOREIGN_KEY, msg));
        }

        return Optional.empty();
    }



    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new AlterTableReviewer(), "ALTER TABLE orders ADD CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES customers(id);\n\n");
        System.out.println(suggests);
    }
}