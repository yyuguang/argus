package com.lnzz.argus.knowledge.vector;

import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量知识库关闭时的空实现，确保主流程可用并回退到字符串匹配。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "argus.vector.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVectorKnowledgeService implements VectorKnowledgeService {

    @Override
    public void storeReviewIssue(com.lnzz.argus.review.entity.ReviewIssue issue,
                                 Long taskId, String authorId, String projectName) {
    }

    @Override
    public List<Document> searchSimilarIssues(String queryText, String authorId,
                                              String projectName, int topK, double minSimilarity) {
        return List.of();
    }

    @Override
    public List<Document> getAuthorTopIssues(String authorId, int topK) {
        return List.of();
    }

    @Override
    public void storeKnowledgeEntry(com.lnzz.argus.knowledge.entity.KnowledgeEntry entry) {
    }

    @Override
    public List<Document> searchSimilarErrors(String queryText, String errorType,
                                              String appName, int topK, double minSimilarity) {
        return List.of();
    }

    @Override
    public List<Document> findKnownFixes(String queryText, String errorType,
                                         String appName, int topK, double minScore) {
        return List.of();
    }
}
