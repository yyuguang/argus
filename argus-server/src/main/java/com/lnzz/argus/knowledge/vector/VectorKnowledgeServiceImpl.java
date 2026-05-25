package com.lnzz.argus.knowledge.vector;

import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 向量知识库服务实现 — 基于 Redis Stack + Spring AI RedisVectorStore。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "argus.vector.enabled", havingValue = "true")
public class VectorKnowledgeServiceImpl implements VectorKnowledgeService {

    private final RedisVectorStore reviewIssueStore;
    private final RedisVectorStore knowledgeEntryStore;
    private final RedisVectorStore ruleDocumentStore;
    private final EmbeddingService embeddingService;
    private final ExecutorService asyncExecutor;

    public VectorKnowledgeServiceImpl(
            @Qualifier("reviewIssueVectorStore") RedisVectorStore reviewIssueStore,
            @Qualifier("knowledgeEntryVectorStore") RedisVectorStore knowledgeEntryStore,
            @Qualifier("ruleDocumentVectorStore") RedisVectorStore ruleDocumentStore,
            EmbeddingService embeddingService) {
        this.reviewIssueStore = reviewIssueStore;
        this.knowledgeEntryStore = knowledgeEntryStore;
        this.ruleDocumentStore = ruleDocumentStore;
        this.embeddingService = embeddingService;
        this.asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "argus-vectorkb");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Phase 1：代码评审 Issue ====================

