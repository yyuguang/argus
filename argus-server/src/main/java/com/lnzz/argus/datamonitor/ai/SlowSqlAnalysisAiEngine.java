package com.lnzz.argus.datamonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 慢 SQL AI 分析引擎。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class SlowSqlAnalysisAiEngine {

    private static final String JSON_REPAIR_TEMPLATE_CODE = "SLOW_SQL_JSON_REPAIR";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RulePromptService rulePromptService;

    public SlowSqlAnalysisAiEngine(ChatClient.Builder chatClientBuilder,
                                   ObjectMapper objectMapper,
                                   RulePromptService rulePromptService) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.rulePromptService = rulePromptService;
    }

    public SlowSqlAiResult analyze(String prompt, Long scmConfigId) {
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseOrRepairResponse(response, scmConfigId);
        } catch (Exception e) {
            log.warn("慢 SQL AI 分析调用失败，回退规则分析: {}", e.getMessage());
            return null;
        }
    }

    private SlowSqlAiResult parseOrRepairResponse(String response, Long scmConfigId) {
        try {
            return parseResponse(response);
        } catch (BizException parseException) {
            if (parseException.getCode() != ResultCode.AI_PARSE_ERROR.getCode()) {
                throw parseException;
            }
            try {
                String repairedResponse = chatClient.prompt()
                        .user(rulePromptService.buildJsonRepairPrompt(JSON_REPAIR_TEMPLATE_CODE, response, scmConfigId))
                        .call()
                        .content();
                return parseResponse(repairedResponse);
            } catch (Exception repairException) {
                log.warn("慢 SQL AI 分析 JSON 修复失败，回退规则分析: {}", repairException.getMessage());
                return null;
            }
        }
    }

    private SlowSqlAiResult parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String summary = text(root, "summary");
            String severity = text(root, "severity");
            String primaryCause = text(root, "primaryCause");
            String impactScope = text(root, "impactScope");
            boolean isBlocker = root.path("isBlocker").asBoolean(false);
            List<String> suggestions = stringArray(root.get("optimizationSuggestions"));
            List<String> actions = stringArray(root.get("actionPlan"));
            List<String> rootCauseLines = new ArrayList<>();
            JsonNode rootCauses = root.get("rootCauses");
            if (rootCauses != null && rootCauses.isArray()) {
                for (JsonNode cause : rootCauses) {
                    String code = text(cause, "code");
                    String title = text(cause, "title");
                    String evidence = text(cause, "evidence");
                    String reasoning = text(cause, "reasoning");
                    StringBuilder line = new StringBuilder();
                    if (StringUtils.hasText(code)) {
                        line.append("[").append(code).append("] ");
                    }
                    line.append(StringUtils.hasText(title) ? title : "未命名根因");
                    if (StringUtils.hasText(evidence)) {
                        line.append("；证据：").append(evidence);
                    }
                    if (StringUtils.hasText(reasoning)) {
                        line.append("；判断：").append(reasoning);
                    }
                    rootCauseLines.add(line.toString());
                }
            }
            if (!StringUtils.hasText(summary) && !StringUtils.hasText(primaryCause) && rootCauseLines.isEmpty()) {
                throw new BizException(ResultCode.AI_PARSE_ERROR, "慢 SQL AI 分析缺少核心结论");
            }
            return new SlowSqlAiResult(summary, severity, primaryCause, impactScope, rootCauseLines, suggestions, actions, isBlocker);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "慢 SQL AI 分析响应解析失败: " + e.getMessage());
        }
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "慢 SQL AI 分析响应为空");
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        throw new BizException(ResultCode.AI_PARSE_ERROR, "慢 SQL AI 分析响应中未找到 JSON");
    }

    private List<String> stringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record SlowSqlAiResult(
            String summary,
            String severity,
            String primaryCause,
            String impactScope,
            List<String> rootCauseLines,
            List<String> optimizationSuggestions,
            List<String> actionPlan,
            boolean blocker
    ) {
    }
}
