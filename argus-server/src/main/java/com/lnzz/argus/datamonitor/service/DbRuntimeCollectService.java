package com.lnzz.argus.datamonitor.service;

import java.util.List;

/**
 * MySQL 运行现场采集服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DbRuntimeCollectService {

    List<DatasourceCollectResult> collectAllEnabled();

    DatasourceCollectResult collectDatasource(Long datasourceId);

    record DatasourceCollectResult(
            Long datasourceId,
            String datasourceCode,
            boolean success,
            Long metricSnapshotId,
            int processSnapshotCount,
            int longSqlCount,
            int lockedCount,
            int metadataLockCount,
            int lockEventCount,
            int innodbTrxCount,
            int innodbLockWaitCount,
            String message
    ) {
    }
}
