package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.DbMetricSnapshot;
import com.lnzz.argus.datamonitor.entity.DbProcessSnapshot;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.DbMetricSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DbProcessSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbLockRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbLockWaitRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbTransactionRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.ProcessRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.RuntimeSnapshot;
import com.lnzz.argus.datamonitor.service.impl.DbRuntimeCollectServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DbRuntimeCollectService - MySQL 5.7 运行现场采集")
class DbRuntimeCollectServiceImplTest {

    @Test
    @DisplayName("采集 GLOBAL STATUS 生成指标快照并按上一快照计算 QPS")
    void collectDatasourceSavesMetricSnapshotAndCalculatesQps() {
        Fixture fixture = new Fixture();
        DbMetricSnapshot previous = new DbMetricSnapshot();
        previous.setQuestions(1000L);
        previous.setCollectedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
        when(fixture.metricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(previous);
        when(fixture.collector.collect(any(DataSourceConfig.class), any())).thenReturn(snapshot(
                LocalDateTime.of(2026, 5, 13, 10, 1),
                List.of(new ProcessRow(10L, "app", "10.0.0.1:1234", "oms", "Query", 2,
                        "executing", "select * from t where id = 1")),
                List.of(),
                List.of(),
                List.of()
        ));

        DbRuntimeCollectService.DatasourceCollectResult result = fixture.service.collectDatasource(100L);

        assertTrue(result.success());
        ArgumentCaptor<DbMetricSnapshot> metricCaptor = ArgumentCaptor.forClass(DbMetricSnapshot.class);
        verify(fixture.metricMapper).insert(metricCaptor.capture());
        DbMetricSnapshot metric = metricCaptor.getValue();
        assertEquals("oms-product", metric.getAppName());
        assertEquals(12, metric.getThreadsConnected());
        assertEquals(new BigDecimal("10.00"), metric.getQps());
    }

    @Test
    @DisplayName("采集 PROCESSLIST 识别长 SQL、锁等待和 metadata lock 风险")
    void collectDatasourceSavesRiskProcessSnapshots() {
        Fixture fixture = new Fixture();
        when(fixture.collector.collect(any(DataSourceConfig.class), any())).thenReturn(snapshot(
                LocalDateTime.of(2026, 5, 13, 10, 1),
                List.of(
                        new ProcessRow(10L, "app", "10.0.0.1:1234", "oms", "Query", 8,
                                "executing", "select * from order where mobile = '13800138000'"),
                        new ProcessRow(11L, "app", "10.0.0.1:1235", "oms", "Query", 3,
                                "Waiting for table metadata lock", "alter table order add column c int"),
                        new ProcessRow(12L, "app", "10.0.0.1:1236", "oms", "Query", 2,
                                "Locked", "update order set status = 1 where id = 10000001")
                ),
                List.of(new InnodbTransactionRow("trx-1", 10L, 40, "RUNNING", "select * from order")),
                List.of(new InnodbLockWaitRow("trx-2", "trx-1", "lock-a", "lock-b")),
                List.of()
        ));

        DbRuntimeCollectService.DatasourceCollectResult result = fixture.service.collectDatasource(100L);

        assertEquals(3, result.processSnapshotCount());
        assertEquals(1, result.longSqlCount());
        assertEquals(1, result.lockedCount());
        assertEquals(1, result.metadataLockCount());
        assertEquals(1, result.innodbTrxCount());
        assertEquals(1, result.innodbLockWaitCount());
        ArgumentCaptor<DbProcessSnapshot> processCaptor = ArgumentCaptor.forClass(DbProcessSnapshot.class);
        verify(fixture.processMapper, org.mockito.Mockito.times(3)).insert(processCaptor.capture());
        List<DbProcessSnapshot> processes = processCaptor.getAllValues();
        assertEquals("LONG_SQL", processes.get(0).getRiskType());
        assertEquals("METADATA_LOCK", processes.get(1).getRiskType());
        assertEquals("LOCKED", processes.get(2).getRiskType());
        assertTrue(processes.get(0).getSqlText().contains("13800138000"));
        assertFalse(processes.get(0).getSqlTextMasked().contains("13800138000"));
        assertEquals(processes.get(0).getSqlFingerprint(),
                fixture.sqlTextSanitizer.fingerprint("select * from order where mobile = 'x'"));
    }

    @Test
    @DisplayName("存在完整 INNODB_LOCK_WAITS 时生成锁等待阻塞事件并关联慢 SQL")
    void collectDatasourceCreatesLockEventFromInnodbLockWaits() {
        Fixture fixture = new Fixture();
        SlowSqlEvent slowSqlEvent = new SlowSqlEvent();
        slowSqlEvent.setId(900L);
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slowSqlEvent);
        String waitingSql = "update order_main set status = 1 where id = 10001";
        when(fixture.collector.collect(any(DataSourceConfig.class), any())).thenReturn(snapshot(
                LocalDateTime.of(2026, 5, 13, 10, 1),
                List.of(
                        new ProcessRow(21L, "app", "10.0.0.1:1234", "oms", "Query", 8,
                                "Locked", waitingSql),
                        new ProcessRow(22L, "app", "10.0.0.1:1235", "oms", "Query", 60,
                                "executing", "update order_main set status = 2 where id = 10001")
                ),
                List.of(
                        new InnodbTransactionRow("waiting-trx", 21L, 8, "LOCK WAIT", waitingSql),
                        new InnodbTransactionRow("blocking-trx", 22L, 60, "RUNNING",
                                "update order_main set status = 2 where id = 10001")
                ),
                List.of(new InnodbLockWaitRow("waiting-trx", "blocking-trx", "lock-w", "lock-b")),
                List.of(new InnodbLockRow("lock-w", "waiting-trx", "X", "RECORD", "`oms`.`order_main`", "PRIMARY"))
        ));

        DbRuntimeCollectService.DatasourceCollectResult result = fixture.service.collectDatasource(100L);

        assertEquals(1, result.lockEventCount());
        ArgumentCaptor<DbLockEvent> eventCaptor = ArgumentCaptor.forClass(DbLockEvent.class);
        verify(fixture.lockEventMapper).insert(eventCaptor.capture());
        DbLockEvent event = eventCaptor.getValue();
        assertEquals("waiting-trx", event.getWaitingTrxId());
        assertEquals("blocking-trx", event.getBlockingTrxId());
        assertEquals(21L, event.getWaitingProcessId());
        assertEquals(22L, event.getBlockingProcessId());
        assertEquals("`oms`.`order_main`", event.getLockTable());
        assertEquals("PRIMARY", event.getLockIndex());
        assertEquals("RECORD", event.getLockType());
        assertEquals("P1", event.getRiskLevel());
        assertEquals("NEW", event.getStatus());
        verify(fixture.slowSqlEventMapper).updateById(slowSqlEvent);
        assertEquals(500L, slowSqlEvent.getRelatedLockEventId());
    }

