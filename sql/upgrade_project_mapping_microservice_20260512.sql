-- 2026-05-12
-- 支持同一个 SCM 仓库绑定多个微服务 appName。
-- 原 uk_scm_project 会限制 (scm_provider, scm_project_id) 只能出现一次，
-- 导致 monorepo / 微服务仓库无法配置多个服务映射。

SET @db_name = DATABASE();

SET @drop_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE argus_project_mapping DROP INDEX uk_scm_project',
        'SELECT ''uk_scm_project not exists'' AS message'
    )
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'argus_project_mapping'
      AND index_name = 'uk_scm_project'
);
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @create_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_project_mapping ADD INDEX idx_scm_project (scm_provider, scm_project_id)',
        'SELECT ''idx_scm_project already exists'' AS message'
    )
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'argus_project_mapping'
      AND index_name = 'idx_scm_project'
);
PREPARE stmt FROM @create_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
