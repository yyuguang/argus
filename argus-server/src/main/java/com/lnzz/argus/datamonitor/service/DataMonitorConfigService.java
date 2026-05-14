package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;

/**
 * 应用级数据监控配置服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataMonitorConfigService {

    /**
     * 查询应用数据监控概览。
     *
     * @param scmConfigId SCM配置ID
     * @param mappingId   应用映射ID
     * @return 监控配置概览
     */
    DataMonitorConfigOverview getOverview(Long scmConfigId, Long mappingId);

    /**
     * 新增或更新应用数据监控总配置。
     *
     * @param scmConfigId SCM配置ID
     * @param mappingId   应用映射ID
     * @param request     更新请求
     * @return 保存后的概览
     */
    DataMonitorConfigOverview saveOrUpdate(Long scmConfigId, Long mappingId, DataMonitorConfigUpdateRequest request);

    record DataMonitorConfigUpdateRequest(
            Boolean enabled,
            String ownerTeam,
            String techOwner,
            String alertWebhookMode,
            Integer defaultRuntimeCollectIntervalSeconds,
            Integer defaultPoolMetricPushIntervalSeconds,
            Integer defaultLogQualityCheckIntervalSeconds,
            Integer alertScanIntervalSeconds,
            String remark
    ) {
    }

    record DataMonitorConfigOverview(
            Long id,
            Long scmConfigId,
            Long mappingId,
            String appName,
            String environment,
            Boolean enabled,
            String ownerTeam,
            String techOwner,
            String alertWebhookMode,
            Integer defaultRuntimeCollectIntervalSeconds,
            Integer defaultPoolMetricPushIntervalSeconds,
            Integer defaultLogQualityCheckIntervalSeconds,
            Integer alertScanIntervalSeconds,
            String remark,
            int datasourceCount,
            int logTableCount,
            boolean poolMonitorEnabled,
            boolean slowLogEnabled
    ) {
    }
}
