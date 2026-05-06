package com.lnzz.argus.scm.service;

import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;

import java.util.List;
import java.util.Map;

/**
 * SCM 平台服务抽象
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ScmPlatformService {

    /**
     * 平台标识
     */
    String getProvider();

    /**
     * 解析 Webhook 事件
     */
    PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload);

    /**
     * 校验 Webhook 签名
     */
    boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload);

    /**
     * 获取 PR/MR Diff
     */
    List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task);

    /**
     * 获取指定 ref 的文件内容
     */
    String getFileContent(ScmConfig config, ReviewTask task, String filePath, String ref);

    /**
     * 添加 PR/MR 评论
     */
    Long addPullRequestComment(ScmConfig config, ReviewTask task, String body);

    /**
     * 设置 PR/MR 标签
     */
    void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels);
}
