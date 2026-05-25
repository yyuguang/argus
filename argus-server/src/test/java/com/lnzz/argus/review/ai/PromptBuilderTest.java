package com.lnzz.argus.review.ai;

import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.review.service.ReviewerProfileService;
import com.lnzz.argus.rule.service.RulePromptService;
import com.lnzz.argus.rule.service.RuleRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PromptBuilder - 配置化 Prompt 构建")
class PromptBuilderTest {

    private static final String REVIEW_SKELETON = """
            评审模板
            规范合规（{{complianceWeight}}%）
            逻辑正确（{{correctnessWeight}}%）
            数据完整（{{dataIntegrityWeight}}%）
            性能风险（{{performanceWeight}}%）
            可维护性（{{maintainabilityWeight}}%）
            严重度:
            {{severityDefinitions}}
            规则参考:
            {{ruleReference}}
            文件: {{filePath}}
            语言: {{languageTag}}
            关注点: {{reviewFocus}}
            完整内容:
            {{fullContent}}
            Diff:
            {{diffContent}}
            {{relatedClassesSection}}{{profileSection}}{{teamKnowledgeSection}}
            """;

    private PromptBuilder builder;
    private ReviewConfig defaultConfig;
    private VectorKnowledgeService vectorKnowledgeService;
    private ReviewerProfileService reviewerProfileService;
    private RuleRetrievalService ruleRetrievalService;
    private RulePromptService rulePromptService;