    @Test
    @DisplayName("INNODB_LOCK_WAITS 缺失时按 processlist Locked 降级生成锁事件")
    void collectDatasourceCreatesFallbackLockEventFromProcesslist() {
        Fixture fixture = new Fixture();
        when(fixture.collector.collect(any(DataSourceConfig.class), any())).thenReturn(snapshot(
                LocalDateTime.of(2026, 5, 13, 10, 1),
                List.of(new ProcessRow(31L, "app", "10.0.0.1:1234", "oms", "Query", 10,
                        "Waiting for table metadata lock", "alter table order_main add column c int")),
                List.of(),
                List.of(),
                List.of()
        ));

        DbRuntimeCollectService.DatasourceCollectResult result = fixture.service.collectDatasource(100L);

        assertEquals(1, result.lockEventCount());
        ArgumentCaptor<DbLockEvent> eventCaptor = ArgumentCaptor.forClass(DbLockEvent.class);
        verify(fixture.lockEventMapper).insert(eventCaptor.capture());
        DbLockEvent event = eventCaptor.getValue();
        assertEquals(31L, event.getWaitingProcessId());
        assertEquals("METADATA_LOCK", event.getLockType());
        assertEquals("P1", event.getRiskLevel());
        assertEquals("alter table order_main add column c int", event.getWaitingSql());
    }

