-- ============================================================
-- Argus Phase 2 / Phase 3 错误治理、应用治理、监控中心菜单初始化脚本
-- 说明：
-- 1. 本脚本补充错误治理、应用治理与监控中心相关独立页面菜单与按钮权限。
-- 2. 本轮菜单拆分后，这些页面不再挂在“代码评审”目录下。
-- 3. 应用联动、数据监控配置统一保留独立页面，不回塞 SCM 页面。
-- ============================================================

-- 错误治理目录。
UPDATE argus_sys_menu
SET parent_id = NULL,
    menu_type = 'DIRECTORY',
    route_path = '/error-governance',
    route_name = 'ErrorGovernance',
    component_path = '#',
    redirect_path = '/error-governance/errors',
    title = '错误治理',
    icon = 'vi-ep:warning-filled',
    active_menu = NULL,
    hidden = 0,
    always_show = 1,
    no_cache = 0,
    breadcrumb = 1,
    affix = 0,
    no_tags_view = 0,
    can_to = 0,
    status = 'ENABLED',
    sort_order = 35,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4311;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4311, NULL, 'DIRECTORY', '/error-governance', 'ErrorGovernance', '#', '/error-governance/errors',
    '错误治理', 'vi-ep:warning-filled', 1, 'ENABLED', 35, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4311);

-- 应用治理目录。
UPDATE argus_sys_menu
SET parent_id = NULL,
    menu_type = 'DIRECTORY',
    route_path = '/application-governance',
    route_name = 'ApplicationGovernance',
    component_path = '#',
    redirect_path = '/application-governance/app-linkage',
    title = '应用治理',
    icon = 'vi-ep:connection',
    active_menu = NULL,
    hidden = 0,
    always_show = 1,
    no_cache = 0,
    breadcrumb = 1,
    affix = 0,
    no_tags_view = 0,
    can_to = 0,
    status = 'ENABLED',
    sort_order = 40,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4312;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4312, NULL, 'DIRECTORY', '/application-governance', 'ApplicationGovernance', '#', '/application-governance/app-linkage',
    '应用治理', 'vi-ep:connection', 1, 'ENABLED', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4312);

-- 监控中心目录。
UPDATE argus_sys_menu
SET parent_id = NULL,
    menu_type = 'DIRECTORY',
    route_path = '/monitor-center',
    route_name = 'MonitorCenter',
    component_path = '#',
    redirect_path = '/monitor-center/data-monitor',
    title = '监控中心',
    icon = 'vi-ep:monitor',
    active_menu = NULL,
    hidden = 0,
    always_show = 1,
    no_cache = 0,
    breadcrumb = 1,
    affix = 0,
    no_tags_view = 0,
    can_to = 0,
    status = 'ENABLED',
    sort_order = 45,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4313;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4313, NULL, 'DIRECTORY', '/monitor-center', 'MonitorCenter', '#', '/monitor-center/data-monitor',
    '监控中心', 'vi-ep:monitor', 1, 'ENABLED', 45, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4313);

-- 错误诊断。
UPDATE argus_sys_menu
SET parent_id = 4311,
    menu_type = 'MENU',
    route_path = 'errors',
    route_name = 'QualityErrors',
    component_path = 'views/Quality/ErrorDiagnosis/ErrorDiagnosis',
    title = '错误诊断',
    status = 'ENABLED',
    sort_order = 10,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4307;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4307, 4311, 'MENU', 'errors', 'QualityErrors', 'views/Quality/ErrorDiagnosis/ErrorDiagnosis',
    '错误诊断', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4307);

-- 错误类型规则。
UPDATE argus_sys_menu
SET parent_id = 4311,
    menu_type = 'MENU',
    route_path = 'error-type-rules',
    route_name = 'QualityErrorTypeRules',
    component_path = 'views/Quality/ErrorTypeRule/ErrorTypeRule',
    title = '错误类型规则',
    status = 'ENABLED',
    sort_order = 20,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4308;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4308, 4311, 'MENU', 'error-type-rules', 'QualityErrorTypeRules', 'views/Quality/ErrorTypeRule/ErrorTypeRule',
    '错误类型规则', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4308);

-- 应用联动配置。
UPDATE argus_sys_menu
SET parent_id = 4312,
    menu_type = 'MENU',
    route_path = 'app-linkage',
    route_name = 'QualityAppLinkage',
    component_path = 'views/Quality/AppLinkage/AppLinkage',
    title = '应用联动配置',
    status = 'ENABLED',
    sort_order = 10,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4309;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4309, 4312, 'MENU', 'app-linkage', 'QualityAppLinkage', 'views/Quality/AppLinkage/AppLinkage',
    '应用联动配置', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4309);

