package com.lnzz.argus.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 评审任务实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_review_task")
public class ReviewTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SCM 平台标识 */
    private String scmProvider;

    /** SCM 配置ID */
    private Long scmConfigId;

    /** 仓库/项目ID */
    private Long projectId;

    /** 仓库/项目名称 */
    private String projectName;

    /** 仓库归属 owner/group */
    private String repoOwner;

    /** 仓库名称 */
    private String repoName;

    /** PR/MR 编号 */
    private Long mrIid;

    /** PR/MR 标题 */
    private String mrTitle;

    /** PR/MR 链接 */
    private String mrUrl;

    /** 提交者唯一ID */
    private String authorId;

    /** 提交者 */
    private String authorName;

    /** 源分支 */
    private String sourceBranch;

    /** 目标分支 */
    private String targetBranch;

    /** 最后提交SHA */
    private String lastCommitSha;

    /** 状态: PENDING/RUNNING/DONE/FAILED/TIMEOUT */
    private String status;

    /** 总评分(0-100) */
    private Integer totalScore;

    /** 评分等级: A/B/C/D/F */
    private String scoreLevel;

    /** 变更文件数 */
    private Integer fileCount;

    /** 新增行数 */
    private Integer addedLines;

    /** 删除行数 */
    private Integer removedLines;

    /** 致命问题数 */
    private Integer criticalCount;

    /** 严重问题数 */
    private Integer majorCount;

    /** 建议问题数 */
    private Integer minorCount;

    /** 消耗Token数 */
    private Integer tokensUsed;

    /** 评审耗时(ms) */
    private Long duration;

    /** 失败原因 */
    private String errorMessage;

    /** 评审总结 */
    private String summary;

    /** SCM 评论ID */
    private Long scmCommentId;

    /** 是否已通知 */
    private Boolean notified;
}
