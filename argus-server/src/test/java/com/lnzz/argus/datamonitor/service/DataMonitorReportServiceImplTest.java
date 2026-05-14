package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataMonitorReport;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorReportMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.impl.DataMonitorReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataMonitorReportService - 数据监控报告")
class DataMonitorReportServiceImplTest {

    @Test
    @DisplayName("生成数据库健康日报")
    void generateDailyReport() {
        Fixture fixture = new Fixture();

        List<DataMonitorReport> reports = fixture.service.generateDaily(LocalDate.of(2026, 5, 13));

        assertEquals(1, reports.size());
        ArgumentCaptor<DataMonitorReport> reportCaptor = ArgumentCaptor.forClass(DataMonitorReport.class);
        verify(fixture.reportMapper).insert(reportCaptor.capture());
        DataMonitorReport report = reportCaptor.getValue();
        assertEquals("DAILY", report.getReportType());
        assertEquals(2, report.getSlowSqlCount());
        assertEquals(62, report.getHealthScore());
    }

    private static class Fixture {
        private final DataMonitorConfigMapper configMapper = mock(DataMonitorConfigMapper.class);
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final DbLockEventMapper lockEventMapper = mock(DbLockEventMapper.class);
        private final ConnectionPoolSnapshotMapper poolSnapshotMapper = mock(ConnectionPoolSnapshotMapper.class);
        private final LogQualityIssueMapper issueMapper = mock(LogQualityIssueMapper.class);
        private final DataMonitorReportMapper reportMapper = mock(DataMonitorReportMapper.class);
        private final DataMonitorReportServiceImpl service = new DataMonitorReportServiceImpl(configMapper,
                slowSqlEventMapper, lockEventMapper, poolSnapshotMapper, issueMapper, reportMapper);

        private Fixture() {
            DataMonitorConfig config = new DataMonitorConfig();
            config.setId(10L);
            config.setAppName("oms-product");
            config.setEnvironment("PROD");
            config.setEnabled(true);
            when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));
            when(slowSqlEventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
            when(lockEventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            when(poolSnapshotMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
            when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        }
    }
}
