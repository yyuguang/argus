package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色菜单按钮权限关系实体。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_role_menu_permission")
public class SysRoleMenuPermission extends BaseEntity {

    /** 角色ID */
    private Long roleId;

    /** 菜单ID */
    private Long menuId;

    /** 菜单按钮权限ID */
    private Long menuPermissionId;
}
