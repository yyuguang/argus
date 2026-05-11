package com.lnzz.argus.review.ai;

import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.review.service.ReviewerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PromptBuilder - 配置化 Prompt 构建")
class PromptBuilderTest {

    private PromptBuilder builder;
    private ReviewConfig defaultConfig;
    private VectorKnowledgeService vectorKnowledgeService;
    private ReviewerProfileService reviewerProfileService;

    @BeforeEach
    void setUp() {
        vectorKnowledgeService = mock(VectorKnowledgeService.class);
        reviewerProfileService = mock(ReviewerProfileService.class);
        builder = new PromptBuilder(vectorKnowledgeService, reviewerProfileService);
        defaultConfig = ReviewConfig.defaults();
    }

    @Test
    @DisplayName("Prompt 包含默认维度权重")
    void promptContainsDefaultDimensionWeights() {
        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String prompt = builder.buildReviewPrompt(ctx, null, defaultConfig);

        assertTrue(prompt.contains("规范合规（25%）"));
        assertTrue(prompt.contains("逻辑正确（25%）"));
        assertTrue(prompt.contains("数据完整（20%）"));
        assertTrue(prompt.contains("性能风险（15%）"));
        assertTrue(prompt.contains("可维护性（15%）"));
    }

    @Test
    @DisplayName("Prompt 包含默认严重度定义")
    void promptContainsDefaultSeverityDefinitions() {
        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String prompt = builder.buildReviewPrompt(ctx, null, defaultConfig);

        assertTrue(prompt.contains("CRITICAL"));
        assertTrue(prompt.contains("致命"));
        assertTrue(prompt.contains("扣 20 分"));
        assertTrue(prompt.contains("MAJOR"));
        assertTrue(prompt.contains("严重"));
        assertTrue(prompt.contains("扣 10 分"));
        assertTrue(prompt.contains("MINOR"));
        assertTrue(prompt.contains("扣 3 分"));
        assertTrue(prompt.contains("SUGGESTION"));
        assertTrue(prompt.contains("扣 0 分"));
    }

    @Test
    @DisplayName("修改 compliance 权重 → Prompt 反映变化")
    void customComplianceWeightReflected() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getDimensions().setCompliance(40);

        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String prompt = builder.buildReviewPrompt(ctx, null, config);

        assertTrue(prompt.contains("规范合规（40%）"));
        assertTrue(prompt.contains("逻辑正确（25%）"));
    }

    @Test
    @DisplayName("修改 CRITICAL 扣分 → Prompt 反映变化")
    void customCriticalDeductionReflected() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getSeverityDefinitions().get("CRITICAL").setDeduction(30);

        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String prompt = builder.buildReviewPrompt(ctx, null, config);

        assertTrue(prompt.contains("扣 30 分"));
        assertFalse(prompt.contains("扣 20 分")); // 原默认值消失
    }

    @Test
    @DisplayName("Prompt 不含任何硬编码权重数字")
    void noHardcodedWeights() {
        // 修改所有权重为非默认值
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getDimensions().setCompliance(40);
        config.getScoring().getDimensions().setCorrectness(30);
        config.getScoring().getDimensions().setDataIntegrity(15);
        config.getScoring().getDimensions().setPerformance(10);
        config.getScoring().getDimensions().setMaintainability(5);

        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String prompt = builder.buildReviewPrompt(ctx, null, config);

        // 不应该出现旧的硬编码值
        assertFalse(prompt.contains("25%）")); // 所有维度都改了，不存在任何旧值
        assertTrue(prompt.contains("40%）"));
        assertTrue(prompt.contains("30%）"));
        assertTrue(prompt.contains("15%）"));
        assertTrue(prompt.contains("10%）"));
        assertTrue(prompt.contains("5%）"));
    }

    @Test
    @DisplayName("编码规范过长时截断")
    void longCodingStandardsTruncated() {
        ReviewContext ctx = ReviewContext.builder()
                .filePath("Test.java")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();

        String longStandards = "x".repeat(15_000);
        String prompt = builder.buildReviewPrompt(ctx, longStandards, defaultConfig);

        assertFalse(prompt.contains(longStandards));
        assertTrue(prompt.contains("已按长度截断"));
    }

    @Test
    @DisplayName("画像注入关闭时 Prompt 不包含画像块")
    void profileInjectionDisabled() {
        ReviewContext ctx = createContext();

        String prompt = builder.buildReviewPrompt(ctx, null, defaultConfig);

        assertFalse(prompt.contains("提交者画像"));
        verifyNoInteractions(reviewerProfileService);
    }

    @Test
    @DisplayName("画像注入开启且存在历史问题时包含画像块与历史参考")
    void profileInjectionEnabled() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getProfile().setInjectEnabled(true);

        ReviewContext ctx = createContext();
        ReviewerProfile profile = new ReviewerProfile();
        profile.setAvgScore(BigDecimal.valueOf(78));
        when(reviewerProfileService.getProfile("zhangsan", "github")).thenReturn(profile);
        when(vectorKnowledgeService.searchSimilarIssues(anyString(), eq("github:zhangsan"), eq("demo-project"), eq(3), eq(0.7)))
                .thenReturn(List.of(Document.builder().text("历史问题A").metadata(java.util.Map.of("rule", "NULL_CHECK")).build()));
        when(vectorKnowledgeService.searchSimilarIssues(anyString(), isNull(), eq("demo-project"), eq(5), eq(0.7)))
                .thenReturn(List.of(Document.builder().text("团队历史问题B").metadata(java.util.Map.of("category", "CORRECTNESS")).build()));

        String prompt = builder.buildReviewPrompt(ctx, null, config);

        assertTrue(prompt.contains("提交者画像"));
        assertTrue(prompt.contains("近 5 次评审平均分：78/100"));
        assertTrue(prompt.contains("NULL_CHECK"));
        assertTrue(prompt.contains("历史相似问题参考"));
        assertTrue(prompt.contains("CORRECTNESS"));
    }

    private ReviewContext createContext() {
        return ReviewContext.builder()
                .filePath("Test.java")
                .projectName("demo-project")
                .scmProvider("github")
                .authorId("github:zhangsan")
                .authorName("zhangsan")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .build();
    }
}
