-- ============================================================
-- Argus 规则管理模块初始化脚本
-- 说明：
-- 1. 本脚本仅包含 Phase 5 规则管理相关表。
-- 2. 与 init.sql、init_sys.sql、init_scm_menu.sql 严格隔离。
-- 3. 表结构遵循 .ai_rules/DB_STYLE.md：snake_case、审计字段、软删除、乐观锁、注释与索引。
-- 4. 本脚本不灌入默认规则文档和评分阈值数据，但包含 Prompt 目录与全局兜底模板初始化数据。
-- ============================================================

-- Section 1：规则文档主表
CREATE TABLE IF NOT EXISTS argus_rule_document (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    document_code  VARCHAR(64)  NOT NULL COMMENT '文档编码，规则管理域内唯一',
    document_name  VARCHAR(128) NOT NULL COMMENT '文档名称',
    category       VARCHAR(32)  NOT NULL COMMENT '规范分类，如 CODING/API/DB/SERVICE',
    scope          VARCHAR(16)  NOT NULL COMMENT '作用域：GLOBAL/SCM',
    scm_config_id  BIGINT       DEFAULT NULL COMMENT 'SCM 仓库配置ID，scope=SCM 时使用',
    source_type    VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型：UPLOAD/MANUAL/MIGRATION',
    file_name      VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    file_ext       VARCHAR(16)  DEFAULT NULL COMMENT '文件扩展名',
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '文档状态：DRAFT/ACTIVE/DISABLED/ARCHIVED',
    parse_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING/SUCCESS/FAILED',
    vector_status  VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态：PENDING/SUCCESS/FAILED',
    content_text   LONGTEXT COMMENT '解析后的全文文本',
    summary_text   TEXT COMMENT '文档摘要',
    chunk_count    INT          NOT NULL DEFAULT 0 COMMENT '分块数量',
    version_no     INT          NOT NULL DEFAULT 1 COMMENT '规则文档业务版本号',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_document_code (document_code),
    INDEX idx_scope_scm_status_category (scope, scm_config_id, status, category),
    INDEX idx_parse_vector_status (parse_status, vector_status),
    INDEX idx_name (document_name),
    INDEX idx_deleted_status (is_deleted, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则文档主表';

-- Section 2：规则文档分块表
CREATE TABLE IF NOT EXISTS argus_rule_document_chunk (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    document_id    BIGINT       NOT NULL COMMENT '所属规则文档ID',
    chunk_no       INT          NOT NULL COMMENT '分块序号，从 1 开始递增',
    title          VARCHAR(255) DEFAULT NULL COMMENT '分块标题',
    content_text   TEXT         NOT NULL COMMENT '分块文本内容',
    token_estimate INT          NOT NULL DEFAULT 0 COMMENT 'Token 预估值',
    vector_doc_id  VARCHAR(128) DEFAULT NULL COMMENT '向量库文档ID',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '分块状态：ACTIVE/DISABLED',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_document_chunk_no (document_id, chunk_no),
    INDEX idx_document_status (document_id, status),
    INDEX idx_deleted_status (is_deleted, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则文档分块表';

-- Section 3：Prompt 模板定义表
CREATE TABLE IF NOT EXISTS argus_prompt_template_definition (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_code        VARCHAR(64)  NOT NULL COMMENT '模板组编码，如 CODE_REVIEW_MAIN',
    template_name        VARCHAR(128) NOT NULL COMMENT '模板组名称',
    category             VARCHAR(32)  NOT NULL COMMENT '一级分类：CODE_REVIEW/ERROR_ANALYSIS/DATA_MONITORING',
    template_scene       VARCHAR(32)  NOT NULL COMMENT '模板场景：MAIN/REPAIR/OTHER',
    support_scm_override TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否支持仓库级覆盖：0-否 1-是',
    sort_no              INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    description          VARCHAR(255) DEFAULT NULL COMMENT '模板说明',
    is_deleted           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version              INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by            VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_template_code (template_code),
    INDEX idx_category_status (category, status),
    INDEX idx_sort_no (sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 模板定义表';

-- Section 4：Prompt 模板方案表
CREATE TABLE IF NOT EXISTS argus_prompt_template_scheme (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_code  VARCHAR(64)  NOT NULL COMMENT '模板组编码',
    scope          VARCHAR(16)  NOT NULL COMMENT '作用域：GLOBAL/SCM',
    scm_config_id  BIGINT       NOT NULL DEFAULT 0 COMMENT 'SCM 仓库配置ID，GLOBAL 固定为 0',
    content_text   LONGTEXT     NOT NULL COMMENT 'Prompt 正文',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_template_scope_scm (template_code, scope, scm_config_id),
    INDEX idx_scope_status (scope, status),
    INDEX idx_scm_status (scm_config_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 模板方案表';

-- Section 5：Prompt 模板定义初始化
INSERT INTO argus_prompt_template_definition (
    template_code, template_name, category, template_scene, support_scm_override,
    sort_no, status, description, create_by, update_by
) VALUES
    ('CODE_REVIEW_MAIN', '代码评审主提示词', 'CODE_REVIEW', 'MAIN', 1,
     10, 'ACTIVE', '代码评审主链路使用的完整提示词模板', 'system', 'system'),
    ('CODE_REVIEW_JSON_REPAIR', '代码评审结果JSON修复提示词', 'CODE_REVIEW', 'REPAIR', 1,
     20, 'ACTIVE', '代码评审结果非结构化时用于 JSON 修复的模板', 'system', 'system'),
    ('ERROR_ANALYSIS_MAIN', '错误分析主提示词', 'ERROR_ANALYSIS', 'MAIN', 1,
     30, 'ACTIVE', '错误分析主链路使用的完整提示词模板', 'system', 'system'),
    ('ERROR_ANALYSIS_JSON_REPAIR', '错误分析结果JSON修复提示词', 'ERROR_ANALYSIS', 'REPAIR', 1,
     40, 'ACTIVE', '错误分析结果非结构化时用于 JSON 修复的模板', 'system', 'system'),
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

-- Section 6：Prompt 全局兜底方案初始化
INSERT INTO argus_prompt_template_scheme (
    template_code, scope, scm_config_id, content_text, remark, status, create_by, update_by
) VALUES
    ('CODE_REVIEW_MAIN', 'GLOBAL', 0,
'你是 Argus 代码评审 AI，一位严格、克制、证据导向的高级工程变更审查员。
你的职责是对代码、SQL、配置文件等变更执行可阻止合并的工程评审，并输出稳定、结构化、可落地的结果。

## 评审维度与权重
1. 规范合规（{{complianceWeight}}%）
2. 逻辑正确（{{correctnessWeight}}%）
3. 数据完整（{{dataIntegrityWeight}}%）
4. 性能风险（{{performanceWeight}}%）
5. 可维护性（{{maintainabilityWeight}}%）

## 问题严重度定义
{{severityDefinitions}}

## 本次评审对象
- 文件路径：{{filePath}}
- 语言：{{languageTag}}
- 重点关注：{{reviewFocus}}

## 规则参考
{{ruleReference}}

## 核心硬规则
1. 所有外部接口调用后的返回值，必须做完整判空、状态码校验、必要日志记录。
2. catch 块不能空实现；吞异常时至少记录关键上下文，并明确是否继续抛出。
3. 涉及订单号、用户ID、业务主键等关键字段时，必须保证数据链路完整可追踪。
4. 变更中的 public 方法、核心业务分支、外部系统交互点，必须具备足够的可读性和可维护性。
5. 会导致生产事故、数据错误、严重排障困难的问题，必须判定为阻塞问题。

## 评审约束
1. 结论必须基于输入代码和上下文中的直接证据，禁止脑补未提供的事实。
2. 只评审本次 Diff 变更，但要结合完整文件上下文分析变更影响。
3. 如果证据不足，最多给出 SUGGESTION，不得虚构 CRITICAL/MAJOR。
4. 同一根因只报告一次，优先指出根因，不要重复报告多个表象问题。
5. 每个问题必须说明证据、风险与修复建议。

## 完整文件内容
```text
{{fullContent}}
```

## 本次变更 Diff
```diff
{{diffContent}}
```

{{relatedClassesSection}}{{profileSection}}{{teamKnowledgeSection}}

## 输出要求
请严格输出 JSON，对象首字符必须是 {，末字符必须是 }。
禁止输出 Markdown 代码块、寒暄或 JSON 外文本。

JSON schema:
{
  "scores": {
    "compliance": 100,
    "correctness": 100,
    "dataSafety": 100,
    "performance": 100,
    "maintainability": 100
  },
  "issues": [],
  "highlights": [],
  "summary": ""
}', '系统全局代码评审兜底模板', 'ACTIVE', 'system', 'system'),
    ('CODE_REVIEW_JSON_REPAIR', 'GLOBAL', 0,
'你需要把下面这段代码评审回复转换为严格 JSON。

只允许输出一个 JSON 对象，首字符必须是 {，末字符必须是 }。
不允许输出 Markdown、解释、寒暄、代码块标记或任何 JSON 外文本。

JSON schema:
{
  "scores": {
    "compliance": 100,
    "correctness": 100,
    "dataSafety": 100,
    "performance": 100,
    "maintainability": 100
  },
  "issues": [],
  "highlights": [],
  "summary": ""
}

如果原回复没有明确问题，请返回空 issues，并在 summary 说明原回复未提供结构化问题。

原回复：
{{originalResponse}}', '系统全局代码评审JSON修复模板', 'ACTIVE', 'system', 'system'),
    ('ERROR_ANALYSIS_MAIN', 'GLOBAL', 0,
'你是 Argus 错误分析 AI，专门负责分析 Java 应用的生产错误并给出根因定位和修复建议。

## 错误事件信息
{{errorInfoTable}}

## 错误消息
{{errorMessageBlock}}

{{stackTraceSection}}{{contextLogsSection}}{{sourceCodeSection}}{{historyCasesSection}}

## 你的分析职责
1. 根因定位：结合错误信息、异常栈和源码，推断最可能的根本原因
2. 技术细节：说明涉及的技术点
3. 影响范围：评估该错误对业务的影响
4. 严重度校准：基于实际影响重新评估严重度 P0/P1/P2/P3
5. 修复方案：给出具体代码级修复建议
6. 预防建议：如何避免同类问题

## 严重度标准
- P0：核心链路不可用，影响所有用户
- P1：核心链路部分不可用，或关键功能受损
- P2：非核心功能异常，或可自动恢复
- P3：轻微问题，不影响业务

## 分析原则
- 优先从源码中寻找证据
- 不凭空猜测，不确定时标明置信度
- 修复方案必须针对源码中的具体行

## 输出格式
严格输出 JSON，不要包含 markdown 代码块外的其他内容：
{
  "rootCause": "",
  "technicalDetail": "",
  "impactScope": "",
  "calibratedSeverity": "P3",
  "severityReason": "",
  "confidence": 0.7,
  "fix": {
    "description": "",
    "codeExample": "",
    "filePath": "",
    "lineRange": ""
  },
  "estimatedEffort": "",
  "preventionAdvice": "",
  "isKnownIssue": false
}

## 请开始分析
请基于以上信息，输出 JSON 格式的分析结果。', '系统全局错误分析兜底模板', 'ACTIVE', 'system', 'system'),
    ('ERROR_ANALYSIS_JSON_REPAIR', 'GLOBAL', 0,
'你需要把下面这段错误分析回复转换为严格 JSON。

只允许输出一个 JSON 对象，首字符必须是 {，末字符必须是 }。
不允许输出 Markdown、解释、寒暄、代码块标记或任何 JSON 外文本。
必须修复未闭合字符串、未闭合对象、非法换行、非法转义、尾随逗号等问题。
如果原文缺少某些字段，请用空字符串、false 或合理默认值补齐。

JSON schema:
{
  "rootCause": "",
  "technicalDetail": "",
  "impactScope": "",
  "calibratedSeverity": "P3",
  "severityReason": "",
  "confidence": 0.7,
  "fix": {
    "description": "",
    "codeExample": "",
    "filePath": "",
    "lineRange": ""
  },
  "estimatedEffort": "",
  "preventionAdvice": "",
  "isKnownIssue": false
}

原回复：
{{originalResponse}}', '系统全局错误分析JSON修复模板', 'ACTIVE', 'system', 'system'),
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
3. 评估风险级别和影响范围，区分“单条 SQL 优化问题”和“系统性资源瓶颈”。
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
4. 给出可执行的处理动作，区分“立刻止血”和“后续治理”。

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
4. 如果问题本质是“日志缺失导致无法追踪业务”，必须明确指出风险等级。

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
