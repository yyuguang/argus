package com.lnzz.argus.gitlab.model;

import lombok.Data;

/**
 * GitLab MR 事件（Webhook 接收后解析）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class MergeRequestEvent {

    /** 事件类型: open / update / merge / close */
    private String eventType;

    /** GitLab 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 项目 Web URL */
    private String projectUrl;

    /** MR 编号 */
    private Long mrIid;

    /** MR 标题 */
    private String mrTitle;

    /** MR 链接 */
    private String mrUrl;

    /** MR 状态 */
    private String mrState;

    /** 提交者ID */
    private Long authorId;

    /** 提交者姓名 */
    private String authorName;

    /** 源分支 */
    private String sourceBranch;

    /** 目标分支 */
    private String targetBranch;

    /** 最后一次提交 SHA */
    private String lastCommitSha;

    /**
     * 是否为需要评审的事件（dev → test 的 open/update）
     */
    public boolean isReviewable() {
        return ("open".equals(eventType) || "update".equals(eventType))
                && "opened".equals(mrState)
                && "test".equals(targetBranch);
    }
}
