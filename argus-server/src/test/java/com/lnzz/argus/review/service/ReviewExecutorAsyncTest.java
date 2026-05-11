package com.lnzz.argus.review.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.CodingStandardsLoader;
import com.lnzz.argus.review.ai.PromptBuilder;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewIssueMapper;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import com.lnzz.argus.review.parser.ContextBuilder;
import com.lnzz.argus.review.parser.DiffParser;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.review.service.ReviewerProfileService;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewExecutor - 异步评分解耦")
class ReviewExecutorAsyncTest {

    @Mock
    private ReviewTaskMapper reviewTaskMapper;
    @Mock
    private ReviewIssueMapper reviewIssueMapper;
    @Mock
    private ScmConfigService scmConfigService;
    @Mock
    private ScmPlatformServiceFactory scmPlatformServiceFactory;
    @Mock
    private ContextBuilder contextBuilder;
    @Mock
    private DiffParser diffParser;
    @Mock
    private PromptBuilder promptBuilder;
    @Mock
    private AiReviewEngine aiReviewEngine;
    @Mock
    private ScoreCalculator scoreCalculator;
    @Mock
    private CodingStandardsLoader codingStandardsLoader;
    private final ReviewReportFormatter reportFormatter = new ReviewReportFormatter();
    @Mock
    private NotificationService notificationService;
    @Mock
    private ScmPlatformService scmService;
    @Mock
    private ReviewerProfileService reviewerProfileService;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;

