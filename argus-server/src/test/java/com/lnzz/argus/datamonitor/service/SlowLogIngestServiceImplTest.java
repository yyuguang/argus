package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogIngestResult;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogPushRequest;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogRawPushRequest;
import com.lnzz.argus.datamonitor.service.impl.SlowLogIngestServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowLogIngestService - slow log 接入")
class SlowLogIngestServiceImplTest {

    @Test
    @DisplayName("接收 slow log 推送并生成 SLOW_LOG 慢 SQL 事件")
    void ingestPushCreatesSlowSqlEvent() {
        Fixture fixture = new Fixture();
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SlowLogIngestResult result = fixture.service.ingest(pushRequest("event-1", 5600L));

        assertTrue(result.accepted());
        ArgumentCaptor<SlowSqlEvent> eventCaptor = ArgumentCaptor.forClass(SlowSqlEvent.class);
        verify(fixture.slowSqlEventMapper).insert(eventCaptor.capture());
        SlowSqlEvent event = eventCaptor.getValue();
        assertEquals("SLOW_LOG", event.getSourceType());
        assertEquals("P2", event.getRiskLevel());
        assertEquals("PENDING", event.getAnalysisStatus());
        assertTrue(event.getSqlText().contains("13800138000"));
        assertFalse(event.getSqlTextMasked().contains("13800138000"));
    }

    @Test
    @DisplayName("低于 minQueryTimeMs 的 slow log 被过滤")
    void ingestFiltersLowValueSlowLog() {
        Fixture fixture = new Fixture();

        SlowLogIngestResult result = fixture.service.ingest(pushRequest("event-low", 500L));

        assertFalse(result.accepted());
        assertNull(result.eventId());
        verify(fixture.slowSqlEventMapper, never()).insert(any(SlowSqlEvent.class));
    }

    @Test
    @DisplayName("相同幂等键不会重复生成慢 SQL 事件")
    void ingestRejectsDuplicatedIdempotentKey() {
        Fixture fixture = new Fixture();
        SlowSqlEvent existing = new SlowSqlEvent();
        existing.setId(99L);
        existing.setSqlFingerprint("fp");
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        SlowLogIngestResult result = fixture.service.ingest(pushRequest("event-dup", 5600L));

        assertTrue(result.duplicated());
        assertEquals(99L, result.eventId());
        verify(fixture.slowSqlEventMapper, never()).insert(any(SlowSqlEvent.class));
    }

    @Test
    @DisplayName("接收 MySQL 5.7 原始 slow log 片段并更新位点")
    void ingestRawParsesSlowLogContentAndUpdatesCursor() {
        Fixture fixture = new Fixture();
        when(fixture.slowSqlEventMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        String content = """
                # Time: 260513 10:00:00
                # Query_time: 5.600000  Lock_time: 0.300000 Rows_sent: 20  Rows_examined: 900000
                SET timestamp=1778647200;
                select * from order_main where id = 10001;
                """;

        SlowLogIngestResult result = fixture.service.ingestRaw(new SlowLogRawPushRequest(
                "oms-product", "PROD", "oms_master", content, 2048L, "raw-1"));

        assertTrue(result.accepted());
        assertEquals(2048L, fixture.slowLogConfig.getCursorOffset());
        verify(fixture.slowLogConfigMapper).updateById(fixture.slowLogConfig);
    }

    private SlowLogPushRequest pushRequest(String key, Long queryTimeMs) {
        return new SlowLogPushRequest(
                "oms-product",
                "PROD",
                "oms_master",
                "MYSQL_SLOW_LOG",
                queryTimeMs,
                300L,
                20L,
                900000L,
                "select * from order_main where mobile = '13800138000'",
                LocalDateTime.of(2026, 5, 13, 10, 0),
                key
        );
    }

    private static class Fixture {
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final DataMonitorConfigMapper monitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final SlowLogConfigMapper slowLogConfigMapper = mock(SlowLogConfigMapper.class);
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final SlowLogConfig slowLogConfig = new SlowLogConfig();
        private final SlowLogIngestServiceImpl service = new SlowLogIngestServiceImpl(
                dataSourceConfigMapper, monitorConfigMapper, slowLogConfigMapper, slowSqlEventMapper,
                new SlowLogParser(), new SqlTextSanitizer());

        private Fixture() {
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setAppName("oms-product");
            monitorConfig.setEnvironment("PROD");
            DataSourceConfig datasource = new DataSourceConfig();
            datasource.setId(100L);
            datasource.setMonitorConfigId(10L);
            datasource.setDatasourceCode("oms_master");
            slowLogConfig.setId(200L);
            slowLogConfig.setDatasourceId(100L);
            slowLogConfig.setEnabled(true);
            slowLogConfig.setMinQueryTimeMs(1000L);
            slowLogConfig.setCollectFullSql(true);
            slowLogConfig.setCursorOffset(0L);
            when(monitorConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(monitorConfig);
            when(monitorConfigMapper.selectById(10L)).thenReturn(monitorConfig);
            when(dataSourceConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(datasource);
            when(slowLogConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slowLogConfig);
            when(slowSqlEventMapper.insert(any(SlowSqlEvent.class))).thenAnswer(invocation -> {
                SlowSqlEvent event = invocation.getArgument(0);
                event.setId(300L);
                return 1;
            });
        }
    }
}
