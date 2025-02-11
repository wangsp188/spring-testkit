package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class CreateTableReviewer implements Reviewer {
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //检查是否包含主键
        //是否有create  modify 时间字段
        return List.of();
    }
}
