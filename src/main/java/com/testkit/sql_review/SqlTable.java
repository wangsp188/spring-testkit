package com.testkit.sql_review;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class SqlTable {  

    // 表名  
    private String tableName;  

    //
    private int rowCount;

    // 字段列表  
    private List<SqlColumn> sqlColumns;

    // 索引列表  
    private List<Index> indices;  

    // 构造函数  
    public SqlTable(String tableName) {  
        this.tableName = tableName;  
        this.sqlColumns = new ArrayList<>();
        this.indices = new ArrayList<>();  
    }


    public SqlColumn findColumn(String name){
        Optional<SqlColumn> first = sqlColumns.stream()
                .filter(new Predicate<SqlColumn>() {
                    @Override
                    public boolean test(SqlColumn sqlColumn) {
                        return Objects.equals(name.toLowerCase(), sqlColumn.getName().toLowerCase());
                    }
                })
                .findFirst();
        return first.orElse(null);
    }

    public String getTableName() {  
        return tableName;  
    }  

    public void setTableName(String tableName) {  
        this.tableName = tableName;  
    }  

    public List<SqlColumn> getFields() {
        return sqlColumns;
    }  

    public void setFields(List<SqlColumn> sqlColumns) {
        this.sqlColumns = sqlColumns;
    }  

    public List<Index> getIndices() {  
        return indices;  
    }  

    public void setIndices(List<Index> indices) {  
        this.indices = indices;  
    }  

    // 添加字段  
    public void addField(String name, String type, int length) {  
        this.sqlColumns.add(new SqlColumn(name, type, length));
    }  

    // 添加索引  
    public void addIndex(String name, List<String> columnNames) {  
        this.indices.add(new Index(name, columnNames));  
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    // 内部静态类：字段
    public static class SqlColumn {
        private String name; // 字段名  
        private String type; // 字段类型（如 VARCHAR、INT 等）  
        private int length;  // 字段长度  

        public SqlColumn(String name, String type, int length) {
            this.name = name;  
            this.type = type;  
            this.length = length;  
        }  

        public String getName() {  
            return name;  
        }  

        public void setName(String name) {  
            this.name = name;  
        }  

        public String getType() {  
            return type;  
        }  

        public void setType(String type) {  
            this.type = type;  
        }  

        public int getLength() {  
            return length;  
        }  

        public void setLength(int length) {  
            this.length = length;  
        }

        @Override
        public String toString() {
            return "Field{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", length=" + length +
                    '}';
        }
    }

    // 内部静态类：索引  
    public static class Index {  
        private String name;            // 索引名  
        private List<String> columnNames; // 索引列列表  

        public Index(String name, List<String> columnNames) {  
            this.name = name;  
            this.columnNames = columnNames;  
        }  

        public String getName() {  
            return name;  
        }  

        public void setName(String name) {  
            this.name = name;  
        }  

        public List<String> getColumnNames() {  
            return columnNames;  
        }  

        public void setColumnNames(List<String> columnNames) {  
            this.columnNames = columnNames;  
        }  

        @Override  
        public String toString() {  
            return "Index{" +  
                   "name='" + name + '\'' +  
                   ", columnNames=" + columnNames +  
                   '}';  
        }  
    }  

    // 重写 toString 方法  

    @Override
    public String toString() {
        return "SqlTable{" +
                "tableName='" + tableName + '\'' +
                ", rowCount=" + rowCount +
                ", fields=" + sqlColumns +
                ", indices=" + indices +
                '}';
    }
}