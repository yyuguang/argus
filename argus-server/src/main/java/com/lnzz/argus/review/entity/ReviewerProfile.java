package com.lnzz.argus.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提交者代码质量画像实体。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_reviewer_profile")
public class ReviewerProfile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提交者 SCM 账号 */
    private String authorName;

    /** 提交者唯一 ID */
    private String authorId;

    /** SCM 平台 */
    private String scmProvider;

    /** 累计评审次数 */
    private Integer totalReviews;

    /** 历史平均分 */
    private BigDecimal avgScore;

    /** 五维度平均分 JSON */
    private String dimensionStats;

    /** 高频问题标签 JSON */
    private String topIssueTags;

    /** 高频违规规则 JSON */
    private String topIssueRules;

    /** 分数趋势 JSON */
    private String scoreTrend;

    /** 最近评审摘要 JSON */
    private String recentReviews;

    /** 首次评审时间 */
    private LocalDateTime firstReviewAt;

    /** 最近评审时间 */
    private LocalDateTime lastReviewAt;
}
