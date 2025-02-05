package com.testkit.sql_review;


import com.testkit.sql_review.rule.SuggestRule;

public class Suggest {

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
    public String toString() {
        return "Suggest{" +
                "rule=" + rule +
                ", detail='" + detail + '\'' +
                '}';
    }
}