package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.system.entity.SysRole;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 系统角色 Mapper。
 * <p>封装角色表常用查询条件，避免 Service 重复拼装数据过滤条件。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 按角色 ID 查询未软删除角色。
     *
     * @param roleId 角色 ID
     * @return 未删除角色；不存在时返回 null
     */
    default SysRole selectNonDeletedById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, roleId)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 按角色编码查询角色，包含已软删除数据。
     *
     * @param roleCode 角色编码
     * @return 匹配角色；不存在时返回 null
     */
    default SysRole selectByRoleCodeIncludeDeleted(String roleCode) {
        if (!hasText(roleCode)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode.trim())
                .last("limit 1"));
    }

    /**
     * 查询未软删除的超级管理员角色。
     *
     * @param superAdminRoleCode 超级管理员角色编码
     * @return 超级管理员角色；不存在时返回 null
     */
    default SysRole selectActiveSuperAdminRole(String superAdminRoleCode) {
        if (!hasText(superAdminRoleCode)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, superAdminRoleCode)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 查询指定 ID 集合中启用且未软删除的角色。
     *
     * @param roleIds       角色 ID 集合
     * @param enabledStatus 启用状态枚举名
     * @return 角色列表
     */
    default List<SysRole> selectEnabledByIds(Collection<Long> roleIds, String enabledStatus) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysRole::getStatus, enabledStatus));
    }

    /**
     * 查询指定 ID 集合中未软删除的角色。
     *
     * @param roleIds 角色 ID 集合
     * @return 角色列表
     */
    default List<SysRole> selectNonDeletedByIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 统计指定 ID 集合中未软删除的角色数量。
     *
     * @param roleIds 角色 ID 集合
     * @return 匹配角色数量
     */
    default long countNonDeletedByIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 软删除指定角色。
     * <p>
     * 项目启用了 MyBatis-Plus 全局逻辑删除字段，常规 {@code updateById} 不会直接更新
     * {@code is_deleted}，因此删除语义在 Mapper 层集中封装，显式维护逻辑删除标记、
     * 审计字段和版本号，避免服务层误以为删除成功。
     * </p>
     *
     * @param roleId   角色 ID
     * @param operator 当前操作者登录账号
     * @return 受影响行数，1 表示删除成功，0 表示角色不存在或已删除
     */
    default int softDeleteById(Long roleId, String operator) {
        if (roleId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<SysRole>()
                .eq(SysRole::getId, roleId)
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(SysRole::getIsDeleted, SystemDataConstants.DELETED)
                .set(SysRole::getUpdateBy, operator)
                .set(SysRole::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    /**
     * 分页查询后台角色。
     *
     * @param page     分页对象
     * @param roleName 角色名称
     * @param status   角色状态
     * @return 角色分页结果
     */
    default Page<SysRole> selectAdminPage(Page<SysRole> page, String roleName, String status) {
        return selectPage(page, new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .like(hasText(roleName), SysRole::getRoleName, roleName)
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getId));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
