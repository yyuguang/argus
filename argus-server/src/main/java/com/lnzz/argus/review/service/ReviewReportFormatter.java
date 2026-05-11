package com.lnzz.argus.review.service;

import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * M3-E: 评审报告格式化器
 * <p>将评审结果格式化为 Markdown 测评因素单，权重/等级/threshold 从 ReviewConfig 动态读取。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ReviewReportFormatter {

    /**
     * M3-E01: 格式化完整评审报告（Markdown）
     */
    public String formatReport(ReviewTask task, ScoreCalculator.ScoreResult score,
                               List<AiReviewEngine.ReviewResult.Issue> issues,
                               ReviewConfig config) {

        ReviewConfig.DimensionsConfig dims = config.getScoring().getDimensions();

        StringBuilder sb = new StringBuilder();

        // 标题
        String statusEmoji = score.isPassed() ? "✅" : "❌";
        sb.append("## ").append(statusEmoji).append(" Argus AI 代码评审报告\n\n");

        // 基本信息
        sb.append("| 项 | 值 |\n|---|---|\n");
        sb.append("| 项目 | ").append(task.getProjectName()).append(" |\n");
        sb.append("| MR | #").append(task.getMrIid()).append(" ").append(task.getMrTitle()).append(" |\n");
        sb.append("| 提交者 | ").append(task.getAuthorName()).append(" |\n");
        sb.append("| 分支 | `").append(task.getSourceBranch()).append("` → `").append(task.getTargetBranch()).append("` |\n");
        sb.append("| 变更 | ").append(task.getFileCount()).append(" 文件, +")
                .append(task.getAddedLines() != null ? task.getAddedLines() : 0).append(" -")
                .append(task.getRemovedLines() != null ? task.getRemovedLines() : 0).append(" |\n\n");

        // 评分总览（维度权重从 ReviewConfig 读取）
        sb.append("### 📊 评分总览\n\n");
        sb.append("| 评分维度 | 得分 | 权重 | 加权 |\n|---|---|---|---|\n");
        appendDimensionRow(sb, "规范合规", score.getComplianceScore(), dims.getCompliance());
        appendDimensionRow(sb, "逻辑正确", score.getCorrectnessScore(), dims.getCorrectness());
        appendDimensionRow(sb, "数据完整", score.getDataSafetyScore(), dims.getDataIntegrity());
        appendDimensionRow(sb, "性能风险", score.getPerformanceScore(), dims.getPerformance());
        appendDimensionRow(sb, "可维护性", score.getMaintainabilityScore(), dims.getMaintainability());

        // 等级标签从 ReviewConfig.scoreLevels 读取
        String levelLabel = resolveLevelLabel(score.getScoreLevel(), config);
        sb.append("| **总分** | **").append(score.getTotalScore()).append("/100** | | **等级 ")
                .append(score.getScoreLevel()).append("（").append(levelLabel).append("）** |\n\n");

        // 结论
        int blockThreshold = config.getScoring().getBlockThreshold();
        if (score.isPassed()) {
            sb.append("> ✅ **评审通过**，代码允许合并\n\n");
        } else {
            sb.append("> ❌ **评审不通过**（低于 ").append(blockThreshold).append(" 分），请修复以下问题后重新提交\n\n");
        }

        // 问题统计
        if (issues != null && !issues.isEmpty()) {
            sb.append("### 🔍 问题清单\n\n");
            sb.append("| 🔴致命 | 🟡严重 | 🔵一般 |\n|---|---|---|\n");
            sb.append("| ").append(score.getCriticalCount()).append(" | ")
                    .append(score.getMajorCount()).append(" | ")
                    .append(score.getMinorCount()).append(" |\n\n");

            for (int i = 0; i < issues.size(); i++) {
                AiReviewEngine.ReviewResult.Issue issue = issues.get(i);
                String severityIcon = switch (issue.getSeverity()) {
                    case "CRITICAL" -> "🔴";
                    case "MAJOR" -> "🟡";
                    case "MINOR" -> "🔵";
                    default -> "💡";
                };

                sb.append("#### ").append(i + 1).append(". ").append(severityIcon).append(" ").append(issue.getSeverity());
                sb.append(" - ").append(issue.getDescription()).append("\n\n");
                sb.append("- **位置**: `").append(issue.getFilePath()).append(":").append(issue.getStartLine()).append("`\n");
                sb.append("- **维度**: ").append(issue.getCategory()).append("\n");

                if (issue.getRule() != null) {
                    sb.append("- **规则**: ").append(issue.getRule()).append("\n");
                }
                if (issue.getSuggestion() != null) {
                    sb.append("- **建议**: ").append(issue.getSuggestion()).append("\n");
                }
                if (issue.getCodeSnippet() != null) {
                    sb.append("\n```java\n").append(issue.getCodeSnippet()).append("\n```\n");
                }
                sb.append("\n");
            }
        }

        sb.append("\n---\n*Powered by Argus AI Review Engine*\n");

        return sb.toString();
    }

    /**
     * 格式化问题清单评论。
     *
     * @param task 评审任务
     * @param issues 问题列表
     * @param degradationNote 降级说明
     * @return Markdown 评论
     */
    public String formatIssueReport(ReviewTask task,
                                    List<AiReviewEngine.ReviewResult.Issue> issues,
                                    String degradationNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔍 Argus AI 评审问题清单\n\n");
        appendTaskSummary(sb, task);
        sb.append("> 问题清单已生成，综合评分计算中...\n\n");

        if (issues == null || issues.isEmpty()) {
            sb.append("> ✅ 当前未发现明确问题，正在整理评分结果。\n\n");
        } else {
            appendIssueSection(sb, issues, countBySeverity(issues, "CRITICAL"),
                    countBySeverity(issues, "MAJOR"), countBySeverity(issues, "MINOR"));
        }

        if (degradationNote != null && !degradationNote.isBlank()) {
            sb.append(degradationNote).append("\n");
        }
        sb.append("\n---\n*Powered by Argus AI Review Engine*\n");
        return sb.toString();
    }

    /**
     * 格式化评分结果评论。
     *
     * @param task 评审任务
     * @param score 综合评分
     * @param config 评审配置
     * @return Markdown 评论
     */
    public String formatScoreReport(ReviewTask task,
                                    ScoreCalculator.ScoreResult score,
                                    ReviewConfig config) {
        ReviewConfig.DimensionsConfig dims = config.getScoring().getDimensions();
        StringBuilder sb = new StringBuilder();
        sb.append("## 📊 Argus AI 综合评分\n\n");
        appendTaskSummary(sb, task);
        sb.append("| 评分维度 | 得分 | 权重 | 加权 |\n|---|---|---|---|\n");
        appendDimensionRow(sb, "规范合规", score.getComplianceScore(), dims.getCompliance());
        appendDimensionRow(sb, "逻辑正确", score.getCorrectnessScore(), dims.getCorrectness());
        appendDimensionRow(sb, "数据完整", score.getDataSafetyScore(), dims.getDataIntegrity());
        appendDimensionRow(sb, "性能风险", score.getPerformanceScore(), dims.getPerformance());
        appendDimensionRow(sb, "可维护性", score.getMaintainabilityScore(), dims.getMaintainability());

        String levelLabel = resolveLevelLabel(score.getScoreLevel(), config);
        sb.append("| **总分** | **").append(score.getTotalScore()).append("/100** | | **等级 ")
                .append(score.getScoreLevel()).append("（").append(levelLabel).append("）** |\n\n");

        int blockThreshold = config.getScoring().getBlockThreshold();
        if (score.isPassed()) {
            sb.append("> ✅ **评审通过**，代码允许合并\n");
        } else {
            sb.append("> ❌ **评审不通过**（低于 ").append(blockThreshold).append(" 分），请优先修复已评论问题\n");
        }

        sb.append("\n---\n*Powered by Argus AI Review Engine*\n");
        return sb.toString();
    }

    // ======================== 内部 ========================

    private void appendTaskSummary(StringBuilder sb, ReviewTask task) {
        sb.append("| 项 | 值 |\n|---|---|\n");
        sb.append("| 项目 | ").append(task.getProjectName()).append(" |\n");
        sb.append("| MR | #").append(task.getMrIid()).append(" ").append(task.getMrTitle()).append(" |\n");
        sb.append("| 提交者 | ").append(task.getAuthorName()).append(" |\n");
        sb.append("| 分支 | `").append(task.getSourceBranch()).append("` → `").append(task.getTargetBranch()).append("` |\n");
        sb.append("| 变更 | ").append(task.getFileCount()).append(" 文件, +")
                .append(task.getAddedLines() != null ? task.getAddedLines() : 0).append(" -")
                .append(task.getRemovedLines() != null ? task.getRemovedLines() : 0).append(" |\n\n");
    }

    private void appendIssueSection(StringBuilder sb,
                                    List<AiReviewEngine.ReviewResult.Issue> issues,
                                    int criticalCount,
                                    int majorCount,
                                    int minorCount) {
        sb.append("### 🔍 问题清单\n\n");
        sb.append("| 🔴致命 | 🟡严重 | 🔵一般 |\n|---|---|---|\n");
        sb.append("| ").append(criticalCount).append(" | ")
                .append(majorCount).append(" | ")
                .append(minorCount).append(" |\n\n");

        for (int i = 0; i < issues.size(); i++) {
            AiReviewEngine.ReviewResult.Issue issue = issues.get(i);
            String severityIcon = switch (issue.getSeverity()) {
                case "CRITICAL" -> "🔴";
                case "MAJOR" -> "🟡";
                case "MINOR" -> "🔵";
                default -> "💡";
            };

            sb.append("#### ").append(i + 1).append(". ").append(severityIcon).append(" ").append(issue.getSeverity());
            sb.append(" - ").append(issue.getDescription()).append("\n\n");
            sb.append("- **位置**: `").append(issue.getFilePath()).append(":").append(issue.getStartLine()).append("`\n");
            sb.append("- **维度**: ").append(issue.getCategory()).append("\n");

            if (issue.getRule() != null) {
                sb.append("- **规则**: ").append(issue.getRule()).append("\n");
            }
            if (issue.getSuggestion() != null) {
                sb.append("- **建议**: ").append(issue.getSuggestion()).append("\n");
            }
            if (issue.getCodeSnippet() != null) {
                sb.append("\n```java\n").append(issue.getCodeSnippet()).append("\n```\n");
            }
            sb.append("\n");
        }
    }

    private void appendDimensionRow(StringBuilder sb, String name, int score, int weight) {
        sb.append("| ").append(name).append(" | ").append(score)
                .append(" | ").append(weight).append("% | ")
                .append(score * weight / 100).append(" |\n");
    }

    private int countBySeverity(List<AiReviewEngine.ReviewResult.Issue> issues, String severity) {
        return (int) issues.stream()
                .filter(issue -> severity.equals(issue.getSeverity()))
                .count();
    }

    private String resolveLevelLabel(String level, ReviewConfig config) {
        ReviewConfig.ScoreLevelConfig slc = config.getScoring().getScoreLevels().get(level);
        return slc != null ? slc.getLabel() : level;
    }
}
