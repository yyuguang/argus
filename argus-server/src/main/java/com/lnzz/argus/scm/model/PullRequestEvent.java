package com.lnzz.argus.scm.model;

import lombok.Data;

import java.util.Set;

/**
 * 统一的 PR/MR 事件模型
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class PullRequestEvent {

    private static final Set<String> REVIEWABLE_EVENTS = Set.of("open", "opened", "update", "synchronize", "reopen", "reopened");

    /** SCM 平台: gitlab/github/gitee */
    private String scmProvider;

    /** 事件类型 */
    private String eventType;

    /** 仓库/项目ID */
    private Long projectId;

    /** 仓库/项目名称 */
    private String projectName;

    /** 仓库 Web URL */
    private String projectUrl;

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

    /** PR/MR 状态 */
    private String mrState;

    /** 提交者ID */
    private String authorId;

    /** 提交者姓名 */
    private String authorName;

    /** 源分支 */
    private String sourceBranch;

    /** 目标分支 */
    private String targetBranch;

    /** 最后一次提交 SHA */
    private String lastCommitSha;

    public boolean isReviewable() {
        return REVIEWABLE_EVENTS.contains(normalize(eventType))
                && "opened".equals(normalizeState())
                && "test".equals(targetBranch);
    }

    private String normalizeState() {
        if ("open".equalsIgnoreCase(mrState)) {
            return "opened";
        }
        return normalize(mrState);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
