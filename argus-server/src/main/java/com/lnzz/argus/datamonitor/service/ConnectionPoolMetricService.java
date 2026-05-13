package com.lnzz.argus.datamonitor.service;

import java.time.LocalDateTime;

/**
 * 连接池指标接入服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ConnectionPoolMetricService {

    PoolMetricResponse ingest(PoolMetricRequest request);

    record PoolMetricRequest(
            String appName,
            String environment,
            String datasourceName,
            String poolType,
            Integer activeConnections,
            Integer idleConnections,
            Integer maxConnections,
            Integer waitingThreads,
            Long connectionAcquireAvgMs,
            Long connectionAcquireMaxMs,
            Long timeoutCount,
            Long errorCount,
            LocalDateTime collectedAt
    ) {
    }

    record PoolMetricResponse(
            Long snapshotId,
            boolean riskDetected,
            String riskType,
            String riskLevel,
            String message
    ) {
    }
}
