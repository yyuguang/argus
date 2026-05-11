package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 错误事件实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_error_event")
public class ErrorEvent extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent生成的日志ID（与 appName 组合唯一，用于幂等去重） */
    private String logId;

    /** 应用名称 */
    private String appName;

    /** 错误类型枚举 */
    private String errorType;

    /** 错误消息 */
    private String errorMessage;

    /** 严重度: P0/P1/P2/P3 */
    private String severity;

    /** 错误指纹（SHA-256） */
    private String errorFingerprint;

    /** 出错类全限定名 */
    private String className;

    /** 出错方法名 */
    private String methodName;

    /** 出错行号 */
    private Integer lineNumber;

    /** 映射的文件路径 */
    private String filePath;

    /** 业务主键 */
    private String businessKey;

    /** 关联接口 */
    private String interfaceRef;

    /** 追踪ID */
    private String traceId;

    /** 原始异常栈 */
    private String rawStackTrace;

    /** 上下文日志(JSON) */
    private String contextLogs;

    /** 请求信息(JSON) */
    private String requestInfo;

    /** 已分析 */
    private Boolean analyzed;

    /** 已通知 */
    private Boolean notified;

    /** 来源日志表ID */
    private Long sourceLogId;

    /** 错误发生时间 */
    private LocalDateTime occurredAt;

    // ======================== 聚合与解析字段（M4-B04/M4-B05） ========================

    /** 环境标识 */
    private String environment;

    /** 主机名 */
    private String hostName;

    /** 聚合同类错误累计次数 */
    private Integer occurrenceCount;

    /** 首次发生时间 */
    private LocalDateTime firstOccurredAt;

    /** 最近发生时间 */
    private LocalDateTime lastOccurredAt;

    /** 最近一次业务主键 */
    private String lastBusinessKey;

    /** 最近一次 traceId */
    private String lastTraceId;

    /** 处理状态 */
    private String processingStatus;

    /** 分析决策: MUST_ANALYZE/CONDITIONAL_ANALYZE/AGGREGATE_ONLY/IGNORE */
    private String analysisDecision;

    /** 规则初判严重度 */
    private String initialSeverity;

    /** AI/人工校准严重度 */
    private String finalSeverity;

    /** 严重度来源: RULE/AI_CALIBRATED/MANUAL */
    private String severitySource;

    /** 严重度判定原因 */
    private String severityReason;

    /** 严重度置信度 */
    private BigDecimal severityConfidence;

    /** 升级次数 */
    private Integer escalationCount;

    /** 最近升级原因 */
    private String lastEscalationReason;

    /** 归属团队 */
    private String ownerTeam;

    /** 来源类型 */
    private String sourceType;
}
