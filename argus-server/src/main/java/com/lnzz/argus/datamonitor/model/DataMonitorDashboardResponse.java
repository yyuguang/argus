package com.lnzz.argus.datamonitor.model;

import lombok.Data;

/**
 * 数据监控工作台概览响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class DataMonitorDashboardResponse {

    /** 健康应用数。 */
    private Integer healthyAppCount;

    /** 已接入监控的应用数。 */
    private Integer monitoredAppCount;

    /** 慢 SQL 数量。 */
    private Integer slowSqlCount;

    /** 连接池风险数量。 */
    private Integer poolRiskCount;

    /** 日志质量问题数量。 */
    private Integer logQualityIssueCount;
}
