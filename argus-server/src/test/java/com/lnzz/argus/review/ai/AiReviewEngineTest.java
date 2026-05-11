package com.lnzz.argus.review.ai;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AiReviewEngine - 响应解析")
class AiReviewEngineTest {

    private final AiReviewEngine engine = new AiReviewEngine((org.springframework.ai.chat.client.ChatClient) null);

    @Test
    @DisplayName("可以从自然语言前缀中提取 JSON 对象")
    void parseResponseExtractsJsonAfterNaturalLanguagePrefix() {
        String response = """
                好的，收到评审任务。我将开始评审。
                {
                  "scores": {
                    "compliance": 88,
                    "correctness": 76,
                    "dataSafety": 82,
                    "performance": 91,
                    "maintainability": 79
                  },
                  "issues": [
                    {
                      "severity": "MAJOR",
                      "category": "CORRECTNESS",
                      "filePath": "src/main/java/Demo.java",
                      "startLine": 12,
                      "endLine": 14,
                      "description": "外部接口返回值未判空",
                      "suggestion": "补充 null 和状态码校验",
                      "rule": "外部接口返回值必须判空"
                    }
                  ],
                  "highlights": ["命名清晰"],
                  "summary": "整体可合并前需修复判空问题"
                }
                """;

        AiReviewEngine.ReviewResult result = engine.parseResponse(response);

        assertEquals(88, result.getComplianceScore());
        assertEquals(76, result.getCorrectnessScore());
        assertEquals(1, result.getIssues().size());
        assertEquals("MAJOR", result.getIssues().get(0).getSeverity());
        assertEquals("命名清晰", result.getHighlights().get(0));
    }

    @Test
    @DisplayName("会跳过响应中的 Java 代码块大括号并提取后续 JSON")
    void parseResponseSkipsNonJsonBalancedBraces() {
        String response = """
                这段变更里有一个构造器：
                { this(null, null); }

                {
                  "scores": {
                    "compliance": 90,
                    "correctness": 88,
                    "dataSafety": 92,
                    "performance": 86,
                    "maintainability": 89
                  },
                  "issues": [],
                  "highlights": ["构造器委托清晰"],
                  "summary": "未发现阻塞问题"
                }
                """;

        AiReviewEngine.ReviewResult result = engine.parseResponse(response);

        assertEquals(90, result.getComplianceScore());
        assertTrue(result.getIssues().isEmpty());
        assertEquals("未发现阻塞问题", result.getSummary());
    }

    @Test
    @DisplayName("可以解析 markdown json 代码块")
    void parseResponseExtractsJsonFence() {
        String response = """
                ```json
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
                  "summary": "未发现明显问题"
                }
                ```
                """;

        AiReviewEngine.ReviewResult result = engine.parseResponse(response);

        assertEquals(100, result.getComplianceScore());
        assertTrue(result.getIssues().isEmpty());
        assertEquals("未发现明显问题", result.getSummary());
    }

    @Test
    @DisplayName("完全没有评审 JSON 时抛出 AI_PARSE_ERROR")
    void parseResponseThrowsParseErrorWhenNoJsonExists() {
        BizException exception = assertThrows(
                BizException.class,
                () -> engine.parseResponse("好的，收到评审任务。我将开始评审。\n{ this(null, null); }"));

        assertEquals(ResultCode.AI_PARSE_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("AI响应解析失败"));
    }
}
