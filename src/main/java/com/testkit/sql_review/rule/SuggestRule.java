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