    private final ExecutorService reviewFileExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService reviewExecutorPool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() throws Exception {
        reviewFileExecutor.shutdownNow();
        reviewExecutorPool.shutdownNow();
        reviewFileExecutor.awaitTermination(1, TimeUnit.SECONDS);
        reviewExecutorPool.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("评分超时也会先发布进度和问题评论")
    void scoreTimeoutStillPublishesProgressAndIssues() throws Exception {
        ReviewExecutor executor = newExecutor();
        ReviewTask task = createTask();
        ScmConfig scmConfig = createScmConfig(withAsyncConfig(1, 0, 1, true));

        when(reviewTaskMapper.selectById(1L)).thenReturn(task);
        when(scmConfigService.requireById(99L)).thenReturn(scmConfig);
        when(scmPlatformServiceFactory.getRequired("github")).thenReturn(scmService);
        when(scmService.getPullRequestDiffs(any(), any())).thenReturn(List.of(createDiff()));
        when(contextBuilder.buildReviewContexts(any(), any(), any(), anyList(), anyString()))
                .thenReturn(List.of(createContext()));
        doNothing().when(contextBuilder).trimToBudget(anyList(), any());
        when(diffParser.calculateStats(anyList())).thenReturn(new DiffParser.DiffStats(1, 12, 4));
        when(codingStandardsLoader.loadCodingStandards()).thenReturn("rules");
        when(promptBuilder.buildReviewPrompt(any(), anyString(), any())).thenReturn("prompt");
        when(aiReviewEngine.executeReview("prompt")).thenReturn(createReviewResult());
        when(scoreCalculator.calculateScore(any(), any())).thenReturn(createFileScore());
        when(scoreCalculator.mergeScores(anyList(), any())).thenAnswer(invocation -> {
            Thread.sleep(1500L);
            return createFinalScore();
        });
        when(scmService.addPullRequestComment(any(), any(), anyString())).thenReturn(1L, 2L, 3L);

        executor.executeReview(1L);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(scmService, timeout(4000).times(3))
                .addPullRequestComment(any(), any(), commentCaptor.capture());
        List<String> comments = commentCaptor.getAllValues();
        assertTrue(comments.get(0).contains("Argus AI 代码评审已启动"));
        assertTrue(comments.get(1).contains("Argus AI 评审问题清单"));
        assertTrue(comments.get(2).contains("Argus AI 综合评分未完成"));
        ArgumentCaptor<ReviewIssue> issueCaptor = ArgumentCaptor.forClass(ReviewIssue.class);
        verify(reviewIssueMapper, timeout(1000)).insert(issueCaptor.capture());
        assertEquals("src/main/java/com/example/DemoService.java", issueCaptor.getValue().getFilePath());
        assertEquals("MAINTAINABILITY", issueCaptor.getValue().getCategory());
        verify(scmService, after(1200).never()).setPullRequestLabels(any(), any(), anyList());
        verify(notificationService, after(1200).never()).sendReviewNotification(any(), any(), any(), any());
        verify(reviewTaskMapper, after(1200).never()).updateById(argThat((ReviewTask updated) -> "DONE".equals(updated.getStatus())));
    }

    @Test
    @DisplayName("评分失败按配置重试后发布降级评论")
    void scoreFailureRetriesThenPublishesFailureComment() {
        ReviewExecutor executor = newExecutor();
        ReviewTask task = createTask();
        ScmConfig scmConfig = createScmConfig(withAsyncConfig(5, 2, 1, true));

        when(reviewTaskMapper.selectById(1L)).thenReturn(task);
        when(scmConfigService.requireById(99L)).thenReturn(scmConfig);
        when(scmPlatformServiceFactory.getRequired("github")).thenReturn(scmService);
        when(scmService.getPullRequestDiffs(any(), any())).thenReturn(List.of(createDiff()));
        when(contextBuilder.buildReviewContexts(any(), any(), any(), anyList(), anyString()))
                .thenReturn(List.of(createContext()));
        doNothing().when(contextBuilder).trimToBudget(anyList(), any());
        when(diffParser.calculateStats(anyList())).thenReturn(new DiffParser.DiffStats(1, 12, 4));
        when(codingStandardsLoader.loadCodingStandards()).thenReturn("rules");
        when(promptBuilder.buildReviewPrompt(any(), anyString(), any())).thenReturn("prompt");
        when(aiReviewEngine.executeReview("prompt")).thenReturn(createReviewResult());
        when(scoreCalculator.calculateScore(any(), any())).thenReturn(createFileScore());
        when(scoreCalculator.mergeScores(anyList(), any()))
                .thenThrow(new IllegalStateException("mock score failure"));
        when(scmService.addPullRequestComment(any(), any(), anyString())).thenReturn(1L, 2L, 3L);

        executor.executeReview(1L);

        verify(scoreCalculator, timeout(3000).times(3)).mergeScores(anyList(), any());
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(scmService, timeout(3000).times(3))
                .addPullRequestComment(any(), any(), commentCaptor.capture());
        List<String> comments = commentCaptor.getAllValues();
        assertTrue(comments.get(2).contains("Argus AI 综合评分未完成"));
        assertTrue(comments.get(2).contains("mock score failure"));
        verify(notificationService, never()).sendReviewNotification(any(), any());
    }

    @Test
    @DisplayName("仓库关闭企微通知时跳过评审通知")
    void wechatDisabledSkipsReviewNotification() {
        ReviewExecutor executor = newExecutor();
        ReviewTask task = createTask();
        ScmConfig scmConfig = createScmConfig(withAsyncConfig(5, 0, 1, true));
        scmConfig.setWechatNotifyEnabled(0);

        when(reviewTaskMapper.selectById(1L)).thenReturn(task);
        when(scmConfigService.requireById(99L)).thenReturn(scmConfig);
        when(scmPlatformServiceFactory.getRequired("github")).thenReturn(scmService);
        when(scmService.getPullRequestDiffs(any(), any())).thenReturn(List.of(createDiff()));
        when(contextBuilder.buildReviewContexts(any(), any(), any(), anyList(), anyString()))
                .thenReturn(List.of(createContext()));
        doNothing().when(contextBuilder).trimToBudget(anyList(), any());
        when(diffParser.calculateStats(anyList())).thenReturn(new DiffParser.DiffStats(1, 12, 4));
        when(codingStandardsLoader.loadCodingStandards()).thenReturn("rules");
        when(promptBuilder.buildReviewPrompt(any(), anyString(), any())).thenReturn("prompt");
        when(aiReviewEngine.executeReview("prompt")).thenReturn(createReviewResult());
        when(scoreCalculator.calculateScore(any(), any())).thenReturn(createFileScore());
        when(scoreCalculator.mergeScores(anyList(), any())).thenReturn(createFinalScore());
        when(scmService.addPullRequestComment(any(), any(), anyString())).thenReturn(1L, 2L, 3L);

        executor.executeReview(1L);

        verify(notificationService, after(1500).never()).sendReviewNotification(any(), any(), any(), any());
        verify(scmService, timeout(2000)).setPullRequestLabels(any(), any(), anyList());
    }

    private ReviewExecutor newExecutor() {
        return new ReviewExecutor(
                reviewTaskMapper,
                reviewIssueMapper,
                reviewerProfileService,
                scmConfigService,
                scmPlatformServiceFactory,
                contextBuilder,
                diffParser,
                promptBuilder,
                aiReviewEngine,
                scoreCalculator,
                codingStandardsLoader,
                reportFormatter,
                notificationService,
                vectorKnowledgeService,
                reviewFileExecutor,
                reviewExecutorPool
        );
    }

    private ReviewTask createTask() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setScmProvider("github");
        task.setScmConfigId(99L);
        task.setProjectName("demo-project");
        task.setAuthorId("github:yyuguang");
        task.setRepoOwner("octo");
        task.setRepoName("demo");
        task.setMrIid(42L);
        task.setMrTitle("feat: async score");
        task.setAuthorName("yyuguang");
        task.setSourceBranch("feature/async-score");
        task.setTargetBranch("test");
        return task;
    }

