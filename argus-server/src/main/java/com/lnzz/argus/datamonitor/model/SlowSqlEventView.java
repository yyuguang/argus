package com.lnzz.argus.datamonitor.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 慢 SQL 工作台视图对象。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class SlowSqlEventView {

    private Long id;
    private String appName;
    private String environment;
    private String datasourceCode;
    private String datasourceName;
    private String sourceType;
    private String sqlFingerprint;
    private String sqlText;
    private String sqlTextMasked;
    private Long durationMs;
    private Long lockTimeMs;
    private Long rowsSent;
    private Long rowsExamined;
    private String processState;
    private String explainJson;
    private Long relatedLockEventId;
    private Long relatedPoolSnapshotId;
    private String causeType;
    private String riskLevel;
    private String status;
    private String analysisStatus;
    private String rootCause;
    private String optimizationSuggestion;
    private String indexSuggestionSql;
    private Boolean needDba;
    private Boolean needDeveloper;
    private Boolean canViewFullSql;
    private Object relatedLockEvent;
    private Object relatedPoolSnapshot;
    private LocalDateTime occurredAt;
}
