package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 接口日志表质量巡检结果。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_log_quality_check_result")
public class LogQualityCheckResult extends BaseEntity {

    private Long logTableConfigId;
    private Long monitorConfigId;
    private Long datasourceId;
    private String appName;
    private String environment;
    private String tableName;
    private LocalDateTime checkWindowStart;
    private LocalDateTime checkWindowEnd;
    private Long totalCount;
    private Long issueCount;
    private Integer qualityScore;
    private String qualityLevel;
    private Integer completenessScore;
    private Integer timelinessScore;
    private Integer uniquenessScore;
    private Integer validityScore;
    private Integer consistencyScore;
    private Integer growthRiskScore;
    private String status;
    private String errorMessage;
}
