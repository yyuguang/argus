package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 菜单创建或修改请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record MenuRequest(
        Integer type,
        String parentId,
        String path,
        String component,
        String name,
        String redirect,
        Integer status,
        Integer sortOrder,
        RouteMeta meta,
        List<MenuPermissionDto> permissionList
) {
}
