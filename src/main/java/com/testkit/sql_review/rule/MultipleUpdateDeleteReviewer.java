package com.testkit.sql_review.rule;

import com.testkit.sql_review.ReviewCtx;
import com.testkit.sql_review.Suggest;

import java.util.Collections;
import java.util.List;

public class MultipleUpdateDeleteReviewer implements Reviewer{
    @Override
    public List<Suggest> suggest(ReviewCtx ctx) {
//        The use of multiple tables in UPDATE or DELETE operation is not recommended. " "Consider breaking down the operation into separate single-table statements or " "using transactions to manage the update/delete across multiple tables safely.
//        UPDATE / DELETE does not recommend using multiple tables
//   大数据量用临时表，中等数据量join，最次子查询
        return Collections.emptyList();
    }
}
