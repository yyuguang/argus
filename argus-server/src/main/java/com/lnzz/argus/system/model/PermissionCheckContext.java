package com.lnzz.argus.system.model;

import java.time.LocalDateTime;

/**
 * 权限裁决上下文。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record PermissionCheckContext(Long userId, String clientIp, LocalDateTime now) {
}
