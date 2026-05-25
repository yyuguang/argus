package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 用户创建或修改请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record UserRequest(
        String username,
        String account,
        String email,
        String phone,
        String password,
        Integer status,
        DepartmentRef department,
        List<String> role
) {
}
