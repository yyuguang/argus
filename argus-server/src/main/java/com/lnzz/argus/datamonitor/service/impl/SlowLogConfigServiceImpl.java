package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * slow log 接入配置服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SlowLogConfigServiceImpl implements SlowLogConfigService {

    private static final String DEFAULT_SOURCE_TYPE = "FILE_TAIL";
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final long DEFAULT_MIN_QUERY_TIME_MS = 1000L;
    private static final int DEFAULT_COLLECT_INTERVAL_SECONDS = 60;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final SlowLogConfigMapper slowLogConfigMapper;

    @Override
    public SlowLogConfigResponse get(Long scmConfigId, Long mappingId, Long datasourceId) {
        DataSourceConfig datasource = requireDatasource(mappingId, datasourceId);
        SlowLogConfig config = findByDatasourceId(datasource.getId());
        if (config == null) {
            config = buildDefaultConfig(datasource.getId());
        }
        return toResponse(config);
    }

    @Override
    public SlowLogConfigResponse saveOrUpdate(Long scmConfigId, Long mappingId, Long datasourceId,
                                              SlowLogConfigRequest request) {
        DataSourceConfig datasource = requireDatasource(mappingId, datasourceId);
        SlowLogConfig config = findByDatasourceId(datasource.getId());
        if (config == null) {
            config = buildDefaultConfig(datasource.getId());
        }
        applyRequest(config, request);
        if (config.getId() == null) {
            slowLogConfigMapper.insert(config);
        } else {
            slowLogConfigMapper.updateById(config);
        }
        return toResponse(config);
    }

    private DataSourceConfig requireDatasource(Long mappingId, Long datasourceId) {
        DataSourceConfig datasource = dataSourceConfigMapper.selectById(datasourceId);
        if (datasource == null || !Objects.equals(datasource.getProjectMappingId(), mappingId)) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源配置不存在: " + datasourceId);
        }
        return datasource;
    }

    private SlowLogConfig findByDatasourceId(Long datasourceId) {
        return slowLogConfigMapper.selectOne(new LambdaQueryWrapper<SlowLogConfig>()
                .eq(SlowLogConfig::getDatasourceId, datasourceId)
                .last("limit 1"));
    }

    private SlowLogConfig buildDefaultConfig(Long datasourceId) {
        SlowLogConfig config = new SlowLogConfig();
        config.setDatasourceId(datasourceId);
        config.setEnabled(Boolean.FALSE);
        config.setSourceType(DEFAULT_SOURCE_TYPE);
        config.setCharset(DEFAULT_CHARSET);
        config.setMinQueryTimeMs(DEFAULT_MIN_QUERY_TIME_MS);
        config.setCollectFullSql(Boolean.TRUE);
        config.setCollectIntervalSeconds(DEFAULT_COLLECT_INTERVAL_SECONDS);
        config.setCursorOffset(0L);
        return config;
    }

    private void applyRequest(SlowLogConfig config, SlowLogConfigRequest request) {
        if (request == null) {
            return;
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.sourceType())) {
            String sourceType = request.sourceType().trim().toUpperCase(Locale.ROOT);
            if (!"FILE_TAIL".equals(sourceType) && !"PUSH".equals(sourceType) && !"TABLE".equals(sourceType)) {
                throw new BizException(ResultCode.PARAM_ERROR, "slow log 来源类型不支持: " + sourceType);
            }
            config.setSourceType(sourceType);
        }
        config.setLogPath(StringUtils.hasText(request.logPath()) ? request.logPath().trim() : config.getLogPath());
        config.setCharset(StringUtils.hasText(request.charset()) ? request.charset().trim() : config.getCharset());
        if (request.minQueryTimeMs() != null) {
            if (request.minQueryTimeMs() < 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "minQueryTimeMs 不能小于 0");
            }
            config.setMinQueryTimeMs(request.minQueryTimeMs());
        }
        if (request.collectFullSql() != null) {
            config.setCollectFullSql(request.collectFullSql());
        }
        if (request.collectIntervalSeconds() != null) {
            if (request.collectIntervalSeconds() < 1) {
                throw new BizException(ResultCode.PARAM_ERROR, "slow log 采集间隔必须大于 0 秒");
            }
            config.setCollectIntervalSeconds(request.collectIntervalSeconds());
        } else if (config.getCollectIntervalSeconds() == null) {
            config.setCollectIntervalSeconds(DEFAULT_COLLECT_INTERVAL_SECONDS);
        }
        if (request.cursorOffset() != null) {
            config.setCursorOffset(Math.max(0L, request.cursorOffset()));
        }
    }

    private SlowLogConfigResponse toResponse(SlowLogConfig config) {
        return new SlowLogConfigResponse(
                config.getId(),
                config.getDatasourceId(),
                config.getEnabled(),
                config.getSourceType(),
                config.getLogPath(),
                config.getCharset(),
                config.getMinQueryTimeMs(),
                config.getCollectFullSql(),
                config.getCollectIntervalSeconds(),
                config.getCursorOffset(),
                config.getLastCollectedAt() == null ? null : TIME_FORMATTER.format(config.getLastCollectedAt())
        );
    }
}
