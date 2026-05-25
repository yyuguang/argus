package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 动态路由元信息。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record RouteMeta(
        String title,
        String icon,
        String activeMenu,
        Boolean hidden,
        Boolean alwaysShow,
        Boolean noCache,
        Boolean breadcrumb,
        Boolean affix,
        Boolean noTagsView,
        Boolean canTo,
        List<String> permission
) {
}
