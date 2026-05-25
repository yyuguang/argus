-- ============================================================
-- Argus Phase 1 代码评审菜单初始化脚本
-- 说明：
-- 1. 本脚本初始化代码评审相关页面的菜单与按钮权限。
-- 2. 依赖 init_sys.sql 中的菜单、权限与角色授权表结构。
-- 3. 本轮菜单拆分后，原“质量治理”根菜单 4300 调整为“代码评审”。
-- ============================================================

-- 清理历史临时按钮权限，避免与正式权限编号和 action 冲突。
DELETE FROM argus_sys_role_menu_permission
WHERE menu_permission_id IN (51102, 51103);

DELETE FROM argus_sys_menu_permission
WHERE id IN (51102, 51103);

-- 代码评审目录。
UPDATE argus_sys_menu
SET parent_id = NULL,
    menu_type = 'DIRECTORY',
    route_path = '/code-review',
    route_name = 'CodeReview',
    component_path = '#',
    redirect_path = '/code-review/scm-config',
    title = '代码评审',
    icon = 'vi-ep:reading',
    active_menu = NULL,
    hidden = 0,
    always_show = 1,
    no_cache = 0,
    breadcrumb = 1,
    affix = 0,
    no_tags_view = 0,
    can_to = 0,
    status = 'ENABLED',
    sort_order = 30,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4300;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4300, NULL, 'DIRECTORY', '/code-review', 'CodeReview', '#', '/code-review/scm-config',
    '代码评审', 'vi-ep:reading', 1, 'ENABLED', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4300);

-- SCM 配置管理。
UPDATE argus_sys_menu
SET parent_id = 4300,
    menu_type = 'MENU',
    route_path = 'scm-config',
    route_name = 'QualityScmConfig',
    component_path = 'views/Quality/ScmConfig/ScmConfig',
    title = 'SCM 配置',
    status = 'ENABLED',
    sort_order = 10,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4301;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4301, 4300, 'MENU', 'scm-config', 'QualityScmConfig', 'views/Quality/ScmConfig/ScmConfig',
    'SCM 配置', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4301);

-- AI 评审任务中心。
UPDATE argus_sys_menu
SET parent_id = 4300,
    menu_type = 'MENU',
    route_path = 'review-task',
    route_name = 'QualityReviewTask',
    component_path = 'views/Quality/ReviewTask/ReviewTask',
    title = '评审任务',
    status = 'ENABLED',
    sort_order = 20,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4302;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4302, 4300, 'MENU', 'review-task', 'QualityReviewTask', 'views/Quality/ReviewTask/ReviewTask',
    '评审任务', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4302);

-- 知识库管理。
UPDATE argus_sys_menu
SET parent_id = 4300,
    menu_type = 'MENU',
    route_path = 'knowledge-base',
    route_name = 'QualityKnowledgeBase',
    component_path = 'views/Quality/KnowledgeBase/KnowledgeBase',
    title = '知识库管理',
    status = 'ENABLED',
    sort_order = 30,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4303;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4303, 4300, 'MENU', 'knowledge-base', 'QualityKnowledgeBase', 'views/Quality/KnowledgeBase/KnowledgeBase',
    '知识库管理', 'ENABLED', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4303);

-- 个人代码质量。
UPDATE argus_sys_menu
SET parent_id = 4300,
    menu_type = 'MENU',
    route_path = 'personal-quality',
    route_name = 'QualityPersonalQuality',
    component_path = 'views/Quality/PersonalQuality/PersonalQuality',
    title = '个人代码质量',
    status = 'ENABLED',
    sort_order = 40,
    is_deleted = 0,
    update_by = 'system'
WHERE id = 4304;

INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4304, 4300, 'MENU', 'personal-quality', 'QualityPersonalQuality', 'views/Quality/PersonalQuality/PersonalQuality',
    '个人代码质量', 'ENABLED', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4304);

-- SCM 配置按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51001, 4301, '查看', 'view', 'quality:scm-config:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51001);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51002, 4301, '新增', 'create', 'quality:scm-config:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51002);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51003, 4301, '编辑', 'update', 'quality:scm-config:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51003);

-- 评审任务按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51101, 4302, '查看', 'view', 'quality:review-task:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51101);

-- 知识库按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51201, 4303, '查看', 'view', 'quality:knowledge-base:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51201);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51202, 4303, '确认', 'confirm', 'quality:knowledge-base:confirm', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51202);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51203, 4303, '标记误报', 'false-positive', 'quality:knowledge-base:false-positive', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51203);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51204, 4303, '忽略', 'ignore', 'quality:knowledge-base:ignore', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51204);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51205, 4303, '提升白名单', 'promote-whitelist', 'quality:knowledge-base:promote-whitelist', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51205);

INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51206, 4303, '降级白名单', 'demote-whitelist', 'quality:knowledge-base:demote-whitelist', 60, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51206);

-- 个人代码质量按钮权限。
INSERT INTO argus_sys_menu_permission (
    id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by
)
SELECT 51301, 4304, '查看', 'view', 'quality:personal-quality:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 51301);

-- SUPER_ADMIN 默认拥有代码评审访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 1, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4300, 4301, 4302, 4303, 4304)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 1, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51001, 51002, 51003, 51101, 51201, 51202, 51203, 51204, 51205, 51206, 51301)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 1 AND rp.menu_permission_id = p.id
  );

-- QUALITY_ADMIN 默认拥有代码评审访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 3, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4300, 4301, 4302, 4303, 4304)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 3, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51001, 51002, 51003, 51101, 51201, 51202, 51203, 51204, 51205, 51206, 51301)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 3 AND rp.menu_permission_id = p.id
  );
