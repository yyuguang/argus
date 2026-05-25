package com.lnzz.argus.error.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lnzz.argus.error.ai.ErrorAnalysisEngine;
import com.lnzz.argus.error.ai.ErrorAnalysisPromptBuilder;
import com.lnzz.argus.config.ErrorProcessingProperties;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorAnalysisTask;
import com.lnzz.argus.error.mapper.ErrorAnalysisTaskMapper;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.mapper.ErrorAnalysisMapper;
import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.error.service.impl.ErrorAnalysisServiceImpl;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.service.KnowledgeMatcher;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorAnalysisService - 向量历史案例检索")
class ErrorAnalysisServiceTest {

    @Mock
    private ErrorAnalysisMapper analysisMapper;
    @Mock
    private ErrorAnalysisTaskMapper analysisTaskMapper;
    @Mock
    private ErrorEventMapper eventMapper;
    @Mock
    private ErrorAnalysisEngine analysisEngine;
    @Mock
    private ErrorAnalysisPromptBuilder promptBuilder;
    @Mock
    private SourceCodeLocator sourceCodeLocator;
    @Mock
    private NotificationService notificationService;
    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private KnowledgeMatcher knowledgeMatcher;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;
    @Mock
    private ScmConfigService scmConfigService;
    @Mock
    private ScmReviewConfigSupport scmReviewConfigSupport;

