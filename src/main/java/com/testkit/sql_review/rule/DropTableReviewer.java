package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class DropTableReviewer implements Reviewer {

    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //确认表无使用
        //查询更新时间
        return List.of();
    }
}
