package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 用户响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record UserResponse(
        String id,
        String username,
        String account,
        String email,
        String phone,
        Integer status,
        String createTime,
        List<String> role,
        List<String> roleNames,
        DepartmentOption department
) {
}
