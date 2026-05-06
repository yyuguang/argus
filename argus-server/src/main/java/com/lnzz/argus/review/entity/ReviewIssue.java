package com.lnzz.argus.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 评审问题实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_review_issue")
public class ReviewIssue extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评审任务ID */
    private Long taskId;

    /** 文件路径 */
    private String filePath;

    /** 问题起始行 */
    private Integer startLine;

    /** 问题结束行 */
    private Integer endLine;

    /** 严重度: CRITICAL/MAJOR/MINOR/SUGGESTION */
    private String severity;

    /** 分类: COMPLIANCE/CORRECTNESS/DATA_SAFETY/PERFORMANCE/MAINTAINABILITY */
    private String category;

    /** 问题描述 */
    private String description;

    /** 修复建议 */
    private String suggestion;

    /** 问题代码片段 */
    private String codeSnippet;

    /** 违反的规则 */
    private String rule;
}
