package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 后台登录响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record LoginResponse(
        String id,
        String username,
        String account,
        String email,
        String role,
        String roleId,
        List<String> roleCodes,
        List<String> permissions,
        String tokenKey,
        String token,
        String expiresAt
) {
}