    @BeforeEach
    void setUp() {
        vectorKnowledgeService = mock(VectorKnowledgeService.class);
        reviewerProfileService = mock(ReviewerProfileService.class);
        ruleRetrievalService = mock(RuleRetrievalService.class);
        rulePromptService = mock(RulePromptService.class);
        builder = new PromptBuilder(vectorKnowledgeService, reviewerProfileService,
                ruleRetrievalService, rulePromptService);
        defaultConfig = ReviewConfig.defaults();
        when(rulePromptService.buildReviewPromptSkeleton(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> renderReviewSkeleton(invocation.getArgument(0)));
        when(rulePromptService.resolveReviewFocus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("FOCUS-JAVA");
    }

    @Test
    @DisplayName("Prompt 包含动态维度权重与严重度定义")
    void promptContainsDynamicWeightsAndSeverityDefinitions() {
        ReviewContext ctx = createContext();

        String prompt = builder.buildReviewPrompt(ctx, null, defaultConfig);

        assertTrue(prompt.contains("规范合规（25%）"));
        assertTrue(prompt.contains("逻辑正确（25%）"));
        assertTrue(prompt.contains("数据完整（20%）"));
        assertTrue(prompt.contains("性能风险（15%）"));
        assertTrue(prompt.contains("可维护性（15%）"));
        assertTrue(prompt.contains("CRITICAL"));
        assertTrue(prompt.contains("MAJOR"));
        assertTrue(prompt.contains("MINOR"));
        assertTrue(prompt.contains("SUGGESTION"));
    }

    @Test
    @DisplayName("修改权重与严重度扣分后 Prompt 反映变化")
    void customWeightsAndSeverityDefinitionsReflected() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getScoring().getDimensions().setCompliance(40);
        config.getScoring().getDimensions().setCorrectness(30);
        config.getScoring().getSeverityDefinitions().get("CRITICAL").setDeduction(30);

        String prompt = builder.buildReviewPrompt(createContext(), null, config);

        assertTrue(prompt.contains("规范合规（40%）"));
        assertTrue(prompt.contains("逻辑正确（30%）"));
        assertTrue(prompt.contains("扣 30 分"));
        assertFalse(prompt.contains("扣 20 分"));
    }

    @Test
    @DisplayName("规则服务与规则检索结果统一注入骨架模板")
    void promptInjectsSkeletonAndRuleReference() {
        ReviewContext ctx = createContext();
        when(ruleRetrievalService.buildRuleReference(ctx, defaultConfig, 99L)).thenReturn("RULE-REFERENCE");

        String prompt = builder.buildReviewPrompt(ctx, "legacy-rules", defaultConfig, 99L);

        assertTrue(prompt.contains("RULE-REFERENCE"));
        assertTrue(prompt.contains("文件: Test.java"));
        assertTrue(prompt.contains("语言: java"));
        assertTrue(prompt.contains("关注点: FOCUS-JAVA"));
        assertTrue(prompt.contains("class Test {}"));
        assertTrue(prompt.contains("+class Test {}"));
        assertFalse(prompt.contains("legacy-rules"));
        verify(rulePromptService).buildReviewPromptSkeleton(defaultConfig, 99L);
        verify(ruleRetrievalService).buildRuleReference(ctx, defaultConfig, 99L);
    }

    @Test
    @DisplayName("规则检索未命中时回退历史规范兜底内容")
    void fallbackToCodingStandardsWhenRuleRetrievalMisses() {
        ReviewContext ctx = createContext();
        when(ruleRetrievalService.buildRuleReference(ctx, defaultConfig, 88L))
                .thenReturn("// 本次未注入规范片段");

        String prompt = builder.buildReviewPrompt(ctx, "legacy-rules", defaultConfig, 88L);

        assertTrue(prompt.contains("legacy-rules"));
        assertFalse(prompt.contains("// 本次未注入规范片段"));
    }

    @Test
    @DisplayName("编码规范过长时截断")
    void longCodingStandardsTruncated() {
        ReviewContext ctx = createContext();
        when(ruleRetrievalService.buildRuleReference(ctx, defaultConfig, null))
                .thenReturn("// 本次未注入规范片段");

        String longStandards = "x".repeat(15_000);
        String prompt = builder.buildReviewPrompt(ctx, longStandards, defaultConfig);

        assertFalse(prompt.contains(longStandards));
        assertTrue(prompt.contains("已按长度截断"));
    }

    @Test
    @DisplayName("画像注入关闭时 Prompt 不包含画像块")
    void profileInjectionDisabled() {
        String prompt = builder.buildReviewPrompt(createContext(), null, defaultConfig);

        assertFalse(prompt.contains("提交者画像"));
        verifyNoInteractions(reviewerProfileService);
    }

    @Test
    @DisplayName("画像注入开启时包含画像块、团队历史与关联类摘要")
    void profileAndKnowledgeSectionsInjected() {
        ReviewConfig config = ReviewConfig.defaults();
        config.getProfile().setInjectEnabled(true);
        ReviewContext ctx = createContextWithRelatedClasses();
        ReviewerProfile profile = new ReviewerProfile();
        profile.setAvgScore(BigDecimal.valueOf(78));
        when(reviewerProfileService.getProfile("zhangsan", "github")).thenReturn(profile);
        when(vectorKnowledgeService.searchSimilarIssues(anyString(), eq("github:zhangsan"), eq("demo-project"), eq(3), eq(0.7)))
                .thenReturn(List.of(Document.builder()
                        .text("历史问题A")
                        .metadata(Map.of("rule", "NULL_CHECK"))
                        .build()));
        when(vectorKnowledgeService.searchSimilarIssues(anyString(), isNull(), eq("demo-project"), eq(5), eq(0.7)))
                .thenReturn(List.of(Document.builder()
                        .text("团队历史问题B")
                        .metadata(Map.of("category", "CORRECTNESS"))
                        .build()));
        when(ruleRetrievalService.buildRuleReference(ctx, config, null)).thenReturn("RULE-REFERENCE");

        String prompt = builder.buildReviewPrompt(ctx, null, config);

        assertTrue(prompt.contains("提交者画像"));
        assertTrue(prompt.contains("近 5 次评审平均分：78/100"));
        assertTrue(prompt.contains("NULL_CHECK"));
        assertTrue(prompt.contains("历史相似问题参考"));
        assertTrue(prompt.contains("CORRECTNESS"));
        assertTrue(prompt.contains("关联类摘要"));
        assertTrue(prompt.contains("UserService.java"));
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

    private ReviewContext createContextWithRelatedClasses() {
        return ReviewContext.builder()
                .filePath("Test.java")
                .projectName("demo-project")
                .scmProvider("github")
                .authorId("github:zhangsan")
                .authorName("zhangsan")
                .fullContent("class Test {}")
                .diffContent("+class Test {}")
                .languageTag("java")
                .relatedClasses(Map.of("UserService.java", "class UserService { void save() {} }"))
                .build();
    }

    private String renderReviewSkeleton(ReviewConfig config) {
        ReviewConfig effectiveConfig = config != null ? config : ReviewConfig.defaults();
        ReviewConfig.DimensionsConfig dimensions = effectiveConfig.getScoring().getDimensions();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{{complianceWeight}}", String.valueOf(dimensions.getCompliance()));
        placeholders.put("{{correctnessWeight}}", String.valueOf(dimensions.getCorrectness()));
        placeholders.put("{{dataIntegrityWeight}}", String.valueOf(dimensions.getDataIntegrity()));
        placeholders.put("{{performanceWeight}}", String.valueOf(dimensions.getPerformance()));
        placeholders.put("{{maintainabilityWeight}}", String.valueOf(dimensions.getMaintainability()));
        placeholders.put("{{severityDefinitions}}", buildSeverityDefinitions(effectiveConfig));
        String rendered = REVIEW_SKELETON;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        return rendered;
    }

    private String buildSeverityDefinitions(ReviewConfig config) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, ReviewConfig.SeverityDefConfig> entry
                : config.getScoring().getSeverityDefinitions().entrySet()) {
            ReviewConfig.SeverityDefConfig definition = entry.getValue();
            builder.append("- **")
                    .append(entry.getKey())
                    .append("（")
                    .append(definition.getLabel())
                    .append("，扣 ")
                    .append(definition.getDeduction())
                    .append(" 分）**：");
            if (definition.getExamples() != null && !definition.getExamples().isEmpty()) {
                builder.append(String.join("、", definition.getExamples()));
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }
}
