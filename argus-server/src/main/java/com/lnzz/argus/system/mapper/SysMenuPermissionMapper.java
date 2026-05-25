package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysMenuPermission;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 菜单按钮权限 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysMenuPermissionMapper extends BaseMapper<SysMenuPermission> {

    /**
     * 按按钮权限 ID 查询未软删除按钮权限。
     *
     * @param permissionId 按钮权限 ID
     * @return 未删除按钮权限；不存在时返回 null
     */
    default SysMenuPermission selectNonDeletedById(Long permissionId) {
        if (permissionId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getId, permissionId)
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 查询启用且未软删除的按钮权限，并按菜单展示顺序排序。
     *
     * @param enabledStatus 启用状态枚举名
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectEnabledOrdered(String enabledStatus) {
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysMenuPermission::getStatus, enabledStatus)
                .orderByAsc(SysMenuPermission::getSortOrder)
                .orderByAsc(SysMenuPermission::getId));
    }

    /**
     * 查询指定按钮权限集合中启用且未软删除的记录。
     *
     * @param permissionIds 按钮权限 ID 集合
     * @param enabledStatus 启用状态枚举名
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectEnabledByIds(Collection<Long> permissionIds, String enabledStatus) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .in(SysMenuPermission::getId, permissionIds)
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysMenuPermission::getStatus, enabledStatus));
    }

    /**
     * 查询指定按钮权限集合中未软删除的记录。
     *
     * @param permissionIds 按钮权限 ID 集合
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectNonDeletedByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .in(SysMenuPermission::getId, permissionIds)
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 查询指定菜单下启用且未软删除的按钮权限。
     *
     * @param menuId        菜单 ID
     * @param enabledStatus 启用状态枚举名
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectEnabledByMenuId(Long menuId, String enabledStatus) {
        if (menuId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getMenuId, menuId)
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysMenuPermission::getStatus, enabledStatus)
                .orderByAsc(SysMenuPermission::getSortOrder)
                .orderByAsc(SysMenuPermission::getId));
    }

    /**
     * 查询指定菜单下未软删除的按钮权限。
     *
     * @param menuId 菜单 ID
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectNonDeletedByMenuIdOrdered(Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getMenuId, menuId)
                .eq(SysMenuPermission::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(SysMenuPermission::getSortOrder)
                .orderByAsc(SysMenuPermission::getId));
    }

    /**
     * 查询指定菜单下所有按钮权限，包含已软删除数据。
     *
     * @param menuId 菜单 ID
     * @return 按钮权限列表
     */
    default List<SysMenuPermission> selectByMenuIdIncludeDeleted(Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getMenuId, menuId));
    }

    /**
     * 按菜单和动作值查询按钮权限，包含已软删除数据。
     *
     * @param menuId      菜单 ID
     * @param actionValue 前端动作值
     * @return 匹配按钮权限；不存在时返回 null
     */
    default SysMenuPermission selectByMenuAndActionIncludeDeleted(Long menuId, String actionValue) {
        if (menuId == null || !hasText(actionValue)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getMenuId, menuId)
                .eq(SysMenuPermission::getActionValue, actionValue.trim())
                .last("limit 1"));
    }

    /**
     * 按后端权限编码查询按钮权限，包含已软删除数据。
     *
     * @param permissionCode 后端权限编码
     * @return 匹配按钮权限；不存在时返回 null
     */
    default SysMenuPermission selectByPermissionCodeIncludeDeleted(String permissionCode) {
        if (!hasText(permissionCode)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenuPermission>()
                .eq(SysMenuPermission::getPermissionCode, permissionCode.trim())
                .last("limit 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
