package com.lnzz.argus.review.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    private final ChatClient chatClient;

    public AiReviewEngine(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
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

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("AI评审完成, duration={}ms, responseLength={}", duration, response != null ? response.length() : 0);

            // M3-D: 解析 AI 响应
            ReviewResult result = parseResponse(response);
            result.setDuration(duration);
            result.setTokensUsed(ReviewResult.estimateTokens(prompt) + ReviewResult.estimateTokens(response));

            return result;
        } catch (Exception e) {
            log.error("AI评审调用失败", e);
            throw new BizException(ResultCode.AI_MODEL_ERROR, "AI模型调用失败: " + e.getMessage());
        }
    }

    /**
     * M3-D01~D04: 解析 AI 响应 JSON
     */
    private ReviewResult parseResponse(String response) {
        try {
            // 提取 JSON 块
            String json = extractJson(response);
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

        // 尝试提取 { ... } 中的内容
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1);
        }

        return response.trim();
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
