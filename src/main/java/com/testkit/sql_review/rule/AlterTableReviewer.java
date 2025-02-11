package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public class AlterTableReviewer implements Reviewer{
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
        //新增字段考虑，表大小
        //删除字段注意代码使用
        return List.of();
    }
}
