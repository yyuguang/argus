package com.lnzz.argus.datamonitor.service;

import java.time.LocalDateTime;

/**
 * slow log 接入服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface SlowLogIngestService {

    SlowLogIngestResult ingest(SlowLogPushRequest request);

    SlowLogIngestResult ingestRaw(SlowLogRawPushRequest request);

    record SlowLogPushRequest(
            String appName,
            String environment,
            String datasourceCode,
            String logSource,
            Long queryTimeMs,
            Long lockTimeMs,
            Long rowsSent,
            Long rowsExamined,
            String sqlText,
            LocalDateTime occurredAt,
            String idempotentKey
    ) {
    }

    record SlowLogRawPushRequest(
            String appName,
            String environment,
            String datasourceCode,
            String content,
            Long cursorOffset,
            String idempotentKey
    ) {
    }

    record SlowLogIngestResult(
            boolean accepted,
            boolean duplicated,
            Long eventId,
            String sqlFingerprint,
            String message
    ) {
    }
}
