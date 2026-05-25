package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 系统用户 Mapper。
 * <p>封装用户表常用查询条件，Service 层只保留业务编排和校验。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户 ID 查询未软删除用户。
     *
     * @param userId 用户 ID
     * @return 未删除用户；不存在时返回 null
     */
    default SysUser selectNonDeletedById(Long userId) {
        if (userId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 按登录账号查询未软删除用户。
     *
     * @param username 登录账号
     * @return 未删除用户；不存在时返回 null
     */
    default SysUser selectNonDeletedByUsername(String username) {
        if (!hasText(username)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 按登录账号查询用户，包含已软删除数据。
     * <p>用于唯一性校验，避免软删除账号被重复创建导致初始化数据冲突。</p>
     *
     * @param username 登录账号
     * @return 匹配用户；不存在时返回 null
     */
    default SysUser selectByUsernameIncludeDeleted(String username) {
        if (!hasText(username)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .last("limit 1"));
    }

    /**
     * 按邮箱查询用户，包含已软删除数据。
     *
     * @param email 邮箱
     * @return 匹配用户；不存在时返回 null
     */
    default SysUser selectByEmailIncludeDeleted(String email) {
        if (!hasText(email)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email.trim())
                .last("limit 1"));
    }

    /**
     * 分页查询后台用户列表。
     * <p>封装软删除过滤、部门/用户名/账号/状态/角色用户集过滤和稳定排序。</p>
     *
     * @param page          分页对象
     * @param departmentId  部门 ID
     * @param displayName   显示名称
     * @param username      登录账号
     * @param status        用户状态
     * @param filterUserIds 外部关系筛选后的用户 ID 集合
     * @return 用户分页结果
     */
    default Page<SysUser> selectAdminPage(Page<SysUser> page,
                                          Long departmentId,
                                          String displayName,
                                          String username,
                                          String status,
                                          List<Long> filterUserIds) {
        return selectPage(page, new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(departmentId != null, SysUser::getDepartmentId, departmentId)
                .like(hasText(displayName), SysUser::getDisplayName, displayName)
                .like(hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .in(filterUserIds != null && !filterUserIds.isEmpty(), SysUser::getId, filterUserIds)
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getId));
    }

    /**
     * 统计指定用户集合中启用且未软删除的用户数量。
     *
     * @param userIds       用户 ID 集合
     * @param enabledStatus 启用状态枚举名
     * @return 匹配用户数量
     */
    default long countEnabledNonDeletedByIds(List<Long> userIds, String enabledStatus) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysUser::getStatus, enabledStatus));
    }

    /**
     * 统计指定用户集合中未软删除的用户数量。
     *
     * @param userIds 用户 ID 集合
     * @return 匹配用户数量
     */
    default long countNonDeletedByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 统计部门下未软删除用户数量。
     *
     * @param departmentId 部门 ID
     * @return 部门关联用户数量
     */
    default long countNonDeletedByDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDepartmentId, departmentId)
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 查询全部未软删除用户并按 ID 升序输出。
     *
     * @return 未删除用户列表
     */
    default List<SysUser> selectNonDeletedOrdered() {
        return selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(SysUser::getId));
    }

    /**
     * 软删除指定用户。
     * <p>
     * 项目启用了 MyBatis-Plus 全局逻辑删除字段，常规 {@code updateById} 不会直接写入逻辑删除列，
     * 因此删除操作统一下沉到 Mapper 层，显式维护 {@code is_deleted}、审计字段和版本号。
     * </p>
     *
     * @param userId   用户 ID
     * @param operator 当前操作者登录账号
     * @return 受影响行数，1 表示删除成功，0 表示用户不存在或已被删除
     */
    default int softDeleteById(Long userId, String operator) {
        if (userId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(SysUser::getIsDeleted, SystemDataConstants.DELETED)
                .set(SysUser::getUpdateBy, operator)
                .set(SysUser::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
