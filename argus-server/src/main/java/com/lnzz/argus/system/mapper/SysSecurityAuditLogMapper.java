package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.system.entity.SysSecurityAuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 系统安全审计日志 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysSecurityAuditLogMapper extends BaseMapper<SysSecurityAuditLog> {

    /**
     * 分页查询安全审计日志。
     *
     * @param page          分页对象
     * @param actorUsername 操作人账号
     * @param action        操作编码
     * @param resourceType  资源类型
     * @param result        审计结果
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 审计日志分页结果
     */
    default Page<SysSecurityAuditLog> selectAdminPage(Page<SysSecurityAuditLog> page,
                                                      String actorUsername,
                                                      String action,
                                                      String resourceType,
                                                      String result,
                                                      LocalDateTime startTime,
                                                      LocalDateTime endTime) {
        return selectPage(page, new LambdaQueryWrapper<SysSecurityAuditLog>()
                .like(hasText(actorUsername), SysSecurityAuditLog::getActorUsername, actorUsername)
                .eq(hasText(action), SysSecurityAuditLog::getAction, action)
                .eq(hasText(resourceType), SysSecurityAuditLog::getResourceType, resourceType)
                .eq(hasText(result), SysSecurityAuditLog::getResult, result)
                .ge(startTime != null, SysSecurityAuditLog::getCreateTime, startTime)
                .le(endTime != null, SysSecurityAuditLog::getCreateTime, endTime)
                .orderByDesc(SysSecurityAuditLog::getCreateTime)
                .orderByDesc(SysSecurityAuditLog::getId));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
