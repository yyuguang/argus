package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.service.DataSourceSecretCodec;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogQualityRules;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 接口日志表质量巡检配置服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class InterfaceLogTableConfigServiceImpl implements InterfaceLogTableConfigService {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final String DEFAULT_SCAN_MODE = "ID_INCREMENT";

    private final InterfaceLogTableConfigMapper logTableConfigMapper;
    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigService scmConfigService;
    private final DataSourceSecretCodec secretCodec;
    private final InterfaceLogTableInspector inspector;

    @Override
    public List<InterfaceLogTableConfigResponse> list(Long scmConfigId, Long mappingId) {
        requireMonitorConfig(scmConfigId, mappingId);
        return logTableConfigMapper.selectList(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                        .eq(InterfaceLogTableConfig::getProjectMappingId, mappingId)
                        .orderByAsc(InterfaceLogTableConfig::getTableName)
                        .orderByAsc(InterfaceLogTableConfig::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public InterfaceLogTableConfigResponse create(Long scmConfigId, Long mappingId,
                                                  InterfaceLogTableConfigRequest request) {
        DataMonitorConfig monitorConfig = requireMonitorConfig(scmConfigId, mappingId);
        DataSourceConfig datasource = requireDatasource(mappingId, request == null ? null : request.datasourceId());
        validateRequest(request, true);
        ensureTableUnique(datasource.getId(), request.tableName(), null);

        InterfaceLogTableConfig config = new InterfaceLogTableConfig();
        config.setMonitorConfigId(monitorConfig.getId());
        config.setProjectMappingId(mappingId);
        config.setDatasourceId(datasource.getId());
        config.setAppName(monitorConfig.getAppName());
        config.setEnvironment(monitorConfig.getEnvironment());
        applyRequest(config, request);
        inspector.validateMapping(datasource, secretCodec.decrypt(datasource.getPasswordSecret()), config);
        logTableConfigMapper.insert(config);
        return toResponse(config);
    }

    @Override
    public InterfaceLogTableConfigResponse update(Long scmConfigId, Long mappingId, Long configId,
                                                  InterfaceLogTableConfigRequest request) {
        DataMonitorConfig monitorConfig = requireMonitorConfig(scmConfigId, mappingId);
        InterfaceLogTableConfig config = requireConfig(mappingId, configId);
        DataSourceConfig datasource = requireDatasource(mappingId,
                request != null && request.datasourceId() != null ? request.datasourceId() : config.getDatasourceId());
        validateRequest(request, false);
        String nextTable = StringUtils.hasText(request.tableName()) ? request.tableName().trim() : config.getTableName();
        ensureTableUnique(datasource.getId(), nextTable, configId);

        config.setMonitorConfigId(monitorConfig.getId());
        config.setDatasourceId(datasource.getId());
        config.setAppName(monitorConfig.getAppName());
        config.setEnvironment(monitorConfig.getEnvironment());
        applyRequest(config, request);
        inspector.validateMapping(datasource, secretCodec.decrypt(datasource.getPasswordSecret()), config);
        logTableConfigMapper.updateById(config);
        return toResponse(config);
    }

    @Override
    public InterfaceLogTableConfigResponse setEnabled(Long scmConfigId, Long mappingId, Long configId,
                                                      EnableRequest request) {
        requireMonitorConfig(scmConfigId, mappingId);
        InterfaceLogTableConfig config = requireConfig(mappingId, configId);
        config.setEnabled(request != null && Boolean.TRUE.equals(request.enabled()));
        logTableConfigMapper.updateById(config);
        return toResponse(config);
    }

    private DataMonitorConfig requireMonitorConfig(Long scmConfigId, Long mappingId) {
        requireMappingBelongsToScm(scmConfigId, mappingId);
        DataMonitorConfig config = dataMonitorConfigMapper.selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getScmConfigId, scmConfigId)
                .eq(DataMonitorConfig::getProjectMappingId, mappingId)
                .last("limit 1"));
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "请先创建应用级数据监控总配置");
        }
        return config;
    }

    private ProjectMapping requireMappingBelongsToScm(Long scmConfigId, Long mappingId) {
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        ProjectMapping mapping = projectMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应用映射不存在: " + mappingId);
        }
        if (!Objects.equals(normalizeProvider(scmConfig.getScmProvider()), normalizeProvider(mapping.getScmProvider()))
                || !Objects.equals(scmConfig.getProjectId(), mapping.getScmProjectId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "应用映射不属于当前 SCM 配置");
        }
        return mapping;
    }

    private DataSourceConfig requireDatasource(Long mappingId, Long datasourceId) {
        if (datasourceId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源不能为空");
        }
        DataSourceConfig datasource = dataSourceConfigMapper.selectById(datasourceId);
        if (datasource == null || !Objects.equals(datasource.getProjectMappingId(), mappingId)) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源配置不存在: " + datasourceId);
        }
        return datasource;
    }

    private InterfaceLogTableConfig requireConfig(Long mappingId, Long configId) {
        InterfaceLogTableConfig config = logTableConfigMapper.selectById(configId);
        if (config == null || !Objects.equals(config.getProjectMappingId(), mappingId)) {
            throw new BizException(ResultCode.NOT_FOUND, "接口日志表配置不存在: " + configId);
        }
        return config;
    }

    private void validateRequest(InterfaceLogTableConfigRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "接口日志表配置不能为空");
        }
        requireIdentifier(request.tableName(), "日志表名", creating);
        requireIdentifier(request.primaryKeyColumn(), "主键字段", creating);
        requireIdentifier(request.interfaceCodeColumn(), "接口编码字段", creating);
        requireIdentifier(request.requestTimeColumn(), "请求时间字段", creating);
        requireIdentifier(request.responseTimeColumn(), "响应时间字段", creating);
        requireIdentifier(request.responseBodyColumn(), "响应体字段", creating);
        requireIdentifier(request.statusCodeColumn(), "状态码字段", false);
        requireIdentifier(request.requestIdColumn(), "请求ID字段", false);
        requireIdentifier(request.traceIdColumn(), "traceId字段", false);
        if (StringUtils.hasText(request.scanMode()) && !Set.of("ID_INCREMENT", "TIME_WINDOW")
                .contains(request.scanMode().trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(ResultCode.PARAM_ERROR, "扫描模式仅支持 ID_INCREMENT / TIME_WINDOW");
        }
    }

    private void requireIdentifier(String value, String name, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BizException(ResultCode.PARAM_ERROR, name + "不能为空");
            }
            return;
        }
        if (!IDENTIFIER.matcher(value.trim()).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, name + "非法: " + value);
        }
    }

    private void ensureTableUnique(Long datasourceId, String tableName, Long excludedId) {
        InterfaceLogTableConfig existing = logTableConfigMapper.selectOne(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                .eq(InterfaceLogTableConfig::getDatasourceId, datasourceId)
                .eq(InterfaceLogTableConfig::getTableName, tableName)
                .last("limit 1"));
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "同一数据源下日志表配置已存在: " + tableName);
        }
    }

    private void applyRequest(InterfaceLogTableConfig config, InterfaceLogTableConfigRequest request) {
        config.setConfigName(trimToNull(request.configName()));
        apply(config::setTableName, request.tableName());
        apply(config::setPrimaryKeyColumn, request.primaryKeyColumn());
        apply(config::setInterfaceCodeColumn, request.interfaceCodeColumn());
        apply(config::setRequestTimeColumn, request.requestTimeColumn());
        apply(config::setResponseTimeColumn, request.responseTimeColumn());
        apply(config::setResponseBodyColumn, request.responseBodyColumn());
        apply(config::setStatusCodeColumn, request.statusCodeColumn());
        apply(config::setRequestIdColumn, request.requestIdColumn());
        apply(config::setTraceIdColumn, request.traceIdColumn());
        config.setScanMode(StringUtils.hasText(request.scanMode())
                ? request.scanMode().trim().toUpperCase(Locale.ROOT)
                : StringUtils.hasText(config.getScanMode()) ? config.getScanMode() : DEFAULT_SCAN_MODE);
        config.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
        config.setQualityRules(request.qualityRules() == null ? config.getQualityRules()
                : JSON.toJSONString(request.qualityRules()));
        config.setAlertRules(StringUtils.hasText(request.alertRules()) ? request.alertRules() : config.getAlertRules());
    }

    private void apply(java.util.function.Consumer<String> consumer, String value) {
        if (StringUtils.hasText(value)) {
            consumer.accept(value.trim());
        }
    }

    private InterfaceLogTableConfigResponse toResponse(InterfaceLogTableConfig config) {
        LogQualityRules rules = parseRules(config.getQualityRules());
        return new InterfaceLogTableConfigResponse(config.getId(), config.getMonitorConfigId(),
                config.getDatasourceId(), config.getAppName(), config.getEnvironment(), config.getConfigName(),
                config.getTableName(), config.getPrimaryKeyColumn(), config.getInterfaceCodeColumn(),
                config.getRequestTimeColumn(), config.getResponseTimeColumn(), config.getResponseBodyColumn(),
                config.getStatusCodeColumn(), config.getRequestIdColumn(), config.getTraceIdColumn(),
                config.getScanMode(), config.getLastScanValue(), config.getEnabled(),
                rules == null ? Set.of() : rules.requiredColumns());
    }

    private LogQualityRules parseRules(String json) {
        return StringUtils.hasText(json) ? JSON.parseObject(json, LogQualityRules.class) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toUpperCase(Locale.ROOT);
    }
}
