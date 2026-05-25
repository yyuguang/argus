package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.rule.service.RuleRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @classname: RuleRetrievalServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 23:58
 * @description: 规则检索服务实现，负责组装检索文本、执行规则分块召回和控制注入预算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleRetrievalServiceImpl implements RuleRetrievalService {

    private static final String FALLBACK_MESSAGE = "// 本次未注入规范片段";

    private final VectorKnowledgeService vectorKnowledgeService;

    @Value("${argus.vector.enabled:false}")
    private boolean vectorEnabled;

    @Value("${argus.rule.retrieval.max-total-chars:4000}")
    private int maxTotalChars;

    @Value("${argus.rule.retrieval.max-chunk-chars:800}")
    private int maxChunkChars;

    /**
     * 构建评审上下文对应的规则参考文本。
     *
     * @param context      评审上下文
     * @param reviewConfig 仓库级评审配置
     * @param scmConfigId  当前仓库配置 ID，可为空
     * @return 可直接注入 Prompt 的规则参考文本
     */
    @Override
    public String buildRuleReference(ReviewContext context, ReviewConfig reviewConfig, Long scmConfigId) {
        if (context == null || reviewConfig == null) {
            return FALLBACK_MESSAGE;
        }
        if (!vectorEnabled || !reviewConfig.getVector().isEnabled()) {
            return FALLBACK_MESSAGE;
        }
        List<String> categories = normalizeCategories(reviewConfig.getRule().getStandardCategories());
        String queryText = buildQueryText(context);
        if (!StringUtils.hasText(queryText)) {
            return FALLBACK_MESSAGE;
        }
        List<Document> documents = vectorKnowledgeService.searchRuleDocumentChunks(
                queryText,
                categories,
                scmConfigId,
                reviewConfig.getVector().getReviewSearchTopk(),
                reviewConfig.getVector().getMinSimilarity());
        if (documents.isEmpty()) {
            return FALLBACK_MESSAGE;
        }
        String rendered = renderRuleDocuments(documents);
        log.info("规则片段检索完成, filePath={}, scmConfigId={}, hitCount={}",
                context.getFilePath(), scmConfigId, documents.size());
        return rendered;
    }

    private String buildQueryText(ReviewContext context) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, context.getFilePath());
        appendPart(sb, context.getLanguageTag());
        appendPart(sb, context.getProjectName());
        appendPart(sb, context.getDiffContent());
        return sb.toString().trim();
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of("CODING", "API", "DATABASE", "SECURITY", "CUSTOM");
        }
        return categories.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String renderRuleDocuments(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        int remainingChars = Math.max(maxTotalChars, 0);
        for (Document document : documents) {
            if (remainingChars <= 0) {
                break;
            }
            String title = Objects.toString(document.getMetadata().get("title"), "未命名规则片段");
            String category = Objects.toString(document.getMetadata().get("category"), "UNKNOWN");
            String scope = Objects.toString(document.getMetadata().get("scope"), "UNKNOWN");
            String text = trimText(document.getText(), Math.min(maxChunkChars, remainingChars));
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String block = "### [" + category + "][" + scope + "] " + title + "\n"
                    + text + "\n\n";
            if (block.length() > remainingChars) {
                block = trimText(block, remainingChars);
            }
            sb.append(block);
            remainingChars -= block.length();
        }
        return sb.isEmpty() ? FALLBACK_MESSAGE : sb.toString().trim();
    }

    private String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 24) {
            return normalized.substring(0, maxChars);
        }
        return normalized.substring(0, maxChars) + "\n// ... 规则片段已截断 ...";
    }

    private void appendPart(StringBuilder sb, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(value.trim());
    }
}
