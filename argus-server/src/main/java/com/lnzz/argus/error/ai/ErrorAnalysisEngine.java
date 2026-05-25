package com.lnzz.argus.error.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.common.enums.AnalysisSource;
import com.lnzz.argus.common.enums.SeverityLevel;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M5-B03/B05: AI 错误分析引擎
 * <p>调用 AI 进行错误根因分析、严重度校准，含重试与超时降级</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ErrorAnalysisEngine {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BACKOFF_MS = 2000;
    private static final long TIMEOUT_MS = 60_000;
    private static final String AI_MODEL = "deepseek-chat";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RulePromptService rulePromptService;

    public ErrorAnalysisEngine(ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper,
                               RulePromptService rulePromptService) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.rulePromptService = rulePromptService;
    }

    /**
     * 执行 AI 错误分析（含重试与超时降级）
     *
     * @param prompt 完整分析 Prompt
     * @param event  原始错误事件
     * @return 分析结果实体
     */
    public ErrorAnalysis analyze(String prompt, ErrorEvent event) {
        return analyze(prompt, event, null);
    }

    /**
     * 执行 AI 错误分析（含重试与超时降级）
     *
     * @param prompt 完整分析 Prompt
     * @param event  原始错误事件
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return 分析结果实体
     */
    public ErrorAnalysis analyze(String prompt, ErrorEvent event, Long scmConfigId) {
        log.info("开始AI错误分析: eventId={}, promptLength={}", event.getId(), prompt.length());
        long startTime = System.currentTimeMillis();

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long backoff = RETRY_BACKOFF_MS * attempt;
                log.info("AI分析重试: attempt={}/{}, backoff={}ms", attempt, MAX_RETRIES, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BizException(ResultCode.AI_MODEL_ERROR, "AI分析被中断");
                }
            }

            try {
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                long duration = System.currentTimeMillis() - startTime;
                log.info("AI分析完成: eventId={}, duration={}ms, responseLength={}",
                        event.getId(), duration, response != null ? response.length() : 0);

                AnalysisResult result = parseOrRepairResponse(response, scmConfigId);
                return buildEntity(result, event, prompt, response, duration);

            } catch (BizException e) {
                lastException = e;
                log.warn("AI分析失败(attempt={}): eventId={}, error={}", attempt, event.getId(), e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("AI分析异常(attempt={}): eventId={}, error={}", attempt, event.getId(), e.getMessage());
                if (attempt >= MAX_RETRIES) break;
            }
        }

        log.error("AI分析全部重试失败: eventId={}", event.getId(), lastException);
        // B05: 超时/失败降级 —— 规则严重度作为最终严重度
        return buildFallbackEntity(event, startTime, lastException);
    }

    /**
     * B05: 失败/超时降级 —— 使用规则初判严重度，标记为降级分析
     */
    private ErrorAnalysis buildFallbackEntity(ErrorEvent event, long startTime, Exception lastException) {
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setErrorEventId(event.getId());
        analysis.setRootCause("AI 分析不可用，以下为规则初判信息：错误类型 "
                + event.getErrorType() + "，" + (event.getSeverityReason() != null ? event.getSeverityReason() : "无详情"));
        analysis.setFinalSeverity(event.getSeverity());
        analysis.setConfidence(BigDecimal.valueOf(0.3)); // 降级置信度
        analysis.setDuration(System.currentTimeMillis() - startTime);
        analysis.setAiModel(AI_MODEL);
        analysis.setSource(AnalysisSource.AI_DEGRADED.getCode());
        log.info("降级分析结果已生成: eventId={}", event.getId());
        return analysis;
    }

    /**
     * M5-B03: 解析 AI 响应 JSON → 结构化结果
     * <p>使用 Jackson 解析，对 AI 返回的 JSON 中非法转义字符有更好的容错性</p>
     */
    private AnalysisResult parseResponse(String response) {
        try {
            String json = extractJson(sanitize(response));
            // 修复 AI 返回 JSON 中可能的非法转义：将字符串值内的反斜杠（非标准转义）双写
            json = fixBrokenEscapes(json);
            JsonNode obj = objectMapper.readTree(json);

            AnalysisResult result = new AnalysisResult();
            result.rootCause = textNode(obj, "rootCause");
            result.technicalDetail = textNode(obj, "technicalDetail");
            result.impactScope = textNode(obj, "impactScope");
            result.calibratedSeverity = textNode(obj, "calibratedSeverity");
            result.severityReason = textNode(obj, "severityReason");

            JsonNode confNode = obj.get("confidence");
            result.confidence = confNode != null && confNode.isNumber()
                    ? BigDecimal.valueOf(confNode.asDouble()) : BigDecimal.valueOf(0.7);

            JsonNode fix = obj.get("fix");
            if (fix != null && fix.isObject()) {
                result.fixDescription = textNode(fix, "description");
                result.fixCodeExample = textNode(fix, "codeExample");
                result.fixFilePath = textNode(fix, "filePath");
                result.fixLineRange = textNode(fix, "lineRange");
            }

            result.estimatedEffort = textNode(obj, "estimatedEffort");
            result.preventionAdvice = textNode(obj, "preventionAdvice");
            if (obj.has("isKnownIssue") && obj.get("isKnownIssue").asBoolean(false)) {
                result.isKnownIssue = true;
            }

            // 必填字段校验
            if (result.rootCause == null || result.rootCause.isBlank()) {
                throw new BizException(ResultCode.AI_PARSE_ERROR, "AI 未返回 rootCause");
            }
            if (result.calibratedSeverity == null || result.calibratedSeverity.isBlank()) {
                result.calibratedSeverity = "P3";
            }

            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI响应解析失败: response={}", response, e);
            throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应解析失败: " + e.getMessage());
        }
    }

    private AnalysisResult parseOrRepairResponse(String response, Long scmConfigId) {
        try {
            return parseResponse(response);
        } catch (BizException parseException) {
            if (parseException.getCode() != ResultCode.AI_PARSE_ERROR.getCode()) {
                throw parseException;
            }
            log.warn("AI错误分析响应不是合法 JSON，调用模型进行一次 JSON 规范化: {}", parseException.getMessage());
            String repairedResponse = repairJsonWithAi(response, scmConfigId);
            try {
                return parseResponse(repairedResponse);
            } catch (BizException repairedParseException) {
                log.error("AI错误分析响应经模型规范化后仍解析失败", repairedParseException);
                throw new BizException(
                        ResultCode.AI_PARSE_ERROR,
                        "AI响应解析失败，模型规范化后仍不可解析: " + parseException.getMessage());
            }
        }
    }

    private String repairJsonWithAi(String originalResponse, Long scmConfigId) {
        try {
            return chatClient.prompt()
                    .user(buildJsonRepairPrompt(originalResponse, scmConfigId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI错误分析 JSON 规范化调用失败", e);
            throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应解析失败，JSON 规范化调用失败: " + e.getMessage());
        }
    }

    private String buildJsonRepairPrompt(String originalResponse, Long scmConfigId) {
        return rulePromptService.buildErrorAnalysisJsonRepairPrompt(originalResponse, scmConfigId);
    }

    private String textNode(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    /**
     * 修复 AI 返回 JSON 中的非法转义字符
     * <p>在 JSON 字符串值内部，将反斜杠后跟非标准转义字符的情况修复为双反斜杠</p>
     */
    private String fixBrokenEscapes(String json) {
        if (json == null || json.isEmpty()) return json;
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                inString = !inString;
                sb.append(c);
            } else if (inString && c == '\\') {
                sb.append(c);
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    // 只有标准 JSON 转义才保留原样，否则双写反斜杠
                    if ("\"\\/bfnrtu".indexOf(next) >= 0) {
                        sb.append(next);
                    } else {
                        sb.append('\\').append(next);
                    }
                    i++;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 将解析结果转为 ErrorAnalysis 实体（B04: 严重度校准在此阶段）
     */
    private ErrorAnalysis buildEntity(AnalysisResult result, ErrorEvent event,
                                       String prompt, String response, long duration) {
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setErrorEventId(event.getId());
        analysis.setRootCause(result.rootCause);
        analysis.setTechnicalDetail(result.technicalDetail);
        analysis.setImpactScope(result.impactScope);

        // M5-B04: AI 严重度校准 vs 规则初判
        SeverityLevel calibratedSeverity = SeverityLevel.fromCode(result.calibratedSeverity);
        analysis.setFinalSeverity(calibratedSeverity.getCode());
        log.info("严重度校准: eventId={}, ruleSeverity={}, aiSeverity={}",
                event.getId(), event.getSeverity(), calibratedSeverity);

        analysis.setFixDescription(result.fixDescription);
        analysis.setFixCodeExample(result.fixCodeExample);
        analysis.setFixFilePath(result.fixFilePath);
        analysis.setFixLineRange(result.fixLineRange);
        analysis.setEstimatedEffort(result.estimatedEffort);
        analysis.setPreventionAdvice(result.preventionAdvice);
        analysis.setConfidence(result.confidence != null ? result.confidence : BigDecimal.valueOf(0.7));
        analysis.setTokensUsed(estimateTokens(prompt) + estimateTokens(response));
        analysis.setDuration(duration);
        analysis.setAiModel(AI_MODEL);
        analysis.setSource(AnalysisSource.AI.getCode());

        return analysis;
    }


    // ======================== JSON 提取（复用 AiReviewEngine 模式） ========================

    private String sanitize(String response) {
        if (response == null) return null;
        return response.replace("﻿", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .trim();
    }

    private String extractJson(String response) {
        if (response == null) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应为空");
        }
        int jsonStart = response.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = response.indexOf("\n", jsonStart) + 1;
            int contentEnd = response.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return response.substring(contentStart, contentEnd).trim();
            }
        }
        String firstObj = extractFirstBalancedJsonObject(response);
        if (firstObj != null) return firstObj;
        return response.trim();
    }

    private String extractFirstBalancedJsonObject(String response) {
        int start = response.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < response.length(); i++) {
            char c = response.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return response.substring(start, i + 1);
            }
        }
        return null;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    /**
     * AI 分析解析结果（中间态）
     */
    private static class AnalysisResult {
        String rootCause;
        String technicalDetail;
        String impactScope;
        String calibratedSeverity;
        String severityReason;
        BigDecimal confidence;
        String fixDescription;
        String fixCodeExample;
        String fixFilePath;
        String fixLineRange;
        String estimatedEffort;
        String preventionAdvice;
        boolean isKnownIssue;
    }
}
