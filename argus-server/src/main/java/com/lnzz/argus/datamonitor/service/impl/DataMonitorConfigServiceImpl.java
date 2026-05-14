package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * 应用级数据监控配置服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DataMonitorConfigServiceImpl implements DataMonitorConfigService {

    private static final String DEFAULT_ENVIRONMENT = "PROD";
    private static final String ALERT_WEBHOOK_MODE_SCM_CONFIG = "SCM_CONFIG";
    private static final int DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_LOG_QUALITY_CHECK_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_ALERT_SCAN_INTERVAL_SECONDS = 60;

    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final SlowLogConfigMapper slowLogConfigMapper;
    private final ConnectionPoolSnapshotMapper connectionPoolSnapshotMapper;
    private final InterfaceLogTableConfigMapper interfaceLogTableConfigMapper;
    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigService scmConfigService;

    @Override
    public DataMonitorConfigOverview getOverview(Long scmConfigId, Long mappingId) {
        ProjectMapping mapping = requireMappingBelongsToScm(scmConfigId, mappingId);
        DataMonitorConfig config = findByMappingId(mappingId);
        if (config == null) {
            config = buildDefaultConfig(scmConfigId, mapping);
        }
        return toOverview(config, mapping);
    }

    @Override
    public DataMonitorConfigOverview saveOrUpdate(Long scmConfigId, Long mappingId, DataMonitorConfigUpdateRequest request) {
        ProjectMapping mapping = requireMappingBelongsToScm(scmConfigId, mappingId);
        DataMonitorConfig config = findByMappingId(mappingId);
        if (config == null) {
            config = buildDefaultConfig(scmConfigId, mapping);
        }
        applyRequest(config, request);

        if (config.getId() == null) {
            dataMonitorConfigMapper.insert(config);
        } else {
            dataMonitorConfigMapper.updateById(config);
        }

        DataMonitorConfig saved = findByMappingId(mappingId);
        if (saved == null) {
            saved = config;
        }
        return toOverview(saved, mapping);
    }

    private DataMonitorConfig findByMappingId(Long mappingId) {
        return dataMonitorConfigMapper.selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getProjectMappingId, mappingId)
                .last("limit 1"));
    }

    private ProjectMapping requireMappingBelongsToScm(Long scmConfigId, Long mappingId) {
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        ProjectMapping mapping = projectMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应用映射不存在: " + mappingId);
        }

        String scmProvider = normalizeProvider(scmConfig.getScmProvider());
        String mappingProvider = normalizeProvider(mapping.getScmProvider());
        boolean sameProvider = Objects.equals(scmProvider, mappingProvider);
        boolean sameProject = Objects.equals(scmConfig.getProjectId(), mapping.getScmProjectId());
        if (!sameProvider || !sameProject) {
            throw new BizException(ResultCode.PARAM_ERROR, "应用映射不属于当前 SCM 配置");
        }
        return mapping;
    }

    private DataMonitorConfig buildDefaultConfig(Long scmConfigId, ProjectMapping mapping) {
        DataMonitorConfig config = new DataMonitorConfig();
        config.setScmConfigId(scmConfigId);
        config.setProjectMappingId(mapping.getId());
        config.setAppName(mapping.getAppName());
        config.setEnvironment(DEFAULT_ENVIRONMENT);
        config.setEnabled(Boolean.FALSE);
        config.setAlertWebhookMode(ALERT_WEBHOOK_MODE_SCM_CONFIG);
        config.setDefaultRuntimeCollectIntervalSeconds(DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS);
        config.setDefaultPoolMetricPushIntervalSeconds(DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS);
        config.setDefaultLogQualityCheckIntervalSeconds(DEFAULT_LOG_QUALITY_CHECK_INTERVAL_SECONDS);
        config.setAlertScanIntervalSeconds(DEFAULT_ALERT_SCAN_INTERVAL_SECONDS);
        return config;
    }

    private void applyRequest(DataMonitorConfig config, DataMonitorConfigUpdateRequest request) {
        if (request == null) {
            return;
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        config.setOwnerTeam(trimToNull(request.ownerTeam()));
        config.setTechOwner(trimToNull(request.techOwner()));
        config.setRemark(trimToNull(request.remark()));
        String alertWebhookMode = trimToNull(request.alertWebhookMode());
        config.setAlertWebhookMode(StringUtils.hasText(alertWebhookMode)
                ? alertWebhookMode.toUpperCase(Locale.ROOT)
                : ALERT_WEBHOOK_MODE_SCM_CONFIG);
        config.setDefaultRuntimeCollectIntervalSeconds(valueOrDefault(
                positiveInterval(request.defaultRuntimeCollectIntervalSeconds(), "默认数据库运行态采集间隔"),
                valueOrDefault(config.getDefaultRuntimeCollectIntervalSeconds(), DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS)));
        config.setDefaultPoolMetricPushIntervalSeconds(valueOrDefault(
                positiveInterval(request.defaultPoolMetricPushIntervalSeconds(), "默认连接池指标推送间隔"),
                valueOrDefault(config.getDefaultPoolMetricPushIntervalSeconds(), DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS)));
        config.setDefaultLogQualityCheckIntervalSeconds(valueOrDefault(
                positiveInterval(request.defaultLogQualityCheckIntervalSeconds(), "默认接口日志质量巡检间隔"),
                valueOrDefault(config.getDefaultLogQualityCheckIntervalSeconds(), DEFAULT_LOG_QUALITY_CHECK_INTERVAL_SECONDS)));
        config.setAlertScanIntervalSeconds(valueOrDefault(
                positiveInterval(request.alertScanIntervalSeconds(), "告警扫描间隔"),
                valueOrDefault(config.getAlertScanIntervalSeconds(), DEFAULT_ALERT_SCAN_INTERVAL_SECONDS)));
    }

    private DataMonitorConfigOverview toOverview(DataMonitorConfig config, ProjectMapping mapping) {
        int datasourceCount = dataSourceConfigMapper.selectCount(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getProjectMappingId, mapping.getId())).intValue();
        boolean slowLogEnabled = slowLogConfigMapper.selectCount(new LambdaQueryWrapper<SlowLogConfig>()
                .inSql(SlowLogConfig::getDatasourceId,
                        "select id from argus_data_source_config where project_mapping_id = " + mapping.getId())
                .eq(SlowLogConfig::getEnabled, true)) > 0;
        boolean poolMonitorEnabled = connectionPoolSnapshotMapper.selectCount(new LambdaQueryWrapper<ConnectionPoolSnapshot>()
                .eq(ConnectionPoolSnapshot::getMonitorConfigId, config.getId())) > 0;
        int logTableCount = interfaceLogTableConfigMapper.selectCount(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                .eq(InterfaceLogTableConfig::getProjectMappingId, mapping.getId())).intValue();
        return new DataMonitorConfigOverview(
                config.getId(),
                config.getScmConfigId(),
                config.getProjectMappingId(),
                config.getAppName() != null ? config.getAppName() : mapping.getAppName(),
                config.getEnvironment(),
                config.getEnabled(),
                config.getOwnerTeam(),
                config.getTechOwner(),
                config.getAlertWebhookMode(),
                valueOrDefault(config.getDefaultRuntimeCollectIntervalSeconds(), DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS),
                valueOrDefault(config.getDefaultPoolMetricPushIntervalSeconds(), DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS),
                valueOrDefault(config.getDefaultLogQualityCheckIntervalSeconds(), DEFAULT_LOG_QUALITY_CHECK_INTERVAL_SECONDS),
                valueOrDefault(config.getAlertScanIntervalSeconds(), DEFAULT_ALERT_SCAN_INTERVAL_SECONDS),
                config.getRemark(),
                datasourceCount,
                logTableCount,
                poolMonitorEnabled,
                slowLogEnabled
        );
    }

    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer positiveInterval(Integer value, String name) {
        if (value != null && value < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, name + "必须大于 0 秒");
        }
        return value;
    }

    private Integer valueOrDefault(Integer value, Integer defaultValue) {
        return value != null ? value : defaultValue;
    }
}
