package com.testkit.sql_review;


import com.testkit.sql_review.rule.SuggestRule;
import org.jetbrains.annotations.NotNull;

public class Suggest implements Comparable<Suggest> {

    private SuggestRule rule;

    private String detail;

    public SuggestRule getRule() {
        return rule;
    }

    public void setRule(SuggestRule rule) {
        this.rule = rule;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }


    @Override
    public int compareTo(@NotNull Suggest o) {
        if (this.rule == null) {
            return 1;
        }
        if (o.getRule() == null) {
            return -1;
        }
        return this.rule.getLevel().getOrder() - o.getRule().getLevel().getOrder();
    }

    @Override
    public String toString() {
        return "Suggest{" +
                "rule=" + rule +
                ", detail='" + detail + '\'' +
                '}';
    }
}