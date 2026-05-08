package com.lnzz.argus.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.review.service.ReviewExecutor;
import com.lnzz.argus.review.service.ReviewService;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 评审服务实现
 * <p>M3-A: 评审编排器，负责创建任务、调度评审、聚合结果</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final long STALE_TASK_SECONDS = 60;

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewExecutor reviewExecutor;

    @Override
    public Long triggerReview(PullRequestEvent event, ScmConfig config) {
        ReviewTask existing = reviewTaskMapper.selectOne(
                new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getScmProvider, event.getScmProvider())
                        .eq(ReviewTask::getProjectId, event.getProjectId())
                        .eq(ReviewTask::getMrIid, event.getMrIid())
                        .eq(ReviewTask::getLastCommitSha, event.getLastCommitSha())
        );
        if (existing != null) {
            String status = existing.getStatus();
            if ("DONE".equals(status)) {
                log.info("评审任务已存在, taskId={}, status={}", existing.getId(), status);
                return existing.getId();
            }

            if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                if (isTaskStale(existing)) {
                    existing.setStatus("PENDING");
                    existing.setErrorMessage("检测到任务长时间未推进，系统已自动重新派发");
                    existing.setDuration(null);
                    existing.setSummary(null);
                    existing.setScmCommentId(null);
                    existing.setNotified(false);
                    reviewTaskMapper.updateById(existing);

                    log.warn("检测到卡住任务，重新派发: taskId={}, previousStatus={}, updateTime={}",
                            existing.getId(), status, existing.getUpdateTime());
                    dispatchReview(existing.getId());
                } else {
                    log.info("评审任务已存在且仍在处理中, taskId={}, status={}", existing.getId(), status);
                }
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
                dispatchReview(existing.getId());
                return existing.getId();
            }
        }

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

        dispatchReview(task.getId());

        return task.getId();
    }

    private void dispatchReview(Long taskId) {
        log.info("派发评审执行, taskId={}", taskId);
        reviewExecutor.executeReview(taskId);
    }

    private boolean isTaskStale(ReviewTask task) {
        LocalDateTime baseTime = task.getUpdateTime() != null ? task.getUpdateTime() : task.getCreateTime();
        if (baseTime == null) {
            return true;
        }
        return Duration.between(baseTime, LocalDateTime.now()).getSeconds() >= STALE_TASK_SECONDS;
    }
}
