package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.SqlReviewer;
import com.testkit.sql_review.Suggest;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class CreateTableReviewer implements Reviewer {
    // 建议规则阈值配置
    private static final int MAX_INDEX_COUNT = 3;  // 最大允许的索引数量
    private static final String[] LARGE_FIELD_TYPES = {"TEXT", "BLOB", "LONGTEXT", "LONGBLOB"};

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        List<Suggest> suggestions = new ArrayList<>();
        Statement stmt = ctx.getStatement();

        if (!(stmt instanceof CreateTable)) {
            return suggestions;
        }

        CreateTable createTable = (CreateTable) stmt;
        Table table = createTable.getTable();
        String tableName = table.getName();

        if (ctx.findTable(tableName) != null) {
            suggestions.add(Suggest.build(SuggestRule.TABLE_EXISTS, tableName + " is exists"));
            return suggestions;
        }


        // 规则1: 必须包含主键
        checkPrimaryKey(createTable, tableName).ifPresent(suggestions::add);

        // 规则2: 索引数量检查
        checkIndexCount(createTable, tableName).ifPresent(suggestions::add);

        // 规则3: 大字段检查
        checkLargeFields(createTable, tableName).ifPresent(suggestions::add);

        // 规则4: 外键检查
        checkForeignKeys(createTable, tableName).ifPresent(suggestions::add);

        return suggestions;
    }


    private Optional<Suggest> checkPrimaryKey(CreateTable createTable, String tableName) {
        // 检查列定义中是否有主键
        boolean hasPrimaryKeyInColumn = createTable.getColumnDefinitions().stream()
                .anyMatch(column -> {
                    List<String> specs = column.getColumnSpecs();
                    if (specs == null || specs.isEmpty()) {
                        return false;
                    }
                    // 将列约束列表拼接为一个字符串（忽略大小写）
                    String specString = String.join(" ", specs).toUpperCase();
                    return specString.contains("PRIMARY KEY");
                });

        // 检查索引列表中是否有主键
        boolean hasPrimaryKeyInIndex = createTable.getIndexes() != null &&
                createTable.getIndexes().stream()
                        .anyMatch(index -> index.getType() != null && "PRIMARY KEY".equalsIgnoreCase(index.getType()));

        // 如果没有主键
        if (!hasPrimaryKeyInColumn && !hasPrimaryKeyInIndex) {
            String msg = String.format("Table %s lacks a primary key definition and must explicitly define it", tableName);
            return Optional.of(createSuggest(SuggestRule.REQUIRED_PRIMARY_KEY, msg));
        }
        return Optional.empty();
    }

    // 规则2: 检查索引数量
    private Optional<Suggest> checkIndexCount(CreateTable createTable, String tableName) {


        // 剔除外键和主键索引，只统计普通索引
        long normalIndexCount = Optional.ofNullable(createTable.getIndexes()).orElse(new ArrayList<>()).stream()
                .filter(index -> {
                    String indexType = index.getType(); // 获取索引类型
                    return indexType == null ||
                            (!indexType.equalsIgnoreCase("FOREIGN KEY") && !indexType.equalsIgnoreCase("PRIMARY KEY"));
                })
                .count();

        normalIndexCount += Optional.ofNullable(createTable.getColumnDefinitions()).orElse(new ArrayList<>())
                .stream()
                .filter(new Predicate<ColumnDefinition>() {
                    @Override
                    public boolean test(ColumnDefinition columnDefinition) {
                        List<String> specs = columnDefinition.getColumnSpecs();
                        if (specs == null || specs.isEmpty()) {
                            return false;
                        }
                        // 将列约束列表拼接为一个字符串（忽略大小写）
                        String specString = String.join(" ", specs).toUpperCase();
                        return specString.contains("UNIQUE");
                    }
                }).count();


        // 判断普通索引数量是否超标
        if (normalIndexCount > MAX_INDEX_COUNT) {
            String msg = String.format("Table %s contains %d normal indexes (a maximum of %d is recommended), which may affect write performance",
                    tableName, normalIndexCount, MAX_INDEX_COUNT);
            return Optional.of(createSuggest(SuggestRule.TOO_MANY_INDEXES, msg));
        }
        return Optional.empty();
    }

    // 规则3: 检查大字段
    private Optional<Suggest> checkLargeFields(CreateTable createTable, String tableName) {
        if (createTable.getColumnDefinitions() == null) {
            return Optional.empty();
        }
        List<String> largeFields = new ArrayList<>();
        for (ColumnDefinition column : createTable.getColumnDefinitions()) {
            ColDataType dataType = column.getColDataType();
            if (dataType == null) continue;

            String typeName = dataType.getDataType().toUpperCase();
            for (String largeType : LARGE_FIELD_TYPES) {
                if (typeName.startsWith(largeType)) {
                    largeFields.add(column.getColumnName());
                    break;
                }
            }
        }

        if (!largeFields.isEmpty()) {
            String msg = String.format("Table %s contains a large field [%s],\nwhich is recommended to be split or used with caution",
                    tableName, String.join(", ", largeFields));
            return Optional.of(createSuggest(SuggestRule.LARGE_FIELD_TYPE, msg));
        }
        return Optional.empty();
    }

    // 规则4: 检查外键
    private Optional<Suggest> checkForeignKeys(CreateTable createTable, String tableName) {
        List<Index> indexes = createTable.getIndexes();
        if (indexes == null) {
            return Optional.empty();
        }
        boolean hasForeignKey = indexes.stream()
                .anyMatch(index -> "FOREIGN KEY".equalsIgnoreCase(index.getType()));

        if (hasForeignKey) {
            String msg = String.format("Table %s contains foreign key constraints.\nIt is recommended to maintain data consistency at the application layer", tableName);
            return Optional.of(createSuggest(SuggestRule.AVOID_FOREIGN_KEY, msg));
        }
        return Optional.empty();
    }

    private Suggest createSuggest(SuggestRule rule, String detail) {
        Suggest suggest = new Suggest();
        suggest.setRule(rule);
        suggest.setDetail(detail);
        return suggest;
    }


    public static void main(String[] args) {
        List<Suggest> suggests = SqlReviewer.testReviewer(new CreateTableReviewer(), "CREATE TABLE user (\n" +
                "    id BIGINT PRIMARY KEY,                    -- 主键索引\n" +
                "    username VARCHAR(255) UNIQUE,             -- 唯一索引\n" +
                "    email VARCHAR(255),                       -- 无索引\n" +
                "    age INT,                                  -- 无索引\n" +
                "    city VARCHAR(255) NOT NULL,               -- 无索引\n" +
                "    group_id INT,                             -- 外键列\n" +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 无索引\n" +
                "    INDEX idx_email (email),                  -- 普通索引\n" +
                "    INDEX idx_city_age (city, age),           -- 复合索引\n" +
                "    INDEX idx_email2 (city),                  -- 普通索引\n" +
                "    INDEX idx_email3 (age)                  -- 普通索引\n" +
                ");");
        System.out.println(suggests);
    }


}