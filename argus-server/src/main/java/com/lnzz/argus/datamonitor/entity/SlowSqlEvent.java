package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 慢 SQL 事件。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_slow_sql_event")
public class SlowSqlEvent extends BaseEntity {

    /** 数据源ID */
    private Long datasourceId;

    /** 应用监控配置ID */
    private Long monitorConfigId;

    /** 应用名称 */
    private String appName;

    /** 环境 */
    private String environment;

    /** 来源类型：PROCESSLIST/SLOW_LOG/MANUAL */
    private String sourceType;

    /** 推送或采集幂等键 */
    private String idempotentKey;

    /** SQL 指纹 */
    private String sqlFingerprint;

    /** 完整 SQL */
    private String sqlText;

    /** 脱敏 SQL */
    private String sqlTextMasked;

    /** 执行耗时 */
    private Long durationMs;

    /** 锁等待耗时 */
    private Long lockTimeMs;

    /** 返回行数 */
    private Long rowsSent;

    /** 扫描行数 */
    private Long rowsExamined;

    /** 执行状态 */
    private String processState;

    /** Explain JSON */
    private String explainJson;

    /** 表信息 JSON */
    private String tableInfoJson;

    /** 索引信息 JSON */
    private String indexInfoJson;

    /** 关联锁事件ID */
    private Long relatedLockEventId;

    /** 关联连接池快照ID */
    private Long relatedPoolSnapshotId;

    /** 根因类型 */
    private String causeType;

    /** 风险等级 */
    private String riskLevel;

    /** 分析状态 */
    private String analysisStatus;

    /** 根因结论 */
    private String rootCause;

    /** 优化建议 */
    private String optimizationSuggestion;

    /** 索引建议 SQL，仅展示 */
    private String indexSuggestionSql;

    /** 是否需要 DBA */
    private Boolean needDba;

    /** 是否需要开发 */
    private Boolean needDeveloper;

    /** 置信度 */
    private BigDecimal confidence;

    /** 发生时间 */
    private LocalDateTime occurredAt;
}
