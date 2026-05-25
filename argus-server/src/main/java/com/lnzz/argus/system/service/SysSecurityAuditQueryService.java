package com.lnzz.argus.system.service;

import com.lnzz.argus.system.model.SecurityAuditLogResponse;
import com.lnzz.argus.common.result.PageResult;

/**
 * @classname: SysSecurityAuditQueryService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 后台安全审计查询接口。
 */
public interface SysSecurityAuditQueryService {

    /**
     * 分页查询后台安全审计日志。
     *
     * @param actorUsername 操作人账号
     * @param action        操作编码
     * @param resourceType  资源类型
     * @param result        审计结果
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 审计日志分页结果
     */
    PageResult<SecurityAuditLogResponse> page(String actorUsername,
                                             String action,
                                             String resourceType,
                                             String result,
                                             String startTime,
                                             String endTime,
                                             Integer pageNo,
                                             Integer pageSize);
}
