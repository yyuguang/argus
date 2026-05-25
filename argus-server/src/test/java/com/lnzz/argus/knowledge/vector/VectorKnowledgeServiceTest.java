package com.lnzz.argus.knowledge.vector;

import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.review.entity.ReviewIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VectorKnowledgeService - 向量存储与语义检索")
class VectorKnowledgeServiceTest {

    @Mock
    private RedisVectorStore reviewIssueStore;
    @Mock
    private RedisVectorStore knowledgeEntryStore;
    @Mock
    private RedisVectorStore ruleDocumentStore;
    @Mock
    private EmbeddingService embeddingService;

    private VectorKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VectorKnowledgeServiceImpl(reviewIssueStore, knowledgeEntryStore, ruleDocumentStore, embeddingService);
    }

    // ==================== storeReviewIssue ====================

    @Test
    @DisplayName("storeReviewIssue 构建正确 Document 并写入")
    void storeReviewIssueBuildsCorrectDocument() throws Exception {
        ReviewIssue issue = new ReviewIssue();
        issue.setId(100L);
        issue.setSeverity("CRITICAL");
        issue.setCategory("COMPLIANCE");
        issue.setRule("SQL注入防护");
        issue.setFilePath("UserController.java");
        issue.setDescription("未参数化的SQL拼接");

        when(embeddingService.buildIssueEmbeddingText(issue)).thenReturn("[CRITICAL][COMPLIANCE] SQL注入防护 UserController.java 未参数化的SQL拼接");

        service.storeReviewIssue(issue, 1L, "zhangsan", "demo-project");

        // 等待异步完成
        Thread.sleep(200);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewIssueStore).add(captor.capture());
        List<Document> docs = captor.getValue();
        assertEquals(1, docs.size());
        Document doc = docs.get(0);
        assertEquals("review:issue:100", doc.getId());
        assertTrue(doc.getText().contains("[CRITICAL]"));
        assertEquals("CRITICAL", doc.getMetadata().get("severity"));
        assertEquals("zhangsan", doc.getMetadata().get("author_id"));
        assertEquals("demo-project", doc.getMetadata().get("project_name"));
    }

    @Test
    @DisplayName("storeReviewIssue null 输入不抛异常")
    void storeReviewIssueNullInput() {
        assertDoesNotThrow(() -> service.storeReviewIssue(null, null, null, null));
    }

    @Test
    @DisplayName("storeReviewIssue 向量库异常不传播")
    void storeReviewIssueDegradesOnError() throws Exception {
        ReviewIssue issue = new ReviewIssue();
        issue.setId(1L);
        issue.setSeverity("MINOR");
        when(embeddingService.buildIssueEmbeddingText(issue)).thenReturn("test");

        doThrow(new RuntimeException("Redis 不可用")).when(reviewIssueStore).add(any());

        // 异步执行，不向外传播异常
        assertDoesNotThrow(() -> service.storeReviewIssue(issue, 1L, "test", "test"));
    }

    // ==================== searchSimilarIssues ====================

    @Test
    @DisplayName("searchSimilarIssues 无过滤条件正常检索")
    void searchSimilarIssuesNoFilter() {
        Document mockDoc = Document.builder().text("test").build();
        when(reviewIssueStore.doSimilaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(mockDoc));

        List<Document> results = service.searchSimilarIssues("N+1查询", null, null, 5, 0.7);

        assertEquals(1, results.size());
        verify(reviewIssueStore).doSimilaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("searchSimilarIssues 按 author_id + project_name 过滤")
    void searchSimilarIssuesWithTagFilter() {
        when(reviewIssueStore.doSimilaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.searchSimilarIssues("空指针", "zhangsan", "demo-project", 3, 0.7);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(reviewIssueStore).doSimilaritySearch(captor.capture());
        SearchRequest req = captor.getValue();
        assertTrue(req.hasFilterExpression());
        assertEquals(3, req.getTopK());
        assertEquals(0.7, req.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("searchSimilarIssues Redis 不可用时降级返回空列表")
    void searchSimilarIssuesDegrades() {
        when(reviewIssueStore.doSimilaritySearch(any())).thenThrow(new RuntimeException("连接超时"));

        List<Document> results = service.searchSimilarIssues("test", null, null, 5, 0.7);

        assertTrue(results.isEmpty());
    }

    // ==================== getAuthorTopIssues ====================

    @Test
    @DisplayName("getAuthorTopIssues 按 author_id 聚类查询")
    void getAuthorTopIssuesFiltersByAuthor() {
        Document doc1 = Document.builder().text("N+1查询").build();
        Document doc2 = Document.builder().text("空指针风险").build();
        when(reviewIssueStore.doSimilaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1, doc2));

        List<Document> results = service.getAuthorTopIssues("zhangsan", 10);

        assertEquals(2, results.size());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(reviewIssueStore).doSimilaritySearch(captor.capture());
        assertTrue(captor.getValue().hasFilterExpression());
    }

    // ==================== storeKnowledgeEntry ====================

    @Test
    @DisplayName("storeKnowledgeEntry 构建正确 Document 并写入")
    void storeKnowledgeEntryBuildsCorrectDocument() throws Exception {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(200L);
        entry.setErrorType("NULL_POINTER");
        entry.setAppName("order-service");
        entry.setErrorPattern("空指针异常");
        entry.setRootCause("未对请求参数做 null 校验");
        entry.setFixSuggestion("增加 @NotNull 校验");
        entry.setStatus("DRAFT");

        service.storeKnowledgeEntry(entry);
        Thread.sleep(200);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeEntryStore).add(captor.capture());
        Document doc = captor.getValue().get(0);
        assertEquals("knowledge:entry:200", doc.getId());
        assertTrue(doc.getText().contains("NULL_POINTER"));
        assertTrue(doc.getText().contains("空指针异常"));
        assertEquals("NULL_POINTER", doc.getMetadata().get("error_type"));
        assertEquals("DRAFT", doc.getMetadata().get("status"));
    }

    @Test
    @DisplayName("storeKnowledgeEntry null 输入不抛异常")
    void storeKnowledgeEntryNullInput() {
        assertDoesNotThrow(() -> service.storeKnowledgeEntry(null));
    }

    // ==================== searchSimilarErrors ====================

    @Test
    @DisplayName("searchSimilarErrors 按 error_type + app_name 过滤")
    void searchSimilarErrorsWithTagFilter() {
        when(knowledgeEntryStore.doSimilaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        service.searchSimilarErrors("连接池耗尽", "SQL_EXCEPTION", "order-service", 5, 0.7);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(knowledgeEntryStore).doSimilaritySearch(captor.capture());
        assertTrue(captor.getValue().hasFilterExpression());
    }

    // ==================== findKnownFixes ====================

    @Test
    @DisplayName("findKnownFixes 仅返回 CONFIRMED/WHITELIST 状态条目")
    void findKnownFixesFiltersConfirmedAndWhitelist() {
        Document fixDoc = Document.builder()
                .text("修复方案: 增加连接池大小")
                .metadata(Map.of("status", "CONFIRMED"))
                .build();
        when(knowledgeEntryStore.doSimilaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(fixDoc));

        List<Document> results = service.findKnownFixes("连接池耗尽", "SQL_EXCEPTION", "order-service", 3, 0.7);

        assertEquals(1, results.size());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(knowledgeEntryStore).doSimilaritySearch(captor.capture());
        assertTrue(captor.getValue().hasFilterExpression());
    }

    @Test
    @DisplayName("searchSimilarErrors Redis 不可用时降级")
    void searchSimilarErrorsDegrades() {
        when(knowledgeEntryStore.doSimilaritySearch(any())).thenThrow(new RuntimeException("超时"));

        List<Document> results = service.searchSimilarErrors("test", null, null, 5, 0.7);

        assertTrue(results.isEmpty());
    }
}
