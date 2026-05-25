package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户权限覆盖实体。
 * <p>用于在角色授权之外，对单个用户临时 ALLOW 或 DENY 页面和按钮权限。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_user_permission_override")
public class SysUserPermissionOverride extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 菜单ID，覆盖页面访问权时使用 */
    private Long menuId;

    /** 菜单按钮权限ID，覆盖按钮权限时使用 */
    private Long menuPermissionId;

    /** 覆盖效果: ALLOW/DENY */
    private String effect;

    /** 条件权限ID */
    private Long conditionId;

    /** 覆盖原因 */
    private String reason;
}
