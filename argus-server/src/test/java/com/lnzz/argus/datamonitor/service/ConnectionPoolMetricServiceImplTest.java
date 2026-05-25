package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.ai.ConnectionPoolRiskAiEngine;
import com.lnzz.argus.datamonitor.ai.ConnectionPoolRiskPromptBuilder;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricRequest;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricResponse;
import com.lnzz.argus.datamonitor.service.impl.ConnectionPoolMetricServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionPoolMetricService - 连接池指标统一接入")
class ConnectionPoolMetricServiceImplTest {

    @Test
    @DisplayName("HikariCP 样本上报成功并识别连接池耗尽")
    void ingestHikariMetricAndDetectsExhausted() {
        Fixture fixture = new Fixture();

        PoolMetricResponse response = fixture.service.ingest(request("HIKARI", 20, 0, 20, 5, 0L));

        assertTrue(response.riskDetected());
        assertEquals("POOL_EXHAUSTED", response.riskType());
        ArgumentCaptor<ConnectionPoolSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ConnectionPoolSnapshot.class);
        verify(fixture.snapshotMapper).insert(snapshotCaptor.capture());
        ConnectionPoolSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals("HIKARI", snapshot.getPoolType());
        assertEquals(100L, snapshot.getDatasourceId());
        assertEquals("P1", snapshot.getRiskLevel());
    }

    @Test
    @DisplayName("Druid 样本上报成功并识别高水位")
    void ingestDruidMetricAndDetectsHighUsage() {
        Fixture fixture = new Fixture();

        PoolMetricResponse response = fixture.service.ingest(request("DRUID", 18, 2, 20, 0, 0L));

        assertTrue(response.riskDetected());
        assertEquals("POOL_HIGH_USAGE", response.riskType());
        ArgumentCaptor<ConnectionPoolSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ConnectionPoolSnapshot.class);
        verify(fixture.snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("DRUID", snapshotCaptor.getValue().getPoolType());
    }

    @Test
    @DisplayName("健康连接池样本不标记风险")
    void ingestHealthyPoolMetric() {
        Fixture fixture = new Fixture();

        PoolMetricResponse response = fixture.service.ingest(request("HIKARI", 5, 15, 20, 0, 0L));

        assertFalse(response.riskDetected());
        assertEquals(null, response.riskType());
    }

    @Test
    @DisplayName("连接获取慢识别为 POOL_ACQUIRE_SLOW")
    void ingestDetectsAcquireSlow() {
        Fixture fixture = new Fixture();

        PoolMetricResponse response = fixture.service.ingest(request("HIKARI", 5, 15, 20, 0, 2500L));

        assertTrue(response.riskDetected());
        assertEquals("POOL_ACQUIRE_SLOW", response.riskType());
    }

    @Test
    @DisplayName("连接池风险命中时使用 AI 增强风险原因")
    void ingestUsesAiRiskReasonWhenAvailable() {
        Fixture fixture = new Fixture();
        SlowSqlEvent slowSqlEvent = new SlowSqlEvent();
        slowSqlEvent.setId(900L);
        slowSqlEvent.setSqlTextMasked("select * from orders where status = 'NEW'");
        slowSqlEvent.setDurationMs(5800L);
        slowSqlEvent.setCauseType("MISSING_INDEX");
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slowSqlEvent);
        when(fixture.promptBuilder.buildPrompt(any(), any(), any(), any(), anyLong()))
                .thenReturn("pool risk ai prompt");
        when(fixture.aiEngine.analyze("pool risk ai prompt"))
                .thenReturn(new ConnectionPoolRiskAiEngine.PoolRiskAiResult(
                        "连接池等待明显升高，已接近耗尽",
                        "HIGH",
                        "慢 SQL 放大了连接占用时长",
                        "订单查询接口可能出现排队",
                        java.util.List.of("活跃连接达到上限", "等待线程持续增长"),
                        java.util.List.of("先限流", "排查慢 SQL"),
                        java.util.List.of("补充连接池观测", "复盘容量阈值"),
                        true));

        PoolMetricResponse response = fixture.service.ingest(request("HIKARI", 20, 0, 20, 5, 0L));

        assertTrue(response.riskDetected());
        assertEquals("POOL_EXHAUSTED", response.riskType());
        ArgumentCaptor<ConnectionPoolSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ConnectionPoolSnapshot.class);
        verify(fixture.snapshotMapper).insert(snapshotCaptor.capture());
        ConnectionPoolSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals("P1", snapshot.getRiskLevel());
        assertEquals("连接池等待明显升高，已接近耗尽\n影响范围：订单查询接口可能出现排队\n证据：活跃连接达到上限；等待线程持续增长\n规则兜底：连接池已接近耗尽，请优先排查长 SQL 或连接泄漏",
                snapshot.getRiskReason());
    }

    @Test
    @DisplayName("连接池风险会关联同时间窗口慢 SQL 事件")
    void ingestLinksSlowSqlEventWhenRiskDetected() {
        Fixture fixture = new Fixture();
        SlowSqlEvent slowSqlEvent = new SlowSqlEvent();
        slowSqlEvent.setId(900L);
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slowSqlEvent);

        fixture.service.ingest(request("HIKARI", 20, 0, 20, 5, 0L));

        verify(fixture.slowSqlEventMapper).updateById(slowSqlEvent);
        assertEquals(1000L, slowSqlEvent.getRelatedPoolSnapshotId());
    }

    @Test
    @DisplayName("健康连接池不会关联慢 SQL 事件")
    void ingestDoesNotLinkSlowSqlWhenNoRisk() {
        Fixture fixture = new Fixture();

        fixture.service.ingest(request("HIKARI", 5, 15, 20, 0, 0L));

        verify(fixture.slowSqlEventMapper, never()).updateById(any(SlowSqlEvent.class));
    }

    private PoolMetricRequest request(String poolType,
                                      int active,
                                      int idle,
                                      int max,
                                      int waiting,
                                      long acquireMaxMs) {
        return new PoolMetricRequest(
                "oms-product",
                "PROD",
                "oms_master",
                poolType,
                active,
                idle,
                max,
                waiting,
                100L,
                acquireMaxMs,
                0L,
                0L,
                LocalDateTime.of(2026, 5, 13, 10, 0)
        );
    }

    private static class Fixture {
        private final DataMonitorConfigMapper monitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final ConnectionPoolSnapshotMapper snapshotMapper = mock(ConnectionPoolSnapshotMapper.class);
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final ConnectionPoolRiskPromptBuilder promptBuilder = mock(ConnectionPoolRiskPromptBuilder.class);
        private final ConnectionPoolRiskAiEngine aiEngine = mock(ConnectionPoolRiskAiEngine.class);
        private final ConnectionPoolMetricServiceImpl service =
                new ConnectionPoolMetricServiceImpl(monitorConfigMapper, dataSourceConfigMapper, snapshotMapper,
                        slowSqlEventMapper, promptBuilder, aiEngine);

        private Fixture() {
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setAppName("oms-product");
            monitorConfig.setEnvironment("PROD");
            monitorConfig.setScmConfigId(300L);
            DataSourceConfig datasource = new DataSourceConfig();
            datasource.setId(100L);
            datasource.setMonitorConfigId(10L);
            datasource.setDatasourceCode("oms_master");
            datasource.setDatasourceName("OMS主库");
            when(monitorConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(monitorConfig);
            when(dataSourceConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(datasource);
            when(slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(aiEngine.analyze(any())).thenReturn(null);
            when(snapshotMapper.insert(any(ConnectionPoolSnapshot.class))).thenAnswer(invocation -> {
                ConnectionPoolSnapshot snapshot = invocation.getArgument(0);
                snapshot.setId(1000L);
                return 1;
            });
        }
    }
}
