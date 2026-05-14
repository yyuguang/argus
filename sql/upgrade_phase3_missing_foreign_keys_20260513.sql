-- Phase 3: 补齐历史库缺失的外键约束
-- 适用场景：历史库已执行 Phase 3 建表升级脚本，但部分表缺少 init.sql 中定义的外键。

SET @db_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_interface_log_table_config ADD CONSTRAINT fk_log_table_monitor_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id)',
        'SELECT ''fk_log_table_monitor_config already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_interface_log_table_config'
      AND constraint_name = 'fk_log_table_monitor_config'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_interface_log_table_config ADD CONSTRAINT fk_log_table_datasource FOREIGN KEY (datasource_id) REFERENCES argus_data_source_config(id)',
        'SELECT ''fk_log_table_datasource already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_interface_log_table_config'
      AND constraint_name = 'fk_log_table_datasource'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_log_quality_check_result ADD CONSTRAINT fk_quality_result_log_table FOREIGN KEY (log_table_config_id) REFERENCES argus_interface_log_table_config(id)',
        'SELECT ''fk_quality_result_log_table already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_log_quality_check_result'
      AND constraint_name = 'fk_quality_result_log_table'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_log_quality_issue ADD CONSTRAINT fk_quality_issue_result FOREIGN KEY (check_result_id) REFERENCES argus_log_quality_check_result(id)',
        'SELECT ''fk_quality_issue_result already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_log_quality_issue'
      AND constraint_name = 'fk_quality_issue_result'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_log_quality_issue ADD CONSTRAINT fk_quality_issue_log_table FOREIGN KEY (log_table_config_id) REFERENCES argus_interface_log_table_config(id)',
        'SELECT ''fk_quality_issue_log_table already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_log_quality_issue'
      AND constraint_name = 'fk_quality_issue_log_table'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_data_monitor_report ADD CONSTRAINT fk_data_monitor_report_config FOREIGN KEY (monitor_config_id) REFERENCES argus_data_monitor_config(id)',
        'SELECT ''fk_data_monitor_report_config already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_data_monitor_report'
      AND constraint_name = 'fk_data_monitor_report_config'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE argus_slow_sql_action_log ADD CONSTRAINT fk_slow_sql_action_event FOREIGN KEY (slow_sql_event_id) REFERENCES argus_slow_sql_event(id)',
        'SELECT ''fk_slow_sql_action_event already exists'' AS message'
    )
    FROM information_schema.referential_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'argus_slow_sql_action_log'
      AND constraint_name = 'fk_slow_sql_action_event'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
