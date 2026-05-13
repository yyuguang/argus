-- Phase 3 TASK-3.5: 锁等待与阻塞事件
-- 说明：
-- 1. 本表只记录 MySQL 5.7 只读采集到的锁等待现场，不提供 kill 连接能力。
-- 2. 完整 INNODB_LOCK_WAITS 缺失时，可由 processlist Locked/metadata lock 降级生成事件。
-- 3. 慢 SQL 事件可通过 related_lock_event_id 反向关联本表。

CREATE TABLE IF NOT EXISTS argus_db_lock_event (
    id                    BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id         BIGINT          NOT NULL COMMENT '数据源ID',
    app_name              VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment           VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    waiting_trx_id        VARCHAR(100)    DEFAULT NULL COMMENT '等待事务ID',
    blocking_trx_id       VARCHAR(100)    DEFAULT NULL COMMENT '阻塞事务ID',
    waiting_process_id    BIGINT          DEFAULT NULL COMMENT '等待线程',
    blocking_process_id   BIGINT          DEFAULT NULL COMMENT '阻塞线程',
    lock_table            VARCHAR(300)    DEFAULT NULL COMMENT '锁表',
    lock_index            VARCHAR(200)    DEFAULT NULL COMMENT '锁索引',
    lock_type             VARCHAR(50)     DEFAULT NULL COMMENT '锁类型',
    wait_seconds          INT             DEFAULT NULL COMMENT '等待时长',
    waiting_sql           LONGTEXT        DEFAULT NULL COMMENT '等待SQL',
    blocking_sql          LONGTEXT        DEFAULT NULL COMMENT '阻塞SQL',
    event_fingerprint     VARCHAR(64)     DEFAULT NULL COMMENT '事件指纹',
    risk_level            VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    status                VARCHAR(30)     NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/ANALYZED/IGNORED/RESOLVED',
    occurred_at           DATETIME        NOT NULL COMMENT '发生时间',
    create_by             VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by             VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, occurred_at),
    INDEX idx_app_time (app_name, environment, occurred_at),
    INDEX idx_waiting_process (waiting_process_id),
    INDEX idx_blocking_process (blocking_process_id),
    INDEX idx_fingerprint (event_fingerprint),
    INDEX idx_status (status, risk_level),
    CONSTRAINT fk_db_lock_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库锁等待与阻塞事件表';
