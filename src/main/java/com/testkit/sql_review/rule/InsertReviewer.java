package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class InsertReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //检查主键是否是固定值
        return List.of();
    }
}
