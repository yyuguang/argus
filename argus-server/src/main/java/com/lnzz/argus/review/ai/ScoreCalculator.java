package com.lnzz.argus.review.ai;

import com.lnzz.argus.config.ReviewProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;

/**
 * M3-D: 评分计算器
 * <p>基于五维度加权评分 + 扣分规则计算最终分数</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final ReviewProperties reviewProperties;

    /**
     * M3-D06: 计算文件级别的最终评分
     *
     * @param result AI 评审结果
     * @return 评分结果
     */
    public ScoreResult calculateScore(AiReviewEngine.ReviewResult result) {
        ScoreResult score = new ScoreResult();

        // 使用 AI 直接给出的维度评分
        score.setComplianceScore(result.getComplianceScore());
        score.setCorrectnessScore(result.getCorrectnessScore());
        score.setDataSafetyScore(result.getDataSafetyScore());
        score.setPerformanceScore(result.getPerformanceScore());
        score.setMaintainabilityScore(result.getMaintainabilityScore());

        // 问题统计
        List<AiReviewEngine.ReviewResult.Issue> issues = result.getIssues();
        if (issues != null) {
            score.setCriticalCount((int) issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count());
            score.setMajorCount((int) issues.stream().filter(i -> "MAJOR".equals(i.getSeverity())).count());
            score.setMinorCount((int) issues.stream().filter(i -> "MINOR".equals(i.getSeverity())).count());

            applyIssueDeductions(score, issues);
        }

        // 加权计算总分
        recalculateTotal(score);

        // 评分等级
        score.setScoreLevel(calculateLevel(score.getTotalScore()));

        // 是否通过
        score.setPassed(score.getTotalScore() >= reviewProperties.getBlockThreshold());

        log.info("评分计算完成: total={}, level={}, passed={}, critical={}, major={}, minor={}",
                score.getTotalScore(), score.getScoreLevel(), score.isPassed(),
                score.getCriticalCount(), score.getMajorCount(), score.getMinorCount());

        return score;
    }

    /**
     * M3-D07: 合并多个文件的评分（取最低分）
     */
    public ScoreResult mergeScores(List<ScoreResult> scores) {
        if (scores == null || scores.isEmpty()) {
            ScoreResult empty = new ScoreResult();
            empty.setTotalScore(100);
            empty.setScoreLevel("A");
            empty.setPassed(true);
            return empty;
        }

        ScoreResult merged = new ScoreResult();
        int totalCompliance = 0, totalCorrectness = 0, totalDataSafety = 0;
        int totalPerformance = 0, totalMaintainability = 0;
        int criticalCount = 0, majorCount = 0, minorCount = 0;

        for (ScoreResult s : scores) {
            totalCompliance += s.getComplianceScore();
            totalCorrectness += s.getCorrectnessScore();
            totalDataSafety += s.getDataSafetyScore();
            totalPerformance += s.getPerformanceScore();
            totalMaintainability += s.getMaintainabilityScore();
            criticalCount += s.getCriticalCount();
            majorCount += s.getMajorCount();
            minorCount += s.getMinorCount();
        }

        int size = scores.size();
        merged.setComplianceScore(totalCompliance / size);
        merged.setCorrectnessScore(totalCorrectness / size);
        merged.setDataSafetyScore(totalDataSafety / size);
        merged.setPerformanceScore(totalPerformance / size);
        merged.setMaintainabilityScore(totalMaintainability / size);
        merged.setCriticalCount(criticalCount);
        merged.setMajorCount(majorCount);
        merged.setMinorCount(minorCount);

        recalculateTotal(merged);
        merged.setScoreLevel(calculateLevel(merged.getTotalScore()));
        merged.setPassed(merged.getTotalScore() >= reviewProperties.getBlockThreshold());

        return merged;
    }

    private void applyIssueDeductions(ScoreResult score, List<AiReviewEngine.ReviewResult.Issue> issues) {
        int complianceDeduction = 0;
        int correctnessDeduction = 0;
        int dataSafetyDeduction = 0;
        int performanceDeduction = 0;
        int maintainabilityDeduction = 0;

        for (AiReviewEngine.ReviewResult.Issue issue : issues) {
            int deduction = deductionBySeverity(issue.getSeverity());
            String category = issue.getCategory() == null ? "" : issue.getCategory().toUpperCase(Locale.ROOT);

            switch (category) {
                case "COMPLIANCE" -> complianceDeduction += deduction;
                case "CORRECTNESS" -> correctnessDeduction += deduction;
                case "DATA_SAFETY" -> dataSafetyDeduction += deduction;
                case "PERFORMANCE" -> performanceDeduction += deduction;
                case "MAINTAINABILITY" -> maintainabilityDeduction += deduction;
                default -> correctnessDeduction += deduction;
            }
        }

        score.setComplianceScore(Math.min(score.getComplianceScore(), Math.max(0, 100 - complianceDeduction)));
        score.setCorrectnessScore(Math.min(score.getCorrectnessScore(), Math.max(0, 100 - correctnessDeduction)));
        score.setDataSafetyScore(Math.min(score.getDataSafetyScore(), Math.max(0, 100 - dataSafetyDeduction)));
        score.setPerformanceScore(Math.min(score.getPerformanceScore(), Math.max(0, 100 - performanceDeduction)));
        score.setMaintainabilityScore(Math.min(score.getMaintainabilityScore(), Math.max(0, 100 - maintainabilityDeduction)));
    }

    private int deductionBySeverity(String severity) {
        if ("CRITICAL".equals(severity)) {
            return reviewProperties.getScoring().getCriticalDeduction();
        }
        if ("MAJOR".equals(severity)) {
            return reviewProperties.getScoring().getMajorDeduction();
        }
        if ("MINOR".equals(severity)) {
            return reviewProperties.getScoring().getMinorDeduction();
        }
        return 0;
    }

    private void recalculateTotal(ScoreResult score) {
        ReviewProperties.Dimensions d = reviewProperties.getDimensions();
        double totalScore = (score.getComplianceScore() * d.getCompliance()
                + score.getCorrectnessScore() * d.getCorrectness()
                + score.getDataSafetyScore() * d.getDataIntegrity()
                + score.getPerformanceScore() * d.getPerformance()
                + score.getMaintainabilityScore() * d.getMaintainability()) / 100.0;
        score.setTotalScore((int) Math.round(totalScore));
    }

    private String calculateLevel(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    @Data
    public static class ScoreResult {
        private int totalScore;
        private String scoreLevel;
        private boolean passed;
        private int complianceScore;
        private int correctnessScore;
        private int dataSafetyScore;
        private int performanceScore;
        private int maintainabilityScore;
        private int criticalCount;
        private int majorCount;
        private int minorCount;
    }
}
