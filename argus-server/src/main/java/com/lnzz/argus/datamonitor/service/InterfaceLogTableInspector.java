package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 接口日志表只读巡检采集器。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface InterfaceLogTableInspector {

    void validateMapping(DataSourceConfig datasource, String password, InterfaceLogTableConfig config);

    LogTableScanMetrics scan(DataSourceConfig datasource,
                             String password,
                             InterfaceLogTableConfig config,
                             LogQualityRules rules,
                             ScanWindow window);

    record ScanWindow(
            String scanMode,
            String lastScanValue,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    record LogQualityRules(
            Set<String> requiredColumns,
            Integer noNewDataMinutes,
            Integer emptyRateThreshold,
            Integer duplicateRateThreshold,
            Integer maxResponseBodyKb,
            Long maxTableRows,
            Set<String> validStatusCodes
    ) {
    }

    record LogTableScanMetrics(
            long totalCount,
            String maxPrimaryKeyValue,
            LocalDateTime latestRequestTime,
            long nullRequiredCount,
            long duplicateRequestIdCount,
            long duplicateTraceIdCount,
            long invalidTimeCount,
            long invalidStatusCount,
            long statusConflictCount,
            long emptyResponseCount,
            long oversizeResponseCount,
            long tableRows,
            long dataLength,
            long indexLength,
            String sampleRecordId,
            String samplePayload
    ) {
    }
}