    @Test
    @DisplayName("批量采集时单个数据源失败不阻断其他数据源")
    void collectAllEnabledDowngradesFailedDatasource() {
        Fixture fixture = new Fixture();
        DataSourceConfig second = datasource(200L, "wms_master");
        when(fixture.datasourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(fixture.datasource, second));
        when(fixture.collector.collect(fixture.datasource, "readonly_pwd"))
                .thenThrow(new IllegalStateException("connection refused"));
        when(fixture.collector.collect(second, "readonly_pwd")).thenReturn(snapshot(
                LocalDateTime.of(2026, 5, 13, 10, 1),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        List<DbRuntimeCollectService.DatasourceCollectResult> results = fixture.service.collectAllEnabled();

        assertEquals(2, results.size());
        assertFalse(results.get(0).success());
        assertTrue(results.get(1).success());
    }

    private RuntimeSnapshot snapshot(LocalDateTime collectedAt,
                                     List<ProcessRow> processRows,
                                     List<InnodbTransactionRow> transactions,
                                     List<InnodbLockWaitRow> lockWaits,
                                     List<InnodbLockRow> locks) {
        return new RuntimeSnapshot(
                Map.of(
                        "Threads_connected", 12L,
                        "Threads_running", 3L,
                        "Max_connections", 200L,
                        "Questions", 1600L,
                        "Com_select", 100L,
                        "Com_insert", 2L,
                        "Com_update", 3L,
                        "Com_delete", 1L,
                        "Slow_queries", 4L
                ),
                processRows,
                transactions,
                lockWaits,
                locks,
                collectedAt
        );
    }

    private static DataSourceConfig datasource(Long id, String code) {
        DataSourceConfig datasource = new DataSourceConfig();
        datasource.setId(id);
        datasource.setMonitorConfigId(10L);
        datasource.setProjectMappingId(2L);
        datasource.setDatasourceCode(code);
        datasource.setDbType("MYSQL");
        datasource.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/oms");
        datasource.setUsername("argus_readonly");
        datasource.setPasswordSecret(new DataSourceSecretCodec().encrypt("readonly_pwd"));
        datasource.setReadonly(true);
        datasource.setEnabled(true);
        datasource.setCollectGlobalStatus(true);
        datasource.setCollectProcesslist(true);
        datasource.setCollectInnodbTrx(true);
        datasource.setCollectInnodbLock(true);
        datasource.setFullSqlCollectEnabled(true);
        datasource.setThresholdConfig("{\"longSqlSeconds\":5,\"longTransactionSeconds\":30,\"lockWaitSeconds\":5,\"connectionUsagePercent\":80}");
        return datasource;
    }

    private static class Fixture {
        private final DataSourceConfigMapper datasourceMapper = mock(DataSourceConfigMapper.class);
        private final DataMonitorConfigMapper monitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final DbMetricSnapshotMapper metricMapper = mock(DbMetricSnapshotMapper.class);
        private final DbProcessSnapshotMapper processMapper = mock(DbProcessSnapshotMapper.class);
        private final DbLockEventMapper lockEventMapper = mock(DbLockEventMapper.class);
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final DataSourceSecretCodec secretCodec = new DataSourceSecretCodec();
        private final MysqlRuntimeCollector collector = mock(MysqlRuntimeCollector.class);
        private final SqlTextSanitizer sqlTextSanitizer = new SqlTextSanitizer();
        private final DbRuntimeCollectServiceImpl service = new DbRuntimeCollectServiceImpl(
                datasourceMapper, monitorConfigMapper, metricMapper, processMapper,
                lockEventMapper, slowSqlEventMapper,
                secretCodec, collector, sqlTextSanitizer);
        private final DataSourceConfig datasource = datasource(100L, "oms_master");

        private Fixture() {
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setAppName("oms-product");
            monitorConfig.setEnvironment("PROD");
            when(datasourceMapper.selectById(100L)).thenReturn(datasource);
            when(monitorConfigMapper.selectById(10L)).thenReturn(monitorConfig);
            when(metricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(metricMapper.insert(any(DbMetricSnapshot.class))).thenAnswer(invocation -> {
                DbMetricSnapshot metric = invocation.getArgument(0);
                metric.setId(1000L);
                return 1;
            });
            when(lockEventMapper.insert(any(DbLockEvent.class))).thenAnswer(invocation -> {
                DbLockEvent event = invocation.getArgument(0);
                event.setId(500L);
                return 1;
            });
            when(slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        }
    }
}
