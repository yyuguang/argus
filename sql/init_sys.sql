-- ============================================================
-- Argus 系统用户权限菜单模块初始化脚本
-- 说明：
-- 1. 本脚本仅包含 Phase 4 后台用户、角色、菜单、权限、会话与审计相关表。
-- 2. 与既有业务初始化脚本 init.sql 隔离，避免权限模块演进影响 Phase 1~3 业务表。
-- 3. 表结构遵循 .ai_rules/DB_STYLE.md：snake_case、审计字段、软删除、乐观锁、注释与索引。
-- 4. 默认数据使用固定 ID，便于多环境幂等初始化和问题排查。
-- ============================================================

-- 系统部门表：对应 vue-element-plus-admin 的 Department 树。
CREATE TABLE IF NOT EXISTS argus_sys_department (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    parent_id       BIGINT       DEFAULT NULL COMMENT '父部门ID，空表示根部门',
    department_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    department_code VARCHAR(64)  NOT NULL COMMENT '部门编码',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '同级排序号',
    remark          VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_department_code (department_code),
    INDEX idx_parent_sort (parent_id, sort_order),
    INDEX idx_status_deleted (status, is_deleted),
    INDEX idx_department_name (department_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统部门表';

-- 系统用户表：Portal 登录用户，区别于 SCM 提交者。
CREATE TABLE IF NOT EXISTS argus_sys_user (
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    department_id             BIGINT       DEFAULT NULL COMMENT '所属部门ID',
    username                  VARCHAR(64)  NOT NULL COMMENT '登录账号，对应前端 account',
    display_name              VARCHAR(100) NOT NULL COMMENT '显示名称，对应前端 username',
    email                     VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone                     VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    password_hash             VARCHAR(255) NOT NULL COMMENT '密码哈希',
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED/LOCKED',
    initial_password_required TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否需要首次修改密码: 0-否 1-是',
    failed_login_count        INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until              DATETIME     DEFAULT NULL COMMENT '锁定截止时间',
    last_login_at             DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    last_login_ip             VARCHAR(64)  DEFAULT NULL COMMENT '最近登录IP',
    external_provider         VARCHAR(50)  DEFAULT NULL COMMENT '外部身份来源，预留 SSO/LDAP/OAuth2',
    external_user_id          VARCHAR(128) DEFAULT NULL COMMENT '外部身份ID',
    is_deleted                TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version                   INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by                 VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                 VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    INDEX idx_department (department_id),
    INDEX idx_status_deleted (status, is_deleted),
    INDEX idx_display_name (display_name),
    INDEX idx_last_login_at (last_login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 系统角色表：RBAC 角色定义。
CREATE TABLE IF NOT EXISTS argus_sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码，如 SUPER_ADMIN/QUALITY_ADMIN',
    role_name   VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_type   VARCHAR(20)  NOT NULL DEFAULT 'CUSTOM' COMMENT '角色类型: SYSTEM/CUSTOM',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_role_code (role_code),
    INDEX idx_status_deleted (status, is_deleted),
    INDEX idx_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 用户角色关系表：用户可拥有多个角色。
CREATE TABLE IF NOT EXISTS argus_sys_user_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    role_id     BIGINT      NOT NULL COMMENT '角色ID',
    create_by   VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_role_id (role_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系表';

-- 系统菜单表：直接映射 vue-element-plus-admin 的 AppCustomRouteRecordRaw。
CREATE TABLE IF NOT EXISTS argus_sys_menu (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    parent_id      BIGINT       DEFAULT NULL COMMENT '父菜单ID，空表示顶级菜单',
    menu_type      VARCHAR(20)  NOT NULL COMMENT '菜单类型: DIRECTORY/MENU',
    route_path     VARCHAR(255) NOT NULL COMMENT '路由 path',
    route_name     VARCHAR(100) NOT NULL COMMENT '路由 name，全局唯一',
    component_path VARCHAR(255) DEFAULT NULL COMMENT '组件路径: #/##/views/...',
    redirect_path  VARCHAR(255) DEFAULT NULL COMMENT '重定向路径',
    title          VARCHAR(100) NOT NULL COMMENT '菜单标题，对应 meta.title',
    icon           VARCHAR(100) DEFAULT NULL COMMENT '菜单图标，对应 meta.icon',
    active_menu    VARCHAR(255) DEFAULT NULL COMMENT '高亮菜单，对应 meta.activeMenu',
    hidden         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否隐藏: 0-否 1-是',
    always_show    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否总是显示根菜单: 0-否 1-是',
    no_cache       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否不缓存: 0-否 1-是',
    breadcrumb     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否显示面包屑: 0-否 1-是',
    affix          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否固定标签页: 0-否 1-是',
    no_tags_view   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否不显示标签页: 0-否 1-是',
    can_to         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '隐藏时是否仍可跳转: 0-否 1-是',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    sort_order     INT          NOT NULL DEFAULT 0 COMMENT '同级排序号',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_route_name (route_name),
    UNIQUE KEY uk_parent_path (parent_id, route_path),
    INDEX idx_parent_sort (parent_id, sort_order),
    INDEX idx_status_deleted (status, is_deleted),
    INDEX idx_menu_type (menu_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单与动态路由表';

-- 菜单按钮权限表：permission_code 用于后端强校验，action_value 用于前端按钮显隐。
CREATE TABLE IF NOT EXISTS argus_sys_menu_permission (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    menu_id         BIGINT       NOT NULL COMMENT '菜单ID',
    label           VARCHAR(100) NOT NULL COMMENT '按钮权限显示名称，如 新增/编辑/删除',
    action_value    VARCHAR(64)  NOT NULL COMMENT '前端动作值，如 create/update/delete',
    permission_code VARCHAR(128) NOT NULL COMMENT '后端完整权限编码',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_menu_action (menu_id, action_value),
    UNIQUE KEY uk_permission_code (permission_code),
    INDEX idx_menu_status (menu_id, status, is_deleted),
    INDEX idx_action_value (action_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单按钮权限表';

-- 角色菜单关系表：控制角色可见页面。
CREATE TABLE IF NOT EXISTS argus_sys_role_menu (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    role_id     BIGINT      NOT NULL COMMENT '角色ID',
    menu_id     BIGINT      NOT NULL COMMENT '菜单ID',
    create_by   VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关系表';

-- 角色菜单按钮权限关系表：控制角色在页面内拥有的按钮动作。
CREATE TABLE IF NOT EXISTS argus_sys_role_menu_permission (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    role_id            BIGINT      NOT NULL COMMENT '角色ID',
    menu_id            BIGINT      NOT NULL COMMENT '菜单ID',
    menu_permission_id BIGINT      NOT NULL COMMENT '菜单按钮权限ID',
    create_by          VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_role_menu_permission (role_id, menu_id, menu_permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id),
    INDEX idx_permission_id (menu_permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单按钮权限关系表';

-- 用户权限覆盖表：用于临时给某用户增加或拒绝某个页面/按钮权限。
CREATE TABLE IF NOT EXISTS argus_sys_user_permission_override (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id            BIGINT      NOT NULL COMMENT '用户ID',
    menu_id            BIGINT      DEFAULT NULL COMMENT '菜单ID，覆盖页面访问权时使用',
    menu_permission_id BIGINT      DEFAULT NULL COMMENT '菜单按钮权限ID，覆盖按钮权限时使用',
    effect             VARCHAR(10) NOT NULL COMMENT '覆盖效果: ALLOW/DENY',
    condition_id       BIGINT      DEFAULT NULL COMMENT '条件权限ID，预留',
    reason             VARCHAR(500) DEFAULT NULL COMMENT '覆盖原因',
    create_by          VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_user_menu_permission (user_id, menu_id, menu_permission_id),
    INDEX idx_user_id (user_id),
    INDEX idx_menu_id (menu_id),
    INDEX idx_permission_id (menu_permission_id),
    INDEX idx_effect (effect)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户权限覆盖表';

-- 系统用户会话表：服务端可撤销的不透明 Token。
CREATE TABLE IF NOT EXISTS argus_sys_session (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT       NOT NULL COMMENT '用户ID',
    token_hash     VARCHAR(128) NOT NULL COMMENT 'Token 哈希',
    client_ip      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent     VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    issued_at      DATETIME     NOT NULL COMMENT '签发时间',
    expires_at     DATETIME     NOT NULL COMMENT '过期时间',
    revoked        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否撤销: 0-否 1-是',
    revoked_reason VARCHAR(255) DEFAULT NULL COMMENT '撤销原因',
    last_active_at DATETIME     DEFAULT NULL COMMENT '最近活跃时间',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_token_hash (token_hash),
    INDEX idx_user_revoked (user_id, revoked),
    INDEX idx_expires_at (expires_at),
    INDEX idx_last_active_at (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户会话表';

-- 条件权限表：首版支持时间段和 IP CIDR，后续可扩展更多条件类型。
CREATE TABLE IF NOT EXISTS argus_sys_permission_condition (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    condition_name VARCHAR(100) NOT NULL COMMENT '条件名称',
    condition_type VARCHAR(30)  NOT NULL COMMENT '条件类型: TIME_RANGE/IP_CIDR',
    condition_json JSON         NOT NULL COMMENT '条件配置JSON',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除: 0-否 1-是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_type_status (condition_type, status),
    INDEX idx_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统条件权限表';

-- 系统安全审计日志表：只追加，不参与软删除。
CREATE TABLE IF NOT EXISTS argus_sys_security_audit_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    actor_user_id  BIGINT       DEFAULT NULL COMMENT '操作者用户ID',
    actor_username VARCHAR(64)  DEFAULT NULL COMMENT '操作者登录账号',
    action         VARCHAR(80)  NOT NULL COMMENT '动作编码，如 USER_CREATE/ROLE_ASSIGN/MENU_UPDATE',
    resource_type  VARCHAR(80)  NOT NULL COMMENT '资源类型: USER/ROLE/MENU/DEPARTMENT/SESSION',
    resource_id    VARCHAR(80)  DEFAULT NULL COMMENT '资源ID',
    before_json    JSON         DEFAULT NULL COMMENT '变更前快照',
    after_json     JSON         DEFAULT NULL COMMENT '变更后快照',
    result         VARCHAR(20)  NOT NULL COMMENT '结果: SUCCESS/FAILED',
    failure_reason VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    client_ip      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent     VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    trace_id       VARCHAR(100) DEFAULT NULL COMMENT '链路追踪ID',
    create_by      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_actor_time (actor_username, create_time),
    INDEX idx_action_time (action, create_time),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_result_time (result, create_time),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统安全审计日志表';

-- ============================================================
-- 默认数据
-- ============================================================

-- 默认部门。
INSERT INTO argus_sys_department (id, parent_id, department_name, department_code, status, sort_order, remark, create_by, update_by)
SELECT 100, NULL, 'Argus 平台组', 'ARGUS_PLATFORM', 'ENABLED', 10, 'Argus 默认平台管理部门', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_department WHERE id = 100);

-- 默认角色。
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 1, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ENABLED', '拥有全部系统权限', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 1);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 2, 'PLATFORM_ADMIN', '平台管理员', 'SYSTEM', 'ENABLED', '管理平台配置和权限', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 2);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 3, 'QUALITY_ADMIN', '质量管理员', 'SYSTEM', 'ENABLED', '管理质量治理功能', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 3);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 4, 'DBA', '数据库负责人', 'SYSTEM', 'ENABLED', '管理数据库监控与慢 SQL 分析', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 4);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 5, 'TECH_LEAD', '技术负责人', 'SYSTEM', 'ENABLED', '查看团队质量与错误诊断结果', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 5);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 6, 'VIEWER', '只读用户', 'SYSTEM', 'ENABLED', '只读查看核心看板', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 6);
INSERT INTO argus_sys_role (id, role_code, role_name, role_type, status, remark, create_by, update_by)
SELECT 7, 'AUDITOR', '审计员', 'SYSTEM', 'ENABLED', '查看系统安全审计日志', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_role WHERE id = 7);

-- 默认超级管理员。默认密码为 Argus@123，使用 PBKDF2 哈希保存，并要求首次登录后修改。
INSERT INTO argus_sys_user (
    id, department_id, username, display_name, email, password_hash, status,
    initial_password_required, create_by, update_by
)
SELECT
    1, 100, 'admin', '平台管理员', 'admin@example.com',
    'PBKDF2$120000$YXJndXMtcGhhc2U0LWFkbWluLXNhbHQ=$jwJAj5PPJUPe0FquFUM8X1nrtcWu1lnG/h2ivUA8z/w=',
    'ENABLED', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_user WHERE id = 1);

INSERT INTO argus_sys_user_role (user_id, role_id, create_by, update_by)
SELECT 1, 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_user_role WHERE user_id = 1 AND role_id = 1);

-- 默认菜单：只初始化 vue-element-plus-admin 当前真实存在的 Dashboard 与 Authorization 页面，避免动态 import 失败。
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, redirect_path, title, icon, always_show, status, sort_order, create_by, update_by)
SELECT 1000, NULL, 'DIRECTORY', '/dashboard', 'Dashboard', '#', '/dashboard/analysis', '首页', 'vi-ant-design:dashboard-filled', 1, 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 1000);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, no_cache, affix, status, sort_order, create_by, update_by)
SELECT 1001, 1000, 'MENU', 'analysis', 'Analysis', 'views/Dashboard/Analysis', '分析页', 1, 1, 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 1001);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, no_cache, status, sort_order, create_by, update_by)
SELECT 1002, 1000, 'MENU', 'workplace', 'Workplace', 'views/Dashboard/Workplace', '工作台', 1, 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 1002);

INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, redirect_path, title, icon, always_show, status, sort_order, create_by, update_by)
SELECT 4200, NULL, 'DIRECTORY', '/authorization', 'Authorization', '#', '/authorization/user', '权限管理', 'vi-eos-icons:role-binding', 1, 'ENABLED', 90, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4200);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, status, sort_order, create_by, update_by)
SELECT 4201, 4200, 'MENU', 'department', 'Department', 'views/Authorization/Department/Department', '部门管理', 'ENABLED', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4201);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, status, sort_order, create_by, update_by)
SELECT 4202, 4200, 'MENU', 'user', 'User', 'views/Authorization/User/User', '用户管理', 'ENABLED', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4202);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, status, sort_order, create_by, update_by)
SELECT 4203, 4200, 'MENU', 'role', 'Role', 'views/Authorization/Role/Role', '角色管理', 'ENABLED', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4203);
INSERT INTO argus_sys_menu (id, parent_id, menu_type, route_path, route_name, component_path, title, status, sort_order, create_by, update_by)
SELECT 4204, 4200, 'MENU', 'menu', 'Menu', 'views/Authorization/Menu/Menu', '菜单管理', 'ENABLED', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu WHERE id = 4204);

