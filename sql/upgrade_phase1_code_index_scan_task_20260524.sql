-- ============================================================
-- Argus Phase 1 源码索引扫描任务升级脚本
-- 说明：
-- 1. 本脚本面向已有环境补齐源码索引扫描任务表。
-- 2. 扫描任务表只记录一次扫描尝试，不参与源码定位主查询。
-- 3. 强制重建运行中或失败时，不覆盖旧成功源码索引。
-- 4. 与 init_code_index.sql 中 argus_code_index_scan_task 保持同构。
-- ============================================================

CREATE TABLE IF NOT EXISTS argus_code_index_scan_task (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_no               VARCHAR(64)  NOT NULL COMMENT '扫描任务编号',
    scm_config_id         BIGINT       NOT NULL COMMENT 'SCM 配置ID',
    scm_provider          VARCHAR(32)  NOT NULL COMMENT 'SCM 平台：GITLAB/GITHUB/GITEE',
    scm_project_id        VARCHAR(128) DEFAULT NULL COMMENT 'SCM 项目标识',
    repo_name             VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    branch_name           VARCHAR(128) NOT NULL COMMENT '扫描目标分支',
    commit_sha            VARCHAR(64)  DEFAULT NULL COMMENT '扫描目标 commit SHA，可为空表示执行阶段解析分支最新提交',
    scan_type             VARCHAR(32)  NOT NULL COMMENT '扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD',
    trigger_type          VARCHAR(32)  NOT NULL COMMENT '触发类型：MANUAL/WEBHOOK/DEPLOY_CALLBACK/SCHEDULED',
    force_rebuild         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否强制重建：0-否 1-是',
    task_status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED/REUSED',
    scan_stage            VARCHAR(32)  NOT NULL DEFAULT 'WAITING' COMMENT '扫描阶段：WAITING/SCM_READING/MODULE_SCANNING/SOURCE_ROOT_DISCOVERING/JAVA_PARSING/INDEX_AGGREGATING/INDEX_PERSISTING/COMPLETED/FAILED',
    progress_percent      INT          NOT NULL DEFAULT 0 COMMENT '扫描进度百分比，取值 0-100',
    stage_message         VARCHAR(512) DEFAULT NULL COMMENT '当前阶段说明',
    loaded_file_count     INT          NOT NULL DEFAULT 0 COMMENT '已物化或读取的文件数量',
    total_java_file_count INT          NOT NULL DEFAULT 0 COMMENT 'Java 文件总数',
    parsed_file_count     INT          NOT NULL DEFAULT 0 COMMENT '已解析 Java 文件数量',
    failed_file_count     INT          NOT NULL DEFAULT 0 COMMENT '解析失败文件数量',
    class_count           INT          NOT NULL DEFAULT 0 COMMENT '解析出的类型数量',
    package_count         INT          NOT NULL DEFAULT 0 COMMENT '解析出的包数量',
    warning_count         INT          NOT NULL DEFAULT 0 COMMENT '扫描告警数量',
    result_index_id       BIGINT       DEFAULT NULL COMMENT '扫描成功后关联的仓库源码索引ID',
    reused_index_id       BIGINT       DEFAULT NULL COMMENT '普通刷新复用的已有成功索引ID',
    latest_error_message  VARCHAR(1024) DEFAULT NULL COMMENT '最近失败原因',
    requested_by          VARCHAR(64)  DEFAULT NULL COMMENT '触发人',
    reason                VARCHAR(255) DEFAULT NULL COMMENT '触发原因',
    started_at            DATETIME     DEFAULT NULL COMMENT '任务开始时间',
    finished_at           DATETIME     DEFAULT NULL COMMENT '任务完成时间',
    last_heartbeat_at     DATETIME     DEFAULT NULL COMMENT '最近进度更新时间',
    is_deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除：0-否 1-是',
    version               INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by             VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by             VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_scan_task_scm_status (scm_config_id, branch_name, task_status, create_time),
    INDEX idx_scan_task_commit (scm_config_id, commit_sha, scan_type),
    INDEX idx_scan_task_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='源码索引扫描任务表';
