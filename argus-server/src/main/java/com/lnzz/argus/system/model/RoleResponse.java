package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 角色响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record RoleResponse(
        String id,
        String roleCode,
        String roleName,
        Integer status,
        String createTime,
        String remark,
        List<RoleMenuGrant> menu
) {
}
