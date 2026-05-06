package com.lnzz.argus.review.service;

import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.CodingStandardsLoader;
import com.lnzz.argus.review.ai.PromptBuilder;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.review.entity.ReviewTask;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

            // Step 2: 过滤 Java 文件
            List<DiffFile> javaDiffs = diffs.stream()
                    .filter(DiffFile::isJavaFile)
                    .filter(d -> !d.isDeletedFile())
                    .toList();

            if (javaDiffs.isEmpty()) {
                completeWithNoJavaFiles(task, startTime);
                return;
            }

            // Step 3: 构建评审上下文
            String reviewRef = task.getLastCommitSha() != null && !task.getLastCommitSha().isBlank()
                    ? task.getLastCommitSha()
                    : task.getSourceBranch();
            List<ReviewContext> contexts = contextBuilder.buildReviewContexts(
                    scmService, scmConfig, task, javaDiffs, reviewRef);

            // 计算变更统计
            DiffParser.DiffStats stats = diffParser.calculateStats(javaDiffs);
            task.setFileCount(stats.fileCount());
            task.setAddedLines(stats.addedLines());
            task.setRemovedLines(stats.removedLines());

            // Step 4: 加载编码规范
            String codingStandards = codingStandardsLoader.loadCodingStandards();

            // Step 5: 对每个文件执行 AI 评审
            List<ScoreCalculator.ScoreResult> allScores = new ArrayList<>();
            List<AiReviewEngine.ReviewResult.Issue> allIssues = new ArrayList<>();
            int totalTokens = 0;

            for (ReviewContext context : contexts) {
                // 构建 Prompt
                String prompt = promptBuilder.buildReviewPrompt(context, codingStandards);

                // 调用 AI
                AiReviewEngine.ReviewResult reviewResult = aiReviewEngine.executeReview(prompt);
                totalTokens += reviewResult.getTokensUsed();

                // 计算评分
                ScoreCalculator.ScoreResult scoreResult = scoreCalculator.calculateScore(reviewResult);
                allScores.add(scoreResult);

                // 收集问题
                if (reviewResult.getIssues() != null) {
                    allIssues.addAll(reviewResult.getIssues());
                }

                log.info("文件评审完成: file={}, score={}, issues={}",
                        context.getFilePath(), scoreResult.getTotalScore(),
                        reviewResult.getIssues() != null ? reviewResult.getIssues().size() : 0);
            }

            // Step 6: 合并评分
            ScoreCalculator.ScoreResult finalScore = scoreCalculator.mergeScores(allScores);
            task.setTotalScore(finalScore.getTotalScore());
            task.setScoreLevel(finalScore.getScoreLevel());
            task.setCriticalCount(finalScore.getCriticalCount());
            task.setMajorCount(finalScore.getMajorCount());
            task.setMinorCount(finalScore.getMinorCount());
            task.setTokensUsed(totalTokens);

            // Step 7: 格式化评审报告
            String report = reportFormatter.formatReport(task, finalScore, allIssues);
            task.setSummary(report);

            // Step 8: 回写 SCM
            Long commentId = scmService.addPullRequestComment(scmConfig, task, report);
            task.setScmCommentId(commentId);

            // 设置 PR/MR 标签
            String label = finalScore.isPassed() ? "AI-Review:PASSED" : "AI-Review:BLOCKED";
            scmService.setPullRequestLabels(scmConfig, task, List.of(label));

            // Step 9: 保存问题到数据库
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
            }

            // Step 10: 完成评审主流程
            task.setStatus("DONE");
            task.setDuration(System.currentTimeMillis() - startTime);
            task.setNotified(false);
            reviewTaskMapper.updateById(task);

            // Step 11: 企微通知
            boolean notified = notificationService.sendReviewNotification(task, finalScore);
            task.setNotified(notified);
            reviewTaskMapper.updateById(task);

            log.info("评审完成: taskId={}, score={}, level={}, passed={}, duration={}ms",
                    taskId, finalScore.getTotalScore(), finalScore.getScoreLevel(),
                    finalScore.isPassed(), task.getDuration());

        } catch (Exception e) {
            log.error("评审执行异常: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setDuration(System.currentTimeMillis() - startTime);
            reviewTaskMapper.updateById(task);
        }
    }

    /**
     * 无 Java 文件变更时的快速完成
     */
    private void completeWithNoJavaFiles(ReviewTask task, long startTime) {
        log.info("无Java文件变更, taskId={}", task.getId());
        task.setStatus("DONE");
        task.setTotalScore(100);
        task.setScoreLevel("A");
        task.setFileCount(0);
        task.setSummary("本次提交无Java文件变更，自动通过");
        task.setDuration(System.currentTimeMillis() - startTime);
        task.setNotified(false);
        reviewTaskMapper.updateById(task);
    }
}
