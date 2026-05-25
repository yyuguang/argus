package com.lnzz.argus.datamonitor.service;

import java.util.List;
import java.util.Set;

/**
 * 接口日志表质量巡检配置服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface InterfaceLogTableConfigService {

    List<InterfaceLogTableConfigResponse> list(Long scmConfigId, Long mappingId);

    InterfaceLogTableConfigResponse create(Long scmConfigId, Long mappingId, InterfaceLogTableConfigRequest request);

    InterfaceLogTableConfigResponse update(Long scmConfigId, Long mappingId, Long configId,
                                           InterfaceLogTableConfigRequest request);

    InterfaceLogTableConfigResponse setEnabled(Long scmConfigId, Long mappingId, Long configId,
                                               EnableRequest request);

    void delete(Long scmConfigId, Long mappingId, Long configId);

    record InterfaceLogTableConfigRequest(
            Long datasourceId,
            String configName,
            String tableName,
            String primaryKeyColumn,
            String interfaceCodeColumn,
            String requestTimeColumn,
            String responseTimeColumn,
            String responseBodyColumn,
            String statusCodeColumn,
            String requestIdColumn,
            String traceIdColumn,
            String scanMode,
            Integer qualityCheckIntervalSeconds,
            Boolean enabled,
            InterfaceLogTableInspector.LogQualityRules qualityRules,
            String alertRules
    ) {
    }

    record EnableRequest(Boolean enabled) {
    }

    record InterfaceLogTableConfigResponse(
            Long id,
            Long monitorConfigId,
            Long datasourceId,
            String appName,
            String environment,
            String configName,
            String tableName,
            String primaryKeyColumn,
            String interfaceCodeColumn,
            String requestTimeColumn,
            String responseTimeColumn,
            String responseBodyColumn,
            String statusCodeColumn,
            String requestIdColumn,
            String traceIdColumn,
            String scanMode,
            Integer qualityCheckIntervalSeconds,
            String lastScanValue,
            Boolean enabled,
            Set<String> requiredColumns
    ) {
    }
}
