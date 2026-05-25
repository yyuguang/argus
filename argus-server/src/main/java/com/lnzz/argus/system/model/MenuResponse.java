package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 菜单响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record MenuResponse(
        String id,
        Integer type,
        String parentId,
        String path,
        String component,
        String redirect,
        String name,
        Integer status,
        String title,
        RouteMeta meta,
        List<MenuPermissionDto> permissionList,
        List<MenuResponse> children
) {
}
