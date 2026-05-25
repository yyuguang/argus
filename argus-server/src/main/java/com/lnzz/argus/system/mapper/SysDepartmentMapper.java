package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.system.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统部门 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    /**
     * 按部门 ID 查询未软删除部门。
     *
     * @param departmentId 部门 ID
     * @return 未删除部门；不存在时返回 null
     */
    default SysDepartment selectNonDeletedById(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getId, departmentId)
                .eq(SysDepartment::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 查询全部未软删除部门，并按树展示顺序排序。
     *
     * @return 部门列表
     */
    default List<SysDepartment> selectNonDeletedOrdered() {
        return selectList(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(SysDepartment::getSortOrder)
                .orderByAsc(SysDepartment::getId));
    }

    /**
     * 分页查询后台部门。
     *
     * @param page           分页对象
     * @param departmentName 部门名称
     * @param status         部门状态
     * @return 部门分页结果
     */
    default Page<SysDepartment> selectAdminPage(Page<SysDepartment> page, String departmentName, String status) {
        return selectPage(page, new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .like(hasText(departmentName), SysDepartment::getDepartmentName, departmentName)
                .eq(status != null, SysDepartment::getStatus, status)
                .orderByAsc(SysDepartment::getSortOrder)
                .orderByAsc(SysDepartment::getId));
    }

    /**
     * 统计指定部门下未软删除子部门数量。
     *
     * @param parentId 父部门 ID
     * @return 子部门数量
     */
    default long countNonDeletedChildren(Long parentId) {
        if (parentId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getParentId, parentId)
                .eq(SysDepartment::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 软删除指定部门。
     * <p>
     * 项目启用了 MyBatis-Plus 全局逻辑删除字段，常规 {@code updateById} 会规避逻辑删除列，
     * 因此删除语义在 Mapper 层集中封装，显式写入 {@code is_deleted}、审计字段和版本号。
     * </p>
     *
     * @param departmentId 部门 ID
     * @param operator     当前操作者
     * @return 受影响行数，1 表示删除成功，0 表示部门不存在或已被删除
     */
    default int softDeleteById(Long departmentId, String operator) {
        if (departmentId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<SysDepartment>()
                .eq(SysDepartment::getId, departmentId)
                .eq(SysDepartment::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(SysDepartment::getIsDeleted, SystemDataConstants.DELETED)
                .set(SysDepartment::getUpdateBy, operator)
                .set(SysDepartment::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
