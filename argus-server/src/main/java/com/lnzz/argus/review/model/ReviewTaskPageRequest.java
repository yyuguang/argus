package com.lnzz.argus.review.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评审任务分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewTaskPageRequest extends BasePageRequest {

    /** SCM 提供方。 */
    private String scmProvider;

    /** 评审任务状态。 */
    private String status;

    /** 关键字，匹配项目名、分支或合并请求标题。 */
    private String keyword;
}
