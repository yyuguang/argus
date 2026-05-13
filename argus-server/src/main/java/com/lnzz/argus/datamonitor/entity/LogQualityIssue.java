package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 接口日志表质量问题明细。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_log_quality_issue")
public class LogQualityIssue extends BaseEntity {

    private Long checkResultId;
    private Long logTableConfigId;
    private String appName;
    private String environment;
    private String tableName;
    private String interfaceCode;
    private String issueType;
    private String severity;
    private Long issueCount;
    private String sampleRecordId;
    private String samplePayload;
    private String description;
    private String suggestion;
    private String status;
    private LocalDateTime occurredAt;
}
