package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataMonitorReport;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorReportMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据监控报告服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DataMonitorReportServiceImpl implements DataMonitorReportService {

    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final SlowSqlEventMapper slowSqlEventMapper;
    private final DbLockEventMapper dbLockEventMapper;
    private final ConnectionPoolSnapshotMapper poolSnapshotMapper;
    private final LogQualityIssueMapper logQualityIssueMapper;
    private final DataMonitorReportMapper reportMapper;

    @Override
    public List<DataMonitorReport> generateDaily(LocalDate date) {
        LocalDate reportDate = date == null ? LocalDate.now().minusDays(1) : date;
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();
        return dataMonitorConfigMapper.selectList(new LambdaQueryWrapper<DataMonitorConfig>()
                        .eq(DataMonitorConfig::getEnabled, true)
                        .orderByAsc(DataMonitorConfig::getAppName))
                .stream()
                .map(config -> generateOne(config, reportDate, start, end))
                .toList();
    }

    private DataMonitorReport generateOne(DataMonitorConfig config,
                                          LocalDate reportDate,
                                          LocalDateTime start,
                                          LocalDateTime end) {
        int slowSql = slowSqlEventMapper.selectCount(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getMonitorConfigId, config.getId())
                .ge(SlowSqlEvent::getOccurredAt, start)
                .lt(SlowSqlEvent::getOccurredAt, end)).intValue();
        int lockEvents = dbLockEventMapper.selectCount(new LambdaQueryWrapper<DbLockEvent>()
                .eq(DbLockEvent::getAppName, config.getAppName())
                .eq(DbLockEvent::getEnvironment, config.getEnvironment())
                .ge(DbLockEvent::getOccurredAt, start)
                .lt(DbLockEvent::getOccurredAt, end)).intValue();
        int poolRisks = poolSnapshotMapper.selectCount(new LambdaQueryWrapper<ConnectionPoolSnapshot>()
                .eq(ConnectionPoolSnapshot::getMonitorConfigId, config.getId())
                .isNotNull(ConnectionPoolSnapshot::getRiskType)
                .ge(ConnectionPoolSnapshot::getCollectedAt, start)
                .lt(ConnectionPoolSnapshot::getCollectedAt, end)).intValue();
        int logIssues = logQualityIssueMapper.selectCount(new LambdaQueryWrapper<LogQualityIssue>()
                .eq(LogQualityIssue::getAppName, config.getAppName())
                .eq(LogQualityIssue::getEnvironment, config.getEnvironment())
                .ge(LogQualityIssue::getOccurredAt, start)
                .lt(LogQualityIssue::getOccurredAt, end)).intValue();
        int healthScore = Math.max(0, 100 - slowSql * 3 - lockEvents * 8 - poolRisks * 6 - logIssues * 2);

        DataMonitorReport report = new DataMonitorReport();
        report.setMonitorConfigId(config.getId());
        report.setAppName(config.getAppName());
        report.setEnvironment(config.getEnvironment());
        report.setReportType("DAILY");
        report.setReportDate(reportDate);
        report.setHealthScore(healthScore);
        report.setSlowSqlCount(slowSql);
        report.setLockEventCount(lockEvents);
        report.setPoolRiskCount(poolRisks);
        report.setLogQualityIssueCount(logIssues);
        report.setSummary("数据库健康分 " + healthScore + "，慢 SQL " + slowSql
                + "，锁等待 " + lockEvents + "，连接池风险 " + poolRisks
                + "，日志质量问题 " + logIssues);
        report.setDetailJson(JSON.toJSONString(Map.of(
                "slowSqlCount", slowSql,
                "lockEventCount", lockEvents,
                "poolRiskCount", poolRisks,
                "logQualityIssueCount", logIssues
        )));
        reportMapper.insert(report);
        return report;
    }
}
