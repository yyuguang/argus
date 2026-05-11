package com.lnzz.argus.knowledge.vector;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.review.entity.ReviewIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService - 文本向量化")
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingModel, 10);
    }

    // ==================== embedIssue ====================

    @Test
    @DisplayName("embedIssue 正常返回向量")
    void embedIssueSuccess() {
        ReviewIssue issue = new ReviewIssue();
        issue.setSeverity("CRITICAL");
        issue.setCategory("COMPLIANCE");
        issue.setRule("SQL注入防护");
        issue.setFilePath("UserController.java");
        issue.setDescription("第42行存在未参数化的SQL拼接");
        issue.setSuggestion("使用PreparedStatement参数化查询");

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        float[] result = embeddingService.embedIssue(issue);

        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
    }

    @Test
    @DisplayName("embedIssue null 输入返回空数组")
    void embedIssueNullReturnsEmpty() {
        float[] result = embeddingService.embedIssue(null);
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("embedIssue 异常时降级返回空数组")
    void embedIssueDegradesOnException() {
        ReviewIssue issue = new ReviewIssue();
        issue.setSeverity("MINOR");
        issue.setDescription("魔法数字");

        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API不可用"));

        float[] result = embeddingService.embedIssue(issue);

        assertEquals(0, result.length);
    }

    // ==================== embedErrorPattern ====================

    @Test
    @DisplayName("embedErrorPattern 正常返回向量")
    void embedErrorPatternSuccess() {
        ErrorEvent event = new ErrorEvent();
        event.setErrorType("NULL_POINTER");
        event.setAppName("demo-service");
        event.setClassName("com.example.UserController");
        event.setErrorMessage("用户名参数为空");

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f, 0.6f});

        float[] result = embeddingService.embedErrorPattern(event);

        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    @DisplayName("embedErrorPattern null 输入返回空数组")
    void embedErrorPatternNullReturnsEmpty() {
        float[] result = embeddingService.embedErrorPattern(null);
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("embedErrorPattern 超时降级返回空数组")
    void embedErrorPatternDegradesOnTimeout() {
        ErrorEvent event = new ErrorEvent();
        event.setErrorType("OUT_OF_MEMORY");
        event.setAppName("batch-service");

        // 模拟超时：延迟 2s，超时设为 1ms
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
            Thread.sleep(2000);
            return new float[]{0.9f};
        });

        EmbeddingService shortTimeoutService = new EmbeddingService(embeddingModel, 1);
        float[] result = shortTimeoutService.embedErrorPattern(event);

        assertEquals(0, result.length);
    }

    // ==================== 嵌入文本格式 ====================

    @Test
    @DisplayName("buildIssueEmbeddingText 拼接所有关键字段")
    void buildIssueEmbeddingTextFormat() {
        ReviewIssue issue = new ReviewIssue();
        issue.setSeverity("MAJOR");
        issue.setCategory("PERFORMANCE");
        issue.setRule("N+1查询");
        issue.setFilePath("OrderService.java");
        issue.setDescription("循环内执行数据库查询");
        issue.setSuggestion("使用批量查询");

        String text = embeddingService.buildIssueEmbeddingText(issue);

        assertTrue(text.contains("[MAJOR]"));
        assertTrue(text.contains("[PERFORMANCE]"));
        assertTrue(text.contains("N+1查询"));
        assertTrue(text.contains("OrderService.java"));
        assertTrue(text.contains("循环内执行数据库查询"));
        assertTrue(text.contains("使用批量查询"));
    }

    @Test
    @DisplayName("buildErrorEmbeddingText 拼接错误关键字段")
    void buildErrorEmbeddingTextFormat() {
        ErrorEvent event = new ErrorEvent();
        event.setErrorType("SQL_EXCEPTION");
        event.setAppName("order-service");
        event.setClassName("com.example.OrderMapper");
        event.setErrorMessage("连接池耗尽");

        String text = embeddingService.buildErrorEmbeddingText(event);

        assertTrue(text.contains("SQL_EXCEPTION"));
        assertTrue(text.contains("order-service"));
        assertTrue(text.contains("com.example.OrderMapper"));
        assertTrue(text.contains("连接池耗尽"));
    }

    @Test
    @DisplayName("buildErrorEmbeddingText 部分字段为空时不抛异常")
    void buildErrorEmbeddingTextWithNullFields() {
        ErrorEvent event = new ErrorEvent();
        event.setErrorType("TIMEOUT");
        event.setAppName(null);
        event.setClassName(null);
        event.setErrorMessage(null);

        String text = embeddingService.buildErrorEmbeddingText(event);

        assertEquals("TIMEOUT", text);
    }
}
