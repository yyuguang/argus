package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.ai.SlowSqlAnalysisAiEngine;
import com.lnzz.argus.datamonitor.ai.SlowSqlPromptBuilder;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.ExplainRow;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.TableInfo;
import com.lnzz.argus.datamonitor.service.impl.SlowSqlAnalysisServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowSqlAnalysisService - 慢 SQL 根因分析")
class SlowSqlAnalysisServiceImplTest {

    @Test
    @DisplayName("EXPLAIN 全表扫描且无候选索引时识别缺失索引")
    void analyzeDetectsMissingIndex() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = fixture.pendingEvent("select * from orders where user_id = 10001");
        when(fixture.slowSqlEventMapper.selectById(1L)).thenReturn(event);
        when(fixture.inspector.explain(eq(fixture.datasource), eq("secret"), any()))
                .thenReturn(List.of(new ExplainRow(1, "SIMPLE", "orders", "ALL",
                        null, null, 500000L, "Using where")));
        when(fixture.inspector.queryTables(eq(fixture.datasource), eq("secret"), anySet()))
                .thenReturn(List.of(new TableInfo("seckill_user", "orders", 500000L, 1024L, 0L)));
        when(fixture.inspector.queryIndexes(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());

        SlowSqlAnalysisService.SlowSqlAnalysisResult result = fixture.service.analyzeEvent(1L);

        assertEquals("DONE", result.analysisStatus());
        assertEquals("MISSING_INDEX", result.causeType());
        ArgumentCaptor<SlowSqlEvent> eventCaptor = ArgumentCaptor.forClass(SlowSqlEvent.class);
        verify(fixture.slowSqlEventMapper).updateById(eventCaptor.capture());
        SlowSqlEvent updated = eventCaptor.getValue();
        assertEquals("P2", updated.getRiskLevel());
        assertNotNull(updated.getExplainJson());
        assertEquals("ALTER TABLE `orders` ADD INDEX `idx_argus_user_id` (`user_id`);",
                updated.getIndexSuggestionSql());
    }

