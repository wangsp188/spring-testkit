package com.testkit.sql_review;

import net.sf.jsqlparser.statement.Statement;

import java.util.Map;

public class ReviewCtx {

    private String sql;

    private Statement statement;

    private Map<String,SqlTable> tables;


    public SqlTable findTable(String tableName) {
        return tables.get(tableName);
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, SqlTable> getTables() {
        return tables;
    }

    public void setTables(Map<String, SqlTable> tables) {
        this.tables = tables;
    }

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }
}
