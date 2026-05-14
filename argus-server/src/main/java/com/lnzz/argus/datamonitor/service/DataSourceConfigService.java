package com.lnzz.argus.datamonitor.service;

import java.util.List;

/**
 * 应用级只读数据源配置服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataSourceConfigService {

    List<DataSourceConfigResponse> list(Long scmConfigId, Long mappingId);

    DataSourceConfigResponse create(Long scmConfigId, Long mappingId, DataSourceConfigRequest request);

    DataSourceConfigResponse update(Long scmConfigId, Long mappingId, Long datasourceId, DataSourceConfigRequest request);

    DataSourceConfigResponse setEnabled(Long scmConfigId, Long mappingId, Long datasourceId, EnableRequest request);

    DataSourceConnectivityTester.DataSourceTestResult test(Long scmConfigId, Long mappingId,
                                                           DataSourceTestRequest request);

    default DataSourceConnectivityTester.DataSourceTestResult testExisting(Long scmConfigId, Long mappingId,
                                                                           Long datasourceId) {
        return testExisting(scmConfigId, mappingId, datasourceId, null);
    }

    DataSourceConnectivityTester.DataSourceTestResult testExisting(Long scmConfigId, Long mappingId,
                                                                    Long datasourceId,
                                                                    ExistingDataSourceTestRequest request);

    record DataSourceConfigRequest(
            String datasourceCode,
            String datasourceName,
            String dbType,
            String dbVersion,
            String jdbcUrl,
            String host,
            Integer port,
            String databaseName,
            String username,
            String password,
            Boolean readonly,
            Boolean enabled,
            Integer runtimeCollectIntervalSeconds,
            Integer poolMetricPushIntervalSeconds,
            ThresholdConfig thresholds,
            CollectOptions collectOptions
    ) {
    }

    record DataSourceTestRequest(
            String jdbcUrl,
            String username,
            String password
    ) {
    }

    record ExistingDataSourceTestRequest(
            String jdbcUrl,
            String username,
            String password
    ) {
    }

    record EnableRequest(Boolean enabled) {
    }

    record ThresholdConfig(
            Integer longSqlSeconds,
            Integer longTransactionSeconds,
            Integer lockWaitSeconds,
            Integer connectionUsagePercent
    ) {
    }

    record CollectOptions(
            Boolean processlist,
            Boolean innodbTransaction,
            Boolean innodbLock,
            Boolean globalStatus,
            Boolean explain,
            Boolean fullSql
    ) {
    }

    record DataSourceConfigResponse(
            Long id,
            Long monitorConfigId,
            Long mappingId,
            String datasourceCode,
            String datasourceName,
            String dbType,
            String dbVersion,
            String jdbcUrl,
            String host,
            Integer port,
            String databaseName,
            String username,
            Boolean readonly,
            Boolean enabled,
            Boolean collectProcesslist,
            Boolean collectInnodbTrx,
            Boolean collectInnodbLock,
            Boolean collectGlobalStatus,
            Boolean explainEnabled,
            Boolean fullSqlCollectEnabled,
            Integer runtimeCollectIntervalSeconds,
            Integer poolMetricPushIntervalSeconds,
            ThresholdConfig thresholds
    ) {
    }
}
