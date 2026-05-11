-- ============================================================
-- Argus 历史库升级脚本
-- 用途：已有 MySQL 数据卷/旧库补齐 Phase 1-Enhance 与 Phase 2 字段
-- 注意：这是完整 ALTER SQL，不做幂等封装；若字段或索引已存在，请不要重复执行对应语句。
-- ============================================================

-- Phase 1-Enhance：评审任务提交者唯一标识
ALTER TABLE argus_review_task
    ADD COLUMN author_id VARCHAR(128) DEFAULT NULL COMMENT '提交者唯一ID' AFTER author_name;

ALTER TABLE argus_review_task
    ADD INDEX idx_author_id (author_id);

-- Phase 1-Enhance：SCM 仓库级通知与评审策略配置
ALTER TABLE argus_scm_config
    ADD COLUMN wechat_notify_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '仓库级企微通知开关: 0-关闭 1-开启' AFTER description,
    ADD COLUMN wechat_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级企微Webhook地址（为空则用全局默认）' AFTER wechat_notify_enabled,
    ADD COLUMN review_config JSON DEFAULT NULL COMMENT '仓库级评审配置JSON（ReviewConfig结构）' AFTER wechat_notify_webhook;

-- Phase 2：错误事件聚合、严重度校准、日志来源与 Nginx 场景字段
ALTER TABLE argus_error_event
    ADD COLUMN log_id VARCHAR(100) DEFAULT NULL COMMENT 'Agent生成的日志ID(幂等去重)' AFTER id;

UPDATE argus_error_event
SET log_id = CONCAT('legacy-', id)
WHERE log_id IS NULL OR log_id = '';

ALTER TABLE argus_error_event
    MODIFY COLUMN log_id VARCHAR(100) NOT NULL COMMENT 'Agent生成的日志ID(幂等去重)' AFTER id,
    ADD COLUMN environment VARCHAR(20) DEFAULT NULL COMMENT '环境标识' AFTER source_log_id,
    ADD COLUMN host_name VARCHAR(100) DEFAULT NULL COMMENT '主机名' AFTER environment,
    ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1 COMMENT '聚合同类错误累计次数' AFTER host_name,
    ADD COLUMN first_occurred_at DATETIME DEFAULT NULL COMMENT '首次发生时间' AFTER occurrence_count,
    ADD COLUMN last_occurred_at DATETIME DEFAULT NULL COMMENT '最近发生时间' AFTER first_occurred_at,
    ADD COLUMN last_business_key VARCHAR(200) DEFAULT NULL COMMENT '最近一次业务主键' AFTER last_occurred_at,
    ADD COLUMN last_trace_id VARCHAR(100) DEFAULT NULL COMMENT '最近一次 traceId' AFTER last_business_key,
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '处理状态: NEW/PROCESSING/DONE/IGNORED' AFTER last_trace_id,
    ADD COLUMN analysis_decision VARCHAR(30) DEFAULT NULL COMMENT '分析决策: MUST_ANALYZE/CONDITIONAL_ANALYZE/AGGREGATE_ONLY/IGNORE' AFTER processing_status,
    ADD COLUMN initial_severity VARCHAR(10) DEFAULT NULL COMMENT '规则初判严重度' AFTER analysis_decision,
    ADD COLUMN final_severity VARCHAR(10) DEFAULT NULL COMMENT 'AI/人工校准严重度' AFTER initial_severity,
    ADD COLUMN severity_source VARCHAR(20) NOT NULL DEFAULT 'RULE' COMMENT '严重度来源: RULE/AI_CALIBRATED/MANUAL' AFTER final_severity,
    ADD COLUMN severity_reason VARCHAR(500) DEFAULT NULL COMMENT '严重度判定原因' AFTER severity_source,
    ADD COLUMN severity_confidence DECIMAL(3,2) DEFAULT NULL COMMENT '严重度置信度' AFTER severity_reason,
    ADD COLUMN escalation_count INT NOT NULL DEFAULT 0 COMMENT '升级次数' AFTER severity_confidence,
    ADD COLUMN last_escalation_reason VARCHAR(500) DEFAULT NULL COMMENT '最近升级原因' AFTER escalation_count,
    ADD COLUMN owner_team VARCHAR(100) DEFAULT NULL COMMENT '归属团队' AFTER last_escalation_reason,
    ADD COLUMN source_type VARCHAR(30) DEFAULT 'APP_LOG' COMMENT '来源类型: APP_LOG/NGINX_ACCESS/NGINX_ERROR' AFTER owner_team,
    ADD UNIQUE KEY uk_app_log_id (app_name, log_id),
    ADD INDEX idx_processing_status (processing_status),
    ADD INDEX idx_source_type (source_type),
    ADD INDEX idx_last_occurred (last_occurred_at);

-- Phase 2：人工补充分析结论
ALTER TABLE argus_error_analysis
    ADD COLUMN source VARCHAR(20) DEFAULT 'AI' COMMENT '分析来源: AI/MANUAL/HYBRID' AFTER ai_model,
    ADD COLUMN manual_conclusion TEXT DEFAULT NULL COMMENT '人工补充结论' AFTER source;
