package com.lnzz.argus.knowledge.vector;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量知识库服务 — 基于 Redis Stack 的语义存储与检索。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface VectorKnowledgeService {

    // ==================== Phase 1：代码评审 Issue ====================

    /**
     * 存储评审 Issue 到向量库。
     *
     * @param issue       评审问题
     * @param taskId      评审任务 ID
     * @param authorId    提交者标识
     * @param projectName 项目名称
     */
    void storeReviewIssue(com.lnzz.argus.review.entity.ReviewIssue issue,
                          Long taskId, String authorId, String projectName);

    /**
     * 语义检索相似的历史评审 Issue。
     *
     * @param queryText     当前 Issue 描述文本
     * @param authorId      可选，按提交者过滤（null 则不过滤）
     * @param projectName   可选，按项目过滤（null 则不过滤）
     * @param topK          返回条数
     * @param minSimilarity 最低相似度阈值
     */
    List<Document> searchSimilarIssues(String queryText, String authorId,
                                       String projectName, int topK, double minSimilarity);

    /**
     * 获取某作者的高频 Issue 模式（用于个人画像聚类）。
     */
    List<Document> getAuthorTopIssues(String authorId, int topK);

    // ==================== Phase 2：错误知识条目 ====================

    /**
     * 存储知识条目到向量库。
     */
    void storeKnowledgeEntry(com.lnzz.argus.knowledge.entity.KnowledgeEntry entry);

    /**
     * 语义检索相似的历史错误知识。
     *
     * @param queryText     错误描述文本
     * @param errorType     可选，按错误类型过滤（null 则不过滤）
     * @param appName       可选，按应用过滤（null 则不过滤）
     * @param topK          返回条数
     * @param minSimilarity 最低相似度阈值
     */
    List<Document> searchSimilarErrors(String queryText, String errorType,
                                       String appName, int topK, double minSimilarity);

    /**
     * 查找已知修复方案（仅 CONFIRMED/WHITELIST 状态，分数高于 minScore）。
     */
    List<Document> findKnownFixes(String queryText, String errorType,
                                  String appName, int topK, double minScore);
}
