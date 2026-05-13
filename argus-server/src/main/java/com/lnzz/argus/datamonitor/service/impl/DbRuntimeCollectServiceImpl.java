package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
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
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.ThresholdConfig;
import com.lnzz.argus.datamonitor.service.DataSourceSecretCodec;
import com.lnzz.argus.datamonitor.service.DbRuntimeCollectService;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbLockRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbLockWaitRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.InnodbTransactionRow;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector.ProcessRow;
import com.lnzz.argus.datamonitor.service.SqlTextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MySQL 5.7 运行现场采集服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbRuntimeCollectServiceImpl implements DbRuntimeCollectService {

    private static final String DEFAULT_ENVIRONMENT = "PROD";

    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final DbMetricSnapshotMapper metricSnapshotMapper;
    private final DbProcessSnapshotMapper processSnapshotMapper;
    private final DbLockEventMapper lockEventMapper;
    private final SlowSqlEventMapper slowSqlEventMapper;
    private final DataSourceSecretCodec secretCodec;
    private final MysqlRuntimeCollector mysqlRuntimeCollector;
    private final SqlTextSanitizer sqlTextSanitizer;

    @Override
    public List<DatasourceCollectResult> collectAllEnabled() {
        List<DataSourceConfig> datasources = dataSourceConfigMapper.selectList(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getEnabled, true)
                .eq(DataSourceConfig::getReadonly, true)
                .eq(DataSourceConfig::getDbType, "MYSQL")
                .orderByAsc(DataSourceConfig::getProjectMappingId)
                .orderByAsc(DataSourceConfig::getDatasourceCode));
        List<DatasourceCollectResult> results = new ArrayList<>();
        for (DataSourceConfig datasource : datasources) {
            try {
                results.add(collect(datasource));
            } catch (Exception e) {
                log.warn("数据源运行现场采集失败，已降级继续: datasourceId={}, code={}, message={}",
                        datasource.getId(), datasource.getDatasourceCode(), e.getMessage());
                results.add(failure(datasource, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public DatasourceCollectResult collectDatasource(Long datasourceId) {
        DataSourceConfig datasource = dataSourceConfigMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源配置不存在: " + datasourceId);
        }
        if (!Boolean.TRUE.equals(datasource.getEnabled()) || !Boolean.TRUE.equals(datasource.getReadonly())) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源未启用或不是只读配置");
        }
        return collect(datasource);
    }

    private DatasourceCollectResult collect(DataSourceConfig datasource) {
        DataMonitorConfig monitorConfig = dataMonitorConfigMapper.selectById(datasource.getMonitorConfigId());
        String password = secretCodec.decrypt(datasource.getPasswordSecret());
        MysqlRuntimeCollector.RuntimeSnapshot snapshot = mysqlRuntimeCollector.collect(datasource, password);
        DbMetricSnapshot metricSnapshot = buildMetricSnapshot(datasource, monitorConfig, snapshot);
        metricSnapshotMapper.insert(metricSnapshot);
        ProcessSaveResult processSaveResult = saveProcessSnapshots(datasource, monitorConfig, snapshot);
        int lockEventCount = saveLockEvents(datasource, monitorConfig, snapshot);
        return new DatasourceCollectResult(
                datasource.getId(),
                datasource.getDatasourceCode(),
                true,
                metricSnapshot.getId(),
                processSaveResult.savedCount(),
                processSaveResult.longSqlCount(),
                processSaveResult.lockedCount(),
                processSaveResult.metadataLockCount(),
                lockEventCount,
                snapshot.innodbTransactions().size(),
                snapshot.innodbLockWaits().size(),
                "采集成功"
        );
    }

    private int saveLockEvents(DataSourceConfig datasource,
                               DataMonitorConfig monitorConfig,
                               MysqlRuntimeCollector.RuntimeSnapshot snapshot) {
        ThresholdConfig thresholds = parseThresholds(datasource.getThresholdConfig());
        int lockWaitSeconds = thresholds != null && thresholds.lockWaitSeconds() != null
                ? thresholds.lockWaitSeconds()
                : 5;
        Map<String, InnodbTransactionRow> trxById = snapshot.innodbTransactions().stream()
                .filter(row -> StringUtils.hasText(row.trxId()))
                .collect(Collectors.toMap(InnodbTransactionRow::trxId, row -> row, (a, b) -> a));
        Map<Long, ProcessRow> processById = snapshot.processRows().stream()
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(ProcessRow::id, row -> row, (a, b) -> a));
        Map<String, InnodbLockRow> lockById = snapshot.innodbLocks().stream()
                .filter(row -> StringUtils.hasText(row.lockId()))
                .collect(Collectors.toMap(InnodbLockRow::lockId, row -> row, (a, b) -> a));

        int saved = 0;
        for (InnodbLockWaitRow waitRow : snapshot.innodbLockWaits()) {
            InnodbTransactionRow waitingTrx = trxById.get(waitRow.requestingTrxId());
            InnodbTransactionRow blockingTrx = trxById.get(waitRow.blockingTrxId());
            ProcessRow waitingProcess = waitingTrx == null ? null : processById.get(waitingTrx.mysqlThreadId());
            ProcessRow blockingProcess = blockingTrx == null ? null : processById.get(blockingTrx.mysqlThreadId());
            InnodbLockRow requestedLock = lockById.get(waitRow.requestedLockId());
            DbLockEvent event = buildLockEvent(datasource, monitorConfig, snapshot.collectedAt(), waitRow,
                    waitingTrx, blockingTrx, waitingProcess, blockingProcess, requestedLock, lockWaitSeconds);
            lockEventMapper.insert(event);
            linkSlowSqlEvent(event);
            saved++;
        }

        if (saved == 0) {
            for (ProcessRow row : snapshot.processRows()) {
                String state = normalize(row.state());
                if (!state.contains("LOCK")) {
                    continue;
                }
                DbLockEvent event = buildFallbackLockEvent(datasource, monitorConfig, snapshot.collectedAt(), row,
                        lockWaitSeconds);
                lockEventMapper.insert(event);
                linkSlowSqlEvent(event);
                saved++;
            }
        }
        return saved;
    }

    private DbLockEvent buildLockEvent(DataSourceConfig datasource,
                                       DataMonitorConfig monitorConfig,
                                       LocalDateTime occurredAt,
                                       InnodbLockWaitRow waitRow,
                                       InnodbTransactionRow waitingTrx,
                                       InnodbTransactionRow blockingTrx,
                                       ProcessRow waitingProcess,
                                       ProcessRow blockingProcess,
                                       InnodbLockRow requestedLock,
                                       int lockWaitSeconds) {
        String waitingSql = firstText(waitingProcess == null ? null : waitingProcess.info(),
                waitingTrx == null ? null : waitingTrx.trxQuery());
        String blockingSql = firstText(blockingProcess == null ? null : blockingProcess.info(),
                blockingTrx == null ? null : blockingTrx.trxQuery());
        int waitSeconds = waitingProcess != null && waitingProcess.time() != null
                ? waitingProcess.time()
                : waitingTrx != null && waitingTrx.trxStartedSeconds() != null ? waitingTrx.trxStartedSeconds() : 0;

        DbLockEvent event = new DbLockEvent();
        event.setDatasourceId(datasource.getId());
        event.setAppName(monitorConfig == null ? null : monitorConfig.getAppName());
        event.setEnvironment(monitorConfig == null ? DEFAULT_ENVIRONMENT : monitorConfig.getEnvironment());
        event.setWaitingTrxId(waitRow.requestingTrxId());
        event.setBlockingTrxId(waitRow.blockingTrxId());
        event.setWaitingProcessId(waitingTrx == null ? null : waitingTrx.mysqlThreadId());
        event.setBlockingProcessId(blockingTrx == null ? null : blockingTrx.mysqlThreadId());
        event.setLockTable(requestedLock == null ? null : requestedLock.lockTable());
        event.setLockIndex(requestedLock == null ? null : requestedLock.lockIndex());
        event.setLockType(requestedLock == null ? null : requestedLock.lockType());
        event.setWaitSeconds(waitSeconds);
        event.setWaitingSql(waitingSql);
        event.setBlockingSql(blockingSql);
        event.setRiskLevel(waitSeconds >= lockWaitSeconds ? "P1" : "P2");
        event.setStatus("NEW");
        event.setOccurredAt(occurredAt);
        event.setEventFingerprint(lockFingerprint(datasource.getId(), event.getWaitingTrxId(), event.getBlockingTrxId(),
                event.getWaitingProcessId(), event.getBlockingProcessId(), waitingSql));
        return event;
    }

    private DbLockEvent buildFallbackLockEvent(DataSourceConfig datasource,
                                               DataMonitorConfig monitorConfig,
                                               LocalDateTime occurredAt,
                                               ProcessRow row,
                                               int lockWaitSeconds) {
        DbLockEvent event = new DbLockEvent();
        event.setDatasourceId(datasource.getId());
        event.setAppName(monitorConfig == null ? null : monitorConfig.getAppName());
        event.setEnvironment(monitorConfig == null ? DEFAULT_ENVIRONMENT : monitorConfig.getEnvironment());
        event.setWaitingProcessId(row.id());
        event.setLockType(normalize(row.state()).contains("METADATA LOCK") ? "METADATA_LOCK" : "PROCESSLIST_LOCK");
        event.setWaitSeconds(row.time());
        event.setWaitingSql(row.info());
        event.setRiskLevel(row.time() != null && row.time() >= lockWaitSeconds ? "P1" : "P2");
        event.setStatus("NEW");
        event.setOccurredAt(occurredAt);
        event.setEventFingerprint(lockFingerprint(datasource.getId(), null, null, row.id(), null, row.info()));
        return event;
    }

    private void linkSlowSqlEvent(DbLockEvent event) {
        String waitingFingerprint = sqlTextSanitizer.fingerprint(event.getWaitingSql());
        if (!StringUtils.hasText(waitingFingerprint)) {
            return;
        }
        SlowSqlEvent slowSqlEvent = slowSqlEventMapper.selectOne(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getDatasourceId, event.getDatasourceId())
                .eq(SlowSqlEvent::getSqlFingerprint, waitingFingerprint)
                .isNull(SlowSqlEvent::getRelatedLockEventId)
                .orderByDesc(SlowSqlEvent::getOccurredAt)
                .last("limit 1"));
        if (slowSqlEvent != null) {
            slowSqlEvent.setRelatedLockEventId(event.getId());
            slowSqlEventMapper.updateById(slowSqlEvent);
        }
    }

    private DbMetricSnapshot buildMetricSnapshot(DataSourceConfig datasource,
                                                 DataMonitorConfig monitorConfig,
                                                 MysqlRuntimeCollector.RuntimeSnapshot snapshot) {
        Map<String, Long> status = snapshot.globalStatus();
        DbMetricSnapshot metric = new DbMetricSnapshot();
        metric.setDatasourceId(datasource.getId());
        metric.setAppName(monitorConfig != null ? monitorConfig.getAppName() : null);
        metric.setEnvironment(monitorConfig != null ? monitorConfig.getEnvironment() : DEFAULT_ENVIRONMENT);
        metric.setThreadsConnected(toInt(status.get("Threads_connected")));
        metric.setThreadsRunning(toInt(status.get("Threads_running")));
        metric.setMaxConnections(toInt(status.get("Max_connections")));
        metric.setQuestions(status.getOrDefault("Questions", 0L));
        metric.setComSelect(status.getOrDefault("Com_select", 0L));
        metric.setComInsert(status.getOrDefault("Com_insert", 0L));
        metric.setComUpdate(status.getOrDefault("Com_update", 0L));
        metric.setComDelete(status.getOrDefault("Com_delete", 0L));
        metric.setSlowQueries(status.getOrDefault("Slow_queries", 0L));
        metric.setInnodbTrxCount(snapshot.innodbTransactions().size());
        metric.setInnodbLockWaitCount(snapshot.innodbLockWaits().size());
        metric.setCollectedAt(snapshot.collectedAt());
        metric.setQps(calculateQps(datasource.getId(), metric.getQuestions(), metric.getCollectedAt()));
        return metric;
    }

    private ProcessSaveResult saveProcessSnapshots(DataSourceConfig datasource,
                                                   DataMonitorConfig monitorConfig,
                                                   MysqlRuntimeCollector.RuntimeSnapshot snapshot) {
        ThresholdConfig thresholds = parseThresholds(datasource.getThresholdConfig());
        int longSqlSeconds = thresholds != null && thresholds.longSqlSeconds() != null
                ? thresholds.longSqlSeconds()
                : 5;
        int longTransactionSeconds = thresholds != null && thresholds.longTransactionSeconds() != null
                ? thresholds.longTransactionSeconds()
                : 30;
        Map<Long, InnodbTransactionRow> transactionByThread = snapshot.innodbTransactions().stream()
                .filter(row -> row.mysqlThreadId() != null)
                .collect(Collectors.toMap(InnodbTransactionRow::mysqlThreadId, row -> row, (a, b) -> a));

        int saved = 0;
        int longSql = 0;
        int locked = 0;
        int metadataLock = 0;
        for (ProcessRow row : snapshot.processRows()) {
            if (!shouldPersistProcess(row, transactionByThread, longSqlSeconds, longTransactionSeconds)) {
                continue;
            }
            DbProcessSnapshot entity = buildProcessSnapshot(datasource, snapshot.collectedAt(), row,
                    transactionByThread.get(row.id()), longSqlSeconds, longTransactionSeconds);
            processSnapshotMapper.insert(entity);
            if ("LONG_SQL".equals(entity.getRiskType()) || "LONG_TRX".equals(entity.getRiskType())) {
                saveProcesslistSlowSqlEvent(datasource, monitorConfig, entity);
            }
            saved++;
            if ("LONG_SQL".equals(entity.getRiskType()) || "LONG_TRX".equals(entity.getRiskType())) {
                longSql++;
            } else if ("LOCKED".equals(entity.getRiskType())) {
                locked++;
            } else if ("METADATA_LOCK".equals(entity.getRiskType())) {
                metadataLock++;
            }
        }
        return new ProcessSaveResult(saved, longSql, locked, metadataLock);
    }

    private void saveProcesslistSlowSqlEvent(DataSourceConfig datasource,
                                             DataMonitorConfig monitorConfig,
                                             DbProcessSnapshot processSnapshot) {
        if (!StringUtils.hasText(processSnapshot.getSqlFingerprint())) {
            return;
        }
        String idempotentKey = "processlist:" + datasource.getId() + ":" + processSnapshot.getMysqlProcessId()
                + ":" + processSnapshot.getCollectedAt();
        SlowSqlEvent existing = slowSqlEventMapper.selectOne(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getIdempotentKey, idempotentKey)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        SlowSqlEvent event = new SlowSqlEvent();
        event.setDatasourceId(datasource.getId());
        event.setMonitorConfigId(datasource.getMonitorConfigId());
        event.setAppName(monitorConfig == null ? null : monitorConfig.getAppName());
        event.setEnvironment(monitorConfig == null ? DEFAULT_ENVIRONMENT : monitorConfig.getEnvironment());
        event.setSourceType("PROCESSLIST");
        event.setIdempotentKey(idempotentKey);
        event.setSqlFingerprint(processSnapshot.getSqlFingerprint());
        event.setSqlText(Boolean.TRUE.equals(datasource.getFullSqlCollectEnabled()) ? processSnapshot.getSqlText() : null);
        event.setSqlTextMasked(processSnapshot.getSqlTextMasked());
        event.setDurationMs(processSnapshot.getDurationSeconds() == null ? null : processSnapshot.getDurationSeconds() * 1000L);
        event.setProcessState(processSnapshot.getProcessState());
        event.setCauseType(processSnapshot.getRiskType());
        event.setRiskLevel(processSnapshot.getRiskLevel());
        event.setAnalysisStatus("PENDING");
        event.setNeedDba(false);
        event.setNeedDeveloper(true);
        event.setOccurredAt(processSnapshot.getCollectedAt());
        slowSqlEventMapper.insert(event);
    }

    private boolean shouldPersistProcess(ProcessRow row,
                                         Map<Long, InnodbTransactionRow> transactionByThread,
                                         int longSqlSeconds,
                                         int longTransactionSeconds) {
        String riskType = resolveRiskType(row, transactionByThread.get(row.id()), longSqlSeconds, longTransactionSeconds);
        return riskType != null || StringUtils.hasText(row.info());
    }

    private DbProcessSnapshot buildProcessSnapshot(DataSourceConfig datasource,
                                                   LocalDateTime collectedAt,
                                                   ProcessRow row,
                                                   InnodbTransactionRow trx,
                                                   int longSqlSeconds,
                                                   int longTransactionSeconds) {
        String sqlText = StringUtils.hasText(row.info()) ? row.info() : trx != null ? trx.trxQuery() : null;
        String riskType = resolveRiskType(row, trx, longSqlSeconds, longTransactionSeconds);
        DbProcessSnapshot entity = new DbProcessSnapshot();
        entity.setDatasourceId(datasource.getId());
        entity.setMysqlProcessId(row.id());
        entity.setUserName(row.user());
        entity.setHostInfo(row.host());
        entity.setDatabaseName(row.db());
        entity.setCommandType(row.command());
        entity.setProcessState(row.state());
        entity.setDurationSeconds(row.time());
        entity.setSqlFingerprint(sqlTextSanitizer.fingerprint(sqlText));
        entity.setSqlText(Boolean.TRUE.equals(datasource.getFullSqlCollectEnabled()) ? sqlText : null);
        entity.setSqlTextMasked(sqlTextSanitizer.mask(sqlText));
        entity.setRiskType(riskType);
        entity.setRiskLevel(resolveRiskLevel(riskType, row, trx));
        entity.setCollectedAt(collectedAt);
        return entity;
    }

    private String resolveRiskType(ProcessRow row,
                                   InnodbTransactionRow trx,
                                   int longSqlSeconds,
                                   int longTransactionSeconds) {
        String state = normalize(row.state());
        if (state.contains("METADATA LOCK")) {
            return "METADATA_LOCK";
        }
        if (state.contains("LOCK") || "LOCKED".equalsIgnoreCase(row.command())) {
            return "LOCKED";
        }
        if (row.time() != null && row.time() >= longSqlSeconds && StringUtils.hasText(row.info())) {
            return "LONG_SQL";
        }
        if (trx != null && trx.trxStartedSeconds() != null && trx.trxStartedSeconds() >= longTransactionSeconds) {
            return "LONG_TRX";
        }
        return null;
    }

    private String resolveRiskLevel(String riskType, ProcessRow row, InnodbTransactionRow trx) {
        if (!StringUtils.hasText(riskType)) {
            return null;
        }
        if ("METADATA_LOCK".equals(riskType) || "LOCKED".equals(riskType)) {
            return "P1";
        }
        int duration = row.time() != null ? row.time() : trx != null && trx.trxStartedSeconds() != null
                ? trx.trxStartedSeconds()
                : 0;
        return duration >= 60 ? "P1" : "P2";
    }

    private BigDecimal calculateQps(Long datasourceId, Long currentQuestions, LocalDateTime collectedAt) {
        DbMetricSnapshot previous = metricSnapshotMapper.selectOne(new LambdaQueryWrapper<DbMetricSnapshot>()
                .eq(DbMetricSnapshot::getDatasourceId, datasourceId)
                .orderByDesc(DbMetricSnapshot::getCollectedAt)
                .last("limit 1"));
        if (previous == null || previous.getQuestions() == null || previous.getCollectedAt() == null
                || currentQuestions == null || collectedAt == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long seconds = Math.max(1, Duration.between(previous.getCollectedAt(), collectedAt).getSeconds());
        long diff = Math.max(0, currentQuestions - previous.getQuestions());
        return BigDecimal.valueOf(diff)
                .divide(BigDecimal.valueOf(seconds), 2, RoundingMode.HALF_UP);
    }

    private ThresholdConfig parseThresholds(String thresholdConfig) {
        if (!StringUtils.hasText(thresholdConfig)) {
            return null;
        }
        return JSON.parseObject(thresholdConfig, ThresholdConfig.class);
    }

    private DatasourceCollectResult failure(DataSourceConfig datasource, String message) {
        return new DatasourceCollectResult(
                datasource.getId(),
                datasource.getDatasourceCode(),
                false,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                message
        );
    }

    private Integer toInt(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String lockFingerprint(Long datasourceId,
                                   String waitingTrxId,
                                   String blockingTrxId,
                                   Long waitingProcessId,
                                   Long blockingProcessId,
                                   String waitingSql) {
        String raw = datasourceId + "|"
                + Objects.toString(waitingTrxId, "") + "|"
                + Objects.toString(blockingTrxId, "") + "|"
                + Objects.toString(waitingProcessId, "") + "|"
                + Objects.toString(blockingProcessId, "") + "|"
                + Objects.toString(sqlTextSanitizer.fingerprint(waitingSql), "");
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record ProcessSaveResult(
            int savedCount,
            int longSqlCount,
            int lockedCount,
            int metadataLockCount
    ) {
    }
}