    private ErrorAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(ErrorEvent.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ErrorEvent.class);
        }
        if (TableInfoHelper.getTableInfo(ErrorAnalysisTask.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ErrorAnalysisTask.class);
        }
        service = new ErrorAnalysisServiceImpl(
                analysisMapper,
                analysisTaskMapper,
                eventMapper,
                analysisEngine,
                promptBuilder,
                sourceCodeLocator,
                notificationService,
                knowledgeService,
                knowledgeMatcher,
                vectorKnowledgeService,
                new ErrorProcessingProperties(),
                scmConfigService,
                scmReviewConfigSupport
        );
        ReflectionTestUtils.setField(service, "vectorEnabled", true);
        ReflectionTestUtils.setField(service, "errorSearchTopk", 5);
        ReflectionTestUtils.setField(service, "knowledgeMinScore", 0.7d);
    }

    @Test
    @DisplayName("vector.enabled=true 时优先走向量检索")
    void analyzeEventUsesVectorHistoryWhenEnabled() {
        ErrorEvent event = createEvent();
        Document vectorDoc = Document.builder()
                .text("向量案例")
                .metadata(Map.of(
                        "root_cause", "数据库连接未及时释放",
                        "fix_suggestion", "统一关闭连接资源",
                        "prevention_advice", "增加连接池监控"
                ))
                .build()
                .mutate()
                .score(0.91d)
                .build();

        when(vectorKnowledgeService.searchSimilarErrors("SQL_EXCEPTION order-service OrderService 连接池耗尽", "SQL_EXCEPTION", "order-service", 5, 0.0d))
                .thenReturn(List.of(vectorDoc));

        List<ErrorAnalysis> historyCases = ReflectionTestUtils.invokeMethod(service, "findHistoryCases", event);

        assertNotNull(historyCases);
        assertEquals(1, historyCases.size());
        assertEquals("数据库连接未及时释放", historyCases.get(0).getRootCause());
        assertEquals("统一关闭连接资源", historyCases.get(0).getFixDescription());
        verify(knowledgeMatcher, never()).findSimilar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("vector.enabled=false 时回退 KnowledgeMatcher")
    void analyzeEventFallsBackToKnowledgeMatcherWhenVectorDisabled() {
        ErrorEvent event = createEvent();
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setRootCause("空指针来源于 DTO 未判空");
        entry.setFixSuggestion("补充参数校验");

        ReflectionTestUtils.setField(service, "vectorEnabled", false);
        when(knowledgeMatcher.findSimilar(event, 3)).thenReturn(List.of(entry));

        List<ErrorAnalysis> historyCases = ReflectionTestUtils.invokeMethod(service, "findHistoryCases", event);

        assertNotNull(historyCases);
        assertEquals("空指针来源于 DTO 未判空", historyCases.get(0).getRootCause());
        verify(vectorKnowledgeService, never()).searchSimilarErrors(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("向量分数低于阈值时回退 KnowledgeMatcher")
    void analyzeEventFallsBackWhenVectorScoreTooLow() {
        ErrorEvent event = createEvent();
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setRootCause("历史兜底案例");
        Document lowScoreDoc = Document.builder()
                .text("低分案例")
                .metadata(Map.of("root_cause", "低分命中"))
                .build()
                .mutate()
                .score(0.4d)
                .build();

        when(vectorKnowledgeService.searchSimilarErrors(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(List.of(lowScoreDoc));
        when(knowledgeMatcher.findSimilar(event, 3)).thenReturn(List.of(entry));

        List<ErrorAnalysis> historyCases = ReflectionTestUtils.invokeMethod(service, "findHistoryCases", event);

        assertNotNull(historyCases);
        assertTrue(historyCases.stream().anyMatch(item -> "历史兜底案例".equals(item.getRootCause())));
    }

    @Test
    @DisplayName("AI 不允许直接将 P1 降级为 P3")
    void aiCalibrationBlocksHighSeverityDowngrade() {
        ErrorEvent event = createEvent();
        event.setSeverity("P1");
        event.setSeveritySource("RULE");
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setFinalSeverity("P3");
        analysis.setRootCause("历史低风险案例相似");
        analysis.setConfidence(BigDecimal.valueOf(0.72));

        ReflectionTestUtils.invokeMethod(service, "updateEventSeverity", event, analysis);

        assertEquals("P1", event.getFinalSeverity());
        assertTrue(event.getSeverityReason().contains("需人工确认"));
        verify(eventMapper).update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AI 可以将 P2 升级为 P1")
    void aiCalibrationAllowsUpgrade() {
        ErrorEvent event = createEvent();
        event.setSeverity("P2");
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setFinalSeverity("P1");
        analysis.setRootCause("支付链路超时影响交易");
        analysis.setConfidence(BigDecimal.valueOf(0.88));

        ReflectionTestUtils.invokeMethod(service, "updateEventSeverity", event, analysis);

        assertEquals("P1", event.getSeverity());
        assertEquals("P1", event.getFinalSeverity());
        assertEquals("AI", event.getSeveritySource());
        assertTrue(event.getSeverityReason().contains("AI校准"));
    }

    @Test
    @DisplayName("人工调整严重度最终生效")
    void manualAdjustSeverityWins() {
        ErrorEvent event = createEvent();
        event.setSeverity("P2");
        when(eventMapper.selectById(1L)).thenReturn(event);

        ErrorEvent adjusted = service.adjustSeverity(1L, "P0", "生产支付不可用");

        assertEquals("P0", adjusted.getSeverity());
        assertEquals("P0", adjusted.getFinalSeverity());
        assertEquals("MANUAL", adjusted.getSeveritySource());
        assertTrue(adjusted.getSeverityReason().contains("生产支付不可用"));
    }

    @Test
    @DisplayName("执行分析时创建任务并在成功后标记完成")
    void analyzeEventCreatesDoneTask() {
        ErrorEvent event = createEvent();
        event.setAnalyzed(false);
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setId(10L);
        analysis.setFinalSeverity("P1");
        analysis.setRootCause("连接池耗尽");
        analysis.setConfidence(BigDecimal.valueOf(0.86));
        analysis.setAiModel("deepseek-chat");

        when(eventMapper.selectById(1L)).thenReturn(event);
        when(sourceCodeLocator.locate(event)).thenReturn(SourceCodeLocator.SourceLocation.notFound("未配置源码映射"));
        when(promptBuilder.buildAnalysisPrompt(org.mockito.ArgumentMatchers.eq(event),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn("prompt");
        when(analysisEngine.analyze("prompt", event, null)).thenReturn(analysis);

        service.analyzeEvent(1L, "MANUAL_RETRY");

        verify(analysisTaskMapper).insert(org.mockito.ArgumentMatchers.any(ErrorAnalysisTask.class));
        verify(analysisTaskMapper, org.mockito.Mockito.atLeastOnce())
                .update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
        verify(analysisMapper).insert(analysis);
        verify(notificationService).sendErrorAlert(event, analysis);
    }

    private ErrorEvent createEvent() {
        ErrorEvent event = new ErrorEvent();
        event.setId(1L);
        event.setAppName("order-service");
        event.setErrorType("SQL_EXCEPTION");
        event.setClassName("OrderService");
        event.setErrorMessage("连接池耗尽");
        event.setSeverity("P1");
        event.setAnalyzed(false);
        return event;
    }

}
