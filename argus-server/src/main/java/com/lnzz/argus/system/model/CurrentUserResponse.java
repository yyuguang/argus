package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 当前登录用户响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record CurrentUserResponse(
        String id,
        String username,
        String account,
        String email,
        Integer status,
        DepartmentOption department,
        List<String> roleCodes,
        List<String> permissions
) {
}
