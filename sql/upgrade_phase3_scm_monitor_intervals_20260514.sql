-- Phase 3 SCM 应用联动监控频率配置补齐
-- 说明：业务监控频率归属 SCM 应用联动配置，不放入 application.yml。

ALTER TABLE argus_data_monitor_config
    ADD COLUMN default_runtime_collect_interval_seconds INT NOT NULL DEFAULT 30 COMMENT '默认数据库运行态采集间隔秒数' AFTER alert_webhook_mode,
    ADD COLUMN default_pool_metric_push_interval_seconds INT NOT NULL DEFAULT 30 COMMENT '默认连接池指标推送间隔秒数' AFTER default_runtime_collect_interval_seconds,
    ADD COLUMN default_log_quality_check_interval_seconds INT NOT NULL DEFAULT 300 COMMENT '默认接口日志质量巡检间隔秒数' AFTER default_pool_metric_push_interval_seconds,
    ADD COLUMN alert_scan_interval_seconds INT NOT NULL DEFAULT 60 COMMENT '告警扫描间隔秒数' AFTER default_log_quality_check_interval_seconds;

ALTER TABLE argus_data_source_config
    ADD COLUMN runtime_collect_interval_seconds INT NOT NULL DEFAULT 30 COMMENT '数据库运行态采集间隔秒数' AFTER full_sql_collect_enabled,
    ADD COLUMN pool_metric_push_interval_seconds INT NOT NULL DEFAULT 30 COMMENT '连接池指标期望推送间隔秒数' AFTER runtime_collect_interval_seconds;

ALTER TABLE argus_slow_log_config
    ADD COLUMN collect_interval_seconds INT NOT NULL DEFAULT 60 COMMENT 'slow log采集间隔秒数' AFTER collect_full_sql;

ALTER TABLE argus_interface_log_table_config
    ADD COLUMN quality_check_interval_seconds INT NOT NULL DEFAULT 300 COMMENT '接口日志质量巡检间隔秒数' AFTER scan_mode;
