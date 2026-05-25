package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 角色创建或修改请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record RoleRequest(
        String roleCode,
        String roleName,
        Integer status,
        String remark,
        List<RoleMenuGrant> menu
) {
}
