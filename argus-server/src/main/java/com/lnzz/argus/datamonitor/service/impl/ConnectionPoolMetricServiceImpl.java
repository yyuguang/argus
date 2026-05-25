package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
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
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 连接池指标接入服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ConnectionPoolMetricServiceImpl implements ConnectionPoolMetricService {

    private static final String DEFAULT_ENVIRONMENT = "PROD";

    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final ConnectionPoolSnapshotMapper connectionPoolSnapshotMapper;
    private final SlowSqlEventMapper slowSqlEventMapper;
    private final ConnectionPoolRiskPromptBuilder connectionPoolRiskPromptBuilder;
    private final ConnectionPoolRiskAiEngine connectionPoolRiskAiEngine;

    @Override
    public PoolMetricResponse ingest(PoolMetricRequest request) {
        validateRequest(request);
        String environment = StringUtils.hasText(request.environment())
                ? request.environment().trim()
                : DEFAULT_ENVIRONMENT;
        DataMonitorConfig monitorConfig = dataMonitorConfigMapper.selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getAppName, request.appName().trim())
                .eq(DataMonitorConfig::getEnvironment, environment)
                .last("limit 1"));
        if (monitorConfig == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应用数据监控配置不存在: " + request.appName());
        }
        DataSourceConfig datasource = resolveDatasource(monitorConfig, request.datasourceName());
        RiskDecision risk = decideRisk(request);
        ConnectionPoolSnapshot snapshot = buildSnapshot(request, monitorConfig, datasource, environment, risk);
        if (risk.detected()) {
            SlowSqlEvent relatedSlowSqlEvent = datasource == null ? null : findRelatedSlowSqlEvent(datasource.getId(), snapshot);
            ConnectionPoolRiskAiEngine.PoolRiskAiResult aiResult = connectionPoolRiskAiEngine.analyze(
                    connectionPoolRiskPromptBuilder.buildPrompt(snapshot, relatedSlowSqlEvent,
                            risk.riskType(), risk.riskLevel(), monitorConfig.getScmConfigId()));
            snapshot.setRiskReason(resolveRiskReason(snapshot, risk, aiResult));
        }
        connectionPoolSnapshotMapper.insert(snapshot);
        if (risk.detected() && datasource != null) {
            linkSlowSqlEvents(datasource.getId(), snapshot);
        }
        return new PoolMetricResponse(snapshot.getId(), risk.detected(), risk.riskType(), risk.riskLevel(), "accepted");
    }

    private DataSourceConfig resolveDatasource(DataMonitorConfig monitorConfig, String datasourceName) {
        return dataSourceConfigMapper.selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getMonitorConfigId, monitorConfig.getId())
                .and(wrapper -> wrapper.eq(DataSourceConfig::getDatasourceCode, datasourceName.trim())
                        .or()
                        .eq(DataSourceConfig::getDatasourceName, datasourceName.trim()))
                .last("limit 1"));
    }

    private ConnectionPoolSnapshot buildSnapshot(PoolMetricRequest request,
                                                 DataMonitorConfig monitorConfig,
                                                 DataSourceConfig datasource,
                                                 String environment,
                                                 RiskDecision risk) {
        ConnectionPoolSnapshot snapshot = new ConnectionPoolSnapshot();
        snapshot.setMonitorConfigId(monitorConfig.getId());
        snapshot.setDatasourceId(datasource == null ? null : datasource.getId());
        snapshot.setAppName(monitorConfig.getAppName());
        snapshot.setEnvironment(environment);
        snapshot.setDatasourceName(request.datasourceName().trim());
        snapshot.setPoolType(request.poolType().trim().toUpperCase(Locale.ROOT));
        snapshot.setActiveConnections(value(request.activeConnections()));
        snapshot.setIdleConnections(value(request.idleConnections()));
        snapshot.setMaxConnections(value(request.maxConnections()));
        snapshot.setWaitingThreads(value(request.waitingThreads()));
        snapshot.setConnectionAcquireAvgMs(value(request.connectionAcquireAvgMs()));
        snapshot.setConnectionAcquireMaxMs(value(request.connectionAcquireMaxMs()));
        snapshot.setTimeoutCount(value(request.timeoutCount()));
        snapshot.setErrorCount(value(request.errorCount()));
        snapshot.setRiskType(risk.riskType());
        snapshot.setRiskLevel(risk.riskLevel());
        snapshot.setRiskReason(resolveDefaultRiskReason(risk));
        snapshot.setCollectedAt(request.collectedAt() != null ? request.collectedAt() : LocalDateTime.now());
        return snapshot;
    }

    private RiskDecision decideRisk(PoolMetricRequest request) {
        int active = value(request.activeConnections());
        int max = value(request.maxConnections());
        int waiting = value(request.waitingThreads());
        long acquireMax = value(request.connectionAcquireMaxMs());
        long timeout = value(request.timeoutCount());
        long error = value(request.errorCount());
        double usage = max > 0 ? (active * 100.0 / max) : 0.0;

        if ((max > 0 && active >= max && waiting > 0) || timeout > 0) {
            return new RiskDecision(true, "POOL_EXHAUSTED", "P1");
        }
        if (usage >= 90.0 || waiting > 0) {
            return new RiskDecision(true, "POOL_HIGH_USAGE", "P2");
        }
        if (acquireMax >= 1000) {
            return new RiskDecision(true, "POOL_ACQUIRE_SLOW", "P2");
        }
        if (error > 0) {
            return new RiskDecision(true, "POOL_ERROR", "P2");
        }
        return new RiskDecision(false, null, null);
    }

    private void linkSlowSqlEvents(Long datasourceId, ConnectionPoolSnapshot snapshot) {
        SlowSqlEvent event = findRelatedSlowSqlEvent(datasourceId, snapshot);
        if (event != null) {
            event.setRelatedPoolSnapshotId(snapshot.getId());
            slowSqlEventMapper.updateById(event);
        }
    }

    private SlowSqlEvent findRelatedSlowSqlEvent(Long datasourceId, ConnectionPoolSnapshot snapshot) {
        LocalDateTime collectedAt = snapshot.getCollectedAt() != null ? snapshot.getCollectedAt() : LocalDateTime.now();
        return slowSqlEventMapper.selectOne(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getDatasourceId, datasourceId)
                .ge(SlowSqlEvent::getOccurredAt, collectedAt.minusMinutes(5))
                .le(SlowSqlEvent::getOccurredAt, collectedAt.plusMinutes(5))
                .orderByDesc(SlowSqlEvent::getOccurredAt)
                .last("limit 1"));
    }

    private String resolveDefaultRiskReason(RiskDecision risk) {
        if (!risk.detected() || !StringUtils.hasText(risk.riskType())) {
            return null;
        }
        return switch (risk.riskType()) {
            case "POOL_EXHAUSTED" -> "连接池已接近耗尽，请优先排查长 SQL 或连接泄漏";
            case "POOL_HIGH_USAGE" -> "连接池使用率持续偏高，请关注峰值流量和慢请求";
            case "POOL_ACQUIRE_SLOW" -> "获取连接耗时偏高，请排查连接池参数和数据库响应";
            case "POOL_ERROR" -> "连接池存在异常或超时，请检查连接稳定性和错误日志";
            default -> risk.riskType();
        };
    }

    private String resolveRiskReason(ConnectionPoolSnapshot snapshot,
                                     RiskDecision risk,
                                     ConnectionPoolRiskAiEngine.PoolRiskAiResult aiResult) {
        String defaultReason = resolveDefaultRiskReason(risk);
        if (aiResult == null) {
            return defaultReason;
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(aiResult.summary())) {
            sb.append(aiResult.summary().trim());
        } else if (StringUtils.hasText(aiResult.primaryCause())) {
            sb.append(aiResult.primaryCause().trim());
        }
        if (StringUtils.hasText(aiResult.impactScope())) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("影响范围：").append(aiResult.impactScope().trim());
        }
        if (aiResult.evidence() != null && !aiResult.evidence().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("证据：").append(String.join("；", aiResult.evidence()));
        }
        if (sb.length() == 0) {
            return defaultReason;
        }
        if (StringUtils.hasText(defaultReason)) {
            sb.append("\n规则兜底：").append(defaultReason);
        }
        return sb.toString();
    }

    private void validateRequest(PoolMetricRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "连接池指标不能为空");
        }
        if (!StringUtils.hasText(request.appName()) || !StringUtils.hasText(request.datasourceName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName 和 datasourceName 不能为空");
        }
        if (!StringUtils.hasText(request.poolType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "poolType 不能为空");
        }
        String poolType = request.poolType().trim().toUpperCase(Locale.ROOT);
        if (!"HIKARI".equals(poolType) && !"DRUID".equals(poolType)) {
            throw new BizException(ResultCode.PARAM_ERROR, "连接池类型不支持: " + poolType);
        }
        if (request.maxConnections() == null || request.maxConnections() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "maxConnections 不能为空且不能小于 0");
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private record RiskDecision(boolean detected, String riskType, String riskLevel) {
    }
}
