package com.testkit.sql_review.rule;

public enum SuggestRule {

    FULL_SCAN(Level.CRITICAL, "Full scan"),
    FIELD_OPERATION(Level.BLOCKER, "Field operation"),
    IMPLICIT_TYPE_CONVERSION(Level.BLOCKER, "Implicit type conversion"),
    NULL_COMPARISON(Level.BLOCKER, "Null comparison"),
    LIMIT_NO_ORDER(Level.MINOR, "Limit no order"),
    SELECT_ALL_COLUMNS(Level.MINOR, "Select *"),
    MULTI_JOIN(Level.CRITICAL, "Multi join"),
    HAVING_TO_WHERE(Level.CRITICAL, "Having to where"),
    DEEP_LIMIT(Level.CRITICAL, "Deep limit"),
    MULTIPLE_UPDATE_DELETE(Level.CRITICAL, "Multiple update delete"),

    REQUIRED_PRIMARY_KEY(Level.BLOCKER, "Required primary key"),
    TOO_MANY_INDEXES(Level.CRITICAL, "Too many indexes"),
    LARGE_FIELD_TYPE(Level.MINOR, "Large field type"),
    AVOID_FOREIGN_KEY(Level.BLOCKER, "Avoid foreign key"),
    LARGE_TABLE_ALTER(Level.CRITICAL, "Large table alter"),
    COLUMN_MODIFY_RISK(Level.MINOR, "Column modify risk"),
    LARGE_TABLE_ADD_COLUMN(Level.CRITICAL, "Large table add column"),
    DROP_COLUMN_DEPENDENCY(Level.BLOCKER, "Drop column dependency"),
    TABLE_EXISTS(Level.BLOCKER, "Table exists"),

    ;
    private Level level;
    private String title;

    SuggestRule(Level level, String title) {
        this.level = level;
        this.title = title;
    }

    public Level getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public static enum Level {
        BLOCKER(0),
        CRITICAL(1),
        MINOR(2);
        private int order;

        Level(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

}