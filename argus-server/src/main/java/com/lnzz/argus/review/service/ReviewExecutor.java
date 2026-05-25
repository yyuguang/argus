package com.lnzz.argus.review.service;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexScanService;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.CodingStandardsLoader;
import com.lnzz.argus.review.ai.PromptBuilder;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.service.ReviewerProfileService;
import com.lnzz.argus.review.mapper.ReviewIssueMapper;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import com.lnzz.argus.review.parser.ContextBuilder;
import com.lnzz.argus.review.parser.DiffParser;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 评审执行器
 * <p>M3-A02: 异步执行完整评审流程</p>
 * <p>流程: 获取Diff → 解析代码 → 构建上下文 → AI评审 → 评分 → 回写SCM → 通知</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewExecutor {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewerProfileService reviewerProfileService;
    private final ScmConfigService scmConfigService;
    private final ScmPlatformServiceFactory scmPlatformServiceFactory;
    private final ContextBuilder contextBuilder;
    private final DiffParser diffParser;
    private final PromptBuilder promptBuilder;
    private final AiReviewEngine aiReviewEngine;
    private final ScoreCalculator scoreCalculator;
    private final CodingStandardsLoader codingStandardsLoader;
    private final ReviewReportFormatter reportFormatter;
    private final NotificationService notificationService;
    private final VectorKnowledgeService vectorKnowledgeService;
    private final CodeIndexService codeIndexService;
    private final CodeIndexScanService codeIndexScanService;
    @Qualifier("reviewFileExecutor")
    private final Executor reviewFileExecutor;
    @Qualifier("reviewExecutorPool")
    private final Executor reviewExecutorPool;

    /**
     * 异步执行评审
     *
     * @param taskId 评审任务ID
     */
    @Async
    public void executeReview(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("评审任务不存在: taskId={}", taskId);
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("开始执行评审: taskId={}, project={}, mrIid={}", taskId, task.getProjectName(), task.getMrIid());

        try {
            ScmConfig scmConfig = scmConfigService.requireById(task.getScmConfigId());
            ScmPlatformService scmService = scmPlatformServiceFactory.getRequired(task.getScmProvider());

            // Step 0: 更新状态为 RUNNING
            task.setStatus("RUNNING");
            task.setErrorMessage(null);
            reviewTaskMapper.updateById(task);

            // Step 1: 获取 PR/MR Diff
            List<DiffFile> diffs = scmService.getPullRequestDiffs(scmConfig, task);

            // 解析仓库级评审配置（前置，供后续过滤/评分/报告使用）
            ReviewConfig reviewConfig = resolveReviewConfig(scmConfig);

            // Step 2: 过滤可评审文件（Java / SQL / 配置文件等）
            List<DiffFile> reviewableDiffs = diffs.stream()
                    .filter(DiffFile::isReviewableFile)
                    .filter(d -> !d.isDeletedFile())
                    .toList();

            // Step 2.5: 大文件前置过滤（glob 排除 + 行数阈值 + 二进制检测）
            List<String> skippedReasons = new ArrayList<>();
            List<DiffFile> filteredDiffs = filterReviewableFiles(
                    reviewableDiffs, reviewConfig.getFileFilter(), skippedReasons);
            String degradationNote = buildDegradationNote(skippedReasons);

            if (filteredDiffs.isEmpty()) {
                completeWithNoReviewableFiles(task, startTime);
                return;
            }

            String reviewRef = task.getLastCommitSha() != null && !task.getLastCommitSha().isBlank()
                    ? task.getLastCommitSha()
                    : task.getSourceBranch();
            String indexWarning = ensureReviewCodeIndex(scmConfig, task, filteredDiffs, reviewRef);
            if (indexWarning != null) {
                skippedReasons.add(indexWarning);
            }
            degradationNote = buildDegradationNote(skippedReasons);

            // Step 3: 构建评审上下文（含 Token 预算分配）
            List<ReviewContext> contexts = contextBuilder.buildReviewContexts(
                    scmService, scmConfig, task, filteredDiffs, reviewRef);

            // Token 预算分配
            contextBuilder.trimToBudget(contexts, reviewConfig.getToken());

            // 计算变更统计
            DiffParser.DiffStats stats = diffParser.calculateStats(filteredDiffs);
            task.setFileCount(stats.fileCount());
            task.setAddedLines(stats.addedLines());
            task.setRemovedLines(stats.removedLines());

            // Step 0.5: 发布“评审中”进度评论
            publishProgressComment(task, scmConfig, scmService, reviewConfig);

            // Step 4: 加载历史编码规范兜底内容，主链优先走规则检索服务。
            String codingStandards = codingStandardsLoader.loadCodingStandards();

            // Step 5: 对每个文件执行 AI 评审
            List<FileReviewResult> fileResults = executeFileReviews(contexts, codingStandards, reviewConfig, scmConfig.getId());
            List<ScoreCalculator.ScoreResult> allScores = new ArrayList<>();
            List<AiReviewEngine.ReviewResult.Issue> allIssues = new ArrayList<>();
            int totalTokens = 0;

            for (FileReviewResult fileResult : fileResults) {
                totalTokens += fileResult.reviewResult().getTokensUsed();
                allScores.add(fileResult.scoreResult());
                if (fileResult.reviewResult().getIssues() != null) {
                    allIssues.addAll(fileResult.reviewResult().getIssues());
                }
            }

            task.setTokensUsed(totalTokens);

            // Step 6: 提前发布评审问题评论
            String issueReport = reportFormatter.formatIssueReport(task, allIssues, degradationNote);
            Long issueCommentId = scmService.addPullRequestComment(scmConfig, task, issueReport);
            task.setScmCommentId(issueCommentId);
            task.setSummary(issueReport);
            reviewTaskMapper.updateById(task);

            // Step 6.5: 保存问题到数据库
            List<ReviewIssue> persistedIssues = new ArrayList<>();
            for (AiReviewEngine.ReviewResult.Issue issue : allIssues) {
                ReviewIssue entity = new ReviewIssue();
                entity.setTaskId(taskId);
                entity.setFilePath(issue.getFilePath());
                entity.setStartLine(issue.getStartLine());
                entity.setEndLine(issue.getEndLine());
                entity.setSeverity(issue.getSeverity());
                entity.setCategory(issue.getCategory());
                entity.setDescription(issue.getDescription());
                entity.setSuggestion(issue.getSuggestion());
                entity.setCodeSnippet(issue.getCodeSnippet());
                entity.setRule(issue.getRule());
                reviewIssueMapper.insert(entity);
                persistedIssues.add(entity);
            }

            // Step 7: 异步评分 + 标签 + 通知
            submitScoreTask(task, scmConfig, scmService, reviewConfig, allScores, persistedIssues, issueReport, startTime);
            log.info("评审问题评论已发布，评分任务异步执行: taskId={}, issues={}", taskId, allIssues.size());

        } catch (Exception e) {
            log.error("评审执行异常: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setDuration(System.currentTimeMillis() - startTime);
            reviewTaskMapper.updateById(task);
        }
    }

    private String ensureReviewCodeIndex(ScmConfig scmConfig,
                                         ReviewTask task,
                                         List<DiffFile> filteredDiffs,
                                         String reviewRef) {
        if (scmConfig == null || scmConfig.getId() == null || task == null) {
            return null;
        }
        if (!hasText(task.getLastCommitSha())) {
            log.debug("评审任务缺少 lastCommitSha，跳过源码索引准备: taskId={}", task.getId());
            return null;
        }
        String branchName = firstText(task.getSourceBranch(), task.getTargetBranch(), CodeIndexConstants.DEFAULT_BRANCH);
        try {
            CodeIndexSummaryResDTO existingIndex = codeIndexService.getSuccessfulIndexByCommit(
                    scmConfig.getId(), task.getLastCommitSha());
            if (existingIndex != null) {
                log.info("评审源码索引已存在，跳过重复扫描: taskId={}, indexId={}, commit={}",
                        task.getId(), existingIndex.getIndexId(), task.getLastCommitSha());
                return null;
            }

            CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
            requestDTO.setBranchName(branchName);
            requestDTO.setCommitSha(task.getLastCommitSha());
            requestDTO.setScanType(CodeIndexConstants.ScanType.INCREMENTAL);
            requestDTO.setReason("REVIEW_PREPARE");
            CodeIndexSummaryResDTO scanResult = codeIndexScanService.scanIncremental(scmConfig, requestDTO, filteredDiffs);
            if (scanResult == null) {
                log.warn("评审源码索引准备未返回结果，降级继续: taskId={}, commit={}", task.getId(), reviewRef);
                return "`源码索引` — 准备未返回结果，已降级为当前文件上下文";
            }
            if (!isUsableIndexStatus(scanResult.getScanStatus())) {
                log.warn("评审源码索引准备未成功，降级继续: taskId={}, commit={}, status={}, error={}",
                        task.getId(), reviewRef, scanResult.getScanStatus(), scanResult.getLatestErrorMessage());
                return "`源码索引` — 准备失败，已降级为当前文件上下文";
            }
            log.info("评审源码索引准备完成: taskId={}, indexId={}, commit={}, status={}",
                    task.getId(), scanResult.getIndexId(), scanResult.getCommitSha(), scanResult.getScanStatus());
            return null;
        } catch (Exception e) {
            log.warn("评审源码索引准备异常，降级继续: taskId={}, commit={}, error={}",
                    task.getId(), reviewRef, e.getMessage());
            log.debug("评审源码索引准备异常详情: taskId={}, commit={}", task.getId(), reviewRef, e);
            return "`源码索引` — 准备异常，已降级为当前文件上下文";
        }
    }

    private boolean isUsableIndexStatus(String scanStatus) {
        return CodeIndexConstants.ScanStatus.SUCCESS.equals(scanStatus)
                || CodeIndexConstants.ScanStatus.PARTIAL.equals(scanStatus);
    }

    /**
     * 无 Java 文件变更时的快速完成
     */
    private void completeWithNoReviewableFiles(ReviewTask task, long startTime) {
        log.info("无可评审文件变更, taskId={}", task.getId());
        task.setStatus("DONE");
        task.setTotalScore(100);
        task.setScoreLevel("A");
        task.setFileCount(0);
        task.setSummary("本次提交无可评审文件变更（如 Java、SQL、YAML、XML、Properties 等），自动通过");
        task.setDuration(System.currentTimeMillis() - startTime);
        task.setNotified(false);
        reviewTaskMapper.updateById(task);
    }

    private List<FileReviewResult> executeFileReviews(List<ReviewContext> contexts,
                                                      String codingStandards,
                                                      ReviewConfig reviewConfig,
                                                      Long scmConfigId) {
        int parallelism = reviewConfig.getAsync().getThreadPoolSize();
        parallelism = Math.max(1, Math.min(parallelism, contexts.size()));

        List<FileReviewResult> results = new ArrayList<>();
        for (int start = 0; start < contexts.size(); start += parallelism) {
            int end = Math.min(start + parallelism, contexts.size());
            List<CompletableFuture<FileReviewResult>> futures = contexts.subList(start, end).stream()
                    .map(context -> CompletableFuture.supplyAsync(
                            () -> reviewSingleFile(context, codingStandards, reviewConfig, scmConfigId),
                            reviewFileExecutor))
                    .toList();
            for (CompletableFuture<FileReviewResult> future : futures) {
                results.add(future.join());
            }
        }
        return results;
    }

    private FileReviewResult reviewSingleFile(ReviewContext context,
                                              String codingStandards,
                                              ReviewConfig reviewConfig,
                                              Long scmConfigId) {
        String prompt = promptBuilder.buildReviewPrompt(context, codingStandards, reviewConfig, scmConfigId);
        AiReviewEngine.ReviewResult reviewResult = aiReviewEngine.executeReview(prompt, scmConfigId);
        normalizeIssues(reviewResult, context.getFilePath());
        ScoreCalculator.ScoreResult scoreResult = scoreCalculator.calculateScore(reviewResult, reviewConfig);
        log.info("文件评审完成: file={}, score={}, issues={}",
                context.getFilePath(), scoreResult.getTotalScore(),
                reviewResult.getIssues() != null ? reviewResult.getIssues().size() : 0);
        return new FileReviewResult(context.getFilePath(), reviewResult, scoreResult);
    }

    private void normalizeIssues(AiReviewEngine.ReviewResult reviewResult, String defaultFilePath) {
        if (reviewResult == null || reviewResult.getIssues() == null) {
            return;
        }
        for (AiReviewEngine.ReviewResult.Issue issue : reviewResult.getIssues()) {
            if (issue.getFilePath() == null || issue.getFilePath().isBlank()) {
                issue.setFilePath(defaultFilePath != null && !defaultFilePath.isBlank() ? defaultFilePath : "unknown");
            }
            if (issue.getSeverity() == null || issue.getSeverity().isBlank()) {
                issue.setSeverity("SUGGESTION");
            }
            if (issue.getCategory() == null || issue.getCategory().isBlank()) {
                issue.setCategory("MAINTAINABILITY");
            }
            if (issue.getDescription() == null || issue.getDescription().isBlank()) {
                issue.setDescription("AI 未返回问题描述，请结合评审总结人工确认。");
            }
        }
    }

    private record FileReviewResult(String filePath,
                                    AiReviewEngine.ReviewResult reviewResult,
                                    ScoreCalculator.ScoreResult scoreResult) {
    }

    private ReviewConfig resolveReviewConfig(ScmConfig scmConfig) {
        if (scmConfig.getReviewConfig() != null && !scmConfig.getReviewConfig().isBlank()) {
            try {
                ReviewConfig override = JSON.parseObject(scmConfig.getReviewConfig(), ReviewConfig.class);
                return ReviewConfig.defaults().merge(override);
            } catch (Exception e) {
                log.warn("ScmConfig.review_config 解析失败，使用默认配置: {}", e.getMessage());
            }
        }
        return ReviewConfig.defaults();
    }

    private void publishProgressComment(ReviewTask task,
                                        ScmConfig scmConfig,
                                        ScmPlatformService scmService,
                                        ReviewConfig reviewConfig) {
        if (!reviewConfig.getAsync().isProgressCommentEnabled()) {
            return;
        }
        Long commentId = scmService.addPullRequestComment(scmConfig, task, buildProgressComment(task));
        task.setScmCommentId(commentId);
        reviewTaskMapper.updateById(task);
    }

    private void submitScoreTask(ReviewTask task,
                                 ScmConfig scmConfig,
                                 ScmPlatformService scmService,
                                 ReviewConfig reviewConfig,
                                 List<ScoreCalculator.ScoreResult> allScores,
                                 List<ReviewIssue> persistedIssues,
                                 String issueReport,
                                 long startTime) {
        AtomicBoolean terminalWritten = new AtomicBoolean(false);
        CompletableFuture.runAsync(
                        () -> completeScorePhase(task, scmConfig, scmService, reviewConfig, allScores,
                                persistedIssues, issueReport, startTime, terminalWritten),
                        reviewExecutorPool)
                .orTimeout(reviewConfig.getAsync().getScoreTimeoutSec(), TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    handleScorePhaseFailure(task, scmConfig, scmService, issueReport, startTime, ex, terminalWritten);
                    return null;
                });
    }

    private void completeScorePhase(ReviewTask task,
                                    ScmConfig scmConfig,
                                    ScmPlatformService scmService,
                                    ReviewConfig reviewConfig,
                                    List<ScoreCalculator.ScoreResult> allScores,
                                    List<ReviewIssue> persistedIssues,
                                    String issueReport,
                                    long startTime,
                                    AtomicBoolean terminalWritten) {
        ScoreCalculator.ScoreResult finalScore = retryMergeScores(allScores, reviewConfig);
        if (terminalWritten.get()) {
            log.warn("评分阶段已由失败/超时处理写入终态，跳过成功写回: taskId={}", task.getId());
            return;
        }
        task.setTotalScore(finalScore.getTotalScore());
        task.setScoreLevel(finalScore.getScoreLevel());
        task.setCriticalCount(finalScore.getCriticalCount());
        task.setMajorCount(finalScore.getMajorCount());
        task.setMinorCount(finalScore.getMinorCount());

        String scoreReport = reportFormatter.formatScoreReport(task, finalScore, reviewConfig);
        scmService.addPullRequestComment(scmConfig, task, scoreReport);
        if (terminalWritten.get()) {
            log.warn("评分评论写回后发现任务已超时/失败，跳过后续成功写回: taskId={}", task.getId());
            return;
        }

        String label = finalScore.isPassed() ? "AI-Review:PASSED" : "AI-Review:BLOCKED";
        scmService.setPullRequestLabels(scmConfig, task, List.of(label));
        if (!terminalWritten.compareAndSet(false, true)) {
            log.warn("评分阶段已由失败/超时处理写入终态，跳过成功状态写回: taskId={}", task.getId());
            return;
        }

        task.setSummary(issueReport + "\n\n" + scoreReport);
        task.setStatus("DONE");
        task.setDuration(System.currentTimeMillis() - startTime);
        task.setNotified(false);
        reviewTaskMapper.updateById(task);

        triggerProfileAndVectorUpdates(task, finalScore, persistedIssues);

        boolean notified = false;
        if (!scmConfig.isWechatNotificationEnabled()) {
            log.info("仓库级企微通知已关闭，跳过评审通知: taskId={}, project={}",
                    task.getId(), task.getProjectName());
        } else if (!reviewConfig.getNotification().isWechatNotifyEnabled()) {
            log.info("ReviewConfig 已关闭企微通知，跳过评审通知: taskId={}, project={}",
                    task.getId(), task.getProjectName());
        } else {
            notified = notificationService.sendReviewNotification(task, finalScore, scmConfig, reviewConfig);
        }
        task.setNotified(notified);
        reviewTaskMapper.updateById(task);

        log.info("评审完成: taskId={}, score={}, level={}, passed={}, duration={}ms",
                task.getId(), finalScore.getTotalScore(), finalScore.getScoreLevel(),
                finalScore.isPassed(), task.getDuration());
    }

    private void triggerProfileAndVectorUpdates(ReviewTask task,
                                                ScoreCalculator.ScoreResult finalScore,
                                                List<ReviewIssue> persistedIssues) {
        try {
            reviewerProfileService.updateProfile(task, finalScore);
        } catch (Exception e) {
            log.warn("画像更新失败，不影响主流程: taskId={}, error={}", task.getId(), e.getMessage());
        }

        String authorId = resolveAuthorId(task);
        for (ReviewIssue issue : persistedIssues) {
            try {
                vectorKnowledgeService.storeReviewIssue(issue, task.getId(), authorId, task.getProjectName());
            } catch (Exception e) {
                log.warn("评审 Issue 向量写入触发失败，不影响主流程: issueId={}, error={}",
                        issue.getId(), e.getMessage());
            }
        }
    }

    private ScoreCalculator.ScoreResult retryMergeScores(List<ScoreCalculator.ScoreResult> allScores,
                                                         ReviewConfig reviewConfig) {
        int maxRetry = Math.max(0, reviewConfig.getAsync().getScoreRetryMax());
        long retryDelayMs = Math.max(0, reviewConfig.getAsync().getScoreRetryDelayMs());
        int attempts = maxRetry + 1;
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return scoreCalculator.mergeScores(allScores, reviewConfig);
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("综合评分计算失败: attempt={}/{}, message={}", attempt, attempts, ex.getMessage());
                if (attempt == attempts) {
                    break;
                }
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("评分重试被中断", interruptedException);
                }
            }
        }

        throw lastException != null ? lastException : new IllegalStateException("综合评分计算失败");
    }

    private void handleScorePhaseFailure(ReviewTask task,
                                         ScmConfig scmConfig,
                                         ScmPlatformService scmService,
                                         String issueReport,
                                         long startTime,
                                         Throwable throwable,
                                         AtomicBoolean terminalWritten) {
        if (!terminalWritten.compareAndSet(false, true)) {
            log.warn("评分阶段已完成成功写回，忽略后续失败/超时处理: taskId={}", task.getId());
            return;
        }
        Throwable cause = unwrapCompletionException(throwable);
        String failureComment = buildScoreFailureComment(task, cause);
        try {
            scmService.addPullRequestComment(scmConfig, task, failureComment);
        } catch (Exception commentException) {
            log.error("评分失败降级评论发送失败: taskId={}", task.getId(), commentException);
        }

        task.setSummary(issueReport + "\n\n" + failureComment);
        task.setStatus(cause instanceof TimeoutException ? "TIMEOUT" : "FAILED");
        task.setErrorMessage(cause != null ? cause.getMessage() : "评分阶段失败");
        task.setDuration(System.currentTimeMillis() - startTime);
        task.setNotified(false);
        reviewTaskMapper.updateById(task);

        log.error("评分阶段失败: taskId={}, status={}, message={}",
                task.getId(), task.getStatus(), task.getErrorMessage(), cause);
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable cause = throwable.getCause();
        return cause != null ? cause : throwable;
    }

    String buildProgressComment(ReviewTask task) {
        return """
                ## 🤖 Argus AI 代码评审已启动

                > **MR**: !%d %s
                > **提交者**: %s
                > **文件数**: %d 个文件，+%d / -%d 行
                > **预计耗时**: 约 2-3 分钟

                评审问题会优先发布，综合评分随后补充。
                """.formatted(
                task.getMrIid(),
                task.getMrTitle(),
                task.getAuthorName(),
                task.getFileCount() != null ? task.getFileCount() : 0,
                task.getAddedLines() != null ? task.getAddedLines() : 0,
                task.getRemovedLines() != null ? task.getRemovedLines() : 0
        );
    }

    String buildScoreFailureComment(ReviewTask task, Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : "评分阶段异常";
        String normalizedReason = throwable instanceof TimeoutException
                ? "评分计算超时，请稍后重试或人工查看问题清单"
                : reason;
        return """
                ## ⚠️ Argus AI 综合评分未完成

                > **MR**: !%d %s
                > **提交者**: %s
                > **原因**: %s

                问题清单已经发布，本次未能追加综合评分，请优先处理已识别问题。
                """.formatted(task.getMrIid(), task.getMrTitle(), task.getAuthorName(), normalizedReason);
    }

    private String firstText(String first, String second, String fallback) {
        if (hasText(first)) {
            return first;
        }
        return hasText(second) ? second : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveAuthorId(ReviewTask task) {
        if (task.getAuthorId() != null && !task.getAuthorId().isBlank()) {
            return task.getAuthorId();
        }
        return task.getScmProvider() + ":" + task.getAuthorName();
    }

    // ======================== 大文件过滤 ========================

    /**
     * 前置过滤：glob 排除 + 行数阈值 + 二进制检测 + 文件数上限
     */
    List<DiffFile> filterReviewableFiles(List<DiffFile> diffs,
                                         ReviewConfig.FileFilterConfig filter,
                                         List<String> skippedReasons) {
        List<DiffFile> accepted = new ArrayList<>();
        for (DiffFile diff : diffs) {
            String path = diff.getNewPath();

            if (isBinary(path, filter.getBinaryExtensions())) {
                skippedReasons.add("`" + path + "` — 二进制/资源文件，跳过审查");
                continue;
            }
            if (matchesGlob(path, filter.getExcludeFilePatterns())) {
                skippedReasons.add("`" + path + "` — 匹配排除模式，跳过审查");
                continue;
            }
            int diffLines = (diff.getAddedLines() != null ? diff.getAddedLines().size() : 0)
                    + (diff.getRemovedLines() != null ? diff.getRemovedLines().size() : 0);
            if (diffLines > filter.getMaxDiffLinesPerFile()) {
                skippedReasons.add("`" + path + "` — diff 行数 " + diffLines
                        + " 超阈值 " + filter.getMaxDiffLinesPerFile() + "，跳过 AI 深度审查");
                continue;
            }
            if (accepted.size() >= filter.getMaxReviewFiles()) {
                skippedReasons.add("`" + path + "` — 评审文件数已达上限 " + filter.getMaxReviewFiles());
                continue;
            }
            accepted.add(diff);
        }
        return accepted;
    }

    private boolean isBinary(String path, List<String> binaryExtensions) {
        if (path == null || binaryExtensions == null || binaryExtensions.isEmpty()) return false;
        String lower = path.toLowerCase();
        return binaryExtensions.stream().anyMatch(lower::endsWith);
    }

    private boolean matchesGlob(String path, List<String> patterns) {
        if (path == null || patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            String clean = pattern.startsWith("**/") ? pattern.substring(3) : pattern;
            if (clean.startsWith("*.")) {
                if (path.endsWith(clean.substring(1))) return true;
            } else if (path.contains(clean) || path.endsWith(clean)) {
                return true;
            }
        }
        return false;
    }

    private String buildDegradationNote(List<String> skippedReasons) {
        if (skippedReasons == null || skippedReasons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n> ⚠️ 以下文件因 diff 过大或命中排除规则，未做 AI 深度审查：\n>\n");
        for (String reason : skippedReasons) {
            sb.append("> - ").append(reason).append("\n");
        }
        return sb.toString();
    }

}
