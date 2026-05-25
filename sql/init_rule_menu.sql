-- ============================================================
-- Argus Phase 5 规则治理菜单初始化脚本
-- 说明：
-- 1. 本脚本初始化 Phase 5 规则治理目录与 3 个独立页面入口。
-- 2. 规则治理不再承载旧单页 Tab，而是拆为规范文档、提示词模板、评分策略。
-- 3. 后端权限码继续沿用既有 `quality:rule-management:*`，避免影响已落地鉴权链。
-- ============================================================

-- 规则治理目录。
UPDATE argus_sys_menu
SET parent_id = NULL,
    menu_type = 'DIRECTORY',
    route_path = '/rule-governance',
    route_name = 'RuleGovernance',
    component_path = '#',
    redirect_path = '/rule-governance/rule-documents',
    title = '规则治理',
    icon = 'vi-ep:management',
    active_menu = NULL,
    hidden = 0,
    always_show = 1,
    no_cache = 0,
    breadcrumb = 1,
    affix = 0,
    no_tags_view = 0,
    can_to = 0,
    status = 'ENABLED',
    sort_order = 50,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4314;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4314, NULL, 'DIRECTORY', '/rule-governance', 'RuleGovernance', '#', '/rule-governance/rule-documents',
    '规则治理', 'vi-ep:management', 1, 'ENABLED', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4314);

-- 规范文档页面。
UPDATE argus_sys_menu
SET parent_id = 4314,
    menu_type = 'MENU',
    route_path = 'rule-documents',
    route_name = 'QualityRuleDocuments',
    component_path = 'views/Quality/RuleGovernance/RuleDocuments/RuleDocuments',
    title = '规范文档',
    status = 'ENABLED',
    sort_order = 10,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4310;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4310, 4314, 'MENU', 'rule-documents', 'QualityRuleDocuments',
    'views/Quality/RuleGovernance/RuleDocuments/RuleDocuments',
    '规范文档', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4310);

-- 提示词模板页面。
UPDATE argus_sys_menu
SET parent_id = 4314,
    menu_type = 'MENU',
    route_path = 'prompt-templates',
    route_name = 'QualityPromptTemplates',
    component_path = 'views/Quality/RuleGovernance/PromptTemplates/PromptTemplates',
    title = '提示词模板',
    status = 'ENABLED',
    sort_order = 20,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4315;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4315, 4314, 'MENU', 'prompt-templates', 'QualityPromptTemplates',
    'views/Quality/RuleGovernance/PromptTemplates/PromptTemplates',
    '提示词模板', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4315);

-- 评分策略页面。
UPDATE argus_sys_menu
SET parent_id = 4314,
    menu_type = 'MENU',
    route_path = 'scoring-policies',
    route_name = 'QualityScoringPolicies',
    component_path = 'views/Quality/RuleGovernance/ScoringPolicies/ScoringPolicies',
    title = '评分策略',
    status = 'ENABLED',
    sort_order = 30,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4316;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4316, 4314, 'MENU', 'scoring-policies', 'QualityScoringPolicies',
    'views/Quality/RuleGovernance/ScoringPolicies/ScoringPolicies',
    '评分策略', 'ENABLED', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4316);

-- 规范文档按钮权限。
UPDATE argus_sys_menu_permission
SET menu_id = 4310,
    label = '查看',
    action_value = 'view',
    permission_code = 'quality:rule-management:view',
    sort_order = 10,
    update_by = 'system'
WHERE id = 51901;

UPDATE argus_sys_menu_permission
SET menu_id = 4310,
    label = '导入',
    action_value = 'import',
    permission_code = 'quality:rule-management:import',
    sort_order = 20,
    update_by = 'system'
WHERE id = 51902;

UPDATE argus_sys_menu_permission
SET menu_id = 4310,
    label = '启用',
    action_value = 'activate',
    permission_code = 'quality:rule-management:activate',
    sort_order = 30,
    update_by = 'system'
WHERE id = 51903;

UPDATE argus_sys_menu_permission
SET menu_id = 4310,
    label = '停用',
    action_value = 'disable',
    permission_code = 'quality:rule-management:disable',
    sort_order = 40,
    update_by = 'system'
WHERE id = 51904;

UPDATE argus_sys_menu_permission
SET menu_id = 4310,
    label = '分块重建',
    action_value = 'reindex',
    permission_code = 'quality:rule-management:reindex',
    sort_order = 50,
    update_by = 'system'
WHERE id = 51905;

-- Prompt 页面按钮权限。
UPDATE argus_sys_menu_permission
SET menu_id = 4315,
    label = '更新模板',
    action_value = 'prompt-update',
    permission_code = 'quality:rule-management:prompt-update',
    sort_order = 10,
    update_by = 'system'
WHERE id = 51906;

-- 评分页面按钮权限。
UPDATE argus_sys_menu_permission
SET menu_id = 4316,
    label = '更新评分',
    action_value = 'scoring-update',
    permission_code = 'quality:rule-management:scoring-update',
    sort_order = 10,
    update_by = 'system'
WHERE id = 51907;

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51901, 4310, '查看', 'view', 'quality:rule-management:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51901);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51902, 4310, '导入', 'import', 'quality:rule-management:import', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51902);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51903, 4310, '启用', 'activate', 'quality:rule-management:activate', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51903);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51904, 4310, '停用', 'disable', 'quality:rule-management:disable', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51904);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51905, 4310, '分块重建', 'reindex', 'quality:rule-management:reindex', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51905);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51906, 4315, '更新模板', 'prompt-update', 'quality:rule-management:prompt-update', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51906);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51907, 4316, '更新评分', 'scoring-update', 'quality:rule-management:scoring-update', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51907);

UPDATE argus_sys_role_menu_permission
SET menu_id = 4310
WHERE menu_permission_id IN (51901, 51902, 51903, 51904, 51905);

UPDATE argus_sys_role_menu_permission
SET menu_id = 4315
WHERE menu_permission_id = 51906;

UPDATE argus_sys_role_menu_permission
SET menu_id = 4316
WHERE menu_permission_id = 51907;

-- SUPER_ADMIN 默认拥有访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 1, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4314, 4310, 4315, 4316)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 1, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51901, 51902, 51903, 51904, 51905, 51906, 51907)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 1 AND rp.menu_permission_id = p.id
  );

-- QUALITY_ADMIN 默认拥有访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 3, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4314, 4310, 4315, 4316)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 3, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51901, 51902, 51903, 51904, 51905, 51906, 51907)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 3 AND rp.menu_permission_id = p.id
  );
