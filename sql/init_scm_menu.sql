-- ============================================================
-- Argus Phase 1 SCM / AI 评审菜单初始化脚本
-- 说明：
-- 1. 本脚本只初始化 vue-element-plus-admin 中 Phase 1 已迁移页面的菜单与按钮权限。
-- 2. 依赖 init_sys.sql 中的 argus_sys_menu、argus_sys_menu_permission、argus_sys_role_menu、
--    argus_sys_role_menu_permission 表结构。
-- 3. 菜单 component_path 必须与前端真实文件路径保持一致，避免动态 import 失败。
-- ============================================================

-- Phase 1 质量治理目录。
INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path, redirect_path,
    title, icon, always_show, status, sort_order, create_by, update_by
)
SELECT
    4300, NULL, 'DIRECTORY', '/quality', 'QualityGovernance', '#', '/quality/scm-config',
    '质量治理', 'vi-ep:operation', 1, 'ENABLED', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4300);

-- SCM 配置管理。
INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4301, 4300, 'MENU', 'scm-config', 'QualityScmConfig', 'views/Quality/ScmConfig/ScmConfig',
    'SCM 配置', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4301);

-- AI 评审任务中心。
INSERT INTO argus_sys_menu (
    id, parent_id, menu_type, route_path, route_name, component_path,
    title, status, sort_order, create_by, update_by
)
SELECT
    4302, 4300, 'MENU', 'review-task', 'QualityReviewTask', 'views/Quality/ReviewTask/ReviewTask',
    '评审任务', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4302);

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

-- SUPER_ADMIN 默认拥有 Phase 1 页面访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 1, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4300, 4301, 4302)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 1, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51001, 51002, 51003, 51101)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 1 AND rp.menu_permission_id = p.id
  );

-- QUALITY_ADMIN 默认拥有 Phase 1 页面访问权。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 3, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE m.id IN (4300, 4301, 4302)
  AND NOT EXISTS (
      SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id
  );

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 3, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE p.id IN (51001, 51002, 51003, 51101)
  AND NOT EXISTS (
      SELECT 1
      FROM argus_sys_role_menu_permission rp
      WHERE rp.role_id = 3 AND rp.menu_permission_id = p.id
  );
