package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysPermissionCondition;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

/**
 * 条件权限 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysPermissionConditionMapper extends BaseMapper<SysPermissionCondition> {

    /**
     * 按条件权限 ID 查询未软删除条件。
     *
     * @param conditionId 条件权限 ID
     * @return 未删除条件权限；不存在时返回 null
     */
    default SysPermissionCondition selectNonDeletedById(Long conditionId) {
        if (conditionId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysPermissionCondition>()
                .eq(SysPermissionCondition::getId, conditionId)
                .eq(SysPermissionCondition::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }
}
