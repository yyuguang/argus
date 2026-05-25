package com.lnzz.argus.review.ai;

import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.review.service.ReviewerProfileService;
import com.lnzz.argus.rule.service.RulePromptService;
import com.lnzz.argus.rule.service.RuleRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M3-B: Prompt 构建器
 * <p>将 ReviewContext + 规则片段注入到结构化 Prompt 中，维度权重和严重度从 ReviewConfig 动态生成。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class PromptBuilder {

    private static final int MAX_STANDARDS_CHARS = 12_000;
    private static final int MAX_RELATED_CLASS_TOTAL_CHARS = 6_000;
    private static final String EMPTY_RULE_REFERENCE = "// 本次未注入规范片段";

    private final VectorKnowledgeService vectorKnowledgeService;
    private final ReviewerProfileService reviewerProfileService;
    private final RuleRetrievalService ruleRetrievalService;
    private final RulePromptService rulePromptService;

    public PromptBuilder() {
        this(null, null, createFallbackRuleRetrievalService(), createFallbackRulePromptService());
    }

    public PromptBuilder(VectorKnowledgeService vectorKnowledgeService,
                         ReviewerProfileService reviewerProfileService) {
        this(vectorKnowledgeService, reviewerProfileService,
                createFallbackRuleRetrievalService(), createFallbackRulePromptService());
    }

    @Autowired
    public PromptBuilder(VectorKnowledgeService vectorKnowledgeService,
                         ReviewerProfileService reviewerProfileService,
                         RuleRetrievalService ruleRetrievalService,
                         RulePromptService rulePromptService) {
        this.vectorKnowledgeService = vectorKnowledgeService;
        this.reviewerProfileService = reviewerProfileService;
        this.ruleRetrievalService = ruleRetrievalService;
        this.rulePromptService = rulePromptService;
    }

    /**
     * M3-B01: 构建 AI 评审 Prompt。
     *
     * @param context 评审上下文
     * @param codingStandards 历史编码规范兜底内容
     * @param config 仓库级评审配置
     * @return 完整 Prompt
     */
    public String buildReviewPrompt(ReviewContext context, String codingStandards, ReviewConfig config) {
        return buildReviewPrompt(context, codingStandards, config, null);
    }

    /**
     * M3-B01: 构建 AI 评审 Prompt。
     *
     * @param context 评审上下文
     * @param codingStandards 历史编码规范兜底内容
     * @param config 仓库级评审配置
     * @param scmConfigId 当前仓库配置 ID
     * @return 完整 Prompt
     */
    public String buildReviewPrompt(ReviewContext context,
                                    String codingStandards,
                                    ReviewConfig config,
                                    Long scmConfigId) {
        ReviewConfig effectiveConfig = config != null ? config : ReviewConfig.defaults();
        String languageTag = resolveLanguageTag(context);
        String reviewFocus = rulePromptService.resolveReviewFocus(languageTag, effectiveConfig);
        String ruleReference = resolveRuleReference(context, codingStandards, effectiveConfig, scmConfigId);
        String skeleton = rulePromptService.buildReviewPromptSkeleton(effectiveConfig, scmConfigId);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{{ruleReference}}", ruleReference);
        placeholders.put("{{filePath}}", context.getFilePath() != null ? context.getFilePath() : "unknown");
        placeholders.put("{{languageTag}}", languageTag);
        placeholders.put("{{reviewFocus}}", reviewFocus);
        placeholders.put("{{fullContent}}", context.getFullContent() != null ? context.getFullContent() : "// 无法获取文件内容");
        placeholders.put("{{diffContent}}", context.getDiffContent() != null ? context.getDiffContent() : "// 无 Diff 内容");
        placeholders.put("{{relatedClassesSection}}", buildRelatedClassesSection(context));
        placeholders.put("{{profileSection}}", buildProfileSection(context, effectiveConfig));
        placeholders.put("{{teamKnowledgeSection}}", buildTeamKnowledgeSection(context, effectiveConfig));
        return renderTemplate(skeleton, placeholders);
    }

    private String resolveRuleReference(ReviewContext context,
                                        String codingStandards,
                                        ReviewConfig config,
                                        Long scmConfigId) {
        if (ruleRetrievalService != null) {
            String ruleReference = ruleRetrievalService.buildRuleReference(context, config, scmConfigId);
            if (StringUtils.hasText(ruleReference) && !EMPTY_RULE_REFERENCE.equals(ruleReference.trim())) {
                return ruleReference;
            }
        }
        log.debug("规则片段未命中，回退历史编码规范加载结果: filePath={}",
                context != null ? context.getFilePath() : null);
        return trimStandards(codingStandards);
    }

    private String trimStandards(String codingStandards) {
        if (codingStandards == null) {
            return "// 未加载到编码规范";
        }
        if (codingStandards.length() <= MAX_STANDARDS_CHARS) {
            return codingStandards;
        }
        return codingStandards.substring(0, MAX_STANDARDS_CHARS)
                + "\n\n// ... 编码规范内容较长，已按长度截断，保留前置关键部分 ...";
    }

    private String trimSnippet(String content, int maxChars) {
        if (content == null) {
            return "// 关联类内容为空";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= 32) {
            return "// 关联类摘要已省略";
        }
        return content.substring(0, maxChars) + "\n// ... 关联类摘要已截断 ...";
    }

    private String resolveLanguageTag(ReviewContext context) {
        if (context.getLanguageTag() == null || context.getLanguageTag().isBlank()) {
            return "text";
        }
        return context.getLanguageTag();
    }

    private String buildProfileSection(ReviewContext context, ReviewConfig config) {
        if (!config.getProfile().isInjectEnabled()
                || reviewerProfileService == null
                || vectorKnowledgeService == null
                || context.getAuthorName() == null
                || context.getScmProvider() == null) {
            return "";
        }

        ReviewerProfile profile = reviewerProfileService.getProfile(context.getAuthorName(), context.getScmProvider());
        if (profile == null) {
            return "";
        }

        String queryText = buildIssueSearchText(context);
        List<Document> similarIssues = vectorKnowledgeService.searchSimilarIssues(
                queryText,
                resolveAuthorId(context),
                context.getProjectName(),
                config.getProfile().getInjectTopk(),
                config.getVector().getMinSimilarity());

        StringBuilder prompt = new StringBuilder();
        prompt.append("## 提交者画像\n\n");
        prompt.append("- 近 ").append(config.getProfile().getRecentReviewCount())
                .append(" 次评审平均分：")
                .append(resolveAvgScore(profile.getAvgScore())).append("/100\n");

        if (similarIssues.isEmpty()) {
            prompt.append("- 暂无相似历史问题样本\n\n");
            return prompt.toString();
        }

        prompt.append("- 近期相似变更中出现过的问题：\n");
        for (Document doc : similarIssues) {
            prompt.append("  - ").append(formatIssueReference(doc)).append("\n");
        }
        prompt.append("- 请在评审时重点检查以上薄弱点。\n\n");
        return prompt.toString();
    }

    private String buildTeamKnowledgeSection(ReviewContext context, ReviewConfig config) {
        if (vectorKnowledgeService == null || !config.getVector().isEnabled()) {
            return "";
        }

        List<Document> similarIssues = vectorKnowledgeService.searchSimilarIssues(
                buildIssueSearchText(context),
                null,
                context.getProjectName(),
                config.getVector().getReviewSearchTopk(),
                config.getVector().getMinSimilarity());

        if (similarIssues.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("## 历史相似问题参考\n\n");
        for (Document doc : similarIssues) {
            prompt.append("- ").append(formatIssueReference(doc)).append("\n");
        }
        prompt.append("\n");
        return prompt.toString();
    }

    private String buildRelatedClassesSection(ReviewContext context) {
        if (context.getRelatedClasses() == null || context.getRelatedClasses().isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("### 关联类摘要（供参考调用关系）\n\n");
        int remainingChars = MAX_RELATED_CLASS_TOTAL_CHARS;
        for (Map.Entry<String, String> entry : context.getRelatedClasses().entrySet()) {
            if (remainingChars <= 0) {
                prompt.append("// ... 更多关联类已省略，以控制上下文大小 ...\n\n");
                break;
            }
            prompt.append("#### ").append(entry.getKey()).append("\n```java\n");
            String snippet = trimSnippet(entry.getValue(), remainingChars);
            prompt.append(snippet);
            prompt.append("\n```\n\n");
            remainingChars -= snippet.length();
        }
        return prompt.toString();
    }

    private String buildIssueSearchText(ReviewContext context) {
        StringBuilder sb = new StringBuilder();
        if (context.getFilePath() != null) {
            sb.append(context.getFilePath()).append(' ');
        }
        if (context.getDiffContent() != null) {
            sb.append(context.getDiffContent());
        }
        return sb.toString().trim();
    }

    private String formatIssueReference(Document doc) {
        String rule = Objects.toString(doc.getMetadata().get("rule"), "");
        String category = Objects.toString(doc.getMetadata().get("category"), "");
        String text = doc.getText() != null ? doc.getText() : "";
        String compactText = text.length() > 120 ? text.substring(0, 120) + "..." : text;
        if (!rule.isBlank()) {
            return "[" + rule + "] " + compactText;
        }
        if (!category.isBlank()) {
            return "[" + category + "] " + compactText;
        }
        return compactText;
    }

    private String resolveAuthorId(ReviewContext context) {
        if (context.getAuthorId() != null && !context.getAuthorId().isBlank()) {
            return context.getAuthorId();
        }
        return context.getScmProvider() + ":" + context.getAuthorName();
    }

    private String resolveAvgScore(BigDecimal avgScore) {
        if (avgScore == null) {
            return "-";
        }
        return avgScore.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String renderTemplate(String template, Map<String, String> placeholders) {
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private static RuleRetrievalService createFallbackRuleRetrievalService() {
        return (context, reviewConfig, scmConfigId) -> EMPTY_RULE_REFERENCE;
    }

    private static RulePromptService createFallbackRulePromptService() {
        return new RulePromptService() {
            @Override
            public String getTemplateContent(String templateCode, Long scmConfigId) {
                return "";
            }

            @Override
            public String buildReviewPromptSkeleton(ReviewConfig config, Long scmConfigId) {
                return "";
            }

            @Override
            public String buildReviewJsonRepairPrompt(String originalResponse, Long scmConfigId) {
                return "";
            }

            @Override
            public String buildJsonRepairPrompt(String templateCode, String originalResponse, Long scmConfigId) {
                return "";
            }

            @Override
            public String resolveReviewFocus(String languageTag, ReviewConfig config) {
                return "";
            }

            @Override
            public String getErrorAnalysisPromptSkeleton(Long scmConfigId) {
                return "";
            }

            @Override
            public String buildErrorAnalysisJsonRepairPrompt(String originalResponse, Long scmConfigId) {
                return "";
            }
        };
    }
}
