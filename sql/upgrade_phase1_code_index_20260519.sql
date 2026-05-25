-- ============================================================
-- Argus Phase 1 自动源码索引升级脚本
-- 说明：
-- 1. 本脚本面向已有环境补齐 Phase 1 自动源码索引相关表。
-- 2. 与 init_code_index.sql 保持同构，便于新旧环境一致。
-- 3. 本脚本不删除旧 sourceRoot/basePackage/moduleSourceRoots/packageModuleMappings 字段。
-- 4. 默认只保存结构化索引元数据，不保存完整源码内容。
-- ============================================================

-- Section 1：仓库源码索引主表
CREATE TABLE IF NOT EXISTS argus_code_repository_index (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scm_config_id           BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    scm_provider            VARCHAR(32)  NOT NULL COMMENT 'SCM 平台：GITLAB/GITHUB/GITEE',
    scm_project_id          VARCHAR(128) DEFAULT NULL COMMENT 'SCM 项目标识',
    repo_owner              VARCHAR(128) DEFAULT NULL COMMENT '仓库 owner 或 group',
    repo_name               VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    branch_name             VARCHAR(128) NOT NULL COMMENT '索引分支名称',
    commit_sha              VARCHAR(64)  NOT NULL COMMENT '索引对应 commit SHA',
    base_commit_sha         VARCHAR(64)  DEFAULT NULL COMMENT '增量扫描基线 commit SHA',
    index_version           INT          NOT NULL DEFAULT 1 COMMENT '索引结构版本',
    scan_type               VARCHAR(32)  NOT NULL COMMENT '扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD',
    trigger_type            VARCHAR(32)  NOT NULL COMMENT '触发类型：FIRST_INIT/WEBHOOK/MANUAL/DEPLOY_CALLBACK/SCHEDULED',
    scan_status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '扫描状态：PENDING/RUNNING/SUCCESS/FAILED',
    module_count            INT          NOT NULL DEFAULT 0 COMMENT '模块数量',
    source_root_count       INT          NOT NULL DEFAULT 0 COMMENT '源码根数量',
    java_file_count         INT          NOT NULL DEFAULT 0 COMMENT 'Java 文件数量',
    class_count             INT          NOT NULL DEFAULT 0 COMMENT '类型数量',
    package_count           INT          NOT NULL DEFAULT 0 COMMENT '包数量',
    ambiguous_package_count INT          NOT NULL DEFAULT 0 COMMENT '歧义包数量',
    warning_count           INT          NOT NULL DEFAULT 0 COMMENT '扫描告警数量',
    confidence              VARCHAR(16)  NOT NULL DEFAULT 'LOW' COMMENT '整体置信度：HIGH/MEDIUM/LOW',
    stale                   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已过期：0-否 1-是',
    latest_error_message    VARCHAR(1024) DEFAULT NULL COMMENT '最近失败原因',
    started_at              DATETIME     DEFAULT NULL COMMENT '扫描开始时间',
    finished_at             DATETIME     DEFAULT NULL COMMENT '扫描完成时间',
    is_deleted              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version                 INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by               VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_code_repo_index_commit (scm_config_id, commit_sha, index_version),
    INDEX idx_code_repo_index_latest (scm_config_id, branch_name, scan_status, finished_at),
    INDEX idx_code_repo_index_status (scan_status, create_time),
    INDEX idx_code_repo_index_stale (scm_config_id, stale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库源码索引主表';

-- Section 2：模块索引表
CREATE TABLE IF NOT EXISTS argus_code_module_index (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    index_id           BIGINT       NOT NULL COMMENT '仓库源码索引ID',
    scm_config_id      BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    module_name        VARCHAR(128) NOT NULL COMMENT '模块名称，优先使用 Maven artifactId',
    module_path        VARCHAR(512) NOT NULL COMMENT '模块相对仓库路径',
    parent_module_path VARCHAR(512) DEFAULT NULL COMMENT '父模块相对仓库路径',
    build_type         VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '构建类型：MAVEN/DISCOVERED/UNKNOWN',
    packaging          VARCHAR(32)  DEFAULT NULL COMMENT 'Maven packaging，如 jar/war/pom',
    source_roots       JSON         DEFAULT NULL COMMENT '源码根列表 JSON',
    java_file_count    INT          NOT NULL DEFAULT 0 COMMENT 'Java 文件数量',
    class_count        INT          NOT NULL DEFAULT 0 COMMENT '类型数量',
    scan_status        VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS' COMMENT '模块扫描状态：SUCCESS/PARTIAL/FAILED',
    warning_message    VARCHAR(1024) DEFAULT NULL COMMENT '模块扫描告警',
    is_deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by          VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_code_module_index_path (index_id, module_path),
    INDEX idx_code_module_index_scm (scm_config_id, module_name),
    INDEX idx_code_module_index_status (index_id, scan_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块源码索引表';

-- Section 3：Java 类型索引表
CREATE TABLE IF NOT EXISTS argus_code_class_index (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    index_id       BIGINT       NOT NULL COMMENT '仓库源码索引ID',
    scm_config_id  BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    module_path    VARCHAR(512) NOT NULL COMMENT '模块相对仓库路径',
    source_root    VARCHAR(512) NOT NULL COMMENT '源码根路径',
    file_path      VARCHAR(1024) NOT NULL COMMENT 'SCM 仓库相对文件路径',
    file_sha       VARCHAR(64)  DEFAULT NULL COMMENT '文件内容 hash',
    package_name   VARCHAR(255) DEFAULT NULL COMMENT 'Java package 名称',
    class_name     VARCHAR(255) NOT NULL COMMENT '简单类型名',
    qualified_name VARCHAR(512) NOT NULL COMMENT 'Java 全限定名',
    class_kind     VARCHAR(32)  NOT NULL DEFAULT 'CLASS' COMMENT '类型种类：CLASS/INTERFACE/ENUM/ANNOTATION/RECORD',
    primary_type   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否主类型：0-否 1-是',
    line_start     INT          DEFAULT NULL COMMENT '类型起始行',
    line_end       INT          DEFAULT NULL COMMENT '类型结束行',
    imports_json   JSON         DEFAULT NULL COMMENT 'import 列表 JSON',
    parser_status  VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS' COMMENT '解析状态：SUCCESS/PARTIAL/FAILED',
    confidence     VARCHAR(16)  NOT NULL DEFAULT 'HIGH' COMMENT '索引置信度：HIGH/MEDIUM/LOW',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_code_class_index_fqn (index_id, qualified_name, file_path(250)),
    INDEX idx_code_class_index_qn (index_id, qualified_name),
    INDEX idx_code_class_index_simple (index_id, class_name),
    INDEX idx_code_class_index_package (index_id, package_name),
    INDEX idx_code_class_index_file (index_id, file_path(250)),
    INDEX idx_code_class_index_scm (scm_config_id, qualified_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Java 类型源码索引表';

-- Section 4：Java 包索引表
CREATE TABLE IF NOT EXISTS argus_code_package_index (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    index_id            BIGINT       NOT NULL COMMENT '仓库源码索引ID',
    scm_config_id       BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    package_name        VARCHAR(255) NOT NULL COMMENT 'Java package 名称',
    module_paths        JSON         DEFAULT NULL COMMENT 'package 分布的模块路径 JSON',
    primary_module_path VARCHAR(512) DEFAULT NULL COMMENT '自动推断的主模块路径',
    class_count         INT          NOT NULL DEFAULT 0 COMMENT '该包下类型数量',
    ambiguous           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否存在多模块歧义：0-否 1-是',
    confidence          VARCHAR(16)  NOT NULL DEFAULT 'LOW' COMMENT '推断置信度：HIGH/MEDIUM/LOW',
    is_deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by           VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_code_package_index_name (index_id, package_name),
    INDEX idx_code_package_index_ambiguous (index_id, ambiguous),
    INDEX idx_code_package_index_scm (scm_config_id, package_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Java 包源码索引表';

-- Section 5：应用版本绑定表
CREATE TABLE IF NOT EXISTS argus_app_version_binding (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    mapping_id     BIGINT       DEFAULT NULL COMMENT 'ProjectMapping 映射ID',
    app_name       VARCHAR(128) NOT NULL COMMENT '应用名称',
    environment    VARCHAR(32)  NOT NULL COMMENT '运行环境，如 dev/test/prod',
    scm_config_id  BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    branch_name    VARCHAR(128) NOT NULL COMMENT '部署分支名称',
    commit_sha     VARCHAR(64)  NOT NULL COMMENT '部署 commit SHA',
    version_name   VARCHAR(128) DEFAULT NULL COMMENT '业务版本号或发布单号',
    index_id       BIGINT       DEFAULT NULL COMMENT '对应源码索引ID，可为空表示索引构建中',
    binding_source VARCHAR(32)  NOT NULL COMMENT '绑定来源：DEPLOY_CALLBACK/APP_REPORT/MANUAL/DEFAULT_BRANCH',
    active         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否当前激活版本：0-否 1-是',
    activated_at   DATETIME     DEFAULT NULL COMMENT '生效时间',
    last_seen_at   DATETIME     DEFAULT NULL COMMENT '最近一次从运行期事件看到该版本的时间',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_app_version_binding_commit (app_name, environment, scm_config_id, commit_sha),
    INDEX idx_app_version_binding_active (app_name, environment, scm_config_id, active),
    INDEX idx_app_version_binding_index (index_id),
    INDEX idx_app_version_binding_mapping (mapping_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用环境源码版本绑定表';
