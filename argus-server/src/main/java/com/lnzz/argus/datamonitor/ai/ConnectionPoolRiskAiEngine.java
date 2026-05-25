package com.lnzz.argus.datamonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接池风险 AI 分析引擎。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConnectionPoolRiskAiEngine {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ConnectionPoolRiskAiEngine(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public PoolRiskAiResult analyze(String prompt) {
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("连接池风险 AI 分析调用失败，回退规则原因: {}", e.getMessage());
            return null;
        }
    }

    private PoolRiskAiResult parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String summary = text(root, "summary");
            String riskLevel = text(root, "riskLevel");
            String primaryCause = text(root, "primaryCause");
            String impactScope = text(root, "impactScope");
            boolean needEscalation = root.path("needEscalation").asBoolean(false);
            List<String> evidence = stringArray(root.get("evidence"));
            List<String> immediateActions = stringArray(root.get("immediateActions"));
            List<String> followupSuggestions = stringArray(root.get("followupSuggestions"));
            if (!StringUtils.hasText(summary) && !StringUtils.hasText(primaryCause) && evidence.isEmpty()) {
                throw new BizException(ResultCode.AI_PARSE_ERROR, "连接池风险 AI 分析缺少核心结论");
            }
            return new PoolRiskAiResult(summary, riskLevel, primaryCause, impactScope, evidence,
                    immediateActions, followupSuggestions, needEscalation);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "连接池风险 AI 分析响应解析失败: " + e.getMessage());
        }
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "连接池风险 AI 分析响应为空");
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        throw new BizException(ResultCode.AI_PARSE_ERROR, "连接池风险 AI 分析响应中未找到 JSON");
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

    public record PoolRiskAiResult(
            String summary,
            String riskLevel,
            String primaryCause,
            String impactScope,
            List<String> evidence,
            List<String> immediateActions,
            List<String> followupSuggestions,
            boolean needEscalation
    ) {
    }
}
