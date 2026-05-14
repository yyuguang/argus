-- Phase 3 数据监控总配置表
-- 适用场景：从 Phase 1/2 历史库升级到支持应用级数据监控配置。

CREATE TABLE IF NOT EXISTS argus_data_monitor_config (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    project_mapping_id  BIGINT          NOT NULL COMMENT '应用映射ID',
    scm_config_id       BIGINT          NOT NULL COMMENT 'SCM配置ID',
    app_name            VARCHAR(100)    NOT NULL COMMENT '应用名称',
    environment         VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境标识',
    enabled             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用数据监控',
    owner_team          VARCHAR(100)    DEFAULT NULL COMMENT '负责人团队',
    tech_owner          VARCHAR(100)    DEFAULT NULL COMMENT '技术负责人',
    alert_webhook_mode  VARCHAR(30)     NOT NULL DEFAULT 'SCM_CONFIG' COMMENT '告警Webhook模式: SCM_CONFIG/CUSTOM',
    remark              VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    create_by           VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_project_mapping (project_mapping_id),
    INDEX idx_scm_config (scm_config_id),
    INDEX idx_app_env (app_name, environment),
    CONSTRAINT fk_data_monitor_project_mapping FOREIGN KEY (project_mapping_id) REFERENCES argus_project_mapping(id),
    CONSTRAINT fk_data_monitor_scm_config FOREIGN KEY (scm_config_id) REFERENCES argus_scm_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用级数据监控总配置表';