    @Override
    public void storeReviewIssue(ReviewIssue issue, Long taskId, String authorId, String projectName) {
        if (issue == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                String text = embeddingService.buildIssueEmbeddingText(issue);
                Map<String, Object> meta = new java.util.HashMap<>();
                putIfNotNull(meta, "severity", issue.getSeverity());
                putIfNotNull(meta, "category", issue.getCategory());
                putIfNotNull(meta, "rule", issue.getRule());
                putIfNotNull(meta, "author_id", authorId);
                putIfNotNull(meta, "project_name", projectName);
                putIfNotNull(meta, "issue_id", issue.getId() != null ? issue.getId().toString() : null);
                putIfNotNull(meta, "task_id", taskId != null ? taskId.toString() : null);

                Document doc = Document.builder()
                        .id("review:issue:" + issue.getId())
                        .text(text)
                        .metadata(meta)
                        .build();
                reviewIssueStore.add(List.of(doc));
                log.debug("评审 Issue 已写入向量库: id={}", issue.getId());
            } catch (Exception e) {
                log.warn("评审 Issue 向量写入失败: id={}, error={}", issue.getId(), e.getMessage());
            }
        }, asyncExecutor);
    }

    @Override
    public List<Document> searchSimilarIssues(String queryText, String authorId,
                                              String projectName, int topK, double minSimilarity) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .similarityThreshold(minSimilarity);

            Filter.Expression tagFilter = buildReviewIssueFilter(authorId, projectName);
            if (tagFilter != null) {
                builder.filterExpression(tagFilter);
            }

            return reviewIssueStore.doSimilaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("评审 Issue 向量检索失败，降级返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Document> getAuthorTopIssues(String authorId, int topK) {
        try {
            Filter.Expression filter = new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("author_id"),
                    new Filter.Value(authorId));

            SearchRequest request = SearchRequest.builder()
                    .query("*")
                    .topK(topK)
                    .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                    .filterExpression(filter)
                    .build();

            return reviewIssueStore.doSimilaritySearch(request);
        } catch (Exception e) {
            log.warn("作者 Issue 聚类查询失败，降级返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== Phase 2：错误知识条目 ====================

    @Override
    public void storeKnowledgeEntry(KnowledgeEntry entry) {
        if (entry == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                String text = buildKnowledgeEmbeddingText(entry);
                Map<String, Object> meta = new java.util.HashMap<>();
                putIfNotNull(meta, "error_type", entry.getErrorType());
                putIfNotNull(meta, "app_name", entry.getAppName());
                putIfNotNull(meta, "status", entry.getStatus());
                putIfNotNull(meta, "title", entry.getTitle());
                putIfNotNull(meta, "root_cause", entry.getRootCause());
                putIfNotNull(meta, "fix_suggestion", entry.getFixSuggestion());
                putIfNotNull(meta, "prevention_advice", entry.getPreventionAdvice());
                putIfNotNull(meta, "source_event_id", entry.getSourceEventId());
                putIfNotNull(meta, "source_analysis_id", entry.getSourceAnalysisId());

                Document doc = Document.builder()
                        .id("knowledge:entry:" + entry.getId())
                        .text(text)
                        .metadata(meta)
                        .build();
                knowledgeEntryStore.add(List.of(doc));
                log.debug("知识条目已写入向量库: id={}", entry.getId());
            } catch (Exception e) {
                log.warn("知识条目向量写入失败: id={}, error={}", entry.getId(), e.getMessage());
            }
        }, asyncExecutor);
    }

    @Override
    public List<Document> searchSimilarErrors(String queryText, String errorType,
                                              String appName, int topK, double minSimilarity) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .similarityThreshold(minSimilarity);

            Filter.Expression tagFilter = buildKnowledgeFilter(errorType, appName, null);
            if (tagFilter != null) {
                builder.filterExpression(tagFilter);
            }

            return knowledgeEntryStore.doSimilaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("错误知识向量检索失败，降级返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Document> findKnownFixes(String queryText, String errorType,
                                         String appName, int topK, double minScore) {
        try {
            // IN 过滤 CONFIRMED + WHITELIST
            Filter.Expression statusFilter = new Filter.Expression(
                    Filter.ExpressionType.IN,
                    new Filter.Key("status"),
                    new Filter.Value(List.of("CONFIRMED", "WHITELIST")));

            Filter.Expression tagFilter = buildKnowledgeFilter(errorType, appName, statusFilter);

            SearchRequest request = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .similarityThreshold(minScore)
                    .filterExpression(tagFilter)
                    .build();

            return knowledgeEntryStore.doSimilaritySearch(request);
        } catch (Exception e) {
            log.warn("已知修复方案查询失败，降级返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== Phase 5：规则文档分块 ====================

    @Override
    public boolean storeRuleDocumentChunks(RuleDocument document, List<RuleDocumentChunk> chunks) {
        if (document == null || document.getId() == null) {
            return false;
        }
        try {
            deleteRuleDocumentChunks(document.getId());
            if (chunks == null || chunks.isEmpty()) {
                log.info("规则文档分块向量写入跳过, documentId={}, reason=无有效分块", document.getId());
                return true;
            }
            List<Document> documents = chunks.stream()
                    .map(chunk -> buildRuleChunkDocument(document, chunk))
                    .toList();
            ruleDocumentStore.add(documents);
            log.info("规则文档分块已写入向量库, documentId={}, chunkCount={}", document.getId(), documents.size());
            return true;
        } catch (Exception ex) {
            log.warn("规则文档分块向量写入失败, documentId={}, error={}", document.getId(), ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public void deleteRuleDocumentChunks(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            Filter.Expression filter = new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("document_id"),
                    new Filter.Value(String.valueOf(documentId)));
            ruleDocumentStore.delete(filter);
            log.info("规则文档分块向量删除完成, documentId={}", documentId);
        } catch (Exception ex) {
            log.warn("规则文档分块向量删除失败, documentId={}, error={}", documentId, ex.getMessage(), ex);
        }
    }

    @Override
    public List<Document> searchRuleDocumentChunks(String queryText, List<String> categories,
                                                   Long scmConfigId, int topK, double minSimilarity) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .similarityThreshold(minSimilarity);
            Filter.Expression filter = buildRuleDocumentFilter(categories, scmConfigId);
            if (filter != null) {
                builder.filterExpression(filter);
            }
            return ruleDocumentStore.doSimilaritySearch(builder.build());
        } catch (Exception ex) {
            log.warn("规则文档向量检索失败，降级返回空列表: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    // ======================== 内部 ========================

    /** 构建评审 Issue 的 TAG 过滤表达式：author_id 和 project_name 可选 AND */
    private Filter.Expression buildReviewIssueFilter(String authorId, String projectName) {
        List<Filter.Expression> parts = new ArrayList<>();
        if (authorId != null && !authorId.isEmpty()) {
            parts.add(new Filter.Expression(Filter.ExpressionType.EQ,
                    new Filter.Key("author_id"), new Filter.Value(authorId)));
        }
        if (projectName != null && !projectName.isEmpty()) {
            parts.add(new Filter.Expression(Filter.ExpressionType.EQ,
                    new Filter.Key("project_name"), new Filter.Value(projectName)));
        }
        if (parts.isEmpty()) return null;
        if (parts.size() == 1) return parts.get(0);
        return new Filter.Expression(Filter.ExpressionType.AND, parts.get(0), parts.get(1));
    }

    /** 构建知识条目的 TAG 过滤表达式：error_type / app_name / extra 可选 AND */
    private Filter.Expression buildKnowledgeFilter(String errorType, String appName,
                                                    Filter.Expression extra) {
        List<Filter.Expression> parts = new ArrayList<>();
        if (errorType != null && !errorType.isEmpty()) {
            parts.add(new Filter.Expression(Filter.ExpressionType.EQ,
                    new Filter.Key("error_type"), new Filter.Value(errorType)));
        }
        if (appName != null && !appName.isEmpty()) {
            parts.add(new Filter.Expression(Filter.ExpressionType.EQ,
                    new Filter.Key("app_name"), new Filter.Value(appName)));
        }
        if (extra != null) {
            parts.add(extra);
        }
        if (parts.isEmpty()) return null;
        Filter.Expression result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = new Filter.Expression(Filter.ExpressionType.AND, result, parts.get(i));
        }
        return result;
    }

    private String buildKnowledgeEmbeddingText(KnowledgeEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.getErrorType() != null) sb.append(entry.getErrorType());
        if (entry.getAppName() != null) sb.append(' ').append(entry.getAppName());
        if (entry.getErrorPattern() != null) sb.append(' ').append(entry.getErrorPattern());
        if (entry.getRootCause() != null) sb.append(' ').append(entry.getRootCause());
        if (entry.getFixSuggestion() != null) sb.append(' ').append(entry.getFixSuggestion());
        return sb.toString().trim();
    }

    private Document buildRuleChunkDocument(RuleDocument document, RuleDocumentChunk chunk) {
        Map<String, Object> meta = new java.util.HashMap<>();
        putIfNotNull(meta, "document_id", document.getId().toString());
        putIfNotNull(meta, "category", document.getCategory());
        putIfNotNull(meta, "scope", document.getScope());
        putIfNotNull(meta, "scm_config_id",
                document.getScmConfigId() == null ? null : document.getScmConfigId().toString());
        putIfNotNull(meta, "status", document.getStatus());
        putIfNotNull(meta, "version_no",
                document.getVersionNo() == null ? null : document.getVersionNo().toString());
        putIfNotNull(meta, "chunk_no",
                chunk.getChunkNo() == null ? null : chunk.getChunkNo().toString());
        putIfNotNull(meta, "title", chunk.getTitle());
        return Document.builder()
                .id("rule:document:chunk:" + document.getId() + ":" + chunk.getChunkNo())
                .text(buildRuleChunkEmbeddingText(document, chunk))
                .metadata(meta)
                .build();
    }

    private String buildRuleChunkEmbeddingText(RuleDocument document, RuleDocumentChunk chunk) {
        StringBuilder sb = new StringBuilder();
        if (document.getCategory() != null) {
            sb.append('[').append(document.getCategory()).append(']');
        }
        if (document.getScope() != null) {
            sb.append('[').append(document.getScope()).append(']');
        }
        if (document.getDocumentName() != null) {
            sb.append(' ').append(document.getDocumentName());
        }
        if (chunk.getTitle() != null) {
            sb.append(' ').append(chunk.getTitle());
        }
        if (chunk.getContentText() != null) {
            sb.append(' ').append(chunk.getContentText());
        }
        return sb.toString().trim();
    }

    private Filter.Expression buildRuleDocumentFilter(List<String> categories, Long scmConfigId) {
        Filter.Expression activeFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("status"),
                new Filter.Value("ACTIVE"));

        Filter.Expression scopeFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("scope"),
                new Filter.Value("GLOBAL"));
        if (scmConfigId != null) {
            Filter.Expression scmScopeFilter = new Filter.Expression(
                    Filter.ExpressionType.AND,
                    new Filter.Expression(Filter.ExpressionType.EQ,
                            new Filter.Key("scope"),
                            new Filter.Value("SCM")),
                    new Filter.Expression(Filter.ExpressionType.EQ,
                            new Filter.Key("scm_config_id"),
                            new Filter.Value(String.valueOf(scmConfigId))));
            scopeFilter = new Filter.Expression(Filter.ExpressionType.OR, scopeFilter, scmScopeFilter);
        }

        Filter.Expression result = new Filter.Expression(Filter.ExpressionType.AND, activeFilter, scopeFilter);
        if (categories != null && !categories.isEmpty()) {
            Filter.Expression categoryFilter = new Filter.Expression(
                    Filter.ExpressionType.IN,
                    new Filter.Key("category"),
                    new Filter.Value(categories));
            result = new Filter.Expression(Filter.ExpressionType.AND, result, categoryFilter);
        }
        return result;
    }

    private void putIfNotNull(Map<String, Object> meta, String key, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            meta.put(key, value);
        }
    }
}
