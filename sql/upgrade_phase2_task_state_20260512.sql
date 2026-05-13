-- Phase 2 TASK-2.14: 错误分析任务表与状态机统一

ALTER TABLE argus_error_event
    MODIFY COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        COMMENT '处理状态: RECEIVED/PARSED/AGGREGATED/ANALYZING/ANALYZED/AI_DEGRADED/NOTIFY_FAILED/IGNORED/FALSE_POSITIVE';

UPDATE argus_error_event
SET processing_status = CASE processing_status
    WHEN 'NEW' THEN 'RECEIVED'
    WHEN 'PROCESSING' THEN 'ANALYZING'
    WHEN 'DONE' THEN 'ANALYZED'
    ELSE processing_status
END
WHERE processing_status IN ('NEW', 'PROCESSING', 'DONE');

CREATE TABLE IF NOT EXISTS argus_error_analysis_task (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    error_event_id  BIGINT          NOT NULL COMMENT '错误事件ID',
    trigger_type    VARCHAR(30)     NOT NULL DEFAULT 'AUTO' COMMENT '触发类型: AUTO/MANUAL/MANUAL_RETRY',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING/RUNNING/DONE/FAILED/TIMEOUT/SKIPPED',
    analysis_id     BIGINT          DEFAULT NULL COMMENT '关联分析结果ID',
    ai_model        VARCHAR(50)     DEFAULT NULL COMMENT 'AI模型',
    error_message   VARCHAR(1000)   DEFAULT NULL COMMENT '失败原因',
    started_at      DATETIME        DEFAULT NULL COMMENT '开始时间',
    finished_at     DATETIME        DEFAULT NULL COMMENT '结束时间',
    duration_ms     BIGINT          DEFAULT NULL COMMENT '执行耗时(ms)',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_event (error_event_id),
    INDEX idx_status (status),
    INDEX idx_trigger_type (trigger_type),
    INDEX idx_create_time (create_time),
    CONSTRAINT fk_analysis_task_event FOREIGN KEY (error_event_id) REFERENCES argus_error_event(id),
    CONSTRAINT fk_analysis_task_analysis FOREIGN KEY (analysis_id) REFERENCES argus_error_analysis(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错误分析任务表';
