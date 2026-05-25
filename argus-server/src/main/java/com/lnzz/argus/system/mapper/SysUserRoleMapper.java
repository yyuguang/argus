package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户角色关系 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询用户拥有的角色 ID。
     *
     * @param userId 用户 ID
     * @return 去重后的角色 ID 列表
     */
    default List<Long> selectRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
    }

    /**
     * 查询拥有指定角色的用户 ID。
     *
     * @param roleId 角色 ID
     * @return 去重后的用户 ID 列表
     */
    default List<Long> selectUserIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId))
                .stream()
                .map(SysUserRole::getUserId)
                .distinct()
                .toList();
    }

    /**
     * 统计用户与角色的绑定关系数量。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 绑定数量
     */
    default long countByUserIdAndRoleId(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId));
    }

    /**
     * 删除指定用户的全部角色关系。
     *
     * @param userId 用户 ID
     */
    default void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    }
}
