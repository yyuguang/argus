package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.DataMonitorConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService;
import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester;
import com.lnzz.argus.datamonitor.service.DataSourceSecretCodec;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 应用级只读数据源配置服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceConfigServiceImpl implements DataSourceConfigService {

    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigService scmConfigService;
    private final DataSourceSecretCodec secretCodec;
    private final DataSourceConnectivityTester connectivityTester;

    @Override
    public List<DataSourceConfigResponse> list(Long scmConfigId, Long mappingId) {
        requireMonitorConfig(scmConfigId, mappingId);
        List<DataSourceConfigResponse> responses = dataSourceConfigMapper.findByMappingId(mappingId)
                .stream()
                .map(this::toResponse)
                .toList();
        log.debug("查询数据源配置: scmConfigId={}, mappingId={}, count={}", scmConfigId, mappingId, responses.size());
        return responses;
    }

    @Override
    public DataSourceConfigResponse create(Long scmConfigId, Long mappingId, DataSourceConfigRequest request) {
        DataMonitorConfig monitorConfig = requireMonitorConfig(scmConfigId, mappingId);
        validateRequest(request, true);
        ensureDatasourceCodeUnique(mappingId, trimToNull(request.datasourceCode()), null);

        DataSourceConfig config = new DataSourceConfig();
        config.setMonitorConfigId(monitorConfig.getId());
        config.setProjectMappingId(mappingId);
        applyRequest(config, request);
        dataSourceConfigMapper.insert(config);
        log.info("创建只读数据源配置: datasourceId={}, mappingId={}, datasourceCode={}, username={}",
                config.getId(), mappingId, config.getDatasourceCode(), config.getUsername());
        return toResponse(config);
    }

    @Override
    public DataSourceConfigResponse update(Long scmConfigId, Long mappingId, Long datasourceId,
                                           DataSourceConfigRequest request) {
        DataMonitorConfig monitorConfig = requireMonitorConfig(scmConfigId, mappingId);
        DataSourceConfig config = requireDatasource(mappingId, datasourceId);
        validateRequest(request, false);
        String nextCode = trimToNull(request.datasourceCode());
        if (StringUtils.hasText(nextCode)) {
            ensureDatasourceCodeUnique(mappingId, nextCode, datasourceId);
        }

        config.setMonitorConfigId(monitorConfig.getId());
        applyRequest(config, request);
        dataSourceConfigMapper.updateById(config);
        log.info("更新只读数据源配置: datasourceId={}, mappingId={}, datasourceCode={}, username={}",
                datasourceId, mappingId, config.getDatasourceCode(), config.getUsername());
        return toResponse(config);
    }

    @Override
    public DataSourceConfigResponse setEnabled(Long scmConfigId, Long mappingId, Long datasourceId,
                                               EnableRequest request) {
        requireMonitorConfig(scmConfigId, mappingId);
        DataSourceConfig config = requireDatasource(mappingId, datasourceId);
        config.setEnabled(request != null && Boolean.TRUE.equals(request.enabled()));
        dataSourceConfigMapper.updateById(config);
        log.info("切换只读数据源启用状态: datasourceId={}, mappingId={}, enabled={}",
                datasourceId, mappingId, config.getEnabled());
        return toResponse(config);
    }

    @Override
    public DataSourceConnectivityTester.DataSourceTestResult test(Long scmConfigId, Long mappingId,
                                                                  DataSourceTestRequest request) {
        requireMonitorConfig(scmConfigId, mappingId);
        if (request == null || !StringUtils.hasText(request.jdbcUrl())
                || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源测试需要 jdbcUrl、username、password");
        }
        validateMysqlJdbcUrl(request.jdbcUrl());
        log.info("测试新数据源连接: scmConfigId={}, mappingId={}, jdbcUrl={}, username={}",
                scmConfigId, mappingId, maskJdbcUrl(request.jdbcUrl()), request.username().trim());
        return connectivityTester.test(new DataSourceConnectivityTester.DataSourceConnectionRequest(
                request.jdbcUrl().trim(), request.username().trim(), request.password()));
    }

    @Override
    public DataSourceConnectivityTester.DataSourceTestResult testExisting(Long scmConfigId, Long mappingId,
                                                                          Long datasourceId,
                                                                          ExistingDataSourceTestRequest request) {
        requireMonitorConfig(scmConfigId, mappingId);
        DataSourceConfig datasource = requireDatasource(mappingId, datasourceId);
        String jdbcUrl = StringUtils.hasText(request == null ? null : request.jdbcUrl())
                ? request.jdbcUrl().trim()
                : datasource.getJdbcUrl();
        String username = StringUtils.hasText(request == null ? null : request.username())
                ? request.username().trim()
                : datasource.getUsername();
        String password = StringUtils.hasText(request == null ? null : request.password())
                ? request.password()
                : null;
        if (!StringUtils.hasText(password) && StringUtils.hasText(datasource.getPasswordSecret())) {
            try {
                password = secretCodec.decrypt(datasource.getPasswordSecret());
            } catch (IllegalStateException ex) {
                log.warn("已保存数据源密码解密失败: datasourceId={}, mappingId={}", datasourceId, mappingId);
                throw new BizException(ResultCode.PARAM_ERROR, "已保存数据源密码无法解密，请重新输入密码后测试或保存");
            }
        }
        if (!StringUtils.hasText(jdbcUrl) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException(ResultCode.PARAM_ERROR, "已有数据源缺少 jdbcUrl、username 或 passwordSecret");
        }
        validateMysqlJdbcUrl(jdbcUrl);
        log.info("测试已有数据源连接: datasourceId={}, mappingId={}, jdbcUrl={}, username={}",
                datasourceId, mappingId, maskJdbcUrl(jdbcUrl), username);
        return connectivityTester.test(new DataSourceConnectivityTester.DataSourceConnectionRequest(
                jdbcUrl, username, password));
    }

    private DataMonitorConfig requireMonitorConfig(Long scmConfigId, Long mappingId) {
        requireMappingBelongsToScm(scmConfigId, mappingId);
        DataMonitorConfig config = dataMonitorConfigMapper.findByScmAndMapping(scmConfigId, mappingId);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "请先创建应用级数据监控总配置");
        }
        return config;
    }

    private ProjectMapping requireMappingBelongsToScm(Long scmConfigId, Long mappingId) {
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        ProjectMapping mapping = projectMappingMapper.findById(mappingId);
        if (mapping == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应用映射不存在: " + mappingId);
        }
        boolean sameProvider = Objects.equals(normalizeProvider(scmConfig.getScmProvider()),
                normalizeProvider(mapping.getScmProvider()));
        boolean sameProject = Objects.equals(scmConfig.getProjectId(), mapping.getScmProjectId());
        if (!sameProvider || !sameProject) {
            log.warn("应用映射归属校验失败: scmConfigId={}, mappingId={}, scmProvider={}, mappingProvider={}, scmProjectId={}, mappingProjectId={}",
                    scmConfigId, mappingId, scmConfig.getScmProvider(), mapping.getScmProvider(),
                    scmConfig.getProjectId(), mapping.getScmProjectId());
            throw new BizException(ResultCode.PARAM_ERROR, "应用映射不属于当前 SCM 配置");
        }
        return mapping;
    }

    private DataSourceConfig requireDatasource(Long mappingId, Long datasourceId) {
        DataSourceConfig config = dataSourceConfigMapper.findByIdAndMappingId(mappingId, datasourceId);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源配置不存在: " + datasourceId);
        }
        return config;
    }

    private void validateRequest(DataSourceConfigRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源配置不能为空");
        }
        if (creating && !StringUtils.hasText(request.datasourceCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源编码不能为空");
        }
        if (creating && !StringUtils.hasText(request.jdbcUrl())) {
            throw new BizException(ResultCode.PARAM_ERROR, "JDBC 地址不能为空");
        }
        if (creating && !StringUtils.hasText(request.username())) {
            throw new BizException(ResultCode.PARAM_ERROR, "只读账号不能为空");
        }
        if (creating && !StringUtils.hasText(request.password())) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源密码不能为空");
        }
        if (request.readonly() != null && !Boolean.TRUE.equals(request.readonly())) {
            throw new BizException(ResultCode.PARAM_ERROR, "数据源账号必须标记为只读");
        }
        if (StringUtils.hasText(request.dbType())
                && !DataMonitorConstants.DB_TYPE_MYSQL.equalsIgnoreCase(request.dbType().trim())) {
            throw new BizException(ResultCode.PARAM_ERROR, "首版仅支持 MySQL 数据源");
        }
        if (StringUtils.hasText(request.dbVersion())
                && !request.dbVersion().trim().startsWith(DataMonitorConstants.DB_VERSION_MYSQL_57_PREFIX)) {
            throw new BizException(ResultCode.PARAM_ERROR, "首版仅支持 MySQL 5.7");
        }
        if (StringUtils.hasText(request.jdbcUrl())) {
            validateMysqlJdbcUrl(request.jdbcUrl());
        }
        validateThresholds(request.thresholds());
        validatePositive(request.runtimeCollectIntervalSeconds(), "数据库运行态采集间隔");
        validatePositive(request.poolMetricPushIntervalSeconds(), "连接池指标推送间隔");
    }

    private void validateThresholds(ThresholdConfig thresholds) {
        if (thresholds == null) {
            return;
        }
        validatePositive(thresholds.longSqlSeconds(), "长 SQL 阈值");
        validatePositive(thresholds.longTransactionSeconds(), "长事务阈值");
        validatePositive(thresholds.lockWaitSeconds(), "锁等待阈值");
        if (thresholds.connectionUsagePercent() != null
                && (thresholds.connectionUsagePercent() < 1 || thresholds.connectionUsagePercent() > 100)) {
            throw new BizException(ResultCode.PARAM_ERROR, "连接池使用率阈值必须在 1-100 之间");
        }
    }

    private void validatePositive(Integer value, String name) {
        if (value != null && value < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, name + "必须大于 0");
        }
    }

    private void validateMysqlJdbcUrl(String jdbcUrl) {
        String value = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith(DataMonitorConstants.MYSQL_JDBC_PREFIX)) {
            throw new BizException(ResultCode.PARAM_ERROR, "JDBC 地址必须是 MySQL 连接");
        }
    }

    private void ensureDatasourceCodeUnique(Long mappingId, String datasourceCode, Long excludedId) {
        DataSourceConfig existing = dataSourceConfigMapper.findByCode(mappingId, datasourceCode);
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "同一应用下数据源编码已存在: " + datasourceCode);
        }
    }

    private void applyRequest(DataSourceConfig config, DataSourceConfigRequest request) {
        if (StringUtils.hasText(request.datasourceCode())) {
            config.setDatasourceCode(request.datasourceCode().trim());
        }
        config.setDatasourceName(trimToNull(request.datasourceName()));
        if (StringUtils.hasText(request.dbType())) {
            config.setDbType(request.dbType().trim().toUpperCase(Locale.ROOT));
        } else if (!StringUtils.hasText(config.getDbType())) {
            config.setDbType(DataMonitorConstants.DB_TYPE_MYSQL);
        }
        if (StringUtils.hasText(request.dbVersion())) {
            config.setDbVersion(request.dbVersion().trim());
        } else if (!StringUtils.hasText(config.getDbVersion())) {
            config.setDbVersion(DataMonitorConstants.DB_VERSION_MYSQL_57_PREFIX);
        }
        if (StringUtils.hasText(request.jdbcUrl())) {
            String jdbcUrl = request.jdbcUrl().trim();
            config.setJdbcUrl(jdbcUrl);
            applyJdbcParts(config, jdbcUrl);
        }
        config.setHost(StringUtils.hasText(request.host()) ? request.host().trim() : config.getHost());
        config.setPort(request.port() != null ? request.port() : config.getPort());
        config.setDatabaseName(StringUtils.hasText(request.databaseName())
                ? request.databaseName().trim()
                : config.getDatabaseName());
        if (StringUtils.hasText(request.username())) {
            config.setUsername(request.username().trim());
        }
        if (StringUtils.hasText(request.password())) {
            config.setPasswordSecret(secretCodec.encrypt(request.password()));
        }
        config.setReadonly(Boolean.TRUE);
        config.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
        config.setRuntimeCollectIntervalSeconds(integerOrDefault(request.runtimeCollectIntervalSeconds(),
                integerOrDefault(config.getRuntimeCollectIntervalSeconds(),
                        DataMonitorConstants.DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS)));
        config.setPoolMetricPushIntervalSeconds(integerOrDefault(request.poolMetricPushIntervalSeconds(),
                integerOrDefault(config.getPoolMetricPushIntervalSeconds(),
                        DataMonitorConstants.DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS)));
        applyCollectOptions(config, request.collectOptions());
        config.setThresholdConfig(request.thresholds() == null ? config.getThresholdConfig()
                : JSON.toJSONString(request.thresholds()));
    }

    private void applyCollectOptions(DataSourceConfig config, CollectOptions options) {
        config.setCollectProcesslist(valueOrDefault(options == null ? null : options.processlist(),
                valueOrDefault(config.getCollectProcesslist(), Boolean.TRUE)));
        config.setCollectInnodbTrx(valueOrDefault(options == null ? null : options.innodbTransaction(),
                valueOrDefault(config.getCollectInnodbTrx(), Boolean.TRUE)));
        config.setCollectInnodbLock(valueOrDefault(options == null ? null : options.innodbLock(),
                valueOrDefault(config.getCollectInnodbLock(), Boolean.TRUE)));
        config.setCollectGlobalStatus(valueOrDefault(options == null ? null : options.globalStatus(),
                valueOrDefault(config.getCollectGlobalStatus(), Boolean.TRUE)));
        config.setExplainEnabled(valueOrDefault(options == null ? null : options.explain(),
                valueOrDefault(config.getExplainEnabled(), Boolean.TRUE)));
        config.setFullSqlCollectEnabled(valueOrDefault(options == null ? null : options.fullSql(),
                valueOrDefault(config.getFullSqlCollectEnabled(), Boolean.TRUE)));
    }

    private Boolean valueOrDefault(Boolean value, Boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    private Integer integerOrDefault(Integer value, Integer defaultValue) {
        return value != null ? value : defaultValue;
    }

    private void applyJdbcParts(DataSourceConfig config, String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            config.setHost(uri.getHost());
            config.setPort(uri.getPort() > 0 ? uri.getPort() : DataMonitorConstants.DEFAULT_MYSQL_PORT);
            String path = uri.getPath();
            if (StringUtils.hasText(path) && path.length() > 1) {
                config.setDatabaseName(path.substring(1));
            }
        } catch (Exception ignored) {
            // JDBC URL 合法性由驱动最终判断；这里只做可用字段提取。
        }
    }

    private DataSourceConfigResponse toResponse(DataSourceConfig config) {
        return new DataSourceConfigResponse(
                config.getId(),
                config.getMonitorConfigId(),
                config.getProjectMappingId(),
                config.getDatasourceCode(),
                config.getDatasourceName(),
                config.getDbType(),
                config.getDbVersion(),
                config.getJdbcUrl(),
                config.getHost(),
                config.getPort(),
                config.getDatabaseName(),
                config.getUsername(),
                config.getReadonly(),
                config.getEnabled(),
                config.getCollectProcesslist(),
                config.getCollectInnodbTrx(),
                config.getCollectInnodbLock(),
                config.getCollectGlobalStatus(),
                config.getExplainEnabled(),
                config.getFullSqlCollectEnabled(),
                config.getRuntimeCollectIntervalSeconds(),
                config.getPoolMetricPushIntervalSeconds(),
                parseThresholds(config.getThresholdConfig())
        );
    }

    private ThresholdConfig parseThresholds(String thresholdConfig) {
        if (!StringUtils.hasText(thresholdConfig)) {
            return null;
        }
        return JSON.parseObject(thresholdConfig, ThresholdConfig.class);
    }

    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String maskJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return "";
        }
        int paramIndex = jdbcUrl.indexOf('?');
        return paramIndex >= 0 ? jdbcUrl.substring(0, paramIndex) + "?***" : jdbcUrl;
    }
}
