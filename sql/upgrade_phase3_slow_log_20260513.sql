-- Phase 3 TASK-3.4: MySQL slow log 接入
-- 说明：
-- 1. slow log 配置必须依附应用数据源，不允许作为全局配置存在。
-- 2. slow log 接入生成 SLOW_LOG 来源的慢 SQL 事件；根因分析由后续 TASK-3.7 完成。
-- 3. index_suggestion_sql 仅用于报告和告警展示，Argus 不执行生产库变更。

CREATE TABLE IF NOT EXISTS argus_slow_log_config (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id       BIGINT          NOT NULL COMMENT '数据源ID',
    enabled             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用',
    source_type         VARCHAR(30)     NOT NULL DEFAULT 'FILE_TAIL' COMMENT '来源类型: FILE_TAIL/PUSH/TABLE',
    log_path            VARCHAR(1000)   DEFAULT NULL COMMENT 'slow log文件路径',
    charset             VARCHAR(50)     NOT NULL DEFAULT 'UTF-8' COMMENT '字符集',
    min_query_time_ms   BIGINT          NOT NULL DEFAULT 1000 COMMENT '最小采集耗时',
    collect_full_sql    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集完整SQL',
    cursor_offset       BIGINT          NOT NULL DEFAULT 0 COMMENT '文件读取位点',
    last_collected_at   DATETIME        DEFAULT NULL COMMENT '最近采集时间',
    create_by           VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_datasource (datasource_id),
    INDEX idx_enabled (enabled),
    CONSTRAINT fk_slow_log_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MySQL slow log接入配置表';

CREATE TABLE IF NOT EXISTS argus_slow_sql_event (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id              BIGINT          NOT NULL COMMENT '数据源ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    source_type                VARCHAR(30)     NOT NULL COMMENT '来源类型: PROCESSLIST/SLOW_LOG/MANUAL',
    idempotent_key             VARCHAR(100)    NOT NULL COMMENT '推送或采集幂等键',
    sql_fingerprint            VARCHAR(64)     DEFAULT NULL COMMENT 'SQL指纹',
    sql_text                   LONGTEXT        DEFAULT NULL COMMENT '完整SQL',
    sql_text_masked            LONGTEXT        DEFAULT NULL COMMENT '脱敏SQL',
    duration_ms                BIGINT          DEFAULT NULL COMMENT '执行耗时',
    lock_time_ms               BIGINT          DEFAULT NULL COMMENT '锁等待耗时',
    rows_sent                  BIGINT          DEFAULT NULL COMMENT '返回行数',
    rows_examined              BIGINT          DEFAULT NULL COMMENT '扫描行数',
    process_state              VARCHAR(200)    DEFAULT NULL COMMENT '执行状态',
    explain_json               JSON            DEFAULT NULL COMMENT 'Explain结果',
    table_info_json            JSON            DEFAULT NULL COMMENT '表信息',
    index_info_json            JSON            DEFAULT NULL COMMENT '索引信息',
    related_lock_event_id      BIGINT          DEFAULT NULL COMMENT '关联锁事件ID',
    related_pool_snapshot_id   BIGINT          DEFAULT NULL COMMENT '关联连接池快照ID',
    cause_type                 VARCHAR(50)     DEFAULT NULL COMMENT '根因类型',
    risk_level                 VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    analysis_status            VARCHAR(30)     NOT NULL DEFAULT 'PENDING' COMMENT '分析状态',
    root_cause                 TEXT            DEFAULT NULL COMMENT '根因结论',
    optimization_suggestion    TEXT            DEFAULT NULL COMMENT '优化建议',
    index_suggestion_sql       TEXT            DEFAULT NULL COMMENT '索引建议SQL，仅展示',
    need_dba                   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否需要DBA',
    need_developer             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否需要开发',
    confidence                 DECIMAL(3,2)    DEFAULT NULL COMMENT '置信度',
    occurred_at                DATETIME        NOT NULL COMMENT '发生时间',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_idempotent_key (idempotent_key),
    INDEX idx_app_time (app_name, environment, occurred_at),
    INDEX idx_datasource_time (datasource_id, occurred_at),
    INDEX idx_fingerprint (sql_fingerprint),
    INDEX idx_cause (cause_type),
    INDEX idx_risk (risk_level, analysis_status),
    CONSTRAINT fk_slow_sql_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id),
    CONSTRAINT fk_slow_sql_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢SQL事件表';
