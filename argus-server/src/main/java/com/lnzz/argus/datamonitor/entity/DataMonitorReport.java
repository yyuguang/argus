package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 数据监控报告。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_data_monitor_report")
public class DataMonitorReport extends BaseEntity {

    private Long monitorConfigId;
    private String appName;
    private String environment;
    private String reportType;
    private LocalDate reportDate;
    private Integer healthScore;
    private Integer slowSqlCount;
    private Integer lockEventCount;
    private Integer poolRiskCount;
    private Integer logQualityIssueCount;
    private String summary;
    private String detailJson;
}
