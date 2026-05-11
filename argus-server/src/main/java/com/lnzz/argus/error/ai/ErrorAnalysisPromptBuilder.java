package com.lnzz.argus.error.ai;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.service.SourceCodeLocator;
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
public class ErrorAnalysisPromptBuilder {

    private static final String SYSTEM_INSTRUCTION = """
            你是 Argus 错误分析 AI，专门负责分析 Java 应用的生产错误并给出根因定位和修复建议。

            ## 你的分析职责
            1. **根因定位** — 结合错误信息、异常栈和源码，推断最可能的根本原因
            2. **技术细节** — 说明涉及的技术点（数据库、缓存、网络、线程等）
            3. **影响范围** — 评估该错误对业务的影响
            4. **严重度校准** — 基于实际影响重新评估严重度 P0/P1/P2/P3
            5. **修复方案** — 给出具体代码级修复建议
            6. **预防建议** — 如何避免同类问题

            ## 严重度标准
            - **P0**: 核心链路不可用，影响所有用户
            - **P1**: 核心链路部分不可用，或关键功能受损
            - **P2**: 非核心功能异常，或可自动恢复
            - **P3**: 轻微问题，不影响业务

            ## 分析原则
            - 优先从源码中寻找证据
            - 不凭空猜测，不确定时标明置信度
            - 结合历史案例（如已提供）判断是否为已知问题
            - 修复方案必须针对源码中的具体行

            ## 输出格式
            严格输出 JSON，不要包含 markdown 代码块外的其他内容：
            ```json
            {
              "rootCause": "根本原因描述",
              "technicalDetail": "涉及的技术细节",
              "impactScope": "影响范围评估",
              "calibratedSeverity": "P0|P1|P2|P3",
              "severityReason": "校准理由",
              "confidence": 0.0-1.0,
              "fix": {
                "description": "修复描述",
                "codeExample": "修复代码示例（可为空）",
                "filePath": "需修改的文件路径",
                "lineRange": "行号范围如 42-58"
              },
              "estimatedEffort": "预估工作量: <1h|1-4h|1d|>1d",
              "preventionAdvice": "预防建议",
              "isKnownIssue": false
            }
            ```
            """;

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
                                       List<ErrorAnalysis> historyCases) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_INSTRUCTION).append("\n\n");

        // === 错误信息 ===
        sb.append("## 错误信息\n\n");
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
        sb.append("\n");

        // === 错误消息 ===
        sb.append("## 错误消息\n\n");
        sb.append("```\n").append(truncate(event.getErrorMessage(), 2000)).append("\n```\n\n");

        // === 异常栈 ===
        if (event.getRawStackTrace() != null && !event.getRawStackTrace().isEmpty()) {
            sb.append("## 异常栈\n\n");
            sb.append("```\n").append(truncate(event.getRawStackTrace(), 3000)).append("\n```\n\n");
        }

        // === 上下文日志 ===
        if (event.getContextLogs() != null && !event.getContextLogs().isEmpty()
                && !"[]".equals(event.getContextLogs())) {
            sb.append("## 上下文日志\n\n");
            sb.append("```\n").append(truncate(event.getContextLogs(), 2000)).append("\n```\n\n");
        }

        // === 源码定位 ===
        if (location != null && location.found()) {
            sb.append("## 源码文件: ").append(location.filePath()).append("\n\n");
            sb.append("```java\n").append(truncate(location.content(), 8000)).append("\n```\n\n");

            if (!location.contextFiles().isEmpty()) {
                sb.append("## 关联文件\n\n");
                location.contextFiles().forEach((path, content) -> {
                    sb.append("### ").append(path).append("\n\n");
                    sb.append("```java\n").append(truncate(content, 3000)).append("\n```\n\n");
                });
            }
        }

        // === B02: 历史相似案例 ===
        if (historyCases != null && !historyCases.isEmpty()) {
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
        }

        sb.append("## 请开始分析\n\n");
        sb.append("请基于以上信息，输出 JSON 格式的分析结果。");

        String prompt = sb.toString();
        log.debug("错误分析 Prompt 已构建: length={}, hasSource={}, hasHistory={}",
                prompt.length(), location != null && location.found(), historyCases != null && !historyCases.isEmpty());
        return prompt;
    }

    private void appendRow(StringBuilder sb, String key, String value) {
        String v = value != null ? value.replace("|", "\\|").replace("\n", " ") : "-";
        sb.append("| ").append(key).append(" | ").append(v).append(" |\n");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n... (已截断, 原长度: " + text.length() + ")";
    }
}
