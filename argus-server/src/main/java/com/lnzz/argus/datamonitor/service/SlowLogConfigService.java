package com.lnzz.argus.datamonitor.service;

/**
 * slow log 接入配置服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface SlowLogConfigService {

    SlowLogConfigResponse get(Long scmConfigId, Long mappingId, Long datasourceId);

    SlowLogConfigResponse saveOrUpdate(Long scmConfigId, Long mappingId, Long datasourceId,
                                       SlowLogConfigRequest request);

    record SlowLogConfigRequest(
            Boolean enabled,
            String sourceType,
            String logPath,
            String charset,
            Long minQueryTimeMs,
            Boolean collectFullSql,
            Long cursorOffset
    ) {
    }

    record SlowLogConfigResponse(
            Long id,
            Long datasourceId,
            Boolean enabled,
            String sourceType,
            String logPath,
            String charset,
            Long minQueryTimeMs,
            Boolean collectFullSql,
            Long cursorOffset,
            String lastCollectedAt
    ) {
    }
}
