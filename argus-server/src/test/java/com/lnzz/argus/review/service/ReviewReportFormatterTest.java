package com.lnzz.argus.review.service;

import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReviewReportFormatter - 配置化报告")
class ReviewReportFormatterTest {

    private ReviewReportFormatter formatter;
    private ReviewConfig defaultConfig;

    @BeforeEach
    void setUp() {
        formatter = new ReviewReportFormatter();
        defaultConfig = ReviewConfig.defaults();
    }

    @Test
    @DisplayName("报告维度权重从 ReviewConfig 读取")
    void dimensionWeightsFromConfig() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(85, 80, 90, 75, 70, 0, 0, 0, true);

        String report = formatter.formatReport(task, score, List.of(), defaultConfig);

        assertTrue(report.contains("规范合规 | 85 | 25% | 21 |"));
        assertTrue(report.contains("逻辑正确 | 80 | 25% | 20 |"));
        assertTrue(report.contains("数据完整 | 90 | 20% | 18 |"));
        assertTrue(report.contains("性能风险 | 75 | 15% | 11 |"));
        assertTrue(report.contains("可维护性 | 70 | 15% | 10 |"));
    }

    @Test
    @DisplayName("修改权重 → 报告加权分变化")
    void customWeightsChangeWeightedScore() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getDimensions().setCompliance(50);
        config.getScoring().getDimensions().setCorrectness(50);

        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(100, 80, 0, 0, 0, 0, 0, 0, true);

        String report = formatter.formatReport(task, score, List.of(), config);

        // compliance: 100×50%=50, correctness: 80×50%=40
        assertTrue(report.contains("规范合规 | 100 | 50% | 50 |"));
        assertTrue(report.contains("逻辑正确 | 80 | 50% | 40 |"));
    }

    @Test
    @DisplayName("等级标签从 ReviewConfig.scoreLevels 读取")
    void levelLabelFromConfig() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getScoreLevels().put("B", new ReviewConfig.ScoreLevelConfig(70, "良好", "建议修复 MAJOR 以上"));

        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(80, 80, 80, 80, 80, 1, 2, 0, true);
        score.setScoreLevel("B");

        String report = formatter.formatReport(task, score, List.of(), config);

        assertTrue(report.contains("等级 B（良好）"));
    }

    @Test
    @DisplayName("blockThreshold 从 ReviewConfig 读取（不通过场景）")
    void blockThresholdFromConfig() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().setBlockThreshold(80);

        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(80, 80, 80, 80, 80, 0, 0, 0, false);
        score.setTotalScore(75);

        String report = formatter.formatReport(task, score, List.of(), config);

        assertTrue(report.contains("❌ **评审不通过**（低于 80 分）"));
    }

    @Test
    @DisplayName("通过场景显示正确信息")
    void passedReportShowsPass() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(90, 90, 90, 90, 90, 0, 0, 0, true);

        String report = formatter.formatReport(task, score, List.of(), defaultConfig);

        assertTrue(report.contains("✅ **评审通过**，代码允许合并"));
    }

    @Test
    @DisplayName("包含问题列表时显示详细信息")
    void reportWithIssues() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(80, 80, 80, 80, 80, 1, 2, 1, false);

        AiReviewEngine.ReviewResult.Issue issue = new AiReviewEngine.ReviewResult.Issue();
        issue.setSeverity("CRITICAL");
        issue.setCategory("DATA_SAFETY");
        issue.setFilePath("UserController.java");
        issue.setStartLine(42);
        issue.setDescription("空指针风险");
        issue.setSuggestion("添加 null 检查");
        issue.setRule("外部接口返回值必须判空");

        String report = formatter.formatReport(task, score, List.of(issue), defaultConfig);

        assertTrue(report.contains("CRITICAL - 空指针风险"));
        assertTrue(report.contains("UserController.java:42"));
        assertTrue(report.contains("添加 null 检查"));
        assertTrue(report.contains("外部接口返回值必须判空"));
    }

    @Test
    @DisplayName("问题评论与评分评论分离输出")
    void splitIssueAndScoreReport() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(88, 84, 82, 80, 78, 1, 1, 0, true);

        AiReviewEngine.ReviewResult.Issue issue = new AiReviewEngine.ReviewResult.Issue();
        issue.setSeverity("MAJOR");
        issue.setCategory("CORRECTNESS");
        issue.setFilePath("OrderService.java");
        issue.setStartLine(18);
        issue.setDescription("事务边界缺失");
        issue.setSuggestion("补充事务注解");

        String issueReport = formatter.formatIssueReport(task, List.of(issue), "> ⚠️ 已跳过 1 个超大文件");
        String scoreReport = formatter.formatScoreReport(task, score, defaultConfig);

        assertTrue(issueReport.contains("Argus AI 评审问题清单"));
        assertTrue(issueReport.contains("综合评分计算中"));
        assertTrue(issueReport.contains("事务边界缺失"));
        assertTrue(issueReport.contains("已跳过 1 个超大文件"));

        assertTrue(scoreReport.contains("Argus AI 综合评分"));
        assertTrue(scoreReport.contains("总分"));
        assertFalse(scoreReport.contains("事务边界缺失"));
    }

    // ======================== helpers ========================

    private ReviewTask createTask() {
        ReviewTask task = new ReviewTask();
        task.setProjectName("demo-project");
        task.setMrIid(1L);
        task.setMrTitle("feat: add user API");
        task.setAuthorName("zhangsan");
        task.setSourceBranch("feature/user-api");
        task.setTargetBranch("test");
        task.setFileCount(3);
        task.setAddedLines(120);
        task.setRemovedLines(30);
        return task;
    }

    private ScoreCalculator.ScoreResult createScore(int comp, int corr, int dataS, int perf, int maint,
                                                     int crit, int major, int minor, boolean passed) {
        ScoreCalculator.ScoreResult s = new ScoreCalculator.ScoreResult();
        s.setComplianceScore(comp);
        s.setCorrectnessScore(corr);
        s.setDataSafetyScore(dataS);
        s.setPerformanceScore(perf);
        s.setMaintainabilityScore(maint);
        s.setCriticalCount(crit);
        s.setMajorCount(major);
        s.setMinorCount(minor);
        s.setPassed(passed);
        s.setScoreLevel(passed ? "A" : "D");
        s.setTotalScore(comp); // simplified
        return s;
    }
}
