package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.constant.DataMonitorConstants;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.model.DataMonitorDashboardResponse;
import com.lnzz.argus.datamonitor.model.LogQualityIssueView;
import com.lnzz.argus.datamonitor.model.PoolRiskView;
import com.lnzz.argus.datamonitor.model.SlowSqlEventView;
import com.lnzz.argus.datamonitor.service.DataMonitorQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据监控工作台查询服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMonitorQueryServiceImpl implements DataMonitorQueryService {

    private static final int DEFAULT_QUERY_LIMIT = 100;
    private static final String DEFAULT_WINDOW = "24h";
    private static final Pattern WINDOW_PATTERN = Pattern.compile("^(\\d+)([hd])$", Pattern.CASE_INSENSITIVE);
    private static final String LOG_QUALITY_STATUS_IGNORED = "IGNORED";

    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final SlowSqlEventMapper slowSqlEventMapper;
    private final ConnectionPoolSnapshotMapper connectionPoolSnapshotMapper;
    private final LogQualityIssueMapper logQualityIssueMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final DbLockEventMapper dbLockEventMapper;
    private final InterfaceLogTableConfigMapper interfaceLogTableConfigMapper;

    @Override
    public DataMonitorDashboardResponse getDashboard(String appName, String environment, String window) {
        QueryWindow queryWindow = resolveWindow(window);
        List<DataMonitorConfig> configs = queryMonitorConfigs(appName, environment);
        List<SlowSqlEvent> slowSqlEvents = querySlowSqlEntities(appName, environment, queryWindow, false);
        List<ConnectionPoolSnapshot> poolRisks = queryPoolRiskEntities(appName, environment, queryWindow, false);
        List<LogQualityIssue> logQualityIssues = queryLogQualityIssueEntities(appName, environment, queryWindow, false);

        Set<String> unhealthyAppKeys = buildUnhealthyAppKeys(slowSqlEvents, poolRisks, logQualityIssues);
        int monitoredAppCount = configs.size();

        DataMonitorDashboardResponse response = new DataMonitorDashboardResponse();
        response.setMonitoredAppCount(monitoredAppCount);
        response.setHealthyAppCount(Math.max(monitoredAppCount - unhealthyAppKeys.size(), 0));
        response.setSlowSqlCount(slowSqlEvents.size());
        response.setPoolRiskCount(poolRisks.size());
        response.setLogQualityIssueCount(logQualityIssues.size());

        log.debug("查询数据监控概览: appName={}, environment={}, window={}, monitoredAppCount={}, slowSqlCount={}, poolRiskCount={}, logQualityIssueCount={}",
                trimToNull(appName), trimToNull(environment), queryWindow.raw(), monitoredAppCount,
                slowSqlEvents.size(), poolRisks.size(), logQualityIssues.size());
        return response;
    }

    @Override
    public List<SlowSqlEventView> listSlowSql(String appName, String environment, String window) {
        QueryWindow queryWindow = resolveWindow(window);
        List<SlowSqlEvent> events = querySlowSqlEntities(appName, environment, queryWindow, true);
        Map<Long, DataSourceConfig> datasourceMap = toDatasourceMap(extractIds(events, SlowSqlEvent::getDatasourceId));
        Map<Long, DbLockEvent> lockEventMap = toLockEventMap(extractIds(events, SlowSqlEvent::getRelatedLockEventId));
        Map<Long, ConnectionPoolSnapshot> poolSnapshotMap =
                toPoolSnapshotMap(extractIds(events, SlowSqlEvent::getRelatedPoolSnapshotId));

        List<SlowSqlEventView> results = events.stream()
                .map(event -> toSlowSqlView(event, datasourceMap, lockEventMap, poolSnapshotMap))
                .toList();
        log.debug("查询慢 SQL 列表: appName={}, environment={}, window={}, count={}",
                trimToNull(appName), trimToNull(environment), queryWindow.raw(), results.size());
        return results;
    }

    @Override
    public List<PoolRiskView> listPoolRisks(String appName, String environment, String window) {
        QueryWindow queryWindow = resolveWindow(window);
        List<ConnectionPoolSnapshot> snapshots = queryPoolRiskEntities(appName, environment, queryWindow, true);
        List<PoolRiskView> results = snapshots.stream()
                .map(this::toPoolRiskView)
                .toList();
        log.debug("查询连接池风险列表: appName={}, environment={}, window={}, count={}",
                trimToNull(appName), trimToNull(environment), queryWindow.raw(), results.size());
        return results;
    }

    @Override
    public List<LogQualityIssueView> listLogQualityIssues(String appName, String environment, String window) {
        QueryWindow queryWindow = resolveWindow(window);
        List<LogQualityIssue> issues = queryLogQualityIssueEntities(appName, environment, queryWindow, true);
        Map<Long, InterfaceLogTableConfig> configMap =
                toLogTableConfigMap(extractIds(issues, LogQualityIssue::getLogTableConfigId));

        List<LogQualityIssueView> results = issues.stream()
                .map(issue -> toLogQualityIssueView(issue, configMap.get(issue.getLogTableConfigId())))
                .toList();
        log.debug("查询日志质量问题列表: appName={}, environment={}, window={}, count={}",
                trimToNull(appName), trimToNull(environment), queryWindow.raw(), results.size());
        return results;
    }

    private List<DataMonitorConfig> queryMonitorConfigs(String appName, String environment) {
        LambdaQueryWrapper<DataMonitorConfig> wrapper = new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getEnabled, true)
                .orderByAsc(DataMonitorConfig::getAppName)
                .orderByAsc(DataMonitorConfig::getId);
        applyAppAndEnvironment(wrapper, appName, environment,
                DataMonitorConfig::getAppName, DataMonitorConfig::getEnvironment);
        return dataMonitorConfigMapper.selectList(wrapper);
    }

    private List<SlowSqlEvent> querySlowSqlEntities(String appName, String environment,
                                                    QueryWindow window, boolean limited) {
        LambdaQueryWrapper<SlowSqlEvent> wrapper = new LambdaQueryWrapper<SlowSqlEvent>()
                .ge(SlowSqlEvent::getOccurredAt, window.start())
                .le(SlowSqlEvent::getOccurredAt, window.end())
                .orderByDesc(SlowSqlEvent::getOccurredAt)
                .orderByDesc(SlowSqlEvent::getId);
        applyAppAndEnvironment(wrapper, appName, environment, SlowSqlEvent::getAppName, SlowSqlEvent::getEnvironment);
        if (limited) {
            wrapper.last("LIMIT " + DEFAULT_QUERY_LIMIT);
        }
        return slowSqlEventMapper.selectList(wrapper);
    }

    private List<ConnectionPoolSnapshot> queryPoolRiskEntities(String appName, String environment,
                                                               QueryWindow window, boolean limited) {
        LambdaQueryWrapper<ConnectionPoolSnapshot> wrapper = new LambdaQueryWrapper<ConnectionPoolSnapshot>()
                .isNotNull(ConnectionPoolSnapshot::getRiskType)
                .ge(ConnectionPoolSnapshot::getCollectedAt, window.start())
                .le(ConnectionPoolSnapshot::getCollectedAt, window.end())
                .orderByDesc(ConnectionPoolSnapshot::getCollectedAt)
                .orderByDesc(ConnectionPoolSnapshot::getId);
        applyAppAndEnvironment(wrapper, appName, environment,
                ConnectionPoolSnapshot::getAppName, ConnectionPoolSnapshot::getEnvironment);
        if (limited) {
            wrapper.last("LIMIT " + DEFAULT_QUERY_LIMIT);
        }
        return connectionPoolSnapshotMapper.selectList(wrapper);
    }

    private List<LogQualityIssue> queryLogQualityIssueEntities(String appName, String environment,
                                                               QueryWindow window, boolean limited) {
        LambdaQueryWrapper<LogQualityIssue> wrapper = new LambdaQueryWrapper<LogQualityIssue>()
                .ne(LogQualityIssue::getStatus, LOG_QUALITY_STATUS_IGNORED)
                .ge(LogQualityIssue::getOccurredAt, window.start())
                .le(LogQualityIssue::getOccurredAt, window.end())
                .orderByDesc(LogQualityIssue::getOccurredAt)
                .orderByDesc(LogQualityIssue::getId);
        applyAppAndEnvironment(wrapper, appName, environment,
                LogQualityIssue::getAppName, LogQualityIssue::getEnvironment);
        if (limited) {
            wrapper.last("LIMIT " + DEFAULT_QUERY_LIMIT);
        }
        return logQualityIssueMapper.selectList(wrapper);
    }

    private SlowSqlEventView toSlowSqlView(SlowSqlEvent event,
                                           Map<Long, DataSourceConfig> datasourceMap,
                                           Map<Long, DbLockEvent> lockEventMap,
                                           Map<Long, ConnectionPoolSnapshot> poolSnapshotMap) {
        DataSourceConfig datasource = datasourceMap.get(event.getDatasourceId());
        SlowSqlEventView view = new SlowSqlEventView();
        view.setId(event.getId());
        view.setAppName(event.getAppName());
        view.setEnvironment(event.getEnvironment());
        view.setDatasourceCode(datasource == null ? null : datasource.getDatasourceCode());
        view.setDatasourceName(datasource == null ? null : datasource.getDatasourceName());
        view.setSourceType(event.getSourceType());
        view.setSqlFingerprint(event.getSqlFingerprint());
        view.setSqlText(event.getSqlText());
        view.setSqlTextMasked(event.getSqlTextMasked());
        view.setDurationMs(event.getDurationMs());
        view.setLockTimeMs(event.getLockTimeMs());
        view.setRowsSent(event.getRowsSent());
        view.setRowsExamined(event.getRowsExamined());
        view.setProcessState(event.getProcessState());
        view.setExplainJson(event.getExplainJson());
        view.setRelatedLockEventId(event.getRelatedLockEventId());
        view.setRelatedPoolSnapshotId(event.getRelatedPoolSnapshotId());
        view.setCauseType(event.getCauseType());
        view.setRiskLevel(event.getRiskLevel());
        view.setStatus(event.getAnalysisStatus());
        view.setAnalysisStatus(event.getAnalysisStatus());
        view.setRootCause(event.getRootCause());
        view.setOptimizationSuggestion(event.getOptimizationSuggestion());
        view.setIndexSuggestionSql(event.getIndexSuggestionSql());
        view.setNeedDba(event.getNeedDba());
        view.setNeedDeveloper(event.getNeedDeveloper());
        view.setCanViewFullSql(StringUtils.hasText(event.getSqlText()));
        view.setRelatedLockEvent(lockEventMap.get(event.getRelatedLockEventId()));
        view.setRelatedPoolSnapshot(poolSnapshotMap.get(event.getRelatedPoolSnapshotId()));
        view.setOccurredAt(event.getOccurredAt());
        return view;
    }

    private PoolRiskView toPoolRiskView(ConnectionPoolSnapshot snapshot) {
        PoolRiskView view = new PoolRiskView();
        view.setId(snapshot.getId());
        view.setAppName(snapshot.getAppName());
        view.setEnvironment(snapshot.getEnvironment());
        view.setDatasourceName(snapshot.getDatasourceName());
        view.setPoolName(snapshot.getDatasourceName());
        view.setPoolType(snapshot.getPoolType());
        view.setActiveConnections(snapshot.getActiveConnections());
        view.setMaxConnections(snapshot.getMaxConnections());
        view.setWaitingThreads(snapshot.getWaitingThreads());
        view.setTimeoutCount(snapshot.getTimeoutCount());
        view.setUsagePercent(calculateUsagePercent(snapshot.getActiveConnections(), snapshot.getMaxConnections()));
        view.setRiskLevel(snapshot.getRiskLevel());
        view.setRiskType(snapshot.getRiskType());
        view.setRiskReason(buildPoolRiskReason(snapshot));
        view.setCollectedAt(snapshot.getCollectedAt());
        return view;
    }

    private LogQualityIssueView toLogQualityIssueView(LogQualityIssue issue, InterfaceLogTableConfig config) {
        LogQualityIssueView view = new LogQualityIssueView();
        view.setId(issue.getId());
        view.setAppName(issue.getAppName());
        view.setEnvironment(issue.getEnvironment());
        view.setConfigName(config == null ? null : config.getConfigName());
        view.setTableName(issue.getTableName());
        view.setIssueType(issue.getIssueType());
        view.setIssueLevel(issue.getSeverity());
        view.setIssueSummary(issue.getDescription());
        view.setDescription(issue.getDescription());
        view.setSuggestion(issue.getSuggestion());
        view.setStatus(issue.getStatus());
        view.setOccurredAt(issue.getOccurredAt());
        return view;
    }

    private String buildPoolRiskReason(ConnectionPoolSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.getRiskReason())) {
            return snapshot.getRiskReason();
        }
        if (DataMonitorConstants.RISK_POOL_EXHAUSTED.equals(snapshot.getRiskType())) {
            return "连接池已接近耗尽，请优先排查长 SQL 或连接泄漏";
        }
        if (DataMonitorConstants.RISK_POOL_HIGH_USAGE.equals(snapshot.getRiskType())) {
            return "连接池使用率持续偏高，请关注峰值流量和慢请求";
        }
        if (DataMonitorConstants.RISK_POOL_ACQUIRE_SLOW.equals(snapshot.getRiskType())) {
            return "获取连接耗时偏高，请排查连接池参数和数据库响应";
        }
        if (DataMonitorConstants.RISK_POOL_ERROR.equals(snapshot.getRiskType())) {
            return "连接池存在异常或超时，请检查连接稳定性和错误日志";
        }
        return snapshot.getRiskType();
    }

    private Double calculateUsagePercent(Integer activeConnections, Integer maxConnections) {
        if (activeConnections == null || maxConnections == null || maxConnections <= 0) {
            return null;
        }
        return activeConnections * 100D / maxConnections;
    }

    private Set<String> buildUnhealthyAppKeys(List<SlowSqlEvent> slowSqlEvents,
                                              List<ConnectionPoolSnapshot> poolRisks,
                                              List<LogQualityIssue> logQualityIssues) {
        Set<String> keys = slowSqlEvents.stream()
                .map(event -> appKey(event.getAppName(), event.getEnvironment()))
                .collect(Collectors.toSet());
        keys.addAll(poolRisks.stream()
                .map(snapshot -> appKey(snapshot.getAppName(), snapshot.getEnvironment()))
                .collect(Collectors.toSet()));
        keys.addAll(logQualityIssues.stream()
                .map(issue -> appKey(issue.getAppName(), issue.getEnvironment()))
                .collect(Collectors.toSet()));
        keys.remove(appKey(null, null));
        return keys;
    }

    private String appKey(String appName, String environment) {
        return normalize(appName) + "#" + normalize(environment);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<Long, DataSourceConfig> toDatasourceMap(Set<Long> datasourceIds) {
        if (datasourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dataSourceConfigMapper.selectBatchIds(datasourceIds).stream()
                .collect(Collectors.toMap(DataSourceConfig::getId, item -> item, (left, right) -> left));
    }

    private Map<Long, DbLockEvent> toLockEventMap(Set<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dbLockEventMapper.selectBatchIds(eventIds).stream()
                .collect(Collectors.toMap(DbLockEvent::getId, item -> item, (left, right) -> left));
    }

    private Map<Long, ConnectionPoolSnapshot> toPoolSnapshotMap(Set<Long> snapshotIds) {
        if (snapshotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return connectionPoolSnapshotMapper.selectBatchIds(snapshotIds).stream()
                .collect(Collectors.toMap(ConnectionPoolSnapshot::getId, item -> item, (left, right) -> left));
    }

    private Map<Long, InterfaceLogTableConfig> toLogTableConfigMap(Set<Long> configIds) {
        if (configIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return interfaceLogTableConfigMapper.selectBatchIds(configIds).stream()
                .collect(Collectors.toMap(InterfaceLogTableConfig::getId, item -> item, (left, right) -> left));
    }

    private <T, R> Set<R> extractIds(Collection<T> source, java.util.function.Function<T, R> extractor) {
        return source.stream()
                .map(extractor)
                .filter(item -> item != null)
                .collect(Collectors.toSet());
    }

    private <T> void applyAppAndEnvironment(LambdaQueryWrapper<T> wrapper,
                                            String appName,
                                            String environment,
                                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> appField,
                                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> envField) {
        if (StringUtils.hasText(appName)) {
            wrapper.eq(appField, appName.trim());
        }
        if (StringUtils.hasText(environment)) {
            wrapper.eq(envField, environment.trim());
        }
    }

    private QueryWindow resolveWindow(String window) {
        String raw = StringUtils.hasText(window) ? window.trim().toLowerCase(Locale.ROOT) : DEFAULT_WINDOW;
        Matcher matcher = WINDOW_PATTERN.matcher(raw);
        LocalDateTime end = LocalDateTime.now();
        if (!matcher.matches()) {
            return new QueryWindow(DEFAULT_WINDOW, end.minusHours(24), end);
        }
        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        LocalDateTime start = "d".equals(unit) ? end.minusDays(amount) : end.minusHours(amount);
        return new QueryWindow(raw, start, end);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record QueryWindow(String raw, LocalDateTime start, LocalDateTime end) {
    }
}
