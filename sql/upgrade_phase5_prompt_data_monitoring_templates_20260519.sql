-- ============================================================
-- Phase 5 Prompt 数据监控模板组补充脚本
-- 说明：
-- 1. 用于已执行过 init_rule_management.sql 的历史库补充 DATA_MONITORING 分类下的首批模板组。
-- 2. 与 init_rule_management.sql 中的 Prompt 基线保持一致。
-- ============================================================

INSERT INTO argus_prompt_template_definition (
    template_code, template_name, category, template_scene, support_scm_override,
    sort_no, status, description, create_by, update_by
) VALUES
    ('SLOW_SQL_ANALYSIS_MAIN', '慢 SQL 分析主提示词', 'DATA_MONITORING', 'MAIN', 1,
     50, 'ACTIVE', 'Phase 3 慢 SQL 根因分析主链路使用的完整提示词模板', 'system', 'system'),
    ('SLOW_SQL_JSON_REPAIR', '慢 SQL 分析结果JSON修复提示词', 'DATA_MONITORING', 'REPAIR', 1,
     60, 'ACTIVE', '慢 SQL 分析结果非结构化时用于 JSON 修复的模板', 'system', 'system'),
    ('DB_POOL_RISK_ANALYSIS_MAIN', '连接池风险分析主提示词', 'DATA_MONITORING', 'MAIN', 1,
     70, 'ACTIVE', 'Phase 3 连接池风险分析主链路使用的完整提示词模板', 'system', 'system'),
    ('LOG_QUALITY_ANALYSIS_MAIN', '日志质量分析主提示词', 'DATA_MONITORING', 'MAIN', 1,
     80, 'ACTIVE', 'Phase 3 日志质量问题分析主链路使用的完整提示词模板', 'system', 'system')
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    category = VALUES(category),
    template_scene = VALUES(template_scene),
    support_scm_override = VALUES(support_scm_override),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    description = VALUES(description),
    update_by = VALUES(update_by),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO argus_prompt_template_scheme (
    template_code, scope, scm_config_id, content_text, remark, status, create_by, update_by
) VALUES
    ('SLOW_SQL_ANALYSIS_MAIN', 'GLOBAL', 0,
'你是 Argus 数据监控 AI，当前负责分析慢 SQL 事件并输出结构化根因与优化建议。

## 事件概要
{{slowSqlEventSummary}}

## SQL 文本
```sql
{{sqlText}}
```

{{explainResultSection}}{{lockContextSection}}{{poolContextSection}}{{analysisHintsSection}}

## 分析职责
1. 判断慢 SQL 的最可能根因，可同时给出 1~3 个主因候选，但必须区分主次。
2. 说明根因证据，证据必须来自 SQL、EXPLAIN、锁等待、连接池或事件上下文。
3. 评估风险级别和影响范围，区分单条 SQL 优化问题和系统性资源瓶颈。
4. 输出优先级明确的优化建议，优先给出能在当前系统里立即执行的动作。
5. 如果事件主要受锁等待、连接池耗尽、长事务影响，必须直接指出，不要只给索引建议。

## 输出要求
严格输出 JSON，不要输出 Markdown 代码块外的任何内容：
{
  "summary": "",
  "severity": "P2",
  "primaryCause": "",
  "rootCauses": [
    {
      "code": "",
      "title": "",
      "evidence": "",
      "reasoning": ""
    }
  ],
  "impactScope": "",
  "optimizationSuggestions": [
    ""
  ],
  "actionPlan": [
    ""
  ],
  "isBlocker": false
}', '系统全局慢 SQL 分析兜底模板', 'ACTIVE', 'system', 'system'),
    ('SLOW_SQL_JSON_REPAIR', 'GLOBAL', 0,
'你需要把下面这段慢 SQL 分析回复转换为严格 JSON。

只允许输出一个 JSON 对象，首字符必须是 {，末字符必须是 }。
不允许输出 Markdown、解释、寒暄、代码块标记或任何 JSON 外文本。
如果原文缺少字段，请按 schema 补齐合理默认值。

JSON schema:
{
  "summary": "",
  "severity": "P2",
  "primaryCause": "",
  "rootCauses": [
    {
      "code": "",
      "title": "",
      "evidence": "",
      "reasoning": ""
    }
  ],
  "impactScope": "",
  "optimizationSuggestions": [
    ""
  ],
  "actionPlan": [
    ""
  ],
  "isBlocker": false
}

原回复：
{{originalResponse}}', '系统全局慢 SQL JSON 修复模板', 'ACTIVE', 'system', 'system'),
    ('DB_POOL_RISK_ANALYSIS_MAIN', 'GLOBAL', 0,
'你是 Argus 数据监控 AI，当前负责分析数据库连接池风险事件并输出结构化结论。

## 事件概要
{{poolRiskSummary}}

## 指标快照
{{poolMetricSnapshot}}

{{relatedSlowSqlSection}}{{relatedAlertSection}}

## 分析职责
1. 判断当前连接池风险属于容量不足、连接泄漏、数据库响应抖动、慢 SQL 放大，还是短时流量峰值。
2. 必须解释风险证据，包括活跃连接、等待线程、获取连接耗时、超时次数等关键指标。
3. 说明业务影响、是否已接近不可用，以及应优先排查应用侧还是数据库侧。
4. 给出可执行的处理动作，区分立刻止血和后续治理。

## 输出要求
严格输出 JSON，不要输出 Markdown 代码块外的任何内容：
{
  "summary": "",
  "riskLevel": "HIGH",
  "primaryCause": "",
  "evidence": [
    ""
  ],
  "impactScope": "",
  "immediateActions": [
    ""
  ],
  "followupSuggestions": [
    ""
  ],
  "needEscalation": true
}', '系统全局连接池风险分析兜底模板', 'ACTIVE', 'system', 'system'),
    ('LOG_QUALITY_ANALYSIS_MAIN', 'GLOBAL', 0,
'你是 Argus 数据监控 AI，当前负责分析接口日志质量问题并输出业务可执行的治理建议。

## 问题概要
{{logQualityIssueSummary}}

## 巡检结果明细
{{logQualityIssueDetail}}

{{affectedFieldSection}}{{sampleDataSection}}

## 分析职责
1. 判断问题属于缺字段、脏数据、重复数据、延迟写入、响应体异常还是日志表设计缺陷。
2. 说明问题对排障、审计、业务追踪和数据分析的影响。
3. 给出优先级清晰的修复建议，区分应用改造、表结构治理、巡检规则调整。
4. 如果问题本质是日志缺失导致无法追踪业务，必须明确指出风险等级。

## 输出要求
严格输出 JSON，不要输出 Markdown 代码块外的任何内容：
{
  "summary": "",
  "issueType": "",
  "severity": "MEDIUM",
  "rootCause": "",
  "impactScope": "",
  "evidence": [
    ""
  ],
  "fixSuggestions": [
    ""
  ],
  "governanceSuggestions": [
    ""
  ]
}', '系统全局日志质量分析兜底模板', 'ACTIVE', 'system', 'system')
ON DUPLICATE KEY UPDATE
    content_text = VALUES(content_text),
    remark = VALUES(remark),
    status = VALUES(status),
    update_by = VALUES(update_by),
    update_time = CURRENT_TIMESTAMP;
