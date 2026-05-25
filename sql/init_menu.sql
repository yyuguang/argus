-- 文档信息
-- 文档名称：init_menu.sql
-- 当前状态：generated
-- 最近更新阶段：system-architect / task-planner
-- 最近更新原因：2026-05-17 19:55:41 按菜单拆分方案重生成完整初始化脚本，拆分为代码评审 / 错误治理 / 应用治理 / 监控中心 / 规则治理

-- 说明：
-- 1. 本脚本基于本地数据库快照并叠加最新菜单拆分方案生成。
-- 2. 覆盖表：argus_sys_menu / argus_sys_menu_permission / argus_sys_role_menu / argus_sys_role_menu_permission。
-- 3. 本脚本不会初始化角色表，仅按当前 role_id 写入菜单授权关系。
-- 4. 当前数据库中存在临时角色和软删除角色的授权关系，已按原样保留。
-- 5. 历史库中 4303 / 4304 的临时 disabled 权限不会作为目标态保留。
-- 6. AUTO_INCREMENT 按 `max(id)+1` 生成，避免后续插入撞号。

-- 角色快照：
-- role_id=1 | role_code=SUPER_ADMIN | role_name=超级管理员 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=2 | role_code=PLATFORM_ADMIN | role_name=平台管理员 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=3 | role_code=QUALITY_ADMIN | role_name=质量管理员 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=4 | role_code=DBA | role_name=数据库负责人 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=5 | role_code=TECH_LEAD | role_name=技术负责人 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=6 | role_code=VIEWER | role_name=只读用户 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=7 | role_code=AUDITOR | role_name=审计员 | role_type=SYSTEM | status=ENABLED | is_deleted=0
-- role_id=8 | role_code=TMP_ROLE_UI_20260517 | role_name=前端联调临时角色 | role_type=CUSTOM | status=ENABLED | is_deleted=0
-- role_id=9 | role_code=UI_ROLE_845514 | role_name=UI角色845514 | role_type=CUSTOM | status=ENABLED | is_deleted=0
-- role_id=10 | role_code=UI_ROLE_210654 | role_name=UI角色210654 | role_type=CUSTOM | status=ENABLED | is_deleted=1
-- role_id=11 | role_code=P4_QUALITY_002707 | role_name=P4质量管理员002707 | role_type=CUSTOM | status=ENABLED | is_deleted=1
-- role_id=12 | role_code=P4_READONLY_002707 | role_name=P4权限只读002707 | role_type=CUSTOM | status=ENABLED | is_deleted=1

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- 清理旧数据
DELETE FROM `argus_sys_role_menu_permission`;
DELETE FROM `argus_sys_role_menu`;
DELETE FROM `argus_sys_menu_permission`;
DELETE FROM `argus_sys_menu`;

