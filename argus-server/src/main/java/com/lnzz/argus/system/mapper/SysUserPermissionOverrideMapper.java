package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysUserPermissionOverride;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 用户权限覆盖 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysUserPermissionOverrideMapper extends BaseMapper<SysUserPermissionOverride> {

    /**
     * 查询用户权限覆盖记录。
     *
     * @param userId 用户 ID
     * @return 用户权限覆盖列表
     */
    default List<SysUserPermissionOverride> selectByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysUserPermissionOverride>()
                .eq(SysUserPermissionOverride::getUserId, userId));
    }

    /**
     * 统计菜单或菜单按钮权限被用户覆盖权限引用的次数。
     *
     * @param menuId        菜单 ID
     * @param permissionIds 菜单下按钮权限 ID 集合
     * @return 引用数量
     */
    default long countByMenuIdOrPermissionIds(Long menuId, Collection<Long> permissionIds) {
        if (menuId == null) {
            return 0;
        }
        LambdaQueryWrapper<SysUserPermissionOverride> wrapper = new LambdaQueryWrapper<SysUserPermissionOverride>()
                .eq(SysUserPermissionOverride::getMenuId, menuId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            wrapper.or().in(SysUserPermissionOverride::getMenuPermissionId, permissionIds);
        }
        return selectCount(wrapper);
    }
}
