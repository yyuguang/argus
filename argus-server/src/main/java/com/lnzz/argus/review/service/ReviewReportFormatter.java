package com.lnzz.argus.review.service;

import com.lnzz.argus.review.ai.AiReviewEngine;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.entity.ReviewTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * M3-E: 评审报告格式化器
 * <p>将评审结果格式化为 Markdown 测评因素单，用于回写 GitLab MR 评论</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ReviewReportFormatter {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * M3-E01: 格式化完整评审报告（Markdown）
     */
    public String formatReport(ReviewTask task, ScoreCalculator.ScoreResult score,
                               List<AiReviewEngine.ReviewResult.Issue> issues) {

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

        // 评分总览
        sb.append("### 📊 评分总览\n\n");
        sb.append("| 评分维度 | 得分 | 权重 | 加权 |\n|---|---|---|---|\n");
        sb.append("| 规范合规 | ").append(score.getComplianceScore()).append(" | 30% | ").append(score.getComplianceScore() * 30 / 100).append(" |\n");
        sb.append("| 逻辑正确 | ").append(score.getCorrectnessScore()).append(" | 25% | ").append(score.getCorrectnessScore() * 25 / 100).append(" |\n");
        sb.append("| 数据完整 | ").append(score.getDataSafetyScore()).append(" | 20% | ").append(score.getDataSafetyScore() * 20 / 100).append(" |\n");
        sb.append("| 性能风险 | ").append(score.getPerformanceScore()).append(" | 15% | ").append(score.getPerformanceScore() * 15 / 100).append(" |\n");
        sb.append("| 可维护性 | ").append(score.getMaintainabilityScore()).append(" | 10% | ").append(score.getMaintainabilityScore() * 10 / 100).append(" |\n");
        sb.append("| **总分** | **").append(score.getTotalScore()).append("/100** | | **等级 ").append(score.getScoreLevel()).append("** |\n\n");

        // 结论
        if (score.isPassed()) {
            sb.append("> ✅ **评审通过**，代码允许合并\n\n");
        } else {
            sb.append("> ❌ **评审不通过**（低于 60 分），请修复以下问题后重新提交\n\n");
        }

        // 问题统计
        if (issues != null && !issues.isEmpty()) {
            sb.append("### 🔍 问题清单\n\n");
            sb.append("| 🔴致命 | 🟡严重 | 🔵一般 |\n|---|---|---|\n");
            sb.append("| ").append(score.getCriticalCount()).append(" | ")
                    .append(score.getMajorCount()).append(" | ")
                    .append(score.getMinorCount()).append(" |\n\n");

            // 详细问题
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
}
