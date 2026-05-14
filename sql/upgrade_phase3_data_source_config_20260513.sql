-- Phase 3 TASK-3.2: 应用级业务库只读数据源配置
-- 说明：
-- 1. 数据源配置必须挂在 SCM 应用联动配置下，不允许作为全局监控项存在。
-- 2. 账号必须是只读账号；Argus 只保存加密后的 password_secret，不保存明文密码。
-- 3. 索引建议只用于告警和报告展示，Argus 不具备生产库变更权限。

CREATE TABLE IF NOT EXISTS argus_data_source_config (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '数据监控总配置ID',
    project_mapping_id         BIGINT          NOT NULL COMMENT '应用映射ID',
    datasource_code            VARCHAR(100)    NOT NULL COMMENT '数据源编码',
    datasource_name            VARCHAR(200)    DEFAULT NULL COMMENT '数据源名称',
    db_type                    VARCHAR(30)     NOT NULL DEFAULT 'MYSQL' COMMENT '数据库类型',
    db_version                 VARCHAR(30)     NOT NULL DEFAULT '5.7' COMMENT '数据库版本',
    jdbc_url                   VARCHAR(1000)   NOT NULL COMMENT 'JDBC地址',
    host                       VARCHAR(200)    DEFAULT NULL COMMENT '主机',
    port                       INT             DEFAULT 3306 COMMENT '端口',
    database_name              VARCHAR(200)    DEFAULT NULL COMMENT '数据库名',
    username                   VARCHAR(200)    NOT NULL COMMENT '只读账号',
    password_secret            VARCHAR(1000)   NOT NULL COMMENT '加密后的密码或密钥引用',
    readonly                   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否只读',
    enabled                    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    collect_processlist        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集 processlist',
    collect_innodb_trx         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集 InnoDB 事务',
    collect_innodb_lock        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集 InnoDB 锁等待',
    collect_global_status      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集 global status',
    explain_enabled            TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许 Explain',
    full_sql_collect_enabled   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集完整 SQL',
    threshold_config           JSON            DEFAULT NULL COMMENT '阈值配置',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_mapping_datasource (project_mapping_id, datasource_code),
    INDEX idx_monitor_config (monitor_config_id),
    INDEX idx_project_mapping (project_mapping_id),
    INDEX idx_enabled (enabled),
    CONSTRAINT fk_data_source_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id),
    CONSTRAINT fk_data_source_project_mapping FOREIGN KEY (project_mapping_id) REFERENCES argus_project_mapping(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用级业务库只读数据源配置表';
