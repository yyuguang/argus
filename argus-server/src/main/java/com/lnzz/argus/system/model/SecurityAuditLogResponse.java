package com.lnzz.argus.system.model;

/**
 * 安全审计日志响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record SecurityAuditLogResponse(
        String id,
        String actorUsername,
        String action,
        String resourceType,
        String resourceId,
        String result,
        String clientIp,
        String traceId,
        String createTime
) {
}
