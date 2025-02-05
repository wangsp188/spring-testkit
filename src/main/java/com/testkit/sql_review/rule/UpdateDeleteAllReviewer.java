package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.Collections;
import java.util.List;

public class UpdateDeleteAllReviewer implements Reviewer{
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
//        UPDATE or DELETE statements should not be executed without a WHERE clause or with a always-true WHERE condition.
        return Collections.emptyList();
    }
}
