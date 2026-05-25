package com.lnzz.argus.security;

import java.util.List;

/**
 * 当前 Portal 登录用户上下文。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record CurrentUser(
        Long userId,
        String username,
        String displayName,
        String clientIp,
        String userAgent,
        Long sessionId,
        List<String> roleCodes
) {
}
