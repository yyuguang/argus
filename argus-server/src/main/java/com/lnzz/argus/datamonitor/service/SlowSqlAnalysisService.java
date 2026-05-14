package com.lnzz.argus.datamonitor.service;

import java.util.List;

/**
 * 慢 SQL 根因分析服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface SlowSqlAnalysisService {

    SlowSqlAnalysisResult analyzeEvent(Long eventId);

    List<SlowSqlAnalysisResult> analyzePending(Integer limit);

    record SlowSqlAnalysisResult(
            Long eventId,
            String analysisStatus,
            String causeType,
            String riskLevel,
            String message
    ) {
    }
}
