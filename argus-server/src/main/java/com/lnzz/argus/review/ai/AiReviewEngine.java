package com.lnzz.argus.review.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * M3-C/D/E: AI 评审引擎
 * <p>调用 DeepSeek V4 执行代码评审，解析结果并计算评分</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class AiReviewEngine {

    private static final int MAX_REPAIR_RESPONSE_CHARS = 16_000;

    private final ChatClient chatClient;

    @Autowired
    public AiReviewEngine(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    AiReviewEngine(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * M3-C01: 调用 AI 执行评审
     *
     * @param prompt 完整评审 Prompt
     * @return 结构化评审结果
     */
    public ReviewResult executeReview(String prompt) {
        log.info("调用AI评审, promptLength={}", prompt.length());
        long startTime = System.currentTimeMillis();

        String response;
        try {
            response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI评审调用失败", e);
            throw new BizException(ResultCode.AI_MODEL_ERROR, "AI模型调用失败: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("AI评审完成, duration={}ms, responseLength={}", duration, response != null ? response.length() : 0);

        try {
            ReviewResult result = parseResponse(response);
            result.setDuration(duration);
            result.setTokensUsed(ReviewResult.estimateTokens(prompt) + ReviewResult.estimateTokens(response));
            return result;
        } catch (BizException parseException) {
            if (parseException.getCode() != ResultCode.AI_PARSE_ERROR.getCode()) {
                throw parseException;
            }
            log.warn("AI响应非结构化，尝试进行一次 JSON 修复后重试解析: {}", parseException.getMessage());
            return repairAndParseResponse(prompt, response, duration, parseException);
        }
    }

    /**
     * M3-D01~D04: 解析 AI 响应 JSON
     */
    ReviewResult parseResponse(String response) {
        try {
            // 提取 JSON 块
            String json = extractJson(sanitizeResponse(response));
            JSONObject obj = JSON.parseObject(json);

            ReviewResult result = new ReviewResult();

            // 解析维度评分
            JSONObject scores = obj.getJSONObject("scores");
            if (scores != null) {
                result.setComplianceScore(scores.getIntValue("compliance", 100));
                result.setCorrectnessScore(scores.getIntValue("correctness", 100));
                result.setDataSafetyScore(scores.getIntValue("dataSafety", 100));
                result.setPerformanceScore(scores.getIntValue("performance", 100));
                result.setMaintainabilityScore(scores.getIntValue("maintainability", 100));
            }

            // 解析问题列表
            JSONArray issues = obj.getJSONArray("issues");
            if (issues != null) {
                List<ReviewResult.Issue> issueList = new ArrayList<>();
                for (int i = 0; i < issues.size(); i++) {
                    JSONObject issueObj = issues.getJSONObject(i);
                    ReviewResult.Issue issue = new ReviewResult.Issue();
                    issue.setSeverity(issueObj.getString("severity"));
                    issue.setCategory(issueObj.getString("category"));
                    issue.setFilePath(issueObj.getString("filePath"));
                    issue.setStartLine(issueObj.getIntValue("startLine", 0));
                    issue.setEndLine(issueObj.getIntValue("endLine", 0));
                    issue.setDescription(issueObj.getString("description"));
                    issue.setSuggestion(issueObj.getString("suggestion"));
                    issue.setCodeSnippet(issueObj.getString("codeSnippet"));
                    issue.setRule(issueObj.getString("rule"));
                    issueList.add(issue);
                }
                result.setIssues(issueList);
            }

            // 解析亮点和总结
            JSONArray highlights = obj.getJSONArray("highlights");
            if (highlights != null) {
                result.setHighlights(highlights.toList(String.class));
            }
            result.setSummary(obj.getString("summary"));

            return result;
        } catch (Exception e) {
            log.error("AI响应解析失败: response={}", response, e);
            throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应解析失败: " + e.getMessage());
        }
    }

    private ReviewResult repairAndParseResponse(String originalPrompt,
                                                String originalResponse,
                                                long originalDuration,
                                                BizException originalParseException) {
        String repairedResponse;
        long repairStart = System.currentTimeMillis();
        try {
            repairedResponse = chatClient.prompt()
                    .user(buildRepairPrompt(originalResponse))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI响应 JSON 修复调用失败", e);
            throw new BizException(
                    ResultCode.AI_PARSE_ERROR,
                    "AI响应解析失败，且 JSON 修复调用失败: " + e.getMessage());
        }

        try {
            ReviewResult repaired = parseResponse(repairedResponse);
            long totalDuration = originalDuration + (System.currentTimeMillis() - repairStart);
            repaired.setDuration(totalDuration);
            repaired.setTokensUsed(ReviewResult.estimateTokens(originalPrompt)
                    + ReviewResult.estimateTokens(originalResponse)
                    + ReviewResult.estimateTokens(repairedResponse));
            return repaired;
        } catch (BizException repairedParseException) {
            log.error("AI响应 JSON 修复后仍解析失败", repairedParseException);
            throw new BizException(
                    ResultCode.AI_PARSE_ERROR,
                    "AI响应解析失败，JSON 修复后仍不可解析: " + originalParseException.getMessage());
        }
    }

    private String buildRepairPrompt(String originalResponse) {
        String compactResponse = originalResponse == null ? "" : originalResponse.trim();
        if (compactResponse.length() > MAX_REPAIR_RESPONSE_CHARS) {
            compactResponse = compactResponse.substring(0, MAX_REPAIR_RESPONSE_CHARS);
        }
        return """
                你需要把下面这段代码评审回复转换为严格 JSON。

                只允许输出一个 JSON 对象，首字符必须是 `{`，末字符必须是 `}`。
                不允许输出 Markdown、解释、寒暄、代码块标记或任何 JSON 外文本。

                JSON schema:
                {
                  "scores": {
                    "compliance": 100,
                    "correctness": 100,
                    "dataSafety": 100,
                    "performance": 100,
                    "maintainability": 100
                  },
                  "issues": [],
                  "highlights": [],
                  "summary": ""
                }

                如果原回复没有明确问题，请返回空 issues，并在 summary 说明原回复未提供结构化问题。

                原回复：
                %s
                """.formatted(compactResponse);
    }

    /**
     * 从 AI 响应中提取 JSON 块（可能被 markdown code fence 包裹）
     */
    private String extractJson(String response) {
        if (response == null) {
            throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应为空");
        }

        // 尝试提取 ```json ... ``` 中的内容
        int jsonStart = response.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = response.indexOf("\n", jsonStart) + 1;
            int contentEnd = response.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return response.substring(contentStart, contentEnd).trim();
            }
        }

        String firstJsonObject = extractFirstReviewJsonObject(response);
        if (firstJsonObject != null) {
            return firstJsonObject;
        }

        throw new BizException(ResultCode.AI_PARSE_ERROR, "AI响应中未找到合法 JSON 对象");
    }

    private String sanitizeResponse(String response) {
        if (response == null) {
            return null;
        }
        return response
                .replace("\uFEFF", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .trim();
    }

    private String extractFirstReviewJsonObject(String response) {
        int searchFrom = 0;
        while (searchFrom < response.length()) {
            BalancedObject object = extractNextBalancedObject(response, searchFrom);
            if (object == null) {
                return null;
            }
            if (isReviewJsonObject(object.content())) {
                return object.content();
            }
            searchFrom = object.endExclusive();
        }
        return null;
    }

    private BalancedObject extractNextBalancedObject(String response, int fromIndex) {
        int start = response.indexOf('{');
        if (fromIndex > 0) {
            start = response.indexOf('{', fromIndex);
        }
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < response.length(); i++) {
            char c = response.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return new BalancedObject(response.substring(start, i + 1), i + 1);
                }
            }
        }
        return null;
    }

    private boolean isReviewJsonObject(String candidate) {
        try {
            JSONObject obj = JSON.parseObject(candidate);
            return obj.containsKey("scores")
                    || obj.containsKey("issues")
                    || obj.containsKey("highlights")
                    || obj.containsKey("summary");
        } catch (Exception ignored) {
            return false;
        }
    }

    private record BalancedObject(String content, int endExclusive) {
    }

    /**
     * AI 评审结果
     */
    @Data
    public static class ReviewResult {
        /** 五维度评分 */
        private int complianceScore = 100;
        private int correctnessScore = 100;
        private int dataSafetyScore = 100;
        private int performanceScore = 100;
        private int maintainabilityScore = 100;

        /** 问题列表 */
        private List<Issue> issues = new ArrayList<>();

        /** 代码亮点 */
        private List<String> highlights = new ArrayList<>();

        /** 评审总结 */
        private String summary;

        /** 耗时(ms) */
        private long duration;

        /** Token 使用量 */
        private int tokensUsed;

        /** 粗估 Token 数 */
        public static int estimateTokens(String text) {
            return text == null ? 0 : text.length() / 4;
        }

        @Data
        public static class Issue {
            private String severity;
            private String category;
            private String filePath;
            private int startLine;
            private int endLine;
            private String description;
            private String suggestion;
            private String codeSnippet;
            private String rule;
        }
    }
}
