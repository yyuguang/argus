package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService;
import com.lnzz.argus.datamonitor.service.SlowLogParser;
import com.lnzz.argus.datamonitor.service.SlowLogParser.ParsedSlowLog;
import com.lnzz.argus.datamonitor.service.SqlTextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * slow log 接入服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SlowLogIngestServiceImpl implements SlowLogIngestService {

    private static final String DEFAULT_ENVIRONMENT = "PROD";
    private static final String SOURCE_TYPE = "SLOW_LOG";
    private static final String ANALYSIS_PENDING = "PENDING";

    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final SlowLogConfigMapper slowLogConfigMapper;
    private final SlowSqlEventMapper slowSqlEventMapper;
    private final SlowLogParser slowLogParser;
    private final SqlTextSanitizer sqlTextSanitizer;

    @Override
    public SlowLogIngestResult ingest(SlowLogPushRequest request) {
        validatePush(request);
        DataSourceConfig datasource = resolveDatasource(request.appName(), request.environment(), request.datasourceCode());
        SlowLogConfig config = requireEnabledSlowLogConfig(datasource.getId());
        if (request.queryTimeMs() != null && request.queryTimeMs() < config.getMinQueryTimeMs()) {
            return new SlowLogIngestResult(false, false, null, null, "低于 slow log 最小采集阈值");
        }
        return saveEvent(datasource, config, request.queryTimeMs(), request.lockTimeMs(), request.rowsSent(),
                request.rowsExamined(), request.sqlText(), request.occurredAt(), request.idempotentKey(), null);
    }

    @Override
    public SlowLogIngestResult ingestRaw(SlowLogRawPushRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new BizException(ResultCode.PARAM_ERROR, "slow log 原始内容不能为空");
        }
        DataSourceConfig datasource = resolveDatasource(request.appName(), request.environment(), request.datasourceCode());
        SlowLogConfig config = requireEnabledSlowLogConfig(datasource.getId());
        ParsedSlowLog parsed = slowLogParser.parse(request.content());
        if (parsed.queryTimeMs() != null && parsed.queryTimeMs() < config.getMinQueryTimeMs()) {
            updateCursor(config, request.cursorOffset());
            return new SlowLogIngestResult(false, false, null, null, "低于 slow log 最小采集阈值");
        }
        return saveEvent(datasource, config, parsed.queryTimeMs(), parsed.lockTimeMs(), parsed.rowsSent(),
                parsed.rowsExamined(), parsed.sqlText(), parsed.occurredAt(), request.idempotentKey(),
                request.cursorOffset());
    }

    private SlowLogIngestResult saveEvent(DataSourceConfig datasource,
                                          SlowLogConfig config,
                                          Long queryTimeMs,
                                          Long lockTimeMs,
                                          Long rowsSent,
                                          Long rowsExamined,
                                          String sqlText,
                                          LocalDateTime occurredAt,
                                          String idempotentKey,
                                          Long cursorOffset) {
        if (!StringUtils.hasText(sqlText)) {
            throw new BizException(ResultCode.PARAM_ERROR, "slow log SQL 不能为空");
        }
        DataMonitorConfig monitorConfig = dataMonitorConfigMapper.selectById(datasource.getMonitorConfigId());
        String fingerprint = sqlTextSanitizer.fingerprint(sqlText);
        String finalKey = StringUtils.hasText(idempotentKey)
                ? idempotentKey.trim()
                : buildIdempotentKey(datasource.getId(), occurredAt, queryTimeMs, fingerprint);
        SlowSqlEvent existing = slowSqlEventMapper.selectOne(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getIdempotentKey, finalKey)
                .last("limit 1"));
        if (existing != null) {
            updateCursor(config, cursorOffset);
            return new SlowLogIngestResult(false, true, existing.getId(), existing.getSqlFingerprint(), "slow log 已接收");
        }

        SlowSqlEvent event = new SlowSqlEvent();
        event.setDatasourceId(datasource.getId());
        event.setMonitorConfigId(datasource.getMonitorConfigId());
        event.setAppName(monitorConfig != null ? monitorConfig.getAppName() : null);
        event.setEnvironment(monitorConfig != null ? monitorConfig.getEnvironment() : DEFAULT_ENVIRONMENT);
        event.setSourceType(SOURCE_TYPE);
        event.setIdempotentKey(finalKey);
        event.setSqlFingerprint(fingerprint);
        event.setSqlText(Boolean.TRUE.equals(config.getCollectFullSql()) ? sqlText : null);
        event.setSqlTextMasked(sqlTextSanitizer.mask(sqlText));
        event.setDurationMs(queryTimeMs);
        event.setLockTimeMs(lockTimeMs);
        event.setRowsSent(rowsSent);
        event.setRowsExamined(rowsExamined);
        event.setRiskLevel(resolveRiskLevel(queryTimeMs, rowsExamined));
        event.setAnalysisStatus(ANALYSIS_PENDING);
        event.setNeedDba(Boolean.FALSE);
        event.setNeedDeveloper(Boolean.TRUE);
        event.setOccurredAt(occurredAt != null ? occurredAt : LocalDateTime.now());
        slowSqlEventMapper.insert(event);
        updateCursor(config, cursorOffset);
        return new SlowLogIngestResult(true, false, event.getId(), fingerprint, "slow log 接收成功");
    }

    private void validatePush(SlowLogPushRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "slow log 请求不能为空");
        }
        if (!StringUtils.hasText(request.appName()) || !StringUtils.hasText(request.datasourceCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName 和 datasourceCode 不能为空");
        }
        if (!StringUtils.hasText(request.sqlText())) {
            throw new BizException(ResultCode.PARAM_ERROR, "sqlText 不能为空");
        }
    }

    private DataSourceConfig resolveDatasource(String appName, String environment, String datasourceCode) {
        String env = StringUtils.hasText(environment) ? environment.trim() : DEFAULT_ENVIRONMENT;
        DataMonitorConfig monitorConfig = dataMonitorConfigMapper.selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getAppName, appName.trim())
                .eq(DataMonitorConfig::getEnvironment, env)
                .last("limit 1"));
        if (monitorConfig == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应用数据监控配置不存在: " + appName);
        }
        DataSourceConfig datasource = dataSourceConfigMapper.selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getMonitorConfigId, monitorConfig.getId())
                .eq(DataSourceConfig::getDatasourceCode, datasourceCode.trim())
                .last("limit 1"));
        if (datasource == null) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源配置不存在: " + datasourceCode);
        }
        return datasource;
    }

    private SlowLogConfig requireEnabledSlowLogConfig(Long datasourceId) {
        SlowLogConfig config = slowLogConfigMapper.selectOne(new LambdaQueryWrapper<SlowLogConfig>()
                .eq(SlowLogConfig::getDatasourceId, datasourceId)
                .last("limit 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BizException(ResultCode.PARAM_ERROR, "slow log 接入未启用");
        }
        return config;
    }

    private void updateCursor(SlowLogConfig config, Long cursorOffset) {
        config.setLastCollectedAt(LocalDateTime.now());
        if (cursorOffset != null) {
            config.setCursorOffset(Math.max(config.getCursorOffset() == null ? 0L : config.getCursorOffset(), cursorOffset));
        }
        if (config.getId() != null) {
            slowLogConfigMapper.updateById(config);
        }
    }

    private String buildIdempotentKey(Long datasourceId, LocalDateTime occurredAt, Long queryTimeMs, String fingerprint) {
        String raw = datasourceId + "|" + Objects.toString(occurredAt, "") + "|" + queryTimeMs + "|" + fingerprint;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveRiskLevel(Long queryTimeMs, Long rowsExamined) {
        long duration = queryTimeMs == null ? 0L : queryTimeMs;
        long rows = rowsExamined == null ? 0L : rowsExamined;
        if (duration >= 30000 || rows >= 1_000_000) {
            return "P1";
        }
        if (duration >= 5000 || rows >= 100_000) {
            return "P2";
        }
        return "P3";
    }
}
