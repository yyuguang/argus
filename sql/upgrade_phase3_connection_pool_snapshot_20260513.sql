-- Phase 3 TASK-3.6: 连接池指标统一接入
-- 说明：
-- 1. 统一接收 HikariCP 与 Druid 指标。
-- 2. 内部接入接口必须校验 X-Argus-Token。
-- 3. 本表只保存连接池快照和风险标记，不操作业务库连接。

CREATE TABLE IF NOT EXISTS argus_connection_pool_snapshot (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    datasource_id              BIGINT          DEFAULT NULL COMMENT '数据源ID',
    app_name                   VARCHAR(100)    NOT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    datasource_name            VARCHAR(100)    NOT NULL COMMENT '数据源名称',
    pool_type                  VARCHAR(30)     NOT NULL COMMENT '连接池类型: HIKARI/DRUID',
    active_connections         INT             NOT NULL DEFAULT 0 COMMENT '活跃连接',
    idle_connections           INT             NOT NULL DEFAULT 0 COMMENT '空闲连接',
    max_connections            INT             NOT NULL DEFAULT 0 COMMENT '最大连接',
    waiting_threads            INT             NOT NULL DEFAULT 0 COMMENT '等待线程',
    connection_acquire_avg_ms  BIGINT          NOT NULL DEFAULT 0 COMMENT '平均获取耗时',
    connection_acquire_max_ms  BIGINT          NOT NULL DEFAULT 0 COMMENT '最大获取耗时',
    timeout_count              BIGINT          NOT NULL DEFAULT 0 COMMENT '超时次数',
    error_count                BIGINT          NOT NULL DEFAULT 0 COMMENT '错误次数',
    risk_type                  VARCHAR(50)     DEFAULT NULL COMMENT '风险类型',
    risk_level                 VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    collected_at               DATETIME        NOT NULL COMMENT '采集时间',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_monitor_time (monitor_config_id, collected_at),
    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_app_time (app_name, environment, collected_at),
    INDEX idx_risk (risk_type, risk_level),
    CONSTRAINT fk_pool_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id),
    CONSTRAINT fk_pool_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='连接池指标快照表';