    @Test
    @DisplayName("连接池风险优先识别为 POOL_EXHAUSTED")
    void analyzeDetectsPoolExhausted() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = fixture.pendingEvent("select * from orders where user_id = 10001");
        event.setRelatedPoolSnapshotId(99L);
        ConnectionPoolSnapshot snapshot = new ConnectionPoolSnapshot();
        snapshot.setId(99L);
        snapshot.setRiskType("POOL_EXHAUSTED");
        when(fixture.slowSqlEventMapper.selectById(1L)).thenReturn(event);
        when(fixture.poolSnapshotMapper.selectById(99L)).thenReturn(snapshot);
        when(fixture.inspector.explain(eq(fixture.datasource), eq("secret"), any())).thenReturn(List.of());
        when(fixture.inspector.queryTables(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());
        when(fixture.inspector.queryIndexes(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());

        SlowSqlAnalysisService.SlowSqlAnalysisResult result = fixture.service.analyzeEvent(1L);

        assertEquals("POOL_EXHAUSTED", result.causeType());
        ArgumentCaptor<SlowSqlEvent> eventCaptor = ArgumentCaptor.forClass(SlowSqlEvent.class);
        verify(fixture.slowSqlEventMapper).updateById(eventCaptor.capture());
        assertEquals("P1", eventCaptor.getValue().getRiskLevel());
    }

    @Test
    @DisplayName("批量分析只处理 PENDING 慢 SQL")
    void analyzePendingUsesLimit() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = fixture.pendingEvent("select * from orders limit 10000, 20");
        when(fixture.slowSqlEventMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(event));
        when(fixture.inspector.explain(eq(fixture.datasource), eq("secret"), any())).thenReturn(List.of());
        when(fixture.inspector.queryTables(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());
        when(fixture.inspector.queryIndexes(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());

        List<SlowSqlAnalysisService.SlowSqlAnalysisResult> results = fixture.service.analyzePending(10);

        assertEquals(1, results.size());
        assertEquals("BAD_PAGINATION", results.get(0).causeType());
    }

    @Test
    @DisplayName("AI 分析可增强根因和建议，但风险基线仍由规则分析控制")
    void analyzeUsesAiPromptResultWhenAvailable() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = fixture.pendingEvent("select * from orders where user_id = 10001");
        when(fixture.slowSqlEventMapper.selectById(1L)).thenReturn(event);
        when(fixture.inspector.explain(eq(fixture.datasource), eq("secret"), any()))
                .thenReturn(List.of(new ExplainRow(1, "SIMPLE", "orders", "ALL",
                        null, null, 500000L, "Using where")));
        when(fixture.inspector.queryTables(eq(fixture.datasource), eq("secret"), anySet()))
                .thenReturn(List.of(new TableInfo("seckill_user", "orders", 500000L, 1024L, 0L)));
        when(fixture.inspector.queryIndexes(eq(fixture.datasource), eq("secret"), anySet())).thenReturn(List.of());
        when(fixture.promptBuilder.buildPrompt(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn("slow sql ai prompt");
        when(fixture.aiEngine.analyze(eq("slow sql ai prompt"), eq(300L)))
                .thenReturn(new SlowSqlAnalysisAiEngine.SlowSqlAiResult(
                        "SQL 主要问题是缺少索引",
                        "HIGH",
                        "缺少 user_id 过滤索引",
                        "订单查询",
                        List.of("[MISSING_INDEX] user_id 过滤未命中索引；证据：EXPLAIN type=ALL"),
                        List.of("为 user_id 补充索引", "上线前让 DBA 评估执行计划"),
                        List.of("先补索引", "再回归核心查询"),
                        true));

        SlowSqlAnalysisService.SlowSqlAnalysisResult result = fixture.service.analyzeEvent(1L);

        assertEquals("DONE", result.analysisStatus());
        assertEquals("MISSING_INDEX", result.causeType());
        ArgumentCaptor<SlowSqlEvent> eventCaptor = ArgumentCaptor.forClass(SlowSqlEvent.class);
        verify(fixture.slowSqlEventMapper).updateById(eventCaptor.capture());
        SlowSqlEvent updated = eventCaptor.getValue();
        assertEquals("P2", updated.getRiskLevel());
        assertEquals("SQL 主要问题是缺少索引\n影响范围：订单查询\n[MISSING_INDEX] user_id 过滤未命中索引；证据：EXPLAIN type=ALL",
                updated.getRootCause());
        assertEquals("优化建议：\n- 为 user_id 补充索引\n- 上线前让 DBA 评估执行计划\n执行动作：\n- 先补索引\n- 再回归核心查询"
                        + "\n规则兜底建议：为高频过滤条件补充联合索引，并确认字段选择性；上线前必须由 DBA 评估执行计划和写入成本。",
                updated.getOptimizationSuggestion());
    }

    private static class Fixture {
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final DataMonitorConfigMapper dataMonitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final DbLockEventMapper dbLockEventMapper = mock(DbLockEventMapper.class);
        private final ConnectionPoolSnapshotMapper poolSnapshotMapper = mock(ConnectionPoolSnapshotMapper.class);
        private final MysqlSlowSqlInspector inspector = mock(MysqlSlowSqlInspector.class);
        private final SlowSqlPromptBuilder promptBuilder = mock(SlowSqlPromptBuilder.class);
        private final SlowSqlAnalysisAiEngine aiEngine = mock(SlowSqlAnalysisAiEngine.class);
        private final DataSourceSecretCodec secretCodec = new DataSourceSecretCodec();
        private final DataSourceConfig datasource = datasource();
        private final SlowSqlAnalysisServiceImpl service = new SlowSqlAnalysisServiceImpl(slowSqlEventMapper,
                dataSourceConfigMapper, dataMonitorConfigMapper, dbLockEventMapper, poolSnapshotMapper,
                secretCodec, inspector, promptBuilder, aiEngine);

        private Fixture() {
            when(dataSourceConfigMapper.selectById(100L)).thenReturn(datasource);
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setScmConfigId(300L);
            when(dataMonitorConfigMapper.selectById(10L)).thenReturn(monitorConfig);
            when(aiEngine.analyze(any(), any())).thenReturn(null);
        }

        private SlowSqlEvent pendingEvent(String sqlText) {
            SlowSqlEvent event = new SlowSqlEvent();
            event.setId(1L);
            event.setDatasourceId(100L);
            event.setMonitorConfigId(10L);
            event.setSourceType("SLOW_LOG");
            event.setSqlText(sqlText);
            event.setSqlTextMasked(sqlText);
            event.setDurationMs(5000L);
            event.setAnalysisStatus("PENDING");
            event.setOccurredAt(LocalDateTime.of(2026, 5, 13, 12, 0));
            return event;
        }

        private DataSourceConfig datasource() {
            DataSourceConfig config = new DataSourceConfig();
            config.setId(100L);
            config.setDatabaseName("seckill_user");
            config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/seckill_user");
            config.setUsername("readonly");
            config.setPasswordSecret("secret");
            config.setExplainEnabled(true);
            return config;
        }
    }
}
