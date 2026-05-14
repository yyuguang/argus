-- Phase 3 TASK-3.9 告警、报告与人工处理

CREATE TABLE IF NOT EXISTS argus_data_monitor_report (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    report_type                VARCHAR(30)     NOT NULL COMMENT '报告类型',
    report_date                DATE            NOT NULL COMMENT '报告日期',
    health_score               INT             DEFAULT NULL COMMENT '健康评分',
    slow_sql_count             INT             NOT NULL DEFAULT 0 COMMENT '慢SQL数量',
    lock_event_count           INT             NOT NULL DEFAULT 0 COMMENT '锁等待数量',
    pool_risk_count            INT             NOT NULL DEFAULT 0 COMMENT '连接池风险数量',
    log_quality_issue_count    INT             NOT NULL DEFAULT 0 COMMENT '日志质量问题数量',
    summary                    TEXT            DEFAULT NULL COMMENT '摘要',
    detail_json                JSON            DEFAULT NULL COMMENT '报告详情',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_monitor_date (monitor_config_id, report_type, report_date),
    INDEX idx_app_date (app_name, environment, report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据监控报告表';

CREATE TABLE IF NOT EXISTS argus_slow_sql_action_log (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    slow_sql_event_id          BIGINT          NOT NULL COMMENT '慢SQL事件ID',
    action_type                VARCHAR(30)     NOT NULL COMMENT '操作类型',
    operator                   VARCHAR(64)     NOT NULL COMMENT '操作人',
    reason                     TEXT            DEFAULT NULL COMMENT '原因',
    before_status              VARCHAR(30)     DEFAULT NULL COMMENT '操作前状态',
    after_status               VARCHAR(30)     DEFAULT NULL COMMENT '操作后状态',
    detail_json                JSON            DEFAULT NULL COMMENT '操作详情',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_slow_sql_event (slow_sql_event_id),
    INDEX idx_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢SQL人工处理日志表';
