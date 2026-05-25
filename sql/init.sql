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
    feishu_notify_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '仓库级飞书通知开关: 0-关闭 1-开启',
    feishu_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级飞书Webhook地址（为空则用全局默认）',
    dingtalk_notify_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '仓库级钉钉通知开关: 0-关闭 1-开启',
    dingtalk_notify_webhook VARCHAR(512) DEFAULT NULL COMMENT '仓库级钉钉Webhook地址（为空则用全局默认）',
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
    processing_status VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED' COMMENT '处理状态: RECEIVED/PARSED/AGGREGATED/ANALYZING/ANALYZED/AI_DEGRADED/NOTIFY_FAILED/IGNORED/FALSE_POSITIVE',
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

-- Error type recognition rule table
CREATE TABLE IF NOT EXISTS argus_error_type_rule (
    id            BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
    rule_name     VARCHAR(100)    NOT NULL COMMENT 'Rule name',
    error_type    VARCHAR(50)     NOT NULL COMMENT 'Resolved standard error type',
    match_field   VARCHAR(30)     NOT NULL COMMENT 'Match field: ANY/EXCEPTION_CLASS/CLASS_NAME/STACK_TRACE/MESSAGE/HTTP_STATUS',
    match_mode    VARCHAR(20)     NOT NULL COMMENT 'Match mode: EXACT/CONTAINS/REGEX/RANGE',
    pattern       VARCHAR(500)    NOT NULL COMMENT 'Match pattern',
    priority      INT             NOT NULL DEFAULT 100 COMMENT 'Lower value has higher priority',
    enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Whether enabled',
    builtin       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Whether builtin seed rule',
    remark        VARCHAR(500)    DEFAULT NULL COMMENT 'Remark',
    create_by     VARCHAR(64)     DEFAULT NULL COMMENT 'Created by',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_by     VARCHAR(64)     DEFAULT NULL COMMENT 'Updated by',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    UNIQUE KEY uk_rule (match_field, match_mode, pattern(191), error_type),
    INDEX idx_enabled_priority (enabled, priority),
    INDEX idx_error_type (error_type),
    INDEX idx_match_field (match_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错误类型识别规则表';

INSERT IGNORE INTO argus_error_type_rule
    (rule_name, error_type, match_field, match_mode, pattern, priority, enabled, builtin, remark)
VALUES
    ('Null pointer exception', 'NULL_POINTER', 'EXCEPTION_CLASS', 'EXACT', 'NullPointerException', 10, 1, 1, 'Standard NPE exception class'),
    ('JDK17 null pointer message', 'NULL_POINTER', 'MESSAGE', 'REGEX', 'Cannot invoke.*because.*is null', 30, 1, 1, 'JDK17 enhanced NPE message'),
    ('Class cast exception', 'CLASS_CAST', 'EXCEPTION_CLASS', 'EXACT', 'ClassCastException', 10, 1, 1, 'Class cast exception'),
    ('Index out of bounds exception', 'INDEX_OUT_OF_BOUNDS', 'EXCEPTION_CLASS', 'REGEX', '(IndexOutOfBoundsException|ArrayIndexOutOfBoundsException|StringIndexOutOfBoundsException)', 10, 1, 1, 'Collection, array, or string index out of bounds'),
    ('IO exception', 'IO_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(IOException|FileNotFoundException|EOFException)', 10, 1, 1, 'File and network IO exceptions'),
    ('Timeout exception', 'TIMEOUT', 'EXCEPTION_CLASS', 'REGEX', '(TimeoutException|ReadTimeoutException|ConnectTimeoutException|SocketTimeoutException)', 10, 1, 1, 'Timeout related exceptions'),
    ('Connection refused exception', 'CONNECTION_REFUSED', 'EXCEPTION_CLASS', 'REGEX', '(ConnectException|NoRouteToHostException)', 10, 1, 1, 'Connection refused or network unreachable'),
    ('Database exception', 'SQL_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(SQLException|DataAccessException|DataIntegrityViolationException|DuplicateKeyException|MysqlDataTruncation|BadSqlGrammarException|MyBatisSystemException|PersistenceException)', 10, 1, 1, 'JDBC, Spring DAO, MyBatis, and MySQL exceptions'),
    ('Business exception', 'BIZ_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(BizException|BusinessException|ServiceException)', 10, 1, 1, 'Business exception base classes'),
    ('HTTP request exception', 'HTTP_ERROR', 'EXCEPTION_CLASS', ß'REGEX', '(HttpClientErrorException|HttpServerErrorException|FeignException|NoResourceFoundException|ResponseStatusException|HttpMessageNotReadableException|HttpRequestMethodNotSupportedException)', 10, 1, 1, 'HTTP client, server, and Spring Web request exceptions'),
    ('Message queue exception', 'MQ_ERROR', 'EXCEPTION_CLASS', 'REGEX', '(JMSException|AmqpException|KafkaException|RocketMQException)', 10, 1, 1, 'MQ related exceptions'),
    ('Serialization exception', 'SERIALIZATION_ERROR', 'EXCEPTION_CLASS', 'REGEX', '(JsonProcessingException|JsonParseException|JsonMappingException|NotSerializableException|SerializationException)', 10, 1, 1, 'JSON and object serialization exceptions'),
    ('Nginx 502', 'NGINX_502', 'HTTP_STATUS', 'EXACT', '502', 10, 1, 1, 'Bad Gateway'),
    ('Nginx 503', 'NGINX_503', 'HTTP_STATUS', 'EXACT', '503', 10, 1, 1, 'Service Unavailable'),
    ('Nginx 504', 'NGINX_504', 'HTTP_STATUS', 'EXACT', '504', 10, 1, 1, 'Gateway Timeout'),
    ('Nginx 499', 'NGINX_499', 'HTTP_STATUS', 'EXACT', '499', 10, 1, 1, 'Client Closed Request'),
    ('Nginx 5xx', 'NGINX_5XX', 'HTTP_STATUS', 'RANGE', '500-599', 50, 1, 1, 'Other gateway 5xx status'),
    ('Nginx 4xx', 'NGINX_4XX', 'HTTP_STATUS', 'RANGE', '400-499', 60, 1, 1, 'Other gateway 4xx status');

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

-- 错误分析任务表
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
    INDEX idx_scm_project (scm_provider, scm_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用-SCM项目映射表';

-- 应用级数据监控总配置表（Phase 3）
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
    default_runtime_collect_interval_seconds      INT NOT NULL DEFAULT 30 COMMENT '默认数据库运行态采集间隔秒数',
    default_pool_metric_push_interval_seconds      INT NOT NULL DEFAULT 30 COMMENT '默认连接池指标推送间隔秒数',
    default_log_quality_check_interval_seconds     INT NOT NULL DEFAULT 300 COMMENT '默认接口日志质量巡检间隔秒数',
    alert_scan_interval_seconds                    INT NOT NULL DEFAULT 60 COMMENT '告警扫描间隔秒数',
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

-- 应用级业务库只读数据源配置表（Phase 3）
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
    runtime_collect_interval_seconds INT          NOT NULL DEFAULT 30 COMMENT '数据库运行态采集间隔秒数',
    pool_metric_push_interval_seconds INT         NOT NULL DEFAULT 30 COMMENT '连接池指标期望推送间隔秒数',
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

-- 数据库运行指标快照表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_db_metric_snapshot (
    id                       BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id            BIGINT          NOT NULL COMMENT '数据源ID',
    app_name                 VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment              VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境标识',
    threads_connected        INT             DEFAULT NULL COMMENT '当前连接数',
    threads_running          INT             DEFAULT NULL COMMENT '活跃线程数',
    max_connections          INT             DEFAULT NULL COMMENT '最大连接数',
    questions                BIGINT          DEFAULT 0 COMMENT 'Questions计数',
    com_select               BIGINT          DEFAULT 0 COMMENT 'Select计数',
    com_insert               BIGINT          DEFAULT 0 COMMENT 'Insert计数',
    com_update               BIGINT          DEFAULT 0 COMMENT 'Update计数',
    com_delete               BIGINT          DEFAULT 0 COMMENT 'Delete计数',
    qps                      DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '计算后的QPS',
    slow_queries             BIGINT          DEFAULT 0 COMMENT '慢查询累计数',
    innodb_trx_count         INT             DEFAULT 0 COMMENT 'InnoDB活跃事务数',
    innodb_lock_wait_count   INT             DEFAULT 0 COMMENT 'InnoDB锁等待数',
    collected_at             DATETIME        NOT NULL COMMENT '采集时间',
    create_by                VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_app_time (app_name, environment, collected_at),
    CONSTRAINT fk_db_metric_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库运行指标快照表';

-- 当前执行 SQL 快照表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_db_process_snapshot (
    id                 BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id      BIGINT          NOT NULL COMMENT '数据源ID',
    mysql_process_id   BIGINT          DEFAULT NULL COMMENT 'MySQL线程ID',
    user_name          VARCHAR(100)    DEFAULT NULL COMMENT '执行用户',
    host_info          VARCHAR(200)    DEFAULT NULL COMMENT '来源host',
    database_name      VARCHAR(200)    DEFAULT NULL COMMENT '数据库名',
    command_type       VARCHAR(50)     DEFAULT NULL COMMENT 'Command',
    process_state      VARCHAR(200)    DEFAULT NULL COMMENT 'State',
    duration_seconds   INT             DEFAULT NULL COMMENT '已执行秒数',
    sql_fingerprint    VARCHAR(64)     DEFAULT NULL COMMENT 'SQL指纹',
    sql_text           LONGTEXT        DEFAULT NULL COMMENT '完整SQL',
    sql_text_masked    LONGTEXT        DEFAULT NULL COMMENT '脱敏SQL',
    risk_type          VARCHAR(50)     DEFAULT NULL COMMENT '风险类型: LONG_SQL/LOCKED/METADATA_LOCK/LONG_TRX',
    risk_level         VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    collected_at       DATETIME        NOT NULL COMMENT '采集时间',
    create_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_fingerprint (sql_fingerprint),
    INDEX idx_risk (risk_type, risk_level),
    CONSTRAINT fk_db_process_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='当前执行SQL快照表';

-- 数据库锁等待与阻塞事件表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_db_lock_event (
    id                    BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id         BIGINT          NOT NULL COMMENT '数据源ID',
    app_name              VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment           VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    waiting_trx_id        VARCHAR(100)    DEFAULT NULL COMMENT '等待事务ID',
    blocking_trx_id       VARCHAR(100)    DEFAULT NULL COMMENT '阻塞事务ID',
    waiting_process_id    BIGINT          DEFAULT NULL COMMENT '等待线程',
    blocking_process_id   BIGINT          DEFAULT NULL COMMENT '阻塞线程',
    lock_table            VARCHAR(300)    DEFAULT NULL COMMENT '锁表',
    lock_index            VARCHAR(200)    DEFAULT NULL COMMENT '锁索引',
    lock_type             VARCHAR(50)     DEFAULT NULL COMMENT '锁类型',
    wait_seconds          INT             DEFAULT NULL COMMENT '等待时长',
    waiting_sql           LONGTEXT        DEFAULT NULL COMMENT '等待SQL',
    blocking_sql          LONGTEXT        DEFAULT NULL COMMENT '阻塞SQL',
    event_fingerprint     VARCHAR(64)     DEFAULT NULL COMMENT '事件指纹',
    risk_level            VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    status                VARCHAR(30)     NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/ANALYZED/IGNORED/RESOLVED',
    occurred_at           DATETIME        NOT NULL COMMENT '发生时间',
    create_by             VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by             VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_datasource_time (datasource_id, occurred_at),
    INDEX idx_app_time (app_name, environment, occurred_at),
    INDEX idx_waiting_process (waiting_process_id),
    INDEX idx_blocking_process (blocking_process_id),
    INDEX idx_fingerprint (event_fingerprint),
    INDEX idx_status (status, risk_level),
    CONSTRAINT fk_db_lock_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库锁等待与阻塞事件表';

-- 连接池指标快照表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_connection_pool_snapshot (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    datasource_id              BIGINT          DEFAULT NULL COMMENT '数据源ID',
    app_name                   VARCHAR(100)    NOT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    datasource_name            VARCHAR(100)    NOT NULL COMMENT '数据源名称',
    pool_type                  VARCHAR(30)     NOT NULL COMMENT '连接池类型: HIKARI/DRUID',
    active_connections         INT             NOT NULL DEFAULT 0 COMMENT '活跃连接',
    idle_connections           INT             NOT NULL DEFAULT 0 COMMENT '空闲连接',
    max_connections            INT             NOT NULL DEFAULT 0 COMMENT '最大连接',
    waiting_threads            INT             NOT NULL DEFAULT 0 COMMENT '等待线程',
    connection_acquire_avg_ms  BIGINT          NOT NULL DEFAULT 0 COMMENT '平均获取耗时',
    connection_acquire_max_ms  BIGINT          NOT NULL DEFAULT 0 COMMENT '最大获取耗时',
    timeout_count              BIGINT          NOT NULL DEFAULT 0 COMMENT '超时次数',
    error_count                BIGINT          NOT NULL DEFAULT 0 COMMENT '错误次数',
    risk_type                  VARCHAR(50)     DEFAULT NULL COMMENT '风险类型',
    risk_level                 VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    risk_reason                VARCHAR(2000)   DEFAULT NULL COMMENT '风险原因分析',
    collected_at               DATETIME        NOT NULL COMMENT '采集时间',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_monitor_time (monitor_config_id, collected_at),
    INDEX idx_datasource_time (datasource_id, collected_at),
    INDEX idx_app_time (app_name, environment, collected_at),
    INDEX idx_risk (risk_type, risk_level),
    CONSTRAINT fk_pool_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id),
    CONSTRAINT fk_pool_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='连接池指标快照表';

-- MySQL slow log 接入配置表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_slow_log_config (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id       BIGINT          NOT NULL COMMENT '数据源ID',
    enabled             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用',
    source_type         VARCHAR(30)     NOT NULL DEFAULT 'FILE_TAIL' COMMENT '来源类型: FILE_TAIL/PUSH/TABLE',
    log_path            VARCHAR(1000)   DEFAULT NULL COMMENT 'slow log文件路径',
    charset             VARCHAR(50)     NOT NULL DEFAULT 'UTF-8' COMMENT '字符集',
    min_query_time_ms   BIGINT          NOT NULL DEFAULT 1000 COMMENT '最小采集耗时',
    collect_full_sql    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否采集完整SQL',
    collect_interval_seconds INT        NOT NULL DEFAULT 60 COMMENT 'slow log采集间隔秒数',
    cursor_offset       BIGINT          NOT NULL DEFAULT 0 COMMENT '文件读取位点',
    last_collected_at   DATETIME        DEFAULT NULL COMMENT '最近采集时间',
    create_by           VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_datasource (datasource_id),
    INDEX idx_enabled (enabled),
    CONSTRAINT fk_slow_log_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MySQL slow log接入配置表';

-- 慢 SQL 事件表（Phase 3）
CREATE TABLE IF NOT EXISTS argus_slow_sql_event (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id              BIGINT          NOT NULL COMMENT '数据源ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    source_type                VARCHAR(30)     NOT NULL COMMENT '来源类型: PROCESSLIST/SLOW_LOG/MANUAL',
    idempotent_key             VARCHAR(100)    NOT NULL COMMENT '推送或采集幂等键',
    sql_fingerprint            VARCHAR(64)     DEFAULT NULL COMMENT 'SQL指纹',
    sql_text                   LONGTEXT        DEFAULT NULL COMMENT '完整SQL',
    sql_text_masked            LONGTEXT        DEFAULT NULL COMMENT '脱敏SQL',
    duration_ms                BIGINT          DEFAULT NULL COMMENT '执行耗时',
    lock_time_ms               BIGINT          DEFAULT NULL COMMENT '锁等待耗时',
    rows_sent                  BIGINT          DEFAULT NULL COMMENT '返回行数',
    rows_examined              BIGINT          DEFAULT NULL COMMENT '扫描行数',
    process_state              VARCHAR(200)    DEFAULT NULL COMMENT '执行状态',
    explain_json               JSON            DEFAULT NULL COMMENT 'Explain结果',
    table_info_json            JSON            DEFAULT NULL COMMENT '表信息',
    index_info_json            JSON            DEFAULT NULL COMMENT '索引信息',
    related_lock_event_id      BIGINT          DEFAULT NULL COMMENT '关联锁事件ID',
    related_pool_snapshot_id   BIGINT          DEFAULT NULL COMMENT '关联连接池快照ID',
    cause_type                 VARCHAR(50)     DEFAULT NULL COMMENT '根因类型',
    risk_level                 VARCHAR(10)     DEFAULT NULL COMMENT '风险等级',
    analysis_status            VARCHAR(30)     NOT NULL DEFAULT 'PENDING' COMMENT '分析状态',
    root_cause                 TEXT            DEFAULT NULL COMMENT '根因结论',
    optimization_suggestion    TEXT            DEFAULT NULL COMMENT '优化建议',
    index_suggestion_sql       TEXT            DEFAULT NULL COMMENT '索引建议SQL，仅展示',
    need_dba                   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否需要DBA',
    need_developer             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否需要开发',
    confidence                 DECIMAL(3,2)    DEFAULT NULL COMMENT '置信度',
    occurred_at                DATETIME        NOT NULL COMMENT '发生时间',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_idempotent_key (idempotent_key),
    INDEX idx_app_time (app_name, environment, occurred_at),
    INDEX idx_datasource_time (datasource_id, occurred_at),
    INDEX idx_fingerprint (sql_fingerprint),
    INDEX idx_cause (cause_type),
    INDEX idx_risk (risk_level, analysis_status),
    CONSTRAINT fk_slow_sql_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id),
    CONSTRAINT fk_slow_sql_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢SQL事件表';

CREATE TABLE IF NOT EXISTS argus_interface_log_table_config (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    project_mapping_id         BIGINT          NOT NULL COMMENT '应用映射ID',
    datasource_id              BIGINT          NOT NULL COMMENT '数据源ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    config_name                VARCHAR(200)    DEFAULT NULL COMMENT '配置名称',
    table_name                 VARCHAR(200)    NOT NULL COMMENT '日志表名',
    primary_key_column         VARCHAR(100)    NOT NULL COMMENT '主键字段',
    interface_code_column      VARCHAR(100)    NOT NULL COMMENT '接口编码字段',
    request_time_column        VARCHAR(100)    NOT NULL COMMENT '请求时间字段',
    response_time_column       VARCHAR(100)    NOT NULL COMMENT '响应时间字段',
    response_body_column       VARCHAR(100)    NOT NULL COMMENT '响应体字段',
    status_code_column         VARCHAR(100)    DEFAULT NULL COMMENT '状态码字段',
    request_id_column          VARCHAR(100)    DEFAULT NULL COMMENT '请求ID字段',
    trace_id_column            VARCHAR(100)    DEFAULT NULL COMMENT 'traceId字段',
    scan_mode                  VARCHAR(30)     NOT NULL DEFAULT 'ID_INCREMENT' COMMENT '扫描模式',
    quality_check_interval_seconds INT          NOT NULL DEFAULT 300 COMMENT '接口日志质量巡检间隔秒数',
    last_scan_value            VARCHAR(200)    DEFAULT NULL COMMENT '最近扫描位点',
    quality_rules              JSON            DEFAULT NULL COMMENT '质量规则',
    alert_rules                JSON            DEFAULT NULL COMMENT '告警规则',
    enabled                    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_datasource_table (datasource_id, table_name),
    INDEX idx_monitor_config (monitor_config_id),
    INDEX idx_project_mapping (project_mapping_id),
    INDEX idx_enabled (enabled),
    CONSTRAINT fk_log_table_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id),
    CONSTRAINT fk_log_table_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口日志表质量巡检配置表';

CREATE TABLE IF NOT EXISTS argus_log_quality_check_result (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    log_table_config_id        BIGINT          NOT NULL COMMENT '日志表配置ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    datasource_id              BIGINT          DEFAULT NULL COMMENT '数据源ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    table_name                 VARCHAR(200)    NOT NULL COMMENT '日志表名',
    check_window_start         DATETIME        DEFAULT NULL COMMENT '巡检窗口开始',
    check_window_end           DATETIME        DEFAULT NULL COMMENT '巡检窗口结束',
    total_count                BIGINT          NOT NULL DEFAULT 0 COMMENT '巡检记录数',
    issue_count                BIGINT          NOT NULL DEFAULT 0 COMMENT '问题数',
    quality_score              INT             DEFAULT NULL COMMENT '质量评分',
    quality_level              VARCHAR(2)      DEFAULT NULL COMMENT '质量等级',
    completeness_score         INT             DEFAULT NULL COMMENT '完整性得分',
    timeliness_score           INT             DEFAULT NULL COMMENT '及时性得分',
    uniqueness_score           INT             DEFAULT NULL COMMENT '唯一性得分',
    validity_score             INT             DEFAULT NULL COMMENT '合法性得分',
    consistency_score          INT             DEFAULT NULL COMMENT '一致性得分',
    growth_risk_score          INT             DEFAULT NULL COMMENT '增长风险得分',
    status                     VARCHAR(30)     NOT NULL DEFAULT 'DONE' COMMENT '状态',
    error_message              VARCHAR(1000)   DEFAULT NULL COMMENT '失败原因',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_config_time (log_table_config_id, create_time),
    INDEX idx_app_table (app_name, environment, table_name),
    INDEX idx_level (quality_level, status),
    CONSTRAINT fk_quality_result_log_table FOREIGN KEY (log_table_config_id) REFERENCES argus_interface_log_table_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志质量巡检结果表';

CREATE TABLE IF NOT EXISTS argus_log_quality_issue (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    check_result_id            BIGINT          NOT NULL COMMENT '巡检结果ID',
    log_table_config_id        BIGINT          NOT NULL COMMENT '日志表配置ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    table_name                 VARCHAR(200)    NOT NULL COMMENT '日志表名',
    interface_code             VARCHAR(200)    DEFAULT NULL COMMENT '接口编码',
    issue_type                 VARCHAR(50)     NOT NULL COMMENT '问题类型',
    severity                   VARCHAR(10)     NOT NULL COMMENT '严重等级',
    issue_count                BIGINT          NOT NULL DEFAULT 0 COMMENT '问题数量',
    sample_record_id           VARCHAR(200)    DEFAULT NULL COMMENT '样本记录ID',
    sample_payload             JSON            DEFAULT NULL COMMENT '样本摘要',
    description                TEXT            DEFAULT NULL COMMENT '问题描述',
    suggestion                 TEXT            DEFAULT NULL COMMENT '修复建议',
    status                     VARCHAR(30)     NOT NULL DEFAULT 'NEW' COMMENT '状态',
    occurred_at                DATETIME        NOT NULL COMMENT '发生时间',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_result (check_result_id),
    INDEX idx_config_type (log_table_config_id, issue_type),
    INDEX idx_app_time (app_name, environment, occurred_at),
    INDEX idx_status (status, severity),
    CONSTRAINT fk_quality_issue_result FOREIGN KEY (check_result_id) REFERENCES argus_log_quality_check_result(id),
    CONSTRAINT fk_quality_issue_log_table FOREIGN KEY (log_table_config_id) REFERENCES argus_interface_log_table_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志质量问题明细表';

CREATE TABLE IF NOT EXISTS argus_data_monitor_report (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    monitor_config_id          BIGINT          NOT NULL COMMENT '应用监控配置ID',
    app_name                   VARCHAR(100)    DEFAULT NULL COMMENT '应用名称',
    environment                VARCHAR(50)     NOT NULL DEFAULT 'PROD' COMMENT '环境',
    report_type                VARCHAR(30)     NOT NULL COMMENT '报告类型',
    report_date                DATE            NOT NULL COMMENT '报告日期',
    health_score               INT             DEFAULT NULL COMMENT '健康评分',
    slow_sql_count             INT             NOT NULL DEFAULT 0 COMMENT '慢SQL数量',
    lock_event_count           INT             NOT NULL DEFAULT 0 COMMENT '锁等待数量',
    pool_risk_count            INT             NOT NULL DEFAULT 0 COMMENT '连接池风险数量',
    log_quality_issue_count    INT             NOT NULL DEFAULT 0 COMMENT '日志质量问题数量',
    summary                    TEXT            DEFAULT NULL COMMENT '摘要',
    detail_json                JSON            DEFAULT NULL COMMENT '报告详情',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_monitor_date (monitor_config_id, report_type, report_date),
    INDEX idx_app_date (app_name, environment, report_date),
    CONSTRAINT fk_data_monitor_report_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据监控报告表';

CREATE TABLE IF NOT EXISTS argus_slow_sql_action_log (
    id                         BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    slow_sql_event_id          BIGINT          NOT NULL COMMENT '慢SQL事件ID',
    action_type                VARCHAR(30)     NOT NULL COMMENT '操作类型',
    operator                   VARCHAR(64)     NOT NULL COMMENT '操作人',
    reason                     TEXT            DEFAULT NULL COMMENT '原因',
    before_status              VARCHAR(30)     DEFAULT NULL COMMENT '操作前状态',
    after_status               VARCHAR(30)     DEFAULT NULL COMMENT '操作后状态',
    detail_json                JSON            DEFAULT NULL COMMENT '操作详情',
    create_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)     DEFAULT NULL COMMENT '修改人',
    update_time                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_slow_sql_event (slow_sql_event_id),
    INDEX idx_action (action_type),
    CONSTRAINT fk_slow_sql_action_event FOREIGN KEY (slow_sql_event_id) REFERENCES argus_slow_sql_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢SQL人工处理日志表';

-- 通知记录表
CREATE TABLE IF NOT EXISTS argus_notification_record (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    type            VARCHAR(30)     NOT NULL COMMENT '类型: REVIEW/ERROR_ALERT/REPORT',
    channel         VARCHAR(20)     NOT NULL DEFAULT 'WECHAT' COMMENT '渠道',
    ref_id          BIGINT          DEFAULT NULL COMMENT '关联业务ID',
    ref_type        VARCHAR(30)     DEFAULT NULL COMMENT '关联类型',
    content_summary VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED/SKIPPED',
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
