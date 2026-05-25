package com.lnzz.argus.common.constant;

/**
 * 系统管理接口完整权限码。
 * <p>
 * 后端接口必须使用完整权限码进行强校验，集中定义可以避免 Controller 上散落权限字符串。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class SystemPermissionCodes {

    /** 部门查看权限。 */
    public static final String DEPARTMENT_VIEW = "system:security:department:view";

    /** 部门创建权限。 */
    public static final String DEPARTMENT_CREATE = "system:security:department:create";

    /** 部门更新权限。 */
    public static final String DEPARTMENT_UPDATE = "system:security:department:update";

    /** 部门删除权限。 */
    public static final String DEPARTMENT_DELETE = "system:security:department:delete";

    /** 用户查看权限。 */
    public static final String USER_VIEW = "system:security:user:view";

    /** 用户创建权限。 */
    public static final String USER_CREATE = "system:security:user:create";

    /** 用户更新权限。 */
    public static final String USER_UPDATE = "system:security:user:update";

    /** 用户删除权限。 */
    public static final String USER_DELETE = "system:security:user:delete";

    /** 用户禁用或启用权限。 */
    public static final String USER_DISABLE = "system:security:user:disable";

    /** 用户重置密码权限。 */
    public static final String USER_RESET_PASSWORD = "system:security:user:reset-password";

    /** 用户批量导入权限。 */
    public static final String USER_IMPORT = "system:security:user:import";

    /** 用户导出权限。 */
    public static final String USER_EXPORT = "system:security:user:export";

    /** 角色查看权限。 */
    public static final String ROLE_VIEW = "system:security:role:view";

    /** 角色创建权限。 */
    public static final String ROLE_CREATE = "system:security:role:create";

    /** 角色更新权限。 */
    public static final String ROLE_UPDATE = "system:security:role:update";

    /** 角色删除权限。 */
    public static final String ROLE_DELETE = "system:security:role:delete";

    /** 角色授权权限。 */
    public static final String ROLE_ASSIGN = "system:security:role:assign";

    /** 菜单查看权限。 */
    public static final String MENU_VIEW = "system:security:menu:view";

    /** 菜单创建权限。 */
    public static final String MENU_CREATE = "system:security:menu:create";

    /** 菜单更新、排序和启停权限。 */
    public static final String MENU_UPDATE = "system:security:menu:update";

    /** 菜单删除权限。 */
    public static final String MENU_DELETE = "system:security:menu:delete";

    /** 安全审计日志查看权限。 */
    public static final String AUDIT_VIEW = "system:security:audit:view";

    /** 规则管理查看权限。 */
    public static final String RULE_MANAGEMENT_VIEW = "quality:rule-management:view";

    /** 规则文档导入权限。 */
    public static final String RULE_MANAGEMENT_IMPORT = "quality:rule-management:import";

    /** 规则文档启用权限。 */
    public static final String RULE_MANAGEMENT_ACTIVATE = "quality:rule-management:activate";

    /** 规则文档停用权限。 */
    public static final String RULE_MANAGEMENT_DISABLE = "quality:rule-management:disable";

    /** 规则文档重建索引权限。 */
    public static final String RULE_MANAGEMENT_REINDEX = "quality:rule-management:reindex";

    /** Prompt 模板更新权限。 */
    public static final String RULE_MANAGEMENT_PROMPT_UPDATE = "quality:rule-management:prompt-update";

    /** 评分阈值更新权限。 */
    public static final String RULE_MANAGEMENT_SCORING_UPDATE = "quality:rule-management:scoring-update";

    private SystemPermissionCodes() {
    }
}
