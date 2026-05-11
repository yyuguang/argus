package com.lnzz.argus.review.ai;

import com.lnzz.argus.review.config.ReviewConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * M3-D: 评分计算器
 * <p>基于 AI 五维度加权分 + 规则基分，通过 {@link ReviewConfig} 驱动所有阈值和权重。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ScoreCalculator {

    /**
     * 计算文件级别最终评分。
     *
     * @param result AI 评审结果（含五维度分 + Issue 列表）
     * @param config 仓库级评审配置
     * @return 评分结果
     */
    public ScoreResult calculateScore(AiReviewEngine.ReviewResult result, ReviewConfig config) {
        ReviewConfig.ScoringConfig sc = config.getScoring();
        ScoreResult score = new ScoreResult();

        // 五维度分直接取 AI 返回，不再叠加扣分
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
        }

        // AI 维度加权总分
        ReviewConfig.DimensionsConfig dims = sc.getDimensions();
        double aiWeightedScore = (score.getComplianceScore() * dims.getCompliance()
                + score.getCorrectnessScore() * dims.getCorrectness()
                + score.getDataSafetyScore() * dims.getDataIntegrity()
                + score.getPerformanceScore() * dims.getPerformance()
                + score.getMaintainabilityScore() * dims.getMaintainability()) / 100.0;

        // 规则基分 = 100 - Σ(issue × deduction)，不低于 0
        int totalDeduction = computeRuleDeduction(issues, sc);
        double ruleScore = Math.max(0, 100 - totalDeduction);

        // 最终分 = AI加权分 × aiWeight + 规则基分 × ruleWeight
        double finalScore = aiWeightedScore * sc.getAiWeight() + ruleScore * sc.getRuleWeight();
        score.setTotalScore((int) Math.round(finalScore));

        // 等级映射 + 通过判定
        score.setScoreLevel(resolveLevel(score.getTotalScore(), sc));
        score.setPassed(score.getTotalScore() >= sc.getBlockThreshold());

        log.info("评分计算完成: total={}, level={}, passed={}, aiWeighted={}, ruleScore={}, critical={}, major={}, minor={}",
                score.getTotalScore(), score.getScoreLevel(), score.isPassed(),
                (int) aiWeightedScore, (int) ruleScore,
                score.getCriticalCount(), score.getMajorCount(), score.getMinorCount());

        return score;
    }

    /**
     * 合并多个文件评分（维度分取平均，Issue 汇总）。
     */
    public ScoreResult mergeScores(List<ScoreResult> scores, ReviewConfig config) {
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

        ReviewConfig.ScoringConfig sc = config.getScoring();
        ReviewConfig.DimensionsConfig dims = sc.getDimensions();
        double aiWeightedScore = (merged.getComplianceScore() * dims.getCompliance()
                + merged.getCorrectnessScore() * dims.getCorrectness()
                + merged.getDataSafetyScore() * dims.getDataIntegrity()
                + merged.getPerformanceScore() * dims.getPerformance()
                + merged.getMaintainabilityScore() * dims.getMaintainability()) / 100.0;

        double finalScore = aiWeightedScore * sc.getAiWeight() + 100.0 * sc.getRuleWeight();
        merged.setTotalScore((int) Math.round(finalScore));
        merged.setScoreLevel(resolveLevel(merged.getTotalScore(), sc));
        merged.setPassed(merged.getTotalScore() >= sc.getBlockThreshold());

        return merged;
    }

    // ======================== 内部 ========================

    /** 统计所有 Issue 的扣分总额 */
    private int computeRuleDeduction(List<AiReviewEngine.ReviewResult.Issue> issues, ReviewConfig.ScoringConfig sc) {
        if (issues == null || issues.isEmpty()) return 0;
        int total = 0;
        for (AiReviewEngine.ReviewResult.Issue issue : issues) {
            ReviewConfig.SeverityDefConfig def = sc.getSeverityDefinitions().get(issue.getSeverity());
            if (def != null) {
                total += def.getDeduction();
            }
        }
        return total;
    }

    /** 按 ReviewConfig.scoreLevels 查找等级 */
    private String resolveLevel(int totalScore, ReviewConfig.ScoringConfig sc) {
        return sc.getScoreLevels().entrySet().stream()
                .filter(e -> totalScore >= e.getValue().getMinScore())
                .max(Comparator.comparingInt(e -> e.getValue().getMinScore()))
                .map(java.util.Map.Entry::getKey)
                .orElse("F");
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
