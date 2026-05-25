-- ============================================================
-- Argus SCM 多通知平台配置升级脚本
-- 用途：已有 MySQL 数据卷/旧库补齐飞书、钉钉仓库级通知字段
-- 注意：这是完整 ALTER SQL，不做幂等封装；若字段已存在，请不要重复执行对应语句。
-- ============================================================

ALTER TABLE argus_scm_config
    ADD COLUMN feishu_notify_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '仓库级飞书通知开关: 0-关闭 1-开启' AFTER wechat_notify_webhook,
    ADD COLUMN feishu_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级飞书Webhook地址（为空则用全局默认）' AFTER feishu_notify_enabled,
    ADD COLUMN dingtalk_notify_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '仓库级钉钉通知开关: 0-关闭 1-开启' AFTER feishu_notify_webhook,
    ADD COLUMN dingtalk_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级钉钉Webhook地址（为空则用全局默认）' AFTER dingtalk_notify_enabled;
