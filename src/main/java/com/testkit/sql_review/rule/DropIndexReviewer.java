package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class DropIndexReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
//        确认代码无使用
        return List.of();
    }
}

