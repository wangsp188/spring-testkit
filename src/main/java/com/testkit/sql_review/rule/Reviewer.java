package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.List;

public interface Reviewer {

    List<Suggest> suggest(ReviewCtx ctx);

}
