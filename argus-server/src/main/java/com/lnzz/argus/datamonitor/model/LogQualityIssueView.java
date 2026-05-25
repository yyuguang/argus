package com.lnzz.argus.datamonitor.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志质量问题工作台视图对象。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class LogQualityIssueView {

    private Long id;
    private String appName;
    private String environment;
    private String configName;
    private String tableName;
    private String issueType;
    private String issueLevel;
    private String issueSummary;
    private String description;
    private String suggestion;
    private String status;
    private LocalDateTime occurredAt;
}
