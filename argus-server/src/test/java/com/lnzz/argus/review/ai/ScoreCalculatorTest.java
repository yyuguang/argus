package com.lnzz.argus.review.ai;

import com.lnzz.argus.review.config.ReviewConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScoreCalculator - 评分公式")
class ScoreCalculatorTest {

    private ScoreCalculator calculator;
    private ReviewConfig defaultConfig;

    @BeforeEach
    void setUp() {
        calculator = new ScoreCalculator();
        defaultConfig = ReviewConfig.defaults();
    }

    // ==================== 新公式验证 ====================

    @Test
    @DisplayName("AI=82 + 1CRITICAL+2MAJOR → 期望 73（B级）")
    void formulaWithCriticalAndMajorIssues() {
        AiReviewEngine.ReviewResult result = buildResult(82, List.of(
                issue("CRITICAL", "COMPLIANCE"),
                issue("MAJOR", "CORRECTNESS"),
                issue("MAJOR", "PERFORMANCE")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        // AI加权=82, ruleWeight=0.4, aiWeight=0.6
        // ruleDeduction: 1×20 + 2×10 = 40, ruleScore = 60
        // final = 82×0.6 + 60×0.4 = 49.2+24 = 73.2 → 73
        assertEquals(73, score.getTotalScore());
        assertEquals("B", score.getScoreLevel());
        assertTrue(score.isPassed()); // 73 >= 60
        assertEquals(1, score.getCriticalCount());
        assertEquals(2, score.getMajorCount());
        assertEquals(0, score.getMinorCount());
    }

    @Test
    @DisplayName("AI=82 + 3SUGGESTION → 期望 89（A级）")
    void formulaWithOnlySuggestions() {
        AiReviewEngine.ReviewResult result = buildResult(82, List.of(
                issue("SUGGESTION", "MAINTAINABILITY"),
                issue("SUGGESTION", "MAINTAINABILITY"),
                issue("SUGGESTION", "MAINTAINABILITY")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        // ruleDeduction: 3×0 = 0, ruleScore = 100
        // final = 82×0.6 + 100×0.4 = 49.2+40 = 89.2 → 89
        assertEquals(89, score.getTotalScore());
        assertEquals("A", score.getScoreLevel());
    }

    @Test
    @DisplayName("无 Issue 时满分 100（A级）")
    void perfectScoreWithNoIssues() {
        AiReviewEngine.ReviewResult result = buildResult(100, List.of());

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        assertEquals(100, score.getTotalScore());
        assertEquals("A", score.getScoreLevel());
        assertTrue(score.isPassed());
    }

    // ==================== 配置化驱动 ====================

    @Test
    @DisplayName("自定义 blockThreshold=90 → AI=90 满分仅通过")
    void customBlockThreshold() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().setBlockThreshold(90);

        AiReviewEngine.ReviewResult result = buildResult(90, List.of(
                issue("MAJOR", "CORRECTNESS")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, config);

        // ruleDeduction: 10, ruleScore=90, final=90×0.6+90×0.4=90
        assertEquals(90, score.getTotalScore());
        assertTrue(score.isPassed()); // 90 >= 90
    }

    @Test
    @DisplayName("自定义权重 aiWeight=0.8 ruleWeight=0.2 → 公式生效")
    void customWeights() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().setAiWeight(0.8);
        config.getScoring().setRuleWeight(0.2);

        AiReviewEngine.ReviewResult result = buildResult(80, List.of(
                issue("CRITICAL", "COMPLIANCE")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, config);

        // ruleDeduction: 20, ruleScore=80
        // final = 80×0.8 + 80×0.2 = 80
        assertEquals(80, score.getTotalScore());
    }

    @Test
    @DisplayName("自定义严重度扣分值")
    void customSeverityDeductions() {
        ReviewConfig config = ReviewConfig.defaults();
        // 修改 CRITICAL 扣分：20 → 30
        config.getScoring().getSeverityDefinitions().get("CRITICAL").setDeduction(30);

        AiReviewEngine.ReviewResult result = buildResult(90, List.of(
                issue("CRITICAL", "DATA_SAFETY")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, config);

        // ruleDeduction: 30, ruleScore=70
        // final = 90×0.6 + 70×0.4 = 54+28 = 82
        assertEquals(82, score.getTotalScore());
    }

    @Test
    @DisplayName("自定义评分等级映射")
    void customScoreLevels() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getScoreLevels().clear();
        config.getScoring().getScoreLevels().put("S", new ReviewConfig.ScoreLevelConfig(90, "卓越", "直接合并"));
        config.getScoring().getScoreLevels().put("P", new ReviewConfig.ScoreLevelConfig(0, "不合格", "阻止合并"));

        AiReviewEngine.ReviewResult result = buildResult(95, List.of());

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, config);

        assertEquals("S", score.getScoreLevel());
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("大量 CRITICAL Issue → 规则基分归零")
    void manyCriticalsFloorAtZero() {
        List<AiReviewEngine.ReviewResult.Issue> issues = List.of(
                issue("CRITICAL", "COMPLIANCE"),
                issue("CRITICAL", "CORRECTNESS"),
                issue("CRITICAL", "DATA_SAFETY"),
                issue("CRITICAL", "PERFORMANCE"),
                issue("CRITICAL", "MAINTAINABILITY"),
                issue("CRITICAL", "COMPLIANCE")
        );

        AiReviewEngine.ReviewResult result = buildResult(50, issues);

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        // 6×20=120, ruleScore = max(0, 100-120) = 0
        // final = 50×0.6 + 0×0.4 = 30
        assertEquals(30, score.getTotalScore());
        assertEquals("F", score.getScoreLevel());
        assertFalse(score.isPassed());
    }

    @Test
    @DisplayName("null issues → 无扣分")
    void nullIssuesHandled() {
        AiReviewEngine.ReviewResult result = buildResult(90, null);

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        // ruleDeduction=0, final=90×0.6+100×0.4=54+40=94
        assertEquals(94, score.getTotalScore());
        assertEquals(0, score.getCriticalCount());
    }

    @Test
    @DisplayName("未知严重度不扣分")
    void unknownSeverityNoDeduction() {
        AiReviewEngine.ReviewResult result = buildResult(88, List.of(
                issue("UNKNOWN_LEVEL", "MAINTAINABILITY")
        ));

        ScoreCalculator.ScoreResult score = calculator.calculateScore(result, defaultConfig);

        // ruleDeduction: 0, final = 88×0.6 + 100×0.4 = 52.8+40 = 92.8 → 93
        assertEquals(93, score.getTotalScore());
    }

    // ==================== mergeScores ====================

    @Test
    @DisplayName("mergeScores 多文件取平均 + 汇总 Issue")
    void mergeScoresAveragesAndAggregates() {
        ScoreCalculator.ScoreResult s1 = makeScoreResult(80, 90, 70, 85, 75, 1, 2, 0);
        ScoreCalculator.ScoreResult s2 = makeScoreResult(90, 80, 80, 75, 85, 0, 1, 3);

        ScoreCalculator.ScoreResult merged = calculator.mergeScores(List.of(s1, s2), defaultConfig);

        // 维度分平均
        assertEquals(85, merged.getComplianceScore());
        assertEquals(85, merged.getCorrectnessScore());
        assertEquals(75, merged.getDataSafetyScore());
        assertEquals(80, merged.getPerformanceScore());
        assertEquals(80, merged.getMaintainabilityScore());
        // Issue 汇总
        assertEquals(1, merged.getCriticalCount());
        assertEquals(3, merged.getMajorCount());
        assertEquals(3, merged.getMinorCount());
        // merge 走 100 规则基分
        assertTrue(merged.getTotalScore() > 0);
    }

    @Test
    @DisplayName("mergeScores 空列表返回满分")
    void mergeScoresEmptyList() {
        ScoreCalculator.ScoreResult merged = calculator.mergeScores(List.of(), defaultConfig);
        assertEquals(100, merged.getTotalScore());
        assertEquals("A", merged.getScoreLevel());
        assertTrue(merged.isPassed());
    }

    @Test
    @DisplayName("mergeScores null 列表返回满分")
    void mergeScoresNullList() {
        ScoreCalculator.ScoreResult merged = calculator.mergeScores(null, defaultConfig);
        assertEquals(100, merged.getTotalScore());
    }

    // ======================== helpers ========================

    private AiReviewEngine.ReviewResult buildResult(int baseDimensionScore,
                                                     List<AiReviewEngine.ReviewResult.Issue> issues) {
        AiReviewEngine.ReviewResult result = new AiReviewEngine.ReviewResult();
        result.setComplianceScore(baseDimensionScore);
        result.setCorrectnessScore(baseDimensionScore);
        result.setDataSafetyScore(baseDimensionScore);
        result.setPerformanceScore(baseDimensionScore);
        result.setMaintainabilityScore(baseDimensionScore);
        result.setIssues(issues);
        return result;
    }

    private AiReviewEngine.ReviewResult.Issue issue(String severity, String category) {
        AiReviewEngine.ReviewResult.Issue i = new AiReviewEngine.ReviewResult.Issue();
        i.setSeverity(severity);
        i.setCategory(category);
        i.setDescription("测试问题");
        return i;
    }

    private ScoreCalculator.ScoreResult makeScoreResult(int comp, int corr, int dataS,
                                                         int perf, int maint,
                                                         int crit, int major, int minor) {
        ScoreCalculator.ScoreResult s = new ScoreCalculator.ScoreResult();
        s.setComplianceScore(comp);
        s.setCorrectnessScore(corr);
        s.setDataSafetyScore(dataS);
        s.setPerformanceScore(perf);
        s.setMaintainabilityScore(maint);
        s.setCriticalCount(crit);
        s.setMajorCount(major);
        s.setMinorCount(minor);
        return s;
    }
}
