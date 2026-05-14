-- Phase 3 TASK-3.3: MySQL 5.7 运行现场采集快照
-- 说明：
-- 1. 采集来源必须是 argus_data_source_config 中的只读 MySQL 5.7 数据源。
-- 2. 本脚本只创建 Argus 自身快照表，不对业务库执行任何 DDL/DML。
-- 3. 完整 SQL 允许保存；展示和通知侧应优先使用 sql_text_masked。

CREATE TABLE IF NOT EXISTS argus_db_metric_snapshot (
    id                       BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id            BIGINT          NOT NULL COMMENT '数据源ID',
    app_name                 VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment              VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境标识',
    threads_connected        INT             DEFAULT NULL COMMENT '当前连接数',
    threads_running          INT             DEFAULT NULL COMMENT '活跃线程数',
    max_connections          INT             DEFAULT NULL COMMENT '最大连接数',
    questions                BIGINT          DEFAULT 0 COMMENT 'Questions计数',
    com_select               BIGINT          DEFAULT 0 COMMENT 'Select计数',
    com_insert               BIGINT          DEFAULT 0 COMMENT 'Insert计数',
    com_update               BIGINT          DEFAULT 0 COMMENT 'Update计数',
    com_delete               BIGINT          DEFAULT 0 COMMENT 'Delete计数',
    qps                      DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '计算后的QPS',
    slow_queries             BIGINT          DEFAULT 0 COMMENT '慢查询累计数',
    innodb_trx_count         INT             DEFAULT 0 COMMENT 'InnoDB活跃事务数',
    innodb_lock_wait_count   INT             DEFAULT 0 COMMENT 'InnoDB锁等待数',
    collected_at             DATETIME        NOT NULL COMMENT '采集时间',
    create_by                VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_app_time (app_name, environment, collected_at),
    CONSTRAINT fk_db_metric_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库运行指标快照表';

CREATE TABLE IF NOT EXISTS argus_db_process_snapshot (
    id                 BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id      BIGINT          NOT NULL COMMENT '数据源ID',
    mysql_process_id   BIGINT          DEFAULT NULL COMMENT 'MySQL线程ID',
    user_name          VARCHAR(100)    DEFAULT NULL COMMENT '执行用户',
    host_info          VARCHAR(200)    DEFAULT NULL COMMENT '来源host',
    database_name      VARCHAR(200)    DEFAULT NULL COMMENT '数据库名',
    command_type       VARCHAR(50)     DEFAULT NULL COMMENT 'Command',
    process_state      VARCHAR(200)    DEFAULT NULL COMMENT 'State',
    duration_seconds   INT             DEFAULT NULL COMMENT '已执行秒数',
    sql_fingerprint    VARCHAR(64)     DEFAULT NULL COMMENT 'SQL指纹',
    sql_text           LONGTEXT        DEFAULT NULL COMMENT '完整SQL',
    sql_text_masked    LONGTEXT        DEFAULT NULL COMMENT '脱敏SQL',
    risk_type          VARCHAR(50)     DEFAULT NULL COMMENT '风险类型: LONG_SQL/LOCKED/METADATA_LOCK/LONG_TRX',
    risk_level         VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    collected_at       DATETIME        NOT NULL COMMENT '采集时间',
    create_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_fingerprint (sql_fingerprint),
    INDEX idx_risk (risk_type, risk_level),
    CONSTRAINT fk_db_process_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='当前执行SQL快照表';
