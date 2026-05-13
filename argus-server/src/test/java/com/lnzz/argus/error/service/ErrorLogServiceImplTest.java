package com.lnzz.argus.error.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.config.ErrorProcessingProperties;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.mapper.AgentPushBatchMapper;
import com.lnzz.argus.error.mapper.ErrorContextLogMapper;
import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.error.metrics.ErrorLogMetrics;
import com.lnzz.argus.error.model.ErrorLogEntry;
import com.lnzz.argus.error.parse.ErrorTypeIdentifier;
import com.lnzz.argus.error.parse.FingerprintGenerator;
import com.lnzz.argus.error.parse.IdentifierExtractor;
import com.lnzz.argus.common.enums.ProcessingStatus;
import com.lnzz.argus.error.parse.SeverityRuleEngine;
import com.lnzz.argus.error.parse.StackTraceParser;
import com.lnzz.argus.error.service.impl.ErrorLogServiceImpl;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.common.enums.KnowledgeEntryStatus;
import com.lnzz.argus.knowledge.service.KnowledgeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorLogServiceImpl - 去重与分析闸门")
class ErrorLogServiceImplTest {

    @Mock
    private ErrorEventMapper errorEventMapper;
    @Mock
    private AgentPushBatchMapper batchMapper;
    @Mock
    private ErrorLogMetrics metrics;
    @Mock
    private ErrorContextLogMapper contextLogMapper;
    @Mock
    private ErrorAnalysisService analysisService;
    @Mock
    private KnowledgeMatcher knowledgeMatcher;

    private ErrorLogServiceImpl service;
    private ErrorProcessingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ErrorProcessingProperties();
        service = new ErrorLogServiceImpl(
                errorEventMapper,
                batchMapper,
                new ObjectMapper(),
                metrics,
                new ErrorTypeIdentifier(),
                new StackTraceParser(),
                new IdentifierExtractor(),
                new FingerprintGenerator(),
                new SeverityRuleEngine(),
                contextLogMapper,
                analysisService,
                knowledgeMatcher,
                properties);
    }

    @Test
    @DisplayName("同 app + environment + fingerprint 窗口期内聚合，不新增分析")
    void aggregateWithinDedupWindow() {
        ErrorEvent existing = new ErrorEvent();
        existing.setId(7L);
        existing.setOccurredAt(LocalDateTime.now().minusSeconds(10));
        existing.setLastOccurredAt(LocalDateTime.now().minusSeconds(10));

        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(existing);

        Map<String, Object> result = service.receiveSingle(appLogEntry("log-1", "PROD"));

        assertEquals("AGGREGATED", result.get("status"));
        assertEquals(7L, result.get("existingEventId"));
        verify(errorEventMapper).aggregateOccurrence(org.mockito.ArgumentMatchers.eq(7L), any(), any(), any());
        verify(errorEventMapper, never()).insert(any(ErrorEvent.class));
        verify(analysisService, never()).analyzeEvent(any());
    }

    @Test
    @DisplayName("Nginx 4xx 低风险事件入库但跳过 AI 分析")
    void nginx4xxSkipsAnalysis() {
        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(null);

        Map<String, Object> result = service.receiveSingle(nginxAccessEntry());

        assertEquals("ACCEPTED", result.get("status"));
        assertEquals("AGGREGATE_ONLY", result.get("analysisDecision"));
        assertEquals(Boolean.TRUE, result.get("analysisSkipped"));
        verify(analysisService, never()).analyzeEvent(any());

        ArgumentCaptor<ErrorEvent> eventCaptor = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(errorEventMapper).insert(eventCaptor.capture());
        ErrorEvent event = eventCaptor.getValue();
        assertEquals("NGINX_4XX", event.getErrorType());
        assertEquals("P3", event.getSeverity());
        assertEquals(ProcessingStatus.PARSED.getCode(), event.getProcessingStatus());
        assertNotNull(event.getErrorFingerprint());
    }

    @Test
    @DisplayName("同指纹窗口外再次出现按已知指纹判定，不再按新指纹升级")
    void knownFingerprintOutsideWindowNotNewFingerprint() {
        ErrorEvent existing = new ErrorEvent();
        existing.setId(8L);
        existing.setOccurredAt(LocalDateTime.now().minusMinutes(10));
        existing.setLastOccurredAt(LocalDateTime.now().minusMinutes(10));

        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        Map<String, Object> result;
        try {
            result = service.receiveSingle(appLogEntry("log-2", "DEV"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals("ACCEPTED", result.get("status"));

        ArgumentCaptor<ErrorEvent> eventCaptor = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(errorEventMapper).insert(eventCaptor.capture());
        ErrorEvent event = eventCaptor.getValue();
        assertEquals("P2", event.getSeverity());
        assertEquals("CONDITIONAL_ANALYZE", event.getAnalysisDecision());
        assertTrue(event.getSeverityReason().contains("决策: 条件分析"));
    }

    @Test
    @DisplayName("精确命中已确认知识时跳过 AI 分析")
    void confirmedKnowledgeHitSkipsAnalysis() {
        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(null);
        when(knowledgeMatcher.findSimilar(any(ErrorEvent.class), eq(1)))
                .thenAnswer(invocation -> {
                    ErrorEvent event = invocation.getArgument(0);
                    KnowledgeEntry entry = new KnowledgeEntry();
                    entry.setId(99L);
                    entry.setErrorFingerprint(event.getErrorFingerprint());
                    entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
                    return List.of(entry);
                });

        Map<String, Object> result = service.receiveSingle(appLogEntry("log-3", "PROD"));

        assertEquals("ACCEPTED", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("analysisSkipped"));
        assertEquals("KNOWLEDGE_HIT", result.get("skipReason"));
        assertEquals(99L, result.get("knowledgeEntryId"));
        verify(analysisService, never()).analyzeEvent(any());
    }

    @Test
    @DisplayName("配置关闭知识命中跳过时继续按分析闸门触发 AI")
    void disabledKnowledgeSkipKeepsAnalysisGate() {
        properties.getAnalysis().setSkipKnownKnowledge(false);
        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(null);

        TransactionSynchronizationManager.initSynchronization();
        try {
            Map<String, Object> result = service.receiveSingle(appLogEntry("log-4", "PROD"));
            assertEquals("ACCEPTED", result.get("status"));
            assertEquals("MUST_ANALYZE", result.get("analysisDecision"));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(knowledgeMatcher, never()).findSimilar(any(ErrorEvent.class), any(Integer.class));
        verify(analysisService).analyzeEvent(any());
    }

    @Test
    @DisplayName("去重窗口支持配置化")
    void dedupWindowIsConfigurable() {
        properties.getDedup().setWindowSeconds(5);
        ErrorEvent existing = new ErrorEvent();
        existing.setId(9L);
        existing.setOccurredAt(LocalDateTime.now().minusSeconds(10));
        existing.setLastOccurredAt(LocalDateTime.now().minusSeconds(10));

        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        Map<String, Object> result;
        try {
            result = service.receiveSingle(appLogEntry("log-5", "DEV"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals("ACCEPTED", result.get("status"));
        verify(errorEventMapper, never()).aggregateOccurrence(any(), any(), any(), any());
        verify(errorEventMapper).insert(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("窗口期重复达到阈值时触发既有事件 AI 分析")
    void repeatedAggregateReachesThresholdTriggersAnalysis() {
        properties.getDedup().setRepeatUpgradeThreshold(10);
        ErrorEvent existing = new ErrorEvent();
        existing.setId(10L);
        existing.setAnalyzed(false);
        existing.setOccurrenceCount(9);
        existing.setOccurredAt(LocalDateTime.now().minusSeconds(10));
        existing.setLastOccurredAt(LocalDateTime.now().minusSeconds(10));

        when(errorEventMapper.findLatestByAppEnvFingerprint(anyString(), any(), anyString()))
                .thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        Map<String, Object> result;
        try {
            result = service.receiveSingle(appLogEntry("log-6", "PROD"));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals("AGGREGATED", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("repeatUpgrade"));
        assertEquals("MUST_ANALYZE", result.get("analysisDecision"));
        verify(analysisService).analyzeEvent(org.mockito.ArgumentMatchers.eq(10L));
    }

    private ErrorLogEntry appLogEntry(String logId, String environment) {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setAppName("order-service");
        entry.setLogId(logId);
        entry.setLogTime(LocalDateTime.now());
        entry.setLogLevel("ERROR");
        entry.setEnvironment(environment);
        entry.setMessage("orderId=123456 创建订单失败");
        entry.setStackTrace("""
                java.lang.NullPointerException: order is null
                    at com.example.OrderService.create(OrderService.java:42)
                """);
        return entry;
    }

    private ErrorLogEntry nginxAccessEntry() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setAppName("gateway");
        entry.setLogId("nginx-1");
        entry.setLogTime(LocalDateTime.now());
        entry.setLogLevel("WARN");
        entry.setEnvironment("PROD");
        entry.setLogSource("NGINX_ACCESS");
        entry.setMessage("GET /api/orders/123456 404");
        entry.setHttpStatus(404);
        entry.setRequestUri("/api/orders/123456?traceId=abc");
        entry.setRemoteAddr("10.0.0.1");
        entry.setRequestMethod("GET");
        return entry;
    }
}
