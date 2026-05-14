package com.lnzz.argus.datamonitor.service;

import java.util.List;

/**
 * 接口日志表质量巡检服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface LogQualityCheckService {

    List<LogQualityCheckResponse> checkAllEnabled();

    LogQualityCheckResponse checkConfig(Long configId);

    record LogQualityCheckResponse(
            Long configId,
            Long resultId,
            String tableName,
            boolean success,
            long totalCount,
            long issueCount,
            Integer qualityScore,
            String qualityLevel,
            String message
    ) {
    }
}
