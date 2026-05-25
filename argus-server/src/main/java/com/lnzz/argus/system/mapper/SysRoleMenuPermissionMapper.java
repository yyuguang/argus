package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysRoleMenuPermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 角色菜单按钮权限关系 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysRoleMenuPermissionMapper extends BaseMapper<SysRoleMenuPermission> {

    /**
     * 查询多个角色的按钮权限授权关系。
     *
     * @param roleIds 角色 ID 集合
     * @return 角色按钮权限关系列表
     */
    default List<SysRoleMenuPermission> selectByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRoleMenuPermission>()
                .in(SysRoleMenuPermission::getRoleId, roleIds));
    }

    /**
     * 查询单个角色的按钮权限授权关系。
     *
     * @param roleId 角色 ID
     * @return 角色按钮权限关系列表
     */
    default List<SysRoleMenuPermission> selectByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRoleMenuPermission>()
                .eq(SysRoleMenuPermission::getRoleId, roleId));
    }

    /**
     * 统计指定菜单被角色按钮权限引用的次数。
     *
     * @param menuId 菜单 ID
     * @return 引用数量
     */
    default long countByMenuId(Long menuId) {
        if (menuId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysRoleMenuPermission>()
                .eq(SysRoleMenuPermission::getMenuId, menuId));
    }

    /**
     * 删除指定角色的按钮权限授权关系。
     *
     * @param roleId 角色 ID
     */
    default void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        delete(new LambdaQueryWrapper<SysRoleMenuPermission>()
                .eq(SysRoleMenuPermission::getRoleId, roleId));
    }
}
