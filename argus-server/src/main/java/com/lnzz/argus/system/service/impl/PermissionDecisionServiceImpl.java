package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.common.enums.PermissionEffect;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.constant.SecurityConstants;
import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.system.entity.SysMenuPermission;
import com.lnzz.argus.system.entity.SysRole;
import com.lnzz.argus.system.entity.SysRoleMenu;
import com.lnzz.argus.system.entity.SysRoleMenuPermission;
import com.lnzz.argus.system.entity.SysUser;
import com.lnzz.argus.system.entity.SysUserPermissionOverride;
import com.lnzz.argus.system.mapper.SysMenuMapper;
import com.lnzz.argus.system.mapper.SysMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysRoleMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.mapper.SysUserPermissionOverrideMapper;
import com.lnzz.argus.system.mapper.SysUserRoleMapper;
import com.lnzz.argus.system.service.PermissionConditionEvaluator;
import com.lnzz.argus.system.service.PermissionDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限裁决服务。
 * <p>
 * 固定裁决顺序：
 * 账号不可用 -> 拒绝；
 * SUPER_ADMIN -> 放行；
 * 用户 DENY -> 拒绝；
 * 用户 ALLOW -> 放行；
 * 角色授权 -> 放行；
 * 未命中 -> 拒绝。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionDecisionServiceImpl implements PermissionDecisionService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysMenuPermissionMapper menuPermissionMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMenuPermissionMapper roleMenuPermissionMapper;
    private final SysUserPermissionOverrideMapper userPermissionOverrideMapper;
    private final PermissionConditionEvaluator conditionEvaluator;

    /**
     * 判断用户是否拥有某个后端完整权限码。
     *
     * @param userId         用户 ID
     * @param permissionCode 后端完整权限码
     * @param clientIp       客户端 IP
     * @return true 表示允许访问
     */
    @Override
    public boolean hasPermission(Long userId, String permissionCode, String clientIp) {
        AuthorizationSnapshot snapshot = authorizationFor(userId, clientIp, LocalDateTime.now());
        boolean allowed = snapshot.superAdmin() || snapshot.permissionCodes().contains(permissionCode);
        log.debug("后台权限裁决: userId={}, permissionCode={}, allowed={}, superAdmin={}, clientIp={}",
                userId, permissionCode, allowed, snapshot.superAdmin(), clientIp);
        return allowed;
    }

    /**
     * 构建当前用户完整授权快照，供动态路由、按钮权限和登录摘要使用。
     *
     * @param userId   用户 ID
     * @param clientIp 客户端 IP
     * @param now      当前时间
     * @return 授权快照
     */
    @Override
    public AuthorizationSnapshot authorizationFor(Long userId, String clientIp, LocalDateTime now) {
        SysUser user = userMapper.selectNonDeletedById(userId);
        if (user == null || !SysStatus.enabled(user.getStatus())) {
            log.warn("后台授权快照为空: userId={}, reason=用户不存在、已删除或未启用", userId);
            return AuthorizationSnapshot.empty(userId);
        }
        List<SysRole> roles = enabledRoles(userId);
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        boolean superAdmin = roleCodes.contains(SecurityConstants.SUPER_ADMIN_ROLE);

        List<SysMenu> menus = enabledMenus();
        Map<Long, SysMenu> menuMap = menus.stream().collect(Collectors.toMap(SysMenu::getId, item -> item));
        List<SysMenuPermission> permissions = enabledMenuPermissions();
        Map<Long, SysMenuPermission> permissionMap = permissions.stream()
                .collect(Collectors.toMap(SysMenuPermission::getId, item -> item));

        Set<Long> menuIds = new HashSet<>();
        Map<Long, Set<String>> menuActions = new HashMap<>();
        Set<String> permissionCodes = new HashSet<>();

        if (superAdmin) {
            menuIds.addAll(menuMap.keySet());
            for (SysMenuPermission permission : permissions) {
                addPermission(permission, menuActions, permissionCodes);
            }
            log.debug("后台授权快照完成: userId={}, superAdmin=true, roleCount={}, menuCount={}, permissionCount={}",
                    userId, roleIds.size(), menuIds.size(), permissionCodes.size());
            return new AuthorizationSnapshot(userId, roleIds, roleCodes, true, menuIds, menuActions, permissionCodes);
        }

        applyRoleGrants(roleIds, menuMap, permissionMap, menuIds, menuActions, permissionCodes);
        applyUserOverrides(userId, clientIp, now, menuMap, permissionMap, menuIds, menuActions, permissionCodes);
        log.debug("后台授权快照完成: userId={}, superAdmin=false, roleCount={}, menuCount={}, permissionCount={}",
                userId, roleIds.size(), menuIds.size(), permissionCodes.size());
        return new AuthorizationSnapshot(userId, roleIds, roleCodes, false, menuIds, menuActions, permissionCodes);
    }

    private List<SysRole> enabledRoles(Long userId) {
        return roleMapper.selectEnabledByIds(userRoleMapper.selectRoleIdsByUserId(userId), SysStatus.ENABLED.name());
    }

    private List<SysMenu> enabledMenus() {
        return menuMapper.selectEnabledOrdered(SysStatus.ENABLED.name());
    }

    private List<SysMenuPermission> enabledMenuPermissions() {
        return menuPermissionMapper.selectEnabledOrdered(SysStatus.ENABLED.name());
    }

    private void applyRoleGrants(List<Long> roleIds,
                                 Map<Long, SysMenu> menuMap,
                                 Map<Long, SysMenuPermission> permissionMap,
                                 Set<Long> menuIds,
                                 Map<Long, Set<String>> menuActions,
                                 Set<String> permissionCodes) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRoleMenu> roleMenus = roleMenuMapper.selectByRoleIds(roleIds);
        for (SysRoleMenu roleMenu : roleMenus) {
            if (menuMap.containsKey(roleMenu.getMenuId())) {
                menuIds.add(roleMenu.getMenuId());
            }
        }
        List<SysRoleMenuPermission> rolePermissions = roleMenuPermissionMapper.selectByRoleIds(roleIds);
        for (SysRoleMenuPermission rolePermission : rolePermissions) {
            SysMenuPermission permission = permissionMap.get(rolePermission.getMenuPermissionId());
            if (permission != null && menuMap.containsKey(permission.getMenuId())) {
                menuIds.add(permission.getMenuId());
                addPermission(permission, menuActions, permissionCodes);
            }
        }
    }

    private void applyUserOverrides(Long userId,
                                    String clientIp,
                                    LocalDateTime now,
                                    Map<Long, SysMenu> menuMap,
                                    Map<Long, SysMenuPermission> permissionMap,
                                    Set<Long> menuIds,
                                    Map<Long, Set<String>> menuActions,
                                    Set<String> permissionCodes) {
        List<SysUserPermissionOverride> overrides = userPermissionOverrideMapper.selectByUserId(userId);
        List<SysUserPermissionOverride> matched = overrides.stream()
                .filter(item -> conditionEvaluator.matches(item.getConditionId(), clientIp, now))
                .toList();

        applyOverrides(matched, PermissionEffect.ALLOW, menuMap, permissionMap, menuIds, menuActions, permissionCodes);
        applyOverrides(matched, PermissionEffect.DENY, menuMap, permissionMap, menuIds, menuActions, permissionCodes);
    }

    private void applyOverrides(List<SysUserPermissionOverride> overrides,
                                PermissionEffect effect,
                                Map<Long, SysMenu> menuMap,
                                Map<Long, SysMenuPermission> permissionMap,
                                Set<Long> menuIds,
                                Map<Long, Set<String>> menuActions,
                                Set<String> permissionCodes) {
        for (SysUserPermissionOverride override : overrides) {
            if (!effect.name().equalsIgnoreCase(override.getEffect())) {
                continue;
            }
            SysMenuPermission permission = override.getMenuPermissionId() == null
                    ? null
                    : permissionMap.get(override.getMenuPermissionId());
            Long menuId = permission == null ? override.getMenuId() : permission.getMenuId();
            if (menuId == null || !menuMap.containsKey(menuId)) {
                continue;
            }
            if (effect == PermissionEffect.ALLOW) {
                menuIds.add(menuId);
                if (permission != null) {
                    addPermission(permission, menuActions, permissionCodes);
                }
            } else {
                if (permission == null) {
                    menuIds.remove(menuId);
                    removeMenuPermissions(menuId, menuActions, permissionMap.values(), permissionCodes);
                } else {
                    removePermission(permission, menuActions, permissionCodes);
                }
            }
        }
    }

    private void addPermission(SysMenuPermission permission,
                               Map<Long, Set<String>> menuActions,
                               Set<String> permissionCodes) {
        menuActions.computeIfAbsent(permission.getMenuId(), ignored -> new HashSet<>())
                .add(permission.getActionValue());
        permissionCodes.add(permission.getPermissionCode());
    }

    private void removePermission(SysMenuPermission permission,
                                  Map<Long, Set<String>> menuActions,
                                  Set<String> permissionCodes) {
        Set<String> actions = menuActions.get(permission.getMenuId());
        if (actions != null) {
            actions.remove(permission.getActionValue());
        }
        permissionCodes.remove(permission.getPermissionCode());
    }

    private void removeMenuPermissions(Long menuId,
                                       Map<Long, Set<String>> menuActions,
                                       Collection<SysMenuPermission> permissions,
                                       Set<String> permissionCodes) {
        menuActions.remove(menuId);
        permissions.stream()
                .filter(item -> Objects.equals(menuId, item.getMenuId()))
                .map(SysMenuPermission::getPermissionCode)
                .forEach(permissionCodes::remove);
    }

}