-- 数据监控工作台。
UPDATE argus_sys_menu
SET parent_id = 4313,
    menu_type = 'MENU',
    route_path = 'data-monitor',
    route_name = 'QualityDataMonitor',
    component_path = 'views/Quality/DataMonitor/DataMonitor',
    title = '数据监控',
    status = 'ENABLED',
    sort_order = 10,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4305;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4305, 4313, 'MENU', 'data-monitor', 'QualityDataMonitor', 'views/Quality/DataMonitor/DataMonitor',
    '数据监控', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4305);

-- 数据监控配置页。
UPDATE argus_sys_menu
SET parent_id = 4313,
    menu_type = 'MENU',
    route_path = 'data-monitor-config',
    route_name = 'QualityDataMonitorConfig',
    component_path = 'views/Quality/DataMonitorConfig/DataMonitorConfig',
    title = '数据监控配置',
    status = 'ENABLED',
    sort_order = 20,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4306;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4306, 4313, 'MENU', 'data-monitor-config', 'QualityDataMonitorConfig', 'views/Quality/DataMonitorConfig/DataMonitorConfig',
    '数据监控配置', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4306);

-- 错误诊断按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51601, 4307, '查看', 'view', 'quality:error:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51601);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51602, 4307, '重试分析', 'retry-analysis', 'quality:error:retry-analysis', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51602);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51603, 4307, '重发通知', 'resend-notify', 'quality:error:resend-notify', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51603);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51604, 4307, '忽略', 'ignore', 'quality:error:ignore', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51604);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51605, 4307, '标记误报', 'false-positive', 'quality:error:false-positive', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51605);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51606, 4307, '人工调级', 'severity-update', 'quality:error:severity:update', 60, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51606);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51607, 4307, '人工结论', 'manual-conclusion', 'quality:error:manual-conclusion', 70, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51607);

-- 错误类型规则按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51701, 4308, '查看', 'view', 'quality:error-type-rule:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51701);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51702, 4308, '新增', 'create', 'quality:error-type-rule:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51702);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51703, 4308, '编辑', 'update', 'quality:error-type-rule:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51703);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51704, 4308, '删除', 'delete', 'quality:error-type-rule:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51704);

-- 应用联动配置按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51801, 4309, '查看', 'view', 'quality:app-linkage:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51801);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51802, 4309, '新增', 'create', 'quality:app-linkage:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51802);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51803, 4309, '编辑', 'update', 'quality:app-linkage:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51803);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51804, 4309, '删除', 'delete', 'quality:app-linkage:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51804);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51805, 4309, '测试', 'test', 'quality:app-linkage:test', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51805);

-- 数据监控按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51401, 4305, '查看', 'view', 'quality:data-monitor:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51401);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51402, 4305, '确认', 'confirm', 'quality:data-monitor:confirm', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51402);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51403, 4305, '忽略', 'ignore', 'quality:data-monitor:ignore', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51403);

-- 数据监控配置按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51501, 4306, '查看', 'view', 'quality:data-monitor-config:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51501);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51502, 4306, '新增', 'create', 'quality:data-monitor-config:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51502);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51503, 4306, '编辑', 'update', 'quality:data-monitor-config:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51503);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51504, 4306, '测试连接', 'test', 'quality:data-monitor-config:test', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51504);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51505, 4306, '删除', 'delete', 'quality:data-monitor-config:delete', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51505);

-- SUPER_ADMIN 默认拥有错误治理、应用治理与监控中心访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 1, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4311, 4312, 4313, 4305, 4306, 4307, 4308, 4309)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 1, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (
    51401, 51402, 51403, 51501, 51502, 51503, 51504, 51505,
    51601, 51602, 51603, 51604, 51605, 51606, 51607,
    51701, 51702, 51703, 51704,
    51801, 51802, 51803, 51804, 51805
)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 1 AND rp.menu_permission_id = p.id
  );

-- QUALITY_ADMIN 默认拥有错误治理、应用治理与监控中心访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 3, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4311, 4312, 4313, 4305, 4306, 4307, 4308, 4309)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 3, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (
    51401, 51402, 51403, 51501, 51502, 51503, 51504, 51505,
    51601, 51602, 51603, 51604, 51605, 51606, 51607,
    51701, 51702, 51703, 51704,
    51801, 51802, 51803, 51804, 51805
)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 3 AND rp.menu_permission_id = p.id
  );
