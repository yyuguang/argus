-- ============================================================
-- Argus 数据库初始化脚本
-- AI 代码质量与接口监控平台
-- ============================================================

-- 评审任务表
CREATE TABLE IF NOT EXISTS argus_review_task (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scm_provider    VARCHAR(20)     NOT NULL COMMENT 'SCM平台: gitlab/github/gitee',
    scm_config_id   BIGINT          DEFAULT NULL COMMENT 'SCM配置ID',
    project_id      BIGINT          NOT NULL COMMENT '仓库/项目ID',
    project_name    VARCHAR(100)    NOT NULL COMMENT '仓库/项目名称',
    repo_owner      VARCHAR(100)    DEFAULT NULL COMMENT '仓库归属 owner/group',
    repo_name       VARCHAR(100)    DEFAULT NULL COMMENT '仓库名称',
    mr_iid          BIGINT          NOT NULL COMMENT 'PR/MR编号',
    mr_title        VARCHAR(500)    NOT NULL COMMENT 'PR/MR标题',
    mr_url          VARCHAR(500)    NOT NULL COMMENT 'PR/MR链接',
    author_id       VARCHAR(128)    DEFAULT NULL COMMENT '提交者唯一ID',
    author_name     VARCHAR(100)    NOT NULL COMMENT '提交者',
    source_branch   VARCHAR(200)    NOT NULL COMMENT '源分支',
    target_branch   VARCHAR(200)    NOT NULL COMMENT '目标分支',
    last_commit_sha VARCHAR(64)     DEFAULT NULL COMMENT '最后提交SHA',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/DONE/FAILED/TIMEOUT',
    total_score     INT             DEFAULT NULL COMMENT '总评分(0-100)',
    score_level     VARCHAR(2)      DEFAULT NULL COMMENT '评分等级: A/B/C/D/F',
    file_count      INT             DEFAULT 0 COMMENT '变更文件数',
    added_lines     INT             DEFAULT 0 COMMENT '新增行数',
    removed_lines   INT             DEFAULT 0 COMMENT '删除行数',
    critical_count  INT             DEFAULT 0 COMMENT '致命问题数',
    major_count     INT             DEFAULT 0 COMMENT '严重问题数',
    minor_count     INT             DEFAULT 0 COMMENT '建议问题数',
    tokens_used     INT             DEFAULT 0 COMMENT '消耗Token数',
    duration        BIGINT          DEFAULT NULL COMMENT '评审耗时(ms)',
    error_message   TEXT            DEFAULT NULL COMMENT '失败原因',
    summary         TEXT            DEFAULT NULL COMMENT '评审总结',
    scm_comment_id  BIGINT          DEFAULT NULL COMMENT 'SCM评论ID',
    notified        TINYINT(1)      DEFAULT 0 COMMENT '是否已通知: 0-否 1-是',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_scm_project_mr_commit (scm_provider, project_id, mr_iid, last_commit_sha),
    INDEX idx_scm_provider (scm_provider),
    INDEX idx_scm_config_id (scm_config_id),
    INDEX idx_status (status),
    INDEX idx_project (project_name),
    INDEX idx_author_id (author_id),
    INDEX idx_author (author_name),
    INDEX idx_created (create_time),
    INDEX idx_score (total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评审任务表';

-- 提交者画像表
CREATE TABLE IF NOT EXISTS argus_reviewer_profile (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    author_name     VARCHAR(128)    NOT NULL COMMENT '提交者（SCM账号）',
    author_id       VARCHAR(128)    NOT NULL COMMENT '唯一ID {scmProvider}:{username}',
    scm_provider    VARCHAR(32)     NOT NULL COMMENT 'SCM平台 github/gitlab/gitee',
    total_reviews   INT             NOT NULL DEFAULT 0 COMMENT '累计评审次数',
    avg_score       DECIMAL(5,2)    DEFAULT NULL COMMENT '历史平均分',
    dimension_stats JSON            DEFAULT NULL COMMENT '五维度平均分 JSON',
    top_issue_tags  JSON            DEFAULT NULL COMMENT '高频问题标签 Top5 JSON',
    top_issue_rules JSON            DEFAULT NULL COMMENT '高频违规规则 Top5 JSON',
    score_trend     JSON            DEFAULT NULL COMMENT '近 N 次分数趋势 JSON',
    recent_reviews  JSON            DEFAULT NULL COMMENT '近 N 次评审摘要 JSON',
    first_review_at DATETIME        DEFAULT NULL COMMENT '首次评审时间',
    last_review_at  DATETIME        DEFAULT NULL COMMENT '最近评审时间',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_author_scm (author_name, scm_provider),
    INDEX idx_author_id (author_id),
    INDEX idx_avg_score (avg_score),
    INDEX idx_last_review_at (last_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交者代码质量画像（MySQL统计层）';

-- SCM 仓库配置表
CREATE TABLE IF NOT EXISTS argus_scm_config (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scm_provider    VARCHAR(20)     NOT NULL COMMENT 'SCM平台: gitlab/github/gitee',
    project_id      BIGINT          DEFAULT NULL COMMENT '仓库/项目ID',
    project_name    VARCHAR(100)    DEFAULT NULL COMMENT '仓库/项目名称',
    repo_owner      VARCHAR(100)    DEFAULT NULL COMMENT '仓库归属 owner/group',
    repo_name       VARCHAR(100)    DEFAULT NULL COMMENT '仓库名称',
    api_base_url    VARCHAR(255)    DEFAULT NULL COMMENT 'API基础地址',
    web_base_url    VARCHAR(255)    DEFAULT NULL COMMENT 'Web基础地址',
    access_token    VARCHAR(500)    DEFAULT NULL COMMENT '访问令牌',
    webhook_secret  VARCHAR(500)    DEFAULT NULL COMMENT 'Webhook密钥',
    base_packages   TEXT            DEFAULT NULL COMMENT '基础包前缀列表(JSON数组)',
    module_source_roots TEXT        DEFAULT NULL COMMENT '模块源码根列表(JSON数组)',
    package_module_mappings TEXT    DEFAULT NULL COMMENT '包前缀到源码根映射(JSON数组)',
    max_related_classes INT         NOT NULL DEFAULT 5 COMMENT '单文件最大关联类数',
    max_context_tokens INT          NOT NULL DEFAULT 16000 COMMENT '单文件最大上下文Token',
    review_parallelism INT          NOT NULL DEFAULT 3 COMMENT '评审并发度',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用: 0-否 1-是',
    wechat_notify_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '仓库级企微通知开关: 0-关闭 1-开启',
    wechat_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级企微Webhook地址（为空则用全局默认）',
    review_config   JSON            DEFAULT NULL COMMENT '仓库级评审配置JSON（ReviewConfig结构）',
    description     VARCHAR(500)    DEFAULT NULL COMMENT '配置说明',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_provider_project (scm_provider, project_id),
    UNIQUE KEY uk_provider_repo (scm_provider, repo_owner, repo_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SCM仓库配置表';

-- 评审评分详情表
CREATE TABLE IF NOT EXISTS argus_review_score (
    id                    BIGINT  PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id               BIGINT  NOT NULL COMMENT '评审任务ID',
    compliance_score      INT     DEFAULT 0 COMMENT '规范合规得分',
    compliance_weight     INT     DEFAULT 30 COMMENT '规范合规权重',
    correctness_score     INT     DEFAULT 0 COMMENT '逻辑正确得分',
    correctness_weight    INT     DEFAULT 25 COMMENT '逻辑正确权重',
    data_safety_score     INT     DEFAULT 0 COMMENT '数据完整得分',
    data_safety_weight    INT     DEFAULT 20 COMMENT '数据完整权重',
    performance_score     INT     DEFAULT 0 COMMENT '性能风险得分',
    performance_weight    INT     DEFAULT 15 COMMENT '性能风险权重',
    maintainability_score INT     DEFAULT 0 COMMENT '可维护性得分',
    maintainability_weight INT    DEFAULT 10 COMMENT '可维护性权重',
    create_by             VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by             VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    update_time           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_task (task_id),
    CONSTRAINT fk_score_task FOREIGN KEY (task_id) REFERENCES argus_review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评审评分详情表';

-- 评审问题表
CREATE TABLE IF NOT EXISTS argus_review_issue (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id         BIGINT          NOT NULL COMMENT '评审任务ID',
    file_path       VARCHAR(500)    NOT NULL COMMENT '文件路径',
    start_line      INT             DEFAULT NULL COMMENT '问题起始行',
    end_line        INT             DEFAULT NULL COMMENT '问题结束行',
    severity        VARCHAR(20)     NOT NULL COMMENT '严重度: CRITICAL/MAJOR/MINOR/SUGGESTION',
    category        VARCHAR(30)     NOT NULL COMMENT '分类: COMPLIANCE/CORRECTNESS/DATA_SAFETY/PERFORMANCE/MAINTAINABILITY',
    description     TEXT            NOT NULL COMMENT '问题描述',
    suggestion      TEXT            DEFAULT NULL COMMENT '修复建议',
    code_snippet    TEXT            DEFAULT NULL COMMENT '问题代码片段',
    rule            VARCHAR(200)    DEFAULT NULL COMMENT '违反的规则',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_task (task_id),
    INDEX idx_severity (severity),
    INDEX idx_category (category),
    CONSTRAINT fk_issue_task FOREIGN KEY (task_id) REFERENCES argus_review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评审问题表';

-- 错误事件表
CREATE TABLE IF NOT EXISTS argus_error_event (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    log_id            VARCHAR(100)    NOT NULL COMMENT 'Agent生成的日志ID(幂等去重)',
    app_name          VARCHAR(100)    NOT NULL COMMENT '应用名称',
    error_type        VARCHAR(50)     NOT NULL COMMENT '错误类型枚举',
    error_message     TEXT            NOT NULL COMMENT '错误消息',
    severity          VARCHAR(10)     NOT NULL DEFAULT 'P3' COMMENT '严重度: P0/P1/P2/P3',
    error_fingerprint VARCHAR(64)     NOT NULL COMMENT '错误指纹',
    class_name        VARCHAR(300)    DEFAULT NULL COMMENT '出错类全限定名',
    method_name       VARCHAR(100)    DEFAULT NULL COMMENT '出错方法名',
    line_number       INT             DEFAULT NULL COMMENT '出错行号',
    file_path         VARCHAR(500)    DEFAULT NULL COMMENT '映射的文件路径',
    business_key      VARCHAR(200)    DEFAULT NULL COMMENT '业务主键',
    interface_ref     VARCHAR(200)    DEFAULT NULL COMMENT '关联接口',
    trace_id          VARCHAR(100)    DEFAULT NULL COMMENT '追踪ID',
    raw_stack_trace   TEXT            DEFAULT NULL COMMENT '原始异常栈',
    context_logs      JSON            DEFAULT NULL COMMENT '上下文日志',
    request_info      JSON            DEFAULT NULL COMMENT '请求信息',
    analyzed          TINYINT(1)      DEFAULT 0 COMMENT '是否已分析: 0-否 1-是',
    notified          TINYINT(1)      DEFAULT 0 COMMENT '是否已通知: 0-否 1-是',
    source_log_id     BIGINT          DEFAULT NULL COMMENT '来源日志表ID',
    environment       VARCHAR(20)     DEFAULT NULL COMMENT '环境标识',
    host_name         VARCHAR(100)    DEFAULT NULL COMMENT '主机名',
    occurrence_count  INT             NOT NULL DEFAULT 1 COMMENT '聚合同类错误累计次数',
    first_occurred_at DATETIME        DEFAULT NULL COMMENT '首次发生时间',
    last_occurred_at  DATETIME        DEFAULT NULL COMMENT '最近发生时间',
    last_business_key VARCHAR(200)    DEFAULT NULL COMMENT '最近一次业务主键',
    last_trace_id     VARCHAR(100)    DEFAULT NULL COMMENT '最近一次 traceId',
    processing_status VARCHAR(20)     NOT NULL DEFAULT 'NEW' COMMENT '处理状态: NEW/PROCESSING/DONE/IGNORED',
    analysis_decision VARCHAR(30)     DEFAULT NULL COMMENT '分析决策: MUST_ANALYZE/CONDITIONAL_ANALYZE/AGGREGATE_ONLY/IGNORE',
    initial_severity  VARCHAR(10)     DEFAULT NULL COMMENT '规则初判严重度',
    final_severity    VARCHAR(10)     DEFAULT NULL COMMENT 'AI/人工校准严重度',
    severity_source   VARCHAR(20)     NOT NULL DEFAULT 'RULE' COMMENT '严重度来源: RULE/AI_CALIBRATED/MANUAL',
    severity_reason   VARCHAR(500)    DEFAULT NULL COMMENT '严重度判定原因',
    severity_confidence DECIMAL(3,2)  DEFAULT NULL COMMENT '严重度置信度',
    escalation_count  INT             NOT NULL DEFAULT 0 COMMENT '升级次数',
    last_escalation_reason VARCHAR(500) DEFAULT NULL COMMENT '最近升级原因',
    owner_team        VARCHAR(100)    DEFAULT NULL COMMENT '归属团队',
    source_type       VARCHAR(30)     DEFAULT 'APP_LOG' COMMENT '来源类型: APP_LOG/NGINX_ACCESS/NGINX_ERROR',
    occurred_at       DATETIME        NOT NULL COMMENT '错误发生时间',
    create_by         VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_app_log_id (app_name, log_id),
    INDEX idx_app (app_name),
    INDEX idx_severity (severity),
    INDEX idx_error_type (error_type),
    INDEX idx_fingerprint (error_fingerprint),
    INDEX idx_occurred (occurred_at),
    INDEX idx_analyzed (analyzed),
    INDEX idx_processing_status (processing_status),
    INDEX idx_source_type (source_type),
    INDEX idx_last_occurred (last_occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错误事件表';

-- 错误分析结果表
CREATE TABLE IF NOT EXISTS argus_error_analysis (
    id               BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    error_event_id   BIGINT          NOT NULL COMMENT '错误事件ID',
    root_cause       TEXT            NOT NULL COMMENT '根本原因',
    technical_detail TEXT            DEFAULT NULL COMMENT '技术细节',
    impact_scope     TEXT            DEFAULT NULL COMMENT '影响范围',
    final_severity   VARCHAR(10)     NOT NULL COMMENT 'AI校准后严重度',
    fix_description  TEXT            DEFAULT NULL COMMENT '修复描述',
    fix_code_example TEXT            DEFAULT NULL COMMENT '修复代码示例',
    fix_file_path    VARCHAR(500)    DEFAULT NULL COMMENT '需修改的文件',
    fix_line_range   VARCHAR(20)     DEFAULT NULL COMMENT '修改行范围',
    estimated_effort VARCHAR(20)     DEFAULT NULL COMMENT '预估工作量',
    prevention_advice TEXT           DEFAULT NULL COMMENT '预防建议',
    confidence       DECIMAL(3,2)    DEFAULT NULL COMMENT 'AI置信度(0-1)',
    tokens_used      INT             DEFAULT 0 COMMENT '消耗Token数',
    duration         BIGINT          DEFAULT NULL COMMENT '分析耗时(ms)',
    ai_model         VARCHAR(50)     DEFAULT NULL COMMENT '使用的模型',
    source           VARCHAR(20)     DEFAULT 'AI' COMMENT '分析来源: AI/MANUAL/HYBRID',
    manual_conclusion TEXT           DEFAULT NULL COMMENT '人工补充结论',
    create_by        VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_event (error_event_id),
    CONSTRAINT fk_analysis_event FOREIGN KEY (error_event_id) REFERENCES argus_error_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错误分析结果表';

-- 项目映射表
CREATE TABLE IF NOT EXISTS argus_project_mapping (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    app_name          VARCHAR(100)    NOT NULL COMMENT '应用名称',
    scm_provider      VARCHAR(20)     NOT NULL DEFAULT 'gitlab' COMMENT 'SCM平台: gitlab/github/gitee',
    scm_project_id    BIGINT          NOT NULL COMMENT '仓库/项目ID',
    source_root       VARCHAR(200)    DEFAULT 'src/main/java' COMMENT '源码根目录',
    base_package      VARCHAR(200)    NOT NULL COMMENT '基础包名',
    default_branch    VARCHAR(100)    DEFAULT 'master' COMMENT '默认分支',
    create_by         VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_app (app_name),
    UNIQUE KEY uk_scm_project (scm_provider, scm_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用-SCM项目映射表';

-- 通知记录表
CREATE TABLE IF NOT EXISTS argus_notification_record (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    type            VARCHAR(30)     NOT NULL COMMENT '类型: REVIEW/ERROR_ALERT/REPORT',
    channel         VARCHAR(20)     NOT NULL DEFAULT 'WECHAT' COMMENT '渠道',
    ref_id          BIGINT          DEFAULT NULL COMMENT '关联业务ID',
    ref_type        VARCHAR(30)     DEFAULT NULL COMMENT '关联类型',
    content_summary VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED',
    error_message   VARCHAR(500)    DEFAULT NULL COMMENT '失败原因',
    retry_count     INT             DEFAULT 0 COMMENT '重试次数',
    sent_at         DATETIME        DEFAULT NULL COMMENT '发送时间',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_type (type),
    INDEX idx_ref (ref_type, ref_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知记录表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS argus_system_config (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    config_key      VARCHAR(100)    NOT NULL COMMENT '配置键',
    config_value    TEXT            NOT NULL COMMENT '配置值(JSON)',
    description     VARCHAR(200)    DEFAULT NULL COMMENT '配置说明',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- Agent 实例表
CREATE TABLE IF NOT EXISTS argus_agent_instance (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    agent_id          VARCHAR(100)    NOT NULL COMMENT 'Agent 实例标识',
    app_name          VARCHAR(100)    NOT NULL COMMENT '所属应用名称',
    host              VARCHAR(100)    DEFAULT NULL COMMENT '主机名',
    ip                VARCHAR(50)     DEFAULT NULL COMMENT 'IP 地址',
    environment       VARCHAR(20)     DEFAULT NULL COMMENT '部署环境: dev/test/prod',
    agent_version     VARCHAR(32)     DEFAULT NULL COMMENT 'Agent 版本号',
    log_sources       VARCHAR(100)    DEFAULT 'APP_LOG' COMMENT '采集日志源: APP_LOG/NGINX_ACCESS/NGINX_ERROR,逗号分隔',
    last_heartbeat_at DATETIME        NOT NULL COMMENT '最近心跳时间',
    status            VARCHAR(20)     NOT NULL DEFAULT 'ONLINE' COMMENT '状态: ONLINE/OFFLINE/DEGRADED',
    create_by         VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_agent_id (agent_id),
    INDEX idx_status (status),
    INDEX idx_heartbeat (last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent实例表';

-- Agent 推送批次记录表
CREATE TABLE IF NOT EXISTS argus_agent_push_batch (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id        VARCHAR(64)     NOT NULL COMMENT '批次标识(UUID)',
    agent_id        VARCHAR(100)    NOT NULL COMMENT 'Agent 实例标识',
    entry_count     INT             NOT NULL DEFAULT 0 COMMENT '推送条目总数',
    accepted_count  INT             NOT NULL DEFAULT 0 COMMENT '接受数',
    duplicated_count INT            NOT NULL DEFAULT 0 COMMENT '重复数',
    error_count     INT             NOT NULL DEFAULT 0 COMMENT '异常数',
    status          VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED' COMMENT '状态: RECEIVED/PROCESSING/DONE',
    received_at     DATETIME        NOT NULL COMMENT '接收时间',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_batch_id (batch_id),
    INDEX idx_agent (agent_id),
    INDEX idx_received (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent推送批次记录表';

-- 错误上下文日志快照表
CREATE TABLE IF NOT EXISTS argus_error_context_log (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    error_event_id  BIGINT          NOT NULL COMMENT '错误事件ID',
    log_time        DATETIME        NOT NULL COMMENT '日志时间',
    log_level       VARCHAR(10)     DEFAULT NULL COMMENT '日志级别',
    logger_name     VARCHAR(300)    DEFAULT NULL COMMENT 'Logger 名称',
    thread_name     VARCHAR(100)    DEFAULT NULL COMMENT '线程名',
    trace_id        VARCHAR(100)    DEFAULT NULL COMMENT '追踪ID',
    message         TEXT            NOT NULL COMMENT '日志内容',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    create_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_event (error_event_id),
    INDEX idx_trace (trace_id),
    INDEX idx_log_time (log_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错误上下文日志快照表';

-- 知识条目表（M8）
CREATE TABLE IF NOT EXISTS argus_knowledge_entry (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    error_fingerprint VARCHAR(64)     DEFAULT NULL COMMENT '错误指纹',
    error_type        VARCHAR(50)     NOT NULL COMMENT '错误类型',
    app_name          VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    title             VARCHAR(300)    NOT NULL COMMENT '知识标题',
    error_pattern     TEXT            NOT NULL COMMENT '错误模式描述',
    root_cause        TEXT            NOT NULL COMMENT '根因',
    fix_suggestion    TEXT            DEFAULT NULL COMMENT '修复建议',
    prevention_advice TEXT            DEFAULT NULL COMMENT '预防建议',
    source_event_id   BIGINT          DEFAULT NULL COMMENT '来源 ErrorEvent ID',
    source_analysis_id BIGINT         DEFAULT NULL COMMENT '来源 ErrorAnalysis ID',
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/CONFIRMED/FALSE_POSITIVE/OUTDATED/WHITELIST',
    source            VARCHAR(20)     NOT NULL DEFAULT 'AUTO' COMMENT '来源: AUTO/MANUAL',
    confirmed_by      VARCHAR(64)     DEFAULT NULL COMMENT '确认人',
    confirmed_at      DATETIME        DEFAULT NULL COMMENT '确认时间',
    occurrence_count  INT             DEFAULT 1 COMMENT '关联发生次数',
    last_occurred_at  DATETIME        DEFAULT NULL COMMENT '最近发生时间',
    tags              JSON            DEFAULT NULL COMMENT '标签(JSON数组)',
    create_by         VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_fingerprint (error_fingerprint),
    INDEX idx_error_type (error_type),
    INDEX idx_app (app_name),
    INDEX idx_status (status),
    INDEX idx_source_event (source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识条目表';

-- 知识操作留痕表（M8-A04）
CREATE TABLE IF NOT EXISTS argus_knowledge_audit (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    knowledge_entry_id BIGINT         NOT NULL COMMENT '知识条目ID',
    action            VARCHAR(30)     NOT NULL COMMENT '操作: CONFIRM/MARK_FALSE_POSITIVE/IGNORE/UPDATE/DELETE/PROMOTE_WHITELIST/DEMOTE_WHITELIST',
    operator          VARCHAR(64)     NOT NULL COMMENT '操作人',
    comment           TEXT            DEFAULT NULL COMMENT '备注',
    before_status     VARCHAR(20)     DEFAULT NULL COMMENT '操作前状态',
    after_status      VARCHAR(20)     DEFAULT NULL COMMENT '操作后状态',
    create_by         VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_knowledge (knowledge_entry_id),
    INDEX idx_action (action),
    INDEX idx_operator (operator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识操作留痕表';