-- argus_sys_menu（23 条）
INSERT INTO `argus_sys_menu` (`id`, `parent_id`, `menu_type`, `route_path`, `route_name`, `component_path`, `redirect_path`, `title`, `icon`, `active_menu`, `hidden`, `always_show`, `no_cache`, `breadcrumb`, `affix`, `no_tags_view`, `can_to`, `status`, `sort_order`, `is_deleted`, `version`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (1000, NULL, 'DIRECTORY', '/dashboard', 'Dashboard', '#', '/dashboard/analysis', '首页', 'vi-ant-design:dashboard-filled', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4300, NULL, 'DIRECTORY', '/code-review', 'CodeReview', '#', '/code-review/scm-config', '代码评审', 'vi-ep:reading', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 30, 0, 0, 'system', '2026-05-17 16:44:24', 'system', '2026-05-17 19:55:41'),
  (4311, NULL, 'DIRECTORY', '/error-governance', 'ErrorGovernance', '#', '/error-governance/errors', '错误治理', 'vi-ep:warning-filled', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 35, 0, 0, 'system', '2026-05-17 19:52:45', 'system', '2026-05-17 19:55:41'),
  (4312, NULL, 'DIRECTORY', '/application-governance', 'ApplicationGovernance', '#', '/application-governance/app-linkage', '应用治理', 'vi-ep:connection', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:52:45', 'system', '2026-05-17 19:55:41'),
  (4313, NULL, 'DIRECTORY', '/monitor-center', 'MonitorCenter', '#', '/monitor-center/data-monitor', '监控中心', 'vi-ep:monitor', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 45, 0, 0, 'system', '2026-05-17 19:52:45', 'system', '2026-05-17 19:55:41'),
  (4314, NULL, 'DIRECTORY', '/rule-governance', 'RuleGovernance', '#', '/rule-governance/rule-documents', '规则治理', 'vi-ep:management', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 50, 0, 0, 'system', '2026-05-17 19:52:45', 'system', '2026-05-18 12:00:00'),
  (4200, NULL, 'DIRECTORY', '/authorization', 'Authorization', '#', '/authorization/user', '权限管理', 'vi-eos-icons:role-binding', NULL, 0, 1, 0, 1, 0, 0, 0, 'ENABLED', 90, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (1001, 1000, 'MENU', 'analysis', 'Analysis', 'views/Dashboard/Analysis', NULL, '分析页', NULL, NULL, 0, 0, 1, 1, 1, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (1002, 1000, 'MENU', 'workplace', 'Workplace', 'views/Dashboard/Workplace', NULL, '工作台', NULL, NULL, 0, 0, 1, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4201, 4200, 'MENU', 'department', 'Department', 'views/Authorization/Department/Department', NULL, '部门管理', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4202, 4200, 'MENU', 'user', 'User', 'views/Authorization/User/User', NULL, '用户管理', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4203, 4200, 'MENU', 'role', 'Role', 'views/Authorization/Role/Role', NULL, '角色管理', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 30, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4204, 4200, 'MENU', 'menu', 'Menu', 'views/Authorization/Menu/Menu', NULL, '菜单管理', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 40, 0, 0, 'system', '2026-05-16 23:54:15', 'system', '2026-05-16 23:54:15'),
  (4301, 4300, 'MENU', 'scm-config', 'QualityScmConfig', 'views/Quality/ScmConfig/ScmConfig', NULL, 'SCM 配置', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-17 16:44:24', 'system', '2026-05-17 19:55:41'),
  (4302, 4300, 'MENU', 'review-task', 'QualityReviewTask', 'views/Quality/ReviewTask/ReviewTask', NULL, '评审任务', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-17 16:44:24', 'system', '2026-05-17 19:55:41'),
  (4303, 4300, 'MENU', 'knowledge-base', 'QualityKnowledgeBase', 'views/Quality/KnowledgeBase/KnowledgeBase', NULL, '知识库管理', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 30, 0, 0, 'system', '2026-05-17 16:44:24', 'system', '2026-05-17 19:55:41'),
  (4304, 4300, 'MENU', 'personal-quality', 'QualityPersonalQuality', 'views/Quality/PersonalQuality/PersonalQuality', NULL, '个人代码质量', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 40, 0, 0, 'system', '2026-05-17 16:57:11', 'system', '2026-05-17 19:55:41'),
  (4307, 4311, 'MENU', 'errors', 'QualityErrors', 'views/Quality/ErrorDiagnosis/ErrorDiagnosis', NULL, '错误诊断', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-17 18:14:44', 'system', '2026-05-17 19:55:41'),
  (4308, 4311, 'MENU', 'error-type-rules', 'QualityErrorTypeRules', 'views/Quality/ErrorTypeRule/ErrorTypeRule', NULL, '错误类型规则', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-17 18:14:44', 'system', '2026-05-17 19:55:41'),
  (4309, 4312, 'MENU', 'app-linkage', 'QualityAppLinkage', 'views/Quality/AppLinkage/AppLinkage', NULL, '应用联动配置', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-17 18:14:44', 'system', '2026-05-17 19:55:41'),
  (4305, 4313, 'MENU', 'data-monitor', 'QualityDataMonitor', 'views/Quality/DataMonitor/DataMonitor', NULL, '数据监控', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-17 18:01:52', 'system', '2026-05-17 19:55:41'),
  (4306, 4313, 'MENU', 'data-monitor-config', 'QualityDataMonitorConfig', 'views/Quality/DataMonitorConfig/DataMonitorConfig', NULL, '数据监控配置', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-17 18:01:52', 'system', '2026-05-17 19:55:41'),
  (4310, 4314, 'MENU', 'rule-documents', 'QualityRuleDocuments', 'views/Quality/RuleGovernance/RuleDocuments/RuleDocuments', NULL, '规范文档', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:52:45', 'system', '2026-05-18 12:00:00'),
  (4315, 4314, 'MENU', 'prompt-templates', 'QualityPromptTemplates', 'views/Quality/RuleGovernance/PromptTemplates/PromptTemplates', NULL, '提示词模板', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 20, 0, 0, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00'),
  (4316, 4314, 'MENU', 'scoring-policies', 'QualityScoringPolicies', 'views/Quality/RuleGovernance/ScoringPolicies/ScoringPolicies', NULL, '评分策略', NULL, NULL, 0, 0, 0, 1, 0, 0, 0, 'ENABLED', 30, 0, 0, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00');

ALTER TABLE `argus_sys_menu` AUTO_INCREMENT = 4317;

-- argus_sys_menu_permission（63 条）
INSERT INTO `argus_sys_menu_permission` (`id`, `menu_id`, `label`, `action_value`, `permission_code`, `status`, `sort_order`, `is_deleted`, `version`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (51001, 4301, '查看', 'view', 'quality:scm-config:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51002, 4301, '新增', 'create', 'quality:scm-config:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51003, 4301, '编辑', 'update', 'quality:scm-config:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51101, 4302, '查看', 'view', 'quality:review-task:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51201, 4303, '查看', 'view', 'quality:knowledge-base:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51202, 4303, '确认', 'confirm', 'quality:knowledge-base:confirm', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51203, 4303, '标记误报', 'false-positive', 'quality:knowledge-base:false-positive', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51204, 4303, '忽略', 'ignore', 'quality:knowledge-base:ignore', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51205, 4303, '提升白名单', 'promote-whitelist', 'quality:knowledge-base:promote-whitelist', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51206, 4303, '降级白名单', 'demote-whitelist', 'quality:knowledge-base:demote-whitelist', 'ENABLED', 60, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51301, 4304, '查看', 'view', 'quality:personal-quality:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51401, 4305, '查看', 'view', 'quality:data-monitor:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51402, 4305, '确认', 'confirm', 'quality:data-monitor:confirm', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51403, 4305, '忽略', 'ignore', 'quality:data-monitor:ignore', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51501, 4306, '查看', 'view', 'quality:data-monitor-config:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51502, 4306, '新增', 'create', 'quality:data-monitor-config:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51503, 4306, '编辑', 'update', 'quality:data-monitor-config:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51504, 4306, '测试连接', 'test', 'quality:data-monitor-config:test', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51505, 4306, '删除', 'delete', 'quality:data-monitor-config:delete', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 21:46:00', 'system', '2026-05-17 21:46:00'),
  (51601, 4307, '查看', 'view', 'quality:error:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51602, 4307, '重试分析', 'retry-analysis', 'quality:error:retry-analysis', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51603, 4307, '重发通知', 'resend-notify', 'quality:error:resend-notify', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51604, 4307, '忽略', 'ignore', 'quality:error:ignore', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51605, 4307, '标记误报', 'false-positive', 'quality:error:false-positive', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51606, 4307, '人工调级', 'severity-update', 'quality:error:severity:update', 'ENABLED', 60, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51607, 4307, '人工结论', 'manual-conclusion', 'quality:error:manual-conclusion', 'ENABLED', 70, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51701, 4308, '查看', 'view', 'quality:error-type-rule:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51702, 4308, '新增', 'create', 'quality:error-type-rule:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51703, 4308, '编辑', 'update', 'quality:error-type-rule:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51704, 4308, '删除', 'delete', 'quality:error-type-rule:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51801, 4309, '查看', 'view', 'quality:app-linkage:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51802, 4309, '新增', 'create', 'quality:app-linkage:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51803, 4309, '编辑', 'update', 'quality:app-linkage:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51804, 4309, '删除', 'delete', 'quality:app-linkage:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51805, 4309, '测试', 'test', 'quality:app-linkage:test', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51901, 4310, '查看', 'view', 'quality:rule-management:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51902, 4310, '导入', 'import', 'quality:rule-management:import', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51903, 4310, '启用', 'activate', 'quality:rule-management:activate', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51904, 4310, '停用', 'disable', 'quality:rule-management:disable', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51905, 4310, '分块重建', 'reindex', 'quality:rule-management:reindex', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00'),
  (51906, 4315, '更新模板', 'prompt-update', 'quality:rule-management:prompt-update', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00'),
  (51907, 4316, '更新评分', 'scoring-update', 'quality:rule-management:scoring-update', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00');

ALTER TABLE `argus_sys_menu_permission` AUTO_INCREMENT = 51908;

-- 补齐权限管理模块按钮权限，避免全量初始化后授权页面操作栏为空
INSERT INTO `argus_sys_menu_permission` (`id`, `menu_id`, `label`, `action_value`, `permission_code`, `status`, `sort_order`, `is_deleted`, `version`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (50001, 4201, '查看', 'view', 'system:security:department:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50002, 4201, '新增', 'create', 'system:security:department:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50003, 4201, '编辑', 'update', 'system:security:department:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50004, 4201, '删除', 'delete', 'system:security:department:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50101, 4202, '查看', 'view', 'system:security:user:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50102, 4202, '新增', 'create', 'system:security:user:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50103, 4202, '编辑', 'update', 'system:security:user:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50104, 4202, '删除', 'delete', 'system:security:user:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50105, 4202, '停用', 'disable', 'system:security:user:disable', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50106, 4202, '重置密码', 'resetPassword', 'system:security:user:reset-password', 'ENABLED', 60, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50107, 4202, '导入', 'import', 'system:security:user:import', 'ENABLED', 70, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50108, 4202, '导出', 'export', 'system:security:user:export', 'ENABLED', 80, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50201, 4203, '查看', 'view', 'system:security:role:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50202, 4203, '新增', 'create', 'system:security:role:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50203, 4203, '编辑', 'update', 'system:security:role:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50204, 4203, '删除', 'delete', 'system:security:role:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50205, 4203, '分配', 'assign', 'system:security:role:assign', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50301, 4204, '查看', 'view', 'system:security:menu:view', 'ENABLED', 10, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50302, 4204, '新增', 'create', 'system:security:menu:create', 'ENABLED', 20, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50303, 4204, '编辑', 'update', 'system:security:menu:update', 'ENABLED', 30, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50304, 4204, '删除', 'delete', 'system:security:menu:delete', 'ENABLED', 40, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (50305, 4204, '审计查看', 'auditView', 'system:security:audit:view', 'ENABLED', 50, 0, 0, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00');

-- argus_sys_role_menu（38 条）
INSERT INTO `argus_sys_role_menu` (`id`, `role_id`, `menu_id`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (1, 1, 4300, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (2, 1, 4301, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (3, 1, 4302, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (4, 1, 4303, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (5, 1, 4304, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (12, 1, 4305, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (13, 1, 4306, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (7, 1, 4307, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (8, 1, 4308, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (10, 1, 4309, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (15, 1, 4310, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (6, 1, 4311, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (9, 1, 4312, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (11, 1, 4313, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (14, 1, 4314, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (16, 3, 4300, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (17, 3, 4301, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (18, 3, 4302, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (19, 3, 4303, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (20, 3, 4304, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (27, 3, 4305, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (28, 3, 4306, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (22, 3, 4307, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (23, 3, 4308, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (25, 3, 4309, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (30, 3, 4310, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (21, 3, 4311, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (24, 3, 4312, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (26, 3, 4313, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (29, 3, 4314, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (39, 1, 4315, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00'),
  (40, 1, 4316, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00'),
  (41, 3, 4315, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00'),
  (42, 3, 4316, 'system', '2026-05-18 12:00:00', 'system', '2026-05-18 12:00:00');

ALTER TABLE `argus_sys_role_menu` AUTO_INCREMENT = 43;

INSERT INTO `argus_sys_role_menu` (`id`, `role_id`, `menu_id`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (31, 1, 4201, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (32, 1, 4202, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (33, 1, 4203, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (34, 1, 4204, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (35, 3, 4201, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (36, 3, 4202, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (37, 3, 4203, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (38, 3, 4204, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00');

ALTER TABLE `argus_sys_role_menu` AUTO_INCREMENT = 39;

-- argus_sys_role_menu_permission（126 条）
INSERT INTO `argus_sys_role_menu_permission` (`id`, `role_id`, `menu_id`, `menu_permission_id`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (1, 1, 4301, 51001, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (2, 1, 4301, 51002, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (3, 1, 4301, 51003, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (4, 1, 4302, 51101, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (5, 1, 4303, 51201, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (6, 1, 4303, 51202, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (7, 1, 4303, 51203, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (8, 1, 4303, 51204, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (9, 1, 4303, 51205, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (10, 1, 4303, 51206, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (11, 1, 4304, 51301, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (12, 1, 4305, 51401, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (13, 1, 4305, 51402, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (14, 1, 4305, 51403, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (15, 1, 4306, 51501, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (16, 1, 4306, 51502, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (17, 1, 4306, 51503, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (18, 1, 4306, 51504, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (128, 1, 4306, 51505, 'system', '2026-05-17 21:46:00', 'system', '2026-05-17 21:46:00'),
  (19, 1, 4307, 51601, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (20, 1, 4307, 51602, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (21, 1, 4307, 51603, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (22, 1, 4307, 51604, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (23, 1, 4307, 51605, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (24, 1, 4307, 51606, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (25, 1, 4307, 51607, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (26, 1, 4308, 51701, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (27, 1, 4308, 51702, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (28, 1, 4308, 51703, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (29, 1, 4308, 51704, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (30, 1, 4309, 51801, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (31, 1, 4309, 51802, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (32, 1, 4309, 51803, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (33, 1, 4309, 51804, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (34, 1, 4309, 51805, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (35, 1, 4310, 51901, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (36, 1, 4310, 51902, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (37, 1, 4310, 51903, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (38, 1, 4310, 51904, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (39, 1, 4310, 51905, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (40, 1, 4315, 51906, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00'),
  (41, 1, 4316, 51907, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00'),
  (42, 3, 4301, 51001, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (43, 3, 4301, 51002, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (44, 3, 4301, 51003, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (45, 3, 4302, 51101, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (46, 3, 4303, 51201, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (47, 3, 4303, 51202, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (48, 3, 4303, 51203, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (49, 3, 4303, 51204, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (50, 3, 4303, 51205, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (51, 3, 4303, 51206, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (52, 3, 4304, 51301, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (53, 3, 4305, 51401, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (54, 3, 4305, 51402, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (55, 3, 4305, 51403, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (56, 3, 4306, 51501, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (57, 3, 4306, 51502, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (58, 3, 4306, 51503, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (59, 3, 4306, 51504, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (129, 3, 4306, 51505, 'system', '2026-05-17 21:46:00', 'system', '2026-05-17 21:46:00'),
  (60, 3, 4307, 51601, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (61, 3, 4307, 51602, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (62, 3, 4307, 51603, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (63, 3, 4307, 51604, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (64, 3, 4307, 51605, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (65, 3, 4307, 51606, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (66, 3, 4307, 51607, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (67, 3, 4308, 51701, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (68, 3, 4308, 51702, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (69, 3, 4308, 51703, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (70, 3, 4308, 51704, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (71, 3, 4309, 51801, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (72, 3, 4309, 51802, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (73, 3, 4309, 51803, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (74, 3, 4309, 51804, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (75, 3, 4309, 51805, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (76, 3, 4310, 51901, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (77, 3, 4310, 51902, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (78, 3, 4310, 51903, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (79, 3, 4310, 51904, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (80, 3, 4310, 51905, 'system', '2026-05-17 19:55:41', 'system', '2026-05-17 19:55:41'),
  (81, 3, 4315, 51906, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00'),
  (82, 3, 4316, 51907, 'system', '2026-05-17 19:55:41', 'system', '2026-05-18 12:00:00');

ALTER TABLE `argus_sys_role_menu_permission` AUTO_INCREMENT = 83;

INSERT INTO `argus_sys_role_menu_permission` (`id`, `role_id`, `menu_id`, `menu_permission_id`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
  (83, 1, 4201, 50001, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (84, 1, 4201, 50002, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (85, 1, 4201, 50003, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (86, 1, 4201, 50004, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (87, 1, 4202, 50101, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (88, 1, 4202, 50102, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (89, 1, 4202, 50103, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (90, 1, 4202, 50104, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (91, 1, 4202, 50105, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (92, 1, 4202, 50106, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (93, 1, 4202, 50107, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (94, 1, 4202, 50108, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (95, 1, 4203, 50201, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (96, 1, 4203, 50202, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (97, 1, 4203, 50203, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (98, 1, 4203, 50204, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (99, 1, 4203, 50205, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (100, 1, 4204, 50301, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (101, 1, 4204, 50302, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (102, 1, 4204, 50303, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (103, 1, 4204, 50304, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (104, 1, 4204, 50305, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (105, 3, 4201, 50001, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (106, 3, 4201, 50002, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (107, 3, 4201, 50003, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (108, 3, 4201, 50004, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (109, 3, 4202, 50101, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (110, 3, 4202, 50102, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (111, 3, 4202, 50103, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (112, 3, 4202, 50104, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (113, 3, 4202, 50105, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (114, 3, 4202, 50106, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (115, 3, 4202, 50107, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (116, 3, 4202, 50108, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (117, 3, 4203, 50201, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (118, 3, 4203, 50202, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (119, 3, 4203, 50203, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (120, 3, 4203, 50204, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (121, 3, 4203, 50205, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (122, 3, 4204, 50301, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (123, 3, 4204, 50302, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (124, 3, 4204, 50303, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (125, 3, 4204, 50304, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00'),
  (126, 3, 4204, 50305, 'system', '2026-05-17 20:46:00', 'system', '2026-05-17 20:46:00');

ALTER TABLE `argus_sys_role_menu_permission` AUTO_INCREMENT = 127;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- 当前菜单结构快照：
-- [1000] 首页 -> /dashboard
--   └─ [1001] 分析页 -> analysis
--   └─ [1002] 工作台 -> workplace
-- [4300] 代码评审 -> /code-review
--   └─ [4301] SCM 配置 -> scm-config
--   └─ [4302] 评审任务 -> review-task
--   └─ [4303] 知识库管理 -> knowledge-base
--   └─ [4304] 个人代码质量 -> personal-quality
-- [4311] 错误治理 -> /error-governance
--   └─ [4307] 错误诊断 -> errors
--   └─ [4308] 错误类型规则 -> error-type-rules
-- [4312] 应用治理 -> /application-governance
--   └─ [4309] 应用联动配置 -> app-linkage
-- [4313] 监控中心 -> /monitor-center
--   └─ [4305] 数据监控 -> data-monitor
--   └─ [4306] 数据监控配置 -> data-monitor-config
-- [4314] 规则治理 -> /rule-governance
--   └─ [4310] 规范文档 -> rule-documents
--   └─ [4315] 提示词模板 -> prompt-templates
--   └─ [4316] 评分策略 -> scoring-policies
-- [4200] 权限管理 -> /authorization
--   └─ [4201] 部门管理 -> department
--   └─ [4202] 用户管理 -> user
--   └─ [4203] 角色管理 -> role
--   └─ [4204] 菜单管理 -> menu
