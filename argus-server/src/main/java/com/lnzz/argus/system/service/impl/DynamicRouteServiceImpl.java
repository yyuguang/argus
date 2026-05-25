package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.system.mapper.SysMenuMapper;
import com.lnzz.argus.system.model.RouteMeta;
import com.lnzz.argus.system.model.RouteRecord;
import com.lnzz.argus.system.service.DynamicRouteService;
import com.lnzz.argus.system.service.PermissionDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vue Admin 动态路由构造服务。
 * <p>输出结构严格贴合 AppCustomRouteRecordRaw，组件字段保留 #、##、views/... 约定。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteServiceImpl implements DynamicRouteService {

    private final SysMenuMapper menuMapper;
    private final PermissionDecisionService permissionDecisionService;

    /**
     * 为当前用户构建动态路由。
     *
     * @param userId   用户 ID
     * @param clientIp 客户端 IP
     * @return Vue Admin 动态路由树
     */
    @Override
    public List<RouteRecord> routesForUser(Long userId, String clientIp) {
        PermissionDecisionService.AuthorizationSnapshot snapshot =
                permissionDecisionService.authorizationFor(userId, clientIp, LocalDateTime.now());
        List<SysMenu> menus = menuMapper.selectEnabledOrdered(SysStatus.ENABLED.name());
        Map<Long, SysMenu> menuMap = menus.stream().collect(Collectors.toMap(SysMenu::getId, item -> item));
        Set<Long> authorizedIds = withAncestors(snapshot.menuIds(), menuMap);
        List<SysMenu> visibleMenus = menus.stream()
                .filter(menu -> authorizedIds.contains(menu.getId()))
                .toList();
        List<RouteRecord> routes = buildChildren(null, visibleMenus, snapshot).stream()
                .filter(this::notEmptyDirectory)
                .toList();
        log.info("构建后台动态路由: userId={}, authorizedMenuCount={}, visibleMenuCount={}, routeCount={}",
                userId, authorizedIds.size(), visibleMenus.size(), routes.size());
        return routes;
    }

    private Set<Long> withAncestors(Set<Long> menuIds, Map<Long, SysMenu> menuMap) {
        Set<Long> result = new HashSet<>(menuIds);
        for (Long menuId : menuIds) {
            SysMenu current = menuMap.get(menuId);
            while (current != null && current.getParentId() != null) {
                result.add(current.getParentId());
                current = menuMap.get(current.getParentId());
            }
        }
        return result;
    }

    private List<RouteRecord> buildChildren(Long parentId,
                                            List<SysMenu> menus,
                                            PermissionDecisionService.AuthorizationSnapshot snapshot) {
        return menus.stream()
                .filter(menu -> Objects.equals(parentId, menu.getParentId()))
                .sorted(Comparator.comparing(SysMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .map(menu -> toRoute(menu, menus, snapshot))
                .filter(this::notEmptyDirectory)
                .toList();
    }

    private RouteRecord toRoute(SysMenu menu,
                                List<SysMenu> menus,
                                PermissionDecisionService.AuthorizationSnapshot snapshot) {
        List<RouteRecord> children = buildChildren(menu.getId(), menus, snapshot);
        List<String> actions = new ArrayList<>(snapshot.menuActions().getOrDefault(menu.getId(), Set.of()));
        actions.sort(String::compareTo);
        RouteMeta meta = new RouteMeta(
                menu.getTitle(),
                menu.getIcon(),
                menu.getActiveMenu(),
                menu.getHidden(),
                menu.getAlwaysShow(),
                menu.getNoCache(),
                menu.getBreadcrumb(),
                menu.getAffix(),
                menu.getNoTagsView(),
                menu.getCanTo(),
                actions.isEmpty() ? null : actions);
        return new RouteRecord(
                menu.getRoutePath(),
                menu.getComponentPath(),
                menu.getRedirectPath(),
                menu.getRouteName(),
                meta,
                children.isEmpty() ? null : children);
    }

    private boolean notEmptyDirectory(RouteRecord route) {
        if (route == null) {
            return false;
        }
        boolean directory = "#".equals(route.component()) || "##".equals(route.component());
        return !directory || (route.children() != null && !route.children().isEmpty());
    }
}