    private ScmConfig createScmConfig(ReviewConfig reviewConfig) {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(99L);
        scmConfig.setScmProvider("github");
        scmConfig.setReviewConfig(JSON.toJSONString(reviewConfig));
        return scmConfig;
    }

    private ReviewConfig withAsyncConfig(int scoreTimeoutSec,
                                         int scoreRetryMax,
                                         int scoreRetryDelayMs,
                                         boolean progressCommentEnabled) {
        ReviewConfig config = ReviewConfig.defaults();
        config.getAsync().setScoreTimeoutSec(scoreTimeoutSec);
        config.getAsync().setScoreRetryMax(scoreRetryMax);
        config.getAsync().setScoreRetryDelayMs(scoreRetryDelayMs);
        config.getAsync().setProgressCommentEnabled(progressCommentEnabled);
        config.getAsync().setThreadPoolSize(1);
        return config;
    }

    private DiffFile createDiff() {
        DiffFile diffFile = new DiffFile();
        diffFile.setNewPath("src/main/java/com/example/DemoService.java");
        diffFile.setAddedLines(List.of(new DiffFile.DiffLine(10, "+demo")));
        diffFile.setRemovedLines(List.of(new DiffFile.DiffLine(11, "-old")));
        return diffFile;
    }

    private ReviewContext createContext() {
        return ReviewContext.builder()
                .filePath("src/main/java/com/example/DemoService.java")
                .languageTag("java")
                .fullContent("class DemoService {}")
                .diffContent("@@ -1 +1 @@")
                .estimatedTokens(100)
                .build();
    }

    private AiReviewEngine.ReviewResult createReviewResult() {
        AiReviewEngine.ReviewResult result = new AiReviewEngine.ReviewResult();
        AiReviewEngine.ReviewResult.Issue issue = new AiReviewEngine.ReviewResult.Issue();
        issue.setSeverity("MAJOR");
        issue.setStartLine(10);
        issue.setDescription("事务边界缺失");
        issue.setSuggestion("补充事务控制");
        result.setIssues(List.of(issue));
        result.setTokensUsed(64);
        return result;
    }

    private ScoreCalculator.ScoreResult createFileScore() {
        ScoreCalculator.ScoreResult score = new ScoreCalculator.ScoreResult();
        score.setComplianceScore(80);
        score.setCorrectnessScore(78);
        score.setDataSafetyScore(82);
        score.setPerformanceScore(76);
        score.setMaintainabilityScore(84);
        score.setMajorCount(1);
        return score;
    }

    private ScoreCalculator.ScoreResult createFinalScore() {
        ScoreCalculator.ScoreResult score = new ScoreCalculator.ScoreResult();
        score.setTotalScore(80);
        score.setScoreLevel("B");
        score.setComplianceScore(80);
        score.setCorrectnessScore(78);
        score.setDataSafetyScore(82);
        score.setPerformanceScore(76);
        score.setMaintainabilityScore(84);
        score.setCriticalCount(0);
        score.setMajorCount(1);
        score.setMinorCount(0);
        score.setPassed(true);
        return score;
    }
}
