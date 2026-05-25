package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 动态路由响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record RouteRecord(
        String path,
        String component,
        String redirect,
        String name,
        RouteMeta meta,
        List<RouteRecord> children
) {
}