-- 默认按钮权限。
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50001, 4201, '查看', 'view', 'system:security:department:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50001);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50002, 4201, '新增', 'create', 'system:security:department:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50002);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50003, 4201, '编辑', 'update', 'system:security:department:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50003);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50004, 4201, '删除', 'delete', 'system:security:department:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50004);

INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50101, 4202, '查看', 'view', 'system:security:user:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50101);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50102, 4202, '新增', 'create', 'system:security:user:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50102);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50103, 4202, '编辑', 'update', 'system:security:user:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50103);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50104, 4202, '删除', 'delete', 'system:security:user:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50104);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50105, 4202, '禁用', 'disable', 'system:security:user:disable', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50105);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50106, 4202, '重置密码', 'resetPassword', 'system:security:user:reset-password', 60, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50106);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50107, 4202, '导入', 'import', 'system:security:user:import', 70, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50107);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50108, 4202, '导出', 'export', 'system:security:user:export', 80, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50108);

INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50201, 4203, '查看', 'view', 'system:security:role:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50201);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50202, 4203, '新增', 'create', 'system:security:role:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50202);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50203, 4203, '编辑', 'update', 'system:security:role:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50203);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50204, 4203, '删除', 'delete', 'system:security:role:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50204);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50205, 4203, '分配权限', 'assign', 'system:security:role:assign', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50205);

INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50301, 4204, '查看', 'view', 'system:security:menu:view', 10, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50301);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50302, 4204, '新增', 'create', 'system:security:menu:create', 20, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50302);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50303, 4204, '编辑', 'update', 'system:security:menu:update', 30, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50303);
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50304, 4204, '删除', 'delete', 'system:security:menu:delete', 40, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50304);

-- 审计查看权限挂到菜单管理下，便于后端强校验；后续如果新增审计页面，可迁移到独立菜单。
INSERT INTO argus_sys_menu_permission (id, menu_id, label, action_value, permission_code, sort_order, create_by, update_by)
SELECT 50305, 4204, '审计查看', 'auditView', 'system:security:audit:view', 50, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM argus_sys_menu_permission WHERE id = 50305);

-- SUPER_ADMIN 默认拥有全部页面与按钮权限。
INSERT INTO argus_sys_role_menu (role_id, menu_id, create_by, update_by)
SELECT 1, m.id, 'system', 'system'
FROM argus_sys_menu m
WHERE NOT EXISTS (
    SELECT 1 FROM argus_sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
);

INSERT INTO argus_sys_role_menu_permission (role_id, menu_id, menu_permission_id, create_by, update_by)
SELECT 1, p.menu_id, p.id, 'system', 'system'
FROM argus_sys_menu_permission p
WHERE NOT EXISTS (
    SELECT 1
    FROM argus_sys_role_menu_permission rp
    WHERE rp.role_id = 1 AND rp.menu_permission_id = p.id
);
