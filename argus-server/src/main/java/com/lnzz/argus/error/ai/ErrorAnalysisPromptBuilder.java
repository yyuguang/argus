package com.lnzz.argus.error.ai;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.service.SourceCodeLocator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * M5-B01: 错误分析 Prompt 组装器
 * <p>将 ErrorEvent + 源码 + 历史案例组合为 AI 分析 Prompt</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorAnalysisPromptBuilder {
    private final RulePromptService rulePromptService;

    /**
     * B01: 组装完整分析 Prompt
     *
     * @param event       错误事件
     * @param location    源码定位结果
     * @param historyCases 历史相似案例（B02）
     * @return 完整 Prompt 文本
     */
    public String buildAnalysisPrompt(ErrorEvent event,
                                      SourceCodeLocator.SourceLocation location,
                                      List<ErrorAnalysis> historyCases,
                                      ReviewConfig reviewConfig,
                                      Long scmConfigId) {
        String prompt = rulePromptService.getErrorAnalysisPromptSkeleton(scmConfigId)
                .replace("{{errorInfoTable}}", buildErrorInfoTable(event))
                .replace("{{errorMessageBlock}}", buildCodeBlock(event.getErrorMessage(), 2000))
                .replace("{{stackTraceSection}}", buildStackTraceSection(event))
                .replace("{{contextLogsSection}}", buildContextLogsSection(event))
                .replace("{{sourceCodeSection}}", buildSourceCodeSection(location))
                .replace("{{historyCasesSection}}", buildHistoryCasesSection(historyCases));
        log.debug("错误分析 Prompt 已构建: length={}, hasSource={}, hasHistory={}",
                prompt.length(), location != null && location.found(), historyCases != null && !historyCases.isEmpty());
        return prompt;
    }

    private void appendRow(StringBuilder sb, String key, String value) {
        String v = value != null ? value.replace("|", "\\|").replace("\n", " ") : "-";
        sb.append("| ").append(key).append(" | ").append(v).append(" |\n");
    }

    private String buildErrorInfoTable(ErrorEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "应用", event.getAppName());
        appendRow(sb, "环境", event.getEnvironment());
        appendRow(sb, "错误类型", event.getErrorType());
        appendRow(sb, "规则严重度", event.getSeverity());
        appendRow(sb, "发生时间", String.valueOf(event.getOccurredAt()));
        appendRow(sb, "发生次数", String.valueOf(event.getOccurrenceCount()));
        appendRow(sb, "类名", event.getClassName());
        appendRow(sb, "方法名", event.getMethodName());
        appendRow(sb, "行号", event.getLineNumber() != null ? String.valueOf(event.getLineNumber()) : "-");
        appendRow(sb, "接口引用", event.getInterfaceRef());
        appendRow(sb, "业务主键", event.getBusinessKey());
        appendRow(sb, "TraceID", event.getTraceId());
        return sb.toString();
    }

    private String buildCodeBlock(String text, int maxLen) {
        return "```\n" + truncate(text, maxLen) + "\n```";
    }

    private String buildStackTraceSection(ErrorEvent event) {
        if (event.getRawStackTrace() == null || event.getRawStackTrace().isEmpty()) {
            return "";
        }
        return "## 异常栈\n\n" + buildCodeBlock(event.getRawStackTrace(), 3000) + "\n\n";
    }

    private String buildContextLogsSection(ErrorEvent event) {
        if (event.getContextLogs() == null || event.getContextLogs().isEmpty()
                || "[]".equals(event.getContextLogs())) {
            return "";
        }
        return "## 上下文日志\n\n" + buildCodeBlock(event.getContextLogs(), 2000) + "\n\n";
    }

    private String buildSourceCodeSection(SourceCodeLocator.SourceLocation location) {
        if (location == null || !location.found()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 源码文件: ").append(location.filePath()).append("\n\n");
        sb.append("```java\n").append(truncate(location.content(), 8000)).append("\n```\n\n");
        if (!location.contextFiles().isEmpty()) {
            sb.append("## 关联文件\n\n");
            location.contextFiles().forEach((path, content) -> {
                sb.append("### ").append(path).append("\n\n");
                sb.append("```java\n").append(truncate(content, 3000)).append("\n```\n\n");
            });
        }
        return sb.toString();
    }

    private String buildHistoryCasesSection(List<ErrorAnalysis> historyCases) {
        if (historyCases == null || historyCases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 历史相似案例分析\n\n");
        sb.append("以下是相同指纹或同类错误的历史分析结果，请参考但勿直接复制：\n\n");
        for (int i = 0; i < Math.min(historyCases.size(), 3); i++) {
            ErrorAnalysis hc = historyCases.get(i);
            sb.append("### 历史案例 ").append(i + 1).append("\n\n");
            sb.append("- 根因: ").append(truncate(hc.getRootCause(), 300)).append("\n");
            if (hc.getFixDescription() != null) {
                sb.append("- 修复: ").append(truncate(hc.getFixDescription(), 300)).append("\n");
            }
            if (hc.getFinalSeverity() != null) {
                sb.append("- 严重度: ").append(hc.getFinalSeverity()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n... (已截断, 原长度: " + text.length() + ")";
    }
}
