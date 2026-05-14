package com.lnzz.argus.review.service;

import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;

/**
 * 评审服务接口
 * <p>MR/PR Webhook 事件的评审编排入口，负责任务创建、幂等、去重、自动重派发与异步调度</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ReviewService {

    /**
     * 触发代码评审（由 Webhook 调用）
     * <p>幂等策略：同一 MR + commit 只评审一次，已成功的直接复用</p>
     * <p>异常恢复：</p>
     * <ul>
     *   <li>RUNNING/PENDING 超过 60s 未更新 → 自动重新派发</li>
     *   <li>FAILED/TIMEOUT → 自动重置为 PENDING 并重试</li>
     * </ul>
     * <p>执行方式：创建任务后通过 {@code ReviewExecutor.executeReview()} 异步执行</p>
     *
     * @param event  MR/PR 事件（含 provider、projectId、mrIid、commit 等）
     * @param config SCM 平台配置（含 token、webhook secret 等）
     * @return 评审任务ID
     */
    Long triggerReview(PullRequestEvent event, ScmConfig config);
}
