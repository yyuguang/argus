package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 角色菜单关系 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 查询多个角色的菜单授权关系。
     *
     * @param roleIds 角色 ID 集合
     * @return 角色菜单关系列表
     */
    default List<SysRoleMenu> selectByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds));
    }

    /**
     * 查询单个角色的菜单授权关系。
     *
     * @param roleId 角色 ID
     * @return 角色菜单关系列表
     */
    default List<SysRoleMenu> selectByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
    }

    /**
     * 统计指定菜单被角色授权引用的次数。
     *
     * @param menuId 菜单 ID
     * @return 引用数量
     */
    default long countByMenuId(Long menuId) {
        if (menuId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getMenuId, menuId));
    }

    /**
     * 删除指定角色的菜单授权关系。
     *
     * @param roleId 角色 ID
     */
    default void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
    }
}
