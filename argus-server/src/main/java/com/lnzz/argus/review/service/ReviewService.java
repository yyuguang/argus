package com.lnzz.argus.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评审服务
 * <p>M3-A: 评审编排器，负责创建任务、调度评审、聚合结果</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewExecutor reviewExecutor;

    /**
     * M3-A01: 触发评审（由 Webhook 调用）
     * <p>创建评审任务，异步执行评审</p>
     *
     * @param event MR 事件
     * @return 任务ID
     */
    public Long triggerReview(PullRequestEvent event, ScmConfig config) {
        // M1-04: 幂等校验（同一个 MR + commit 只评审一次）
        ReviewTask existing = reviewTaskMapper.selectOne(
                new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getScmProvider, event.getScmProvider())
                        .eq(ReviewTask::getProjectId, event.getProjectId())
                        .eq(ReviewTask::getMrIid, event.getMrIid())
                        .eq(ReviewTask::getLastCommitSha, event.getLastCommitSha())
        );
        if (existing != null) {
            String status = existing.getStatus();
            // 已成功或正在处理中的任务直接复用；失败/超时任务允许重试同一 commit。
            if ("DONE".equals(status) || "RUNNING".equals(status) || "PENDING".equals(status)) {
                log.info("评审任务已存在, taskId={}, status={}", existing.getId(), status);
                return existing.getId();
            }

            if ("FAILED".equals(status) || "TIMEOUT".equals(status)) {
                existing.setStatus("PENDING");
                existing.setErrorMessage(null);
                existing.setDuration(null);
                existing.setSummary(null);
                existing.setScmCommentId(null);
                existing.setNotified(false);
                reviewTaskMapper.updateById(existing);

                log.info("重试已有评审任务, taskId={}, previousStatus={}", existing.getId(), status);
                reviewExecutor.executeReview(existing.getId());
                return existing.getId();
            }
        }

        // 创建评审任务
        ReviewTask task = new ReviewTask();
        task.setScmProvider(event.getScmProvider());
        task.setScmConfigId(config.getId());
        task.setProjectId(event.getProjectId());
        task.setProjectName(event.getProjectName());
        task.setRepoOwner(event.getRepoOwner());
        task.setRepoName(event.getRepoName());
        task.setMrIid(event.getMrIid());
        task.setMrTitle(event.getMrTitle());
        task.setMrUrl(event.getMrUrl());
        task.setAuthorName(event.getAuthorName());
        task.setSourceBranch(event.getSourceBranch());
        task.setTargetBranch(event.getTargetBranch());
        task.setLastCommitSha(event.getLastCommitSha());
        task.setStatus("PENDING");
        task.setNotified(false);
        reviewTaskMapper.insert(task);

        log.info("创建评审任务, taskId={}, project={}, mrIid={}", task.getId(), event.getProjectName(), event.getMrIid());

        // 异步执行评审
        reviewExecutor.executeReview(task.getId());

        return task.getId();
    }
}
