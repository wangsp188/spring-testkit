package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class CreateIndexReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //当前索引几个
        return List.of();
    }
}

