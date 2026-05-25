-- Phase 3 连接池风险 AI 增强字段升级
-- 说明：
-- 1. 为连接池快照补充风险原因分析字段，用于沉淀 AI 增强后的业务可读结论。
-- 2. 不变更既有 risk_type / risk_level 语义，继续以规则判定为安全基线。

ALTER TABLE argus_connection_pool_snapshot
    ADD COLUMN risk_reason VARCHAR(2000) DEFAULT NULL COMMENT '风险原因分析' AFTER risk_level;
