package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SysMenuType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.system.entity.SysMenuPermission;
import com.lnzz.argus.system.mapper.SysMenuMapper;
import com.lnzz.argus.system.mapper.SysMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysUserPermissionOverrideMapper;
import com.lnzz.argus.system.model.MenuOrderRequest;
import com.lnzz.argus.system.model.MenuPermissionDto;
import com.lnzz.argus.system.model.MenuRequest;
import com.lnzz.argus.system.model.MenuResponse;
import com.lnzz.argus.system.model.MenuStatusRequest;
import com.lnzz.argus.system.model.RouteMeta;
import com.lnzz.argus.system.service.SecurityAuditService;
import com.lnzz.argus.system.service.SysMenuAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单与按钮权限管理服务。
 * <p>菜单数据既服务管理页，也服务动态路由生成。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuAdminServiceImpl implements SysMenuAdminService {

    private final SysMenuMapper menuMapper;
    private final SysMenuPermissionMapper menuPermissionMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMenuPermissionMapper roleMenuPermissionMapper;
    private final SysUserPermissionOverrideMapper userPermissionOverrideMapper;
    private final SecurityAuditService auditService;

    /**
     * 查询菜单树和按钮权限列表。
     *
     * @return 菜单树
     */
    @Override
    public List<MenuResponse> tree() {
        List<SysMenu> menus = menuMapper.selectNonDeletedOrdered();
        return buildChildren(null, menus);
    }

    /**
     * 创建菜单并同步按钮权限。
     *
     * @param request 菜单请求
     * @return 新菜单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuResponse create(MenuRequest request) {
        validate(request, null);
        SysMenu menu = new SysMenu();
        applyRequest(menu, request);
        menu.setIsDeleted(SystemAdminSupport.NOT_DELETED);
        menu.setVersion(0);
        menuMapper.insert(menu);
        syncPermissionList(menu.getId(), request.permissionList());
        auditService.success("MENU_CREATE", SecurityAuditResourceType.MENU, menu.getId(), null, menu);
        log.info("创建后台菜单: menuId={}, routeName={}, path={}, parentId={}, type={}",
                menu.getId(), menu.getRouteName(), menu.getRoutePath(), menu.getParentId(), menu.getMenuType());
        return toResponse(menu, List.of());
    }

    /**
     * 更新菜单并按需同步按钮权限。
     *
     * @param menuId  菜单 ID
     * @param request 菜单请求
     * @return 更新后菜单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuResponse update(Long menuId, MenuRequest request) {
        SysMenu menu = requireMenu(menuId);
        validate(request, menuId);
        SysMenu before = copy(menu);
        applyRequest(menu, request);
        menuMapper.updateById(menu);
        if (request.permissionList() != null) {
            syncPermissionList(menuId, request.permissionList());
        }
        auditService.success("MENU_UPDATE", SecurityAuditResourceType.MENU, menuId, before, menu);
        log.info("更新后台菜单: menuId={}, routeName={}, path={}, status={}, permissionChanged={}",
                menuId, menu.getRouteName(), menu.getRoutePath(), menu.getStatus(), request.permissionList() != null);
        return toResponse(menu, List.of());
    }

    /**
     * 批量删除菜单，存在授权引用时拒绝删除。
     *
     * @param menuIds 菜单 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单 ID 不能为空");
        }
        for (Long menuId : menuIds) {
            SysMenu menu = requireMenu(menuId);
            assertNoDeleteReferences(menuId);
            SysMenu before = copy(menu);
            int deletedRows = menuMapper.softDeleteById(menuId, LoginUtil.currentUsernameOrSystem());
            if (deletedRows < 1) {
                log.warn("删除菜单失败，记录不存在或已删除: menuId={}, routeName={}", menuId, menu.getRouteName());
                throw new BizException(ResultCode.NOT_FOUND, "菜单不存在或已删除: " + menuId);
            }
            menuPermissionMapper.selectNonDeletedByMenuIdOrdered(menuId)
                    .forEach(permission -> {
                        permission.setStatus(SysStatus.DISABLED.name());
                        menuPermissionMapper.updateById(permission);
                    });
            auditService.success("MENU_DELETE", SecurityAuditResourceType.MENU, menuId, before, null);
            log.info("删除后台菜单: menuId={}, routeName={}", menuId, menu.getRouteName());
        }
    }

    /**
     * 批量更新菜单状态。
     *
     * @param request 状态更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(MenuStatusRequest request) {
        List<Long> ids = SystemAdminSupport.parseIds(request == null ? null : request.ids(), "菜单ID");
        if (ids.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单 ID 不能为空");
        }
        String status = SysStatus.fromApi(request.status());
        for (Long id : ids) {
            SysMenu menu = requireMenu(id);
            SysMenu before = copy(menu);
            menu.setStatus(status);
            menuMapper.updateById(menu);
            auditService.success("MENU_STATUS_UPDATE", SecurityAuditResourceType.MENU, id, before, menu);
            log.info("更新后台菜单状态: menuId={}, routeName={}, nextStatus={}",
                    id, menu.getRouteName(), status);
        }
    }

    /**
     * 批量更新菜单排序。
     *
     * @param request 排序请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(MenuOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单排序项不能为空");
        }
        for (var item : request.items()) {
            Long menuId = SystemAdminSupport.parseId(item.id(), "菜单ID");
            SysMenu menu = requireMenu(menuId);
            menu.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder());
            menuMapper.updateById(menu);
            log.info("更新后台菜单排序: menuId={}, routeName={}, sortOrder={}",
                    menuId, menu.getRouteName(), menu.getSortOrder());
        }
        auditService.success("MENU_ORDER_UPDATE", SecurityAuditResourceType.MENU, "batch", null, request);
    }

    /**
     * 根据 ID 查询菜单，不存在时抛出业务异常。
     *
     * @param menuId 菜单 ID
     * @return 菜单实体
     */
    @Override
    public SysMenu requireMenu(Long menuId) {
        SysMenu menu = menuMapper.selectNonDeletedById(menuId);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在: " + menuId);
        }
        return menu;
    }

    private void validate(MenuRequest request, Long excludedId) {
        if (request == null || !StringUtils.hasText(request.path()) || !StringUtils.hasText(request.name())) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单 path 和 name 不能为空");
        }
        String menuType = SysMenuType.fromApi(request.type());
        validateComponent(menuType, request.component());
        SysMenu byName = menuMapper.selectByRouteNameIncludeDeleted(request.name());
        if (byName != null && !Objects.equals(byName.getId(), excludedId)) {
            throw new BizException(ResultCode.ADMIN_MENU_NAME_CONFLICT);
        }
        Long parentId = SystemAdminSupport.parseId(request.parentId(), "父菜单ID");
        SysMenu byPath = menuMapper.selectByParentAndPathIncludeDeleted(parentId, request.path());
        if (byPath != null && !Objects.equals(byPath.getId(), excludedId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "同级菜单 path 已存在");
        }
        boolean parentMissing = parentId != null && menuMapper.selectNonDeletedById(parentId) == null;
        if (parentMissing) {
            throw new BizException(ResultCode.NOT_FOUND, "父菜单不存在: " + parentId);
        }
        validatePermissionList(request.permissionList(), excludedId);
    }

    private void validateComponent(String menuType, String component) {
        String value = SystemAdminSupport.trimToNull(component);
        if (SysMenuType.directory(menuType)) {
            if (!"#".equals(value) && !"##".equals(value)) {
                throw new BizException(ResultCode.PARAM_ERROR, "目录组件必须是 # 或 ##");
            }
            return;
        }
        if (!StringUtils.hasText(value) || !value.startsWith("views/")) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单组件必须使用 views/... 路径");
        }
    }

    private void validatePermissionList(List<MenuPermissionDto> permissionList, Long menuId) {
        if (permissionList == null || permissionList.isEmpty()) {
            return;
        }
        Set<String> actions = new LinkedHashSet<>();
        for (MenuPermissionDto item : permissionList) {
            if (!StringUtils.hasText(item.label()) || !StringUtils.hasText(item.value())
                    || !StringUtils.hasText(item.permissionCode())) {
                throw new BizException(ResultCode.PARAM_ERROR, "按钮权限 label、value、permissionCode 不能为空");
            }
            if (!actions.add(item.value().trim())) {
                throw new BizException(ResultCode.PARAM_ERROR, "同一菜单下按钮动作重复: " + item.value());
            }
            SysMenuPermission existing = menuPermissionMapper.selectByPermissionCodeIncludeDeleted(item.permissionCode());
            Long itemId = SystemAdminSupport.parseId(item.id(), "按钮权限ID");
            if (existing != null && !Objects.equals(existing.getId(), itemId)
                    && (menuId == null || !Objects.equals(existing.getMenuId(), menuId))) {
                throw new BizException(ResultCode.ADMIN_PERMISSION_CODE_CONFLICT);
            }
        }
    }

    private void applyRequest(SysMenu menu, MenuRequest request) {
        RouteMeta meta = request.meta();
        menu.setParentId(SystemAdminSupport.parseId(request.parentId(), "父菜单ID"));
        menu.setMenuType(SysMenuType.fromApi(request.type()));
        menu.setRoutePath(request.path().trim());
        menu.setRouteName(request.name().trim());
        menu.setComponentPath(SystemAdminSupport.trimToNull(request.component()));
        menu.setRedirectPath(SystemAdminSupport.trimToNull(request.redirect()));
        menu.setTitle(meta != null && StringUtils.hasText(meta.title()) ? meta.title().trim() : request.name().trim());
        menu.setIcon(meta == null ? null : SystemAdminSupport.trimToNull(meta.icon()));
        menu.setActiveMenu(meta == null ? null : SystemAdminSupport.trimToNull(meta.activeMenu()));
        menu.setHidden(meta != null && Boolean.TRUE.equals(meta.hidden()));
        menu.setAlwaysShow(meta != null && Boolean.TRUE.equals(meta.alwaysShow()));
        menu.setNoCache(meta != null && Boolean.TRUE.equals(meta.noCache()));
        menu.setBreadcrumb(meta == null || meta.breadcrumb() == null || Boolean.TRUE.equals(meta.breadcrumb()));
        menu.setAffix(meta != null && Boolean.TRUE.equals(meta.affix()));
        menu.setNoTagsView(meta != null && Boolean.TRUE.equals(meta.noTagsView()));
        menu.setCanTo(meta != null && Boolean.TRUE.equals(meta.canTo()));
        menu.setStatus(SysStatus.fromApi(request.status()));
        menu.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void syncPermissionList(Long menuId, List<MenuPermissionDto> permissionList) {
        if (permissionList == null) {
            return;
        }
        Map<String, SysMenuPermission> existingByAction = menuPermissionMapper.selectByMenuIdIncludeDeleted(menuId)
                .stream()
                .collect(Collectors.toMap(SysMenuPermission::getActionValue, item -> item, (a, b) -> a));
        Set<String> requestedActions = new LinkedHashSet<>();
        int order = 10;
        for (MenuPermissionDto item : permissionList) {
            String action = item.value().trim();
            requestedActions.add(action);
            SysMenuPermission permission = existingByAction.getOrDefault(action, new SysMenuPermission());
            permission.setMenuId(menuId);
            permission.setLabel(item.label().trim());
            permission.setActionValue(action);
            permission.setPermissionCode(item.permissionCode().trim());
            permission.setStatus(item.status() == null ? SysStatus.ENABLED.name() : SysStatus.fromApi(item.status()));
            permission.setSortOrder(order);
            permission.setIsDeleted(SystemAdminSupport.NOT_DELETED);
            if (permission.getId() == null) {
                permission.setVersion(0);
                menuPermissionMapper.insert(permission);
            } else {
                menuPermissionMapper.updateById(permission);
            }
            order += 10;
        }
        for (SysMenuPermission permission : existingByAction.values()) {
            if (!requestedActions.contains(permission.getActionValue())) {
                permission.setStatus(SysStatus.DISABLED.name());
                menuPermissionMapper.updateById(permission);
            }
        }
    }

    private void assertNoDeleteReferences(Long menuId) {
        long childCount = menuMapper.countNonDeletedChildren(menuId);
        if (childCount > 0) {
            log.warn("删除菜单被拒绝: menuId={}, reason=存在子菜单", menuId);
            throw new BizException(ResultCode.PARAM_ERROR, "菜单存在子菜单，不能删除");
        }
        long roleMenuCount = roleMenuMapper.countByMenuId(menuId);
        long rolePermissionCount = roleMenuPermissionMapper.countByMenuId(menuId);
        List<Long> permissionIds = menuPermissionMapper.selectByMenuIdIncludeDeleted(menuId).stream()
                .map(SysMenuPermission::getId)
                .toList();
        long overrideCount = userPermissionOverrideMapper.countByMenuIdOrPermissionIds(menuId, permissionIds);
        if (roleMenuCount + rolePermissionCount + overrideCount > 0) {
            log.warn("删除菜单被拒绝: menuId={}, roleMenuCount={}, rolePermissionCount={}, overrideCount={}",
                    menuId, roleMenuCount, rolePermissionCount, overrideCount);
            throw new BizException(ResultCode.PARAM_ERROR, "菜单已有角色授权或用户覆盖权限，不能删除");
        }
    }

    private List<MenuResponse> buildChildren(Long parentId, List<SysMenu> menus) {
        return menus.stream()
                .filter(item -> Objects.equals(parentId, item.getParentId()))
                .sorted(Comparator.comparing(SysMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .map(item -> toResponse(item, buildChildren(item.getId(), menus)))
                .toList();
    }

    private MenuResponse toResponse(SysMenu menu, List<MenuResponse> children) {
        List<MenuPermissionDto> permissions = menuPermissionMapper.selectNonDeletedByMenuIdOrdered(menu.getId())
                .stream()
                .map(item -> new MenuPermissionDto(
                        String.valueOf(item.getId()),
                        item.getLabel(),
                        item.getActionValue(),
                        item.getPermissionCode(),
                        SysStatus.toApi(item.getStatus())))
                .toList();
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
                null);
        return new MenuResponse(
                String.valueOf(menu.getId()),
                SysMenuType.toApi(menu.getMenuType()),
                SystemAdminSupport.stringId(menu.getParentId()),
                menu.getRoutePath(),
                menu.getComponentPath(),
                menu.getRedirectPath(),
                menu.getRouteName(),
                SysStatus.toApi(menu.getStatus()),
                menu.getTitle(),
                meta,
                permissions.isEmpty() ? null : permissions,
                children == null || children.isEmpty() ? null : children);
    }

    private SysMenu copy(SysMenu source) {
        SysMenu target = new SysMenu();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setMenuType(source.getMenuType());
        target.setRoutePath(source.getRoutePath());
        target.setRouteName(source.getRouteName());
        target.setComponentPath(source.getComponentPath());
        target.setTitle(source.getTitle());
        target.setStatus(source.getStatus());
        target.setSortOrder(source.getSortOrder());
        return target;
    }
}
