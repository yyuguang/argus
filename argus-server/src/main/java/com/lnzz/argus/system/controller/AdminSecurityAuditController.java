package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.system.model.SecurityAuditLogPageRequest;
import com.lnzz.argus.system.model.SecurityAuditLogResponse;
import com.lnzz.argus.system.service.SysSecurityAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安全审计查询 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/security-audit-logs")
@RequiredArgsConstructor
public class AdminSecurityAuditController {

    private final SysSecurityAuditQueryService auditQueryService;

    /**
     * 分页查询安全审计日志。
     *
     * @param request 安全审计日志分页查询请求
     * @return 安全审计日志分页结果
     */
    @PostMapping("/page")
    @RequirePermission(SystemPermissionCodes.AUDIT_VIEW)
    public Result<PageResult<SecurityAuditLogResponse>> page(
            @RequestBody(required = false) SecurityAuditLogPageRequest request) {
        SecurityAuditLogPageRequest safeRequest = request == null ? new SecurityAuditLogPageRequest() : request;
        return Result.success(auditQueryService.page(
                safeRequest.getActorUsername(),
                safeRequest.getAction(),
                safeRequest.getResourceType(),
                safeRequest.getResult(),
                safeRequest.getStartTime(),
                safeRequest.getEndTime(),
                safeRequest.effectivePageNo(),
                safeRequest.getPageSize()));
    }
}
