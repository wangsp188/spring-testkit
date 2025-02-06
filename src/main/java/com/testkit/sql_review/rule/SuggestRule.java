package com.testkit.sql_review.rule;

public enum SuggestRule {

    full_scan(Level.CRITICAL, "Full scan"),
    field_operation(Level.BLOCKER, "Field operation"),
    implicit_type_conversion(Level.BLOCKER, "Implicit type conversion"),
    null_comparison(Level.BLOCKER, "Null comparison"),
    limit_no_order(Level.MINOR, "Limit no order"),
    select_all_columns_rule(Level.MINOR, "Select *"),
    multi_join_rule(Level.CRITICAL, "Multi join"),
    having_to_where_rule(Level.CRITICAL, "Having to where"),
    deep_limit_rule(Level.CRITICAL, "Deep limit"),

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