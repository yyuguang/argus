package com.lnzz.argus.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SecurityAuditResult;
import com.lnzz.argus.system.entity.SysSecurityAuditLog;
import com.lnzz.argus.system.mapper.SysSecurityAuditLogMapper;
import com.lnzz.argus.security.CurrentUser;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全审计服务。
 * <p>所有后台登录、权限拒绝和管理写操作都通过此服务追加审计日志。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SysSecurityAuditLogMapper auditLogMapper;

    /**
     * 记录成功审计事件。
     *
     * @param action       操作编码
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param before       变更前快照
     * @param after        变更后快照
     */
    @Override
    public void success(String action, SecurityAuditResourceType resourceType, Object resourceId,
                        Object before, Object after) {
        write(action, resourceType, resourceId, SecurityAuditResult.SUCCESS, null, before, after);
    }

    /**
     * 记录失败审计事件。
     *
     * @param action       操作编码
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param reason       失败原因
     * @param before       变更前快照
     * @param after        变更后快照
     */
    @Override
    public void failed(String action, SecurityAuditResourceType resourceType, Object resourceId,
                       String reason, Object before, Object after) {
        write(action, resourceType, resourceId, SecurityAuditResult.FAILED, reason, before, after);
    }

    private void write(String action, SecurityAuditResourceType resourceType, Object resourceId,
                       SecurityAuditResult result, String failureReason, Object before, Object after) {
        HttpServletRequest request = currentRequest();
        CurrentUser user = LoginUtil.currentUser().orElse(null);
        SysSecurityAuditLog auditLog = new SysSecurityAuditLog();
        auditLog.setActorUserId(user == null ? null : user.userId());
        auditLog.setActorUsername(LoginUtil.currentUsernameOrAnonymous());
        auditLog.setAction(action);
        auditLog.setResourceType(resourceType.name());
        auditLog.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        auditLog.setBeforeJson(before == null ? null : JSON.toJSONString(before));
        auditLog.setAfterJson(after == null ? null : JSON.toJSONString(after));
        auditLog.setResult(result.name());
        auditLog.setFailureReason(failureReason);
        auditLog.setClientIp(request == null ? null : request.getRemoteAddr());
        auditLog.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        auditLog.setTraceId(traceId(request));
        auditLogMapper.insert(auditLog);
        log.debug("写入安全审计日志: action={}, resourceType={}, resourceId={}, result={}, traceId={}",
                action, resourceType, auditLog.getResourceId(), result, auditLog.getTraceId());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String traceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("X-Trace-Id");
        if (value == null || value.isBlank()) {
            value = request.getHeader("X-Request-Id");
        }
        return value;
    }
}
