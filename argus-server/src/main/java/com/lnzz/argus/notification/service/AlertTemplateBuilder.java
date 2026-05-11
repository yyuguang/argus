package com.lnzz.argus.notification.service;

import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.parse.SeverityLevel;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * M7-A02: 告警通知模板构建器
 * <p>支持基础告警（brief）和详细告警（detailed）两种模板</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class AlertTemplateBuilder {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    /** 企微 Markdown 消息上限，预留 buffer */
    private static final int WECHAT_MAX_CHARS = 3800;

    /**
     * 基础告警模板 —— 简要通知
     */
    public String buildBriefAlert(ErrorEvent event, ErrorAnalysis analysis) {
        String severityIcon = severityIcon(event.getSeverity());
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(severityIcon).append(" 错误告警\n\n");
        sb.append("> **应用**: ").append(esc(event.getAppName())).append("\n");
        sb.append("> **环境**: ").append(esc(event.getEnvironment())).append("\n");
        sb.append("> **严重度**: ").append(event.getSeverity()).append("\n");
        sb.append("> **类型**: ").append(esc(event.getErrorType())).append("\n");
        if (event.getOccurredAt() != null) {
            sb.append("> **时间**: ").append(event.getOccurredAt().format(TIME_FMT)).append("\n");
        }
        if (event.getOccurrenceCount() != null && event.getOccurrenceCount() > 1) {
            sb.append("> **累计**: ").append(event.getOccurrenceCount()).append(" 次\n");
        }
        sb.append("\n");
        sb.append("**错误信息**: ").append(truncate(event.getErrorMessage(), 200)).append("\n");

        if (analysis != null && analysis.getRootCause() != null) {
            sb.append("\n**AI 根因**: ").append(truncate(analysis.getRootCause(), 150)).append("\n");
        }

        if (event.getInterfaceRef() != null) {
            sb.append("\n**接口**: `").append(esc(event.getInterfaceRef())).append("`\n");
        }
        return sb.toString();
    }

    /**
     * 详细告警模板 —— 含 AI 分析完整结果，自动适配企微长度限制
     */
    public String buildDetailedAlert(ErrorEvent event, ErrorAnalysis analysis) {
        String brief = buildBriefAlert(event, analysis);
        StringBuilder detail = new StringBuilder();
        detail.append("\n---\n\n");

        if (analysis != null) {
            if (analysis.getRootCause() != null) {
                detail.append("### 根因分析\n\n").append(analysis.getRootCause()).append("\n\n");
            }
            if (analysis.getTechnicalDetail() != null) {
                detail.append("### 技术细节\n\n").append(analysis.getTechnicalDetail()).append("\n\n");
            }
            if (analysis.getImpactScope() != null) {
                detail.append("### 影响范围\n\n").append(analysis.getImpactScope()).append("\n\n");
            }
            if (analysis.getFixDescription() != null) {
                detail.append("### 修复建议\n\n").append(analysis.getFixDescription()).append("\n\n");
                if (analysis.getFixFilePath() != null) {
                    detail.append("- 文件: `").append(esc(analysis.getFixFilePath())).append("`\n");
                }
                if (analysis.getFixLineRange() != null) {
                    detail.append("- 行号: ").append(analysis.getFixLineRange()).append("\n");
                }
                if (analysis.getEstimatedEffort() != null) {
                    detail.append("- 预估: ").append(analysis.getEstimatedEffort()).append("\n");
                }
                detail.append("\n");
            }
            if (analysis.getFixCodeExample() != null && !analysis.getFixCodeExample().isEmpty()) {
                detail.append("### 修复示例\n\n```\n").append(analysis.getFixCodeExample()).append("\n```\n\n");
            }
            if (analysis.getPreventionAdvice() != null) {
                detail.append("### 预防建议\n\n").append(analysis.getPreventionAdvice()).append("\n\n");
            }
            if (analysis.getConfidence() != null) {
                detail.append("> AI 置信度: ").append(String.format("%.0f%%", analysis.getConfidence().doubleValue() * 100)).append("\n");
            }
        }

        if (event.getTraceId() != null) {
            detail.append("\n> TraceID: `").append(esc(event.getTraceId())).append("`\n");
        }

        return fitToWechatLimit(brief, detail.toString());
    }

    /**
     * 智能适配企微长度限制：优先保留根因分析，逐段裁剪次要内容
     */
    private String fitToWechatLimit(String brief, String detail) {
        String full = brief + detail;
        if (full.length() <= WECHAT_MAX_CHARS) {
            return full;
        }

        // 按优先级拆分 detail 段落（越靠后越先被裁剪）
        String[] sections = detail.split("\n\n(?=###|>)");
        StringBuilder result = new StringBuilder(brief);
        int budget = WECHAT_MAX_CHARS - brief.length() - 80; // 留出截断提示空间

        if (budget <= 0) {
            return brief + "\n\n> ⚠️ 内容过长，完整分析请查看系统后台";
        }

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;

            // 修复示例代码块最占空间，限制 800 字符
            if (trimmed.startsWith("### 修复示例")) {
                trimmed = limitCodeBlock(trimmed, 600);
            }
            // 技术细节限制 500 字符
            else if (trimmed.startsWith("### 技术细节")) {
                trimmed = truncateSection(trimmed, 500);
            }
            // 预防建议限制 400 字符
            else if (trimmed.startsWith("### 预防建议")) {
                trimmed = truncateSection(trimmed, 400);
            }
            // 影响范围限制 300 字符
            else if (trimmed.startsWith("### 影响范围")) {
                trimmed = truncateSection(trimmed, 300);
            }
            // 根因分析保留尽可能多
            else if (trimmed.startsWith("### 根因分析")) {
                trimmed = truncateSection(trimmed, 800);
            }

            if (result.length() + trimmed.length() + 2 <= WECHAT_MAX_CHARS - 30) {
                result.append("\n\n").append(trimmed);
            } else {
                int remaining = WECHAT_MAX_CHARS - result.length() - 50;
                if (remaining > 100 && !trimmed.startsWith("### 修复示例")) {
                    result.append("\n\n").append(trimmed, 0, Math.min(remaining, trimmed.length()));
                    result.append("...");
                }
                result.append("\n\n> ⚠️ 内容过长已截断，完整分析请查看系统后台");
                break;
            }
        }

        return result.toString();
    }

    private String limitCodeBlock(String section, int maxLen) {
        String prefix = "### 修复示例\n\n```\n";
        String suffix = "\n```";
        String body = section.substring(prefix.length(), section.length() - suffix.length());
        if (body.length() <= maxLen) return section;
        return prefix + body.substring(0, maxLen) + "\n...(已截断)" + suffix;
    }

    private String truncateSection(String section, int maxLen) {
        if (section.length() <= maxLen) return section;
        String header = section.substring(0, section.indexOf('\n'));
        String body = section.substring(header.length()).trim();
        return header + "\n\n" + truncate(body, maxLen);
    }

    /**
     * 评审通知模板（保留原逻辑）
     */
    public String buildReviewAlert(String projectName, Long mrIid, String mrTitle,
                                    String authorName, String sourceBranch, String targetBranch,
                                    int totalScore, String scoreLevel, boolean passed,
                                    int criticalCount, int majorCount, int minorCount,
                                    String mrUrl) {
        String emoji = passed ? "✅" : "❌";
        String status = passed ? "通过" : "不通过（阻止合并）";

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(emoji).append(" AI 代码评审通知\n\n");
        sb.append("> **项目**: ").append(esc(projectName)).append("\n");
        sb.append("> **MR**: #").append(mrIid).append(" ").append(esc(mrTitle)).append("\n");
        sb.append("> **提交者**: ").append(esc(authorName)).append("\n");
        sb.append("> **分支**: `").append(esc(sourceBranch)).append("` → `").append(esc(targetBranch)).append("`\n\n");
        sb.append("**评分**: ").append(totalScore).append("/100（等级 ").append(scoreLevel).append("）\n");
        sb.append("**结果**: ").append(status).append("\n\n");

        if (criticalCount > 0 || majorCount > 0) {
            sb.append("**问题统计**: 🔴致命 ").append(criticalCount)
                    .append(" / 🟡严重 ").append(majorCount)
                    .append(" / 🔵一般 ").append(minorCount).append("\n\n");
        }
        if (mrUrl != null) {
            sb.append("[查看详情](").append(mrUrl).append(")\n");
        }
        return sb.toString();
    }

    // ======================== 辅助方法 ========================

    private String severityIcon(String severity) {
        SeverityLevel level = SeverityLevel.fromCode(severity);
        return switch (level) {
            case P0 -> "🔴";
            case P1 -> "🟠";
            case P2 -> "🟡";
            case P3 -> "🔵";
        };
    }

    private String esc(String s) {
        if (s == null) return "-";
        return s.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(">", "\\>");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "-";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
