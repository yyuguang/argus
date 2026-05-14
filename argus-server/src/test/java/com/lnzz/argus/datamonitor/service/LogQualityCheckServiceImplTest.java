package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.entity.LogQualityCheckResult;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityCheckResultMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogQualityRules;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogTableScanMetrics;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.ScanWindow;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService.LogQualityCheckResponse;
import com.lnzz.argus.datamonitor.service.impl.LogQualityCheckServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LogQualityCheckService - 接口日志表质量巡检")
class LogQualityCheckServiceImplTest {

    @Test
    @DisplayName("ID_INCREMENT 巡检会识别质量问题并推进位点")
    void checkIdIncrementDetectsIssuesAndAdvancesCursor() {
        Fixture fixture = new Fixture();
        when(fixture.inspector.scan(any(), any(), any(), any(), any())).thenReturn(new LogTableScanMetrics(
                100, "120", LocalDateTime.now().minusMinutes(2),
                3, 2, 1, 4, 1, 0, 5, 1,
                900000L, 1024L, 512L, "120", "{\"id\":120}"));

        LogQualityCheckResponse response = fixture.service.checkConfig(300L);

        assertTrue(response.success());
        assertEquals("120", fixture.config.getLastScanValue());
        ArgumentCaptor<LogQualityCheckResult> resultCaptor = ArgumentCaptor.forClass(LogQualityCheckResult.class);
        verify(fixture.resultMapper).insert(resultCaptor.capture());
        assertEquals("DONE", resultCaptor.getValue().getStatus());
        assertTrue(resultCaptor.getValue().getQualityScore() < 100);
        verify(fixture.issueMapper, atLeastOnce()).insert(any(LogQualityIssue.class));
        verify(fixture.configMapper).updateById(fixture.config);
    }

    @Test
    @DisplayName("TIME_WINDOW 巡检会设置窗口并检测无新增")
    void checkTimeWindowDetectsNoNewData() {
        Fixture fixture = new Fixture();
        fixture.config.setScanMode("TIME_WINDOW");
        when(fixture.inspector.scan(any(), any(), any(), any(), any())).thenReturn(new LogTableScanMetrics(
                0, null, LocalDateTime.now().minusMinutes(30),
                0, 0, 0, 0, 0, 0, 0, 0,
                10L, 1024L, 512L, null, "{\"table\":\"api_call_log\"}"));

        LogQualityCheckResponse response = fixture.service.checkConfig(300L);

        assertTrue(response.success());
        assertTrue(response.qualityScore() < 100);
        assertTrue(fixture.config.getLastScanValue().contains("T"));
    }

    @Test
    @DisplayName("批量巡检启用配置")
    void checkAllEnabledScansEnabledConfigs() {
        Fixture fixture = new Fixture();
        when(fixture.configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(fixture.config));
        when(fixture.inspector.scan(any(), any(), any(), any(), any())).thenReturn(new LogTableScanMetrics(
                10, "10", LocalDateTime.now(), 0, 0, 0, 0, 0, 0, 0, 0,
                10L, 1024L, 512L, "10", "{}"));

        List<LogQualityCheckResponse> responses = fixture.service.checkAllEnabled();

        assertEquals(1, responses.size());
        assertEquals("A", responses.get(0).qualityLevel());
    }

    private static class Fixture {
        private final InterfaceLogTableConfigMapper configMapper = mock(InterfaceLogTableConfigMapper.class);
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final LogQualityCheckResultMapper resultMapper = mock(LogQualityCheckResultMapper.class);
        private final LogQualityIssueMapper issueMapper = mock(LogQualityIssueMapper.class);
        private final DataSourceSecretCodec secretCodec = new DataSourceSecretCodec();
        private final InterfaceLogTableInspector inspector = mock(InterfaceLogTableInspector.class);
        private final LogQualityCheckServiceImpl service = new LogQualityCheckServiceImpl(configMapper,
                dataSourceConfigMapper, resultMapper, issueMapper, secretCodec, inspector);
        private final InterfaceLogTableConfig config = config();

        private Fixture() {
            DataSourceConfig datasource = new DataSourceConfig();
            datasource.setId(100L);
            datasource.setReadonly(true);
            datasource.setPasswordSecret("secret");
            when(configMapper.selectById(300L)).thenReturn(config);
            when(dataSourceConfigMapper.selectById(100L)).thenReturn(datasource);
            when(resultMapper.insert(any(LogQualityCheckResult.class))).thenAnswer(invocation -> {
                LogQualityCheckResult result = invocation.getArgument(0);
                result.setId(1000L);
                return 1;
            });
        }

        private InterfaceLogTableConfig config() {
            InterfaceLogTableConfig entity = new InterfaceLogTableConfig();
            entity.setId(300L);
            entity.setMonitorConfigId(10L);
            entity.setDatasourceId(100L);
            entity.setAppName("oms-product");
            entity.setEnvironment("PROD");
            entity.setTableName("api_call_log");
            entity.setPrimaryKeyColumn("id");
            entity.setInterfaceCodeColumn("api_code");
            entity.setRequestTimeColumn("start_time");
            entity.setResponseTimeColumn("end_time");
            entity.setResponseBodyColumn("response_body");
            entity.setRequestIdColumn("request_id");
            entity.setTraceIdColumn("trace_id");
            entity.setStatusCodeColumn("response_code");
            entity.setScanMode("ID_INCREMENT");
            entity.setQualityRules(com.alibaba.fastjson2.JSON.toJSONString(new LogQualityRules(
                    Set.of("api_code", "start_time", "end_time", "response_body"),
                    10, 5, 1, 512, 1000L, Set.of("200", "0"))));
            entity.setEnabled(true);
            return entity;
        }
    }
}
