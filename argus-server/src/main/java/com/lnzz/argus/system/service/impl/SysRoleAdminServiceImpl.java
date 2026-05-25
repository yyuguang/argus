package com.lnzz.argus.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.system.entity.SysMenuPermission;
import com.lnzz.argus.system.entity.SysRole;
import com.lnzz.argus.system.entity.SysRoleMenu;
import com.lnzz.argus.system.entity.SysRoleMenuPermission;
import com.lnzz.argus.system.mapper.SysMenuMapper;
import com.lnzz.argus.system.mapper.SysMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysRoleMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuMapper;
import com.lnzz.argus.system.mapper.SysRoleMenuPermissionMapper;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.mapper.SysUserRoleMapper;
import com.lnzz.argus.system.model.RoleGrantRequest;
import com.lnzz.argus.system.model.RoleMenuGrant;
import com.lnzz.argus.system.model.RoleRequest;
import com.lnzz.argus.system.model.RoleResponse;
import com.lnzz.argus.system.model.RouteMeta;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.system.service.SecurityAuditService;
import com.lnzz.argus.system.service.SysRoleAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理与授权服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleAdminServiceImpl implements SysRoleAdminService {

    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysMenuPermissionMapper menuPermissionMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMenuPermissionMapper roleMenuPermissionMapper;
    private final SecurityAuditService auditService;

    /**
     * 分页查询角色。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param roleName 角色名称
     * @param status   状态
     * @return 角色分页结果
     */
    @Override
    public PageResult<RoleResponse> page(Integer pageNo, Integer pageSize, String roleName, Integer status) {
        int normalizedPageNo = SystemAdminSupport.pageNo(pageNo);
        int normalizedPageSize = SystemAdminSupport.pageSize(pageSize);
        Page<SysRole> page = roleMapper.selectAdminPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                roleName,
                status == null ? null : SysStatus.fromApi(status));
        return PageResult.of(
                page.getRecords().stream().map(this::toResponse).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal());
    }

    /**
     * 创建角色并初始化授权。
     *
     * @param request 角色请求
     * @return 新角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponse create(RoleRequest request) {
        validate(request);
        ensureRoleCodeUnique(request.roleCode(), null);
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode().trim());
        role.setRoleName(request.roleName().trim());
        role.setRoleType(ROLE_TYPE_CUSTOM);
        role.setStatus(SysStatus.fromApi(request.status()));
        role.setRemark(SystemAdminSupport.trimToNull(request.remark()));
        role.setIsDeleted(SystemAdminSupport.NOT_DELETED);
        role.setVersion(0);
        roleMapper.insert(role);
        replaceGrants(role.getId(), request.menu());
        auditService.success("ROLE_CREATE", SecurityAuditResourceType.ROLE, role.getId(), null, role);
        log.info("创建后台角色: roleId={}, roleCode={}, roleName={}, status={}",
                role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus());
        return toResponse(role);
    }

    /**
     * 更新角色基础信息和授权。
     *
     * @param roleId  角色 ID
     * @param request 角色请求
     * @return 更新后角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponse update(Long roleId, RoleRequest request) {
        SysRole role = requireRole(roleId);
        validate(request);
        if (ROLE_TYPE_SYSTEM.equalsIgnoreCase(role.getRoleType())
                && !role.getRoleCode().equals(request.roleCode().trim())) {
            log.warn("系统角色编码修改被拒绝: roleId={}, oldCode={}, newCode={}",
                    roleId, role.getRoleCode(), request.roleCode());
            throw new BizException(ResultCode.PARAM_ERROR, "系统角色编码不允许修改");
        }
        ensureRoleCodeUnique(request.roleCode(), roleId);
        SysRole before = copy(role);
        role.setRoleCode(request.roleCode().trim());
        role.setRoleName(request.roleName().trim());
        role.setStatus(SysStatus.fromApi(request.status()));
        role.setRemark(SystemAdminSupport.trimToNull(request.remark()));
        roleMapper.updateById(role);
        if (request.menu() != null) {
            replaceGrants(roleId, request.menu());
        }
        auditService.success("ROLE_UPDATE", SecurityAuditResourceType.ROLE, roleId, before, role);
        log.info("更新后台角色: roleId={}, roleCode={}, roleName={}, status={}, grantChanged={}",
                roleId, role.getRoleCode(), role.getRoleName(), role.getStatus(), request.menu() != null);
        return toResponse(role);
    }

    /**
     * 批量删除角色。
     *
     * @param roleIds 角色 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色 ID 不能为空");
        }
        for (Long roleId : roleIds) {
            SysRole role = requireRole(roleId);
            if (ROLE_TYPE_SYSTEM.equalsIgnoreCase(role.getRoleType())) {
                log.warn("删除系统角色被拒绝: roleId={}, roleCode={}", roleId, role.getRoleCode());
                throw new BizException(ResultCode.PARAM_ERROR, "系统内置角色不允许删除: " + role.getRoleName());
            }
            List<Long> assignedUserIds = userRoleMapper.selectUserIdsByRoleId(roleId);
            long userCount = userMapper.countNonDeletedByIds(assignedUserIds);
            if (userCount > 0) {
                log.warn("删除角色被拒绝: roleId={}, roleCode={}, userCount={}",
                        roleId, role.getRoleCode(), userCount);
                throw new BizException(ResultCode.PARAM_ERROR, "角色已分配给用户，不能删除: " + role.getRoleName());
            }
            SysRole before = copy(role);
            int deletedRows = roleMapper.softDeleteById(roleId, LoginUtil.currentUsernameOrSystem());
            if (deletedRows < 1) {
                log.warn("删除角色失败，记录不存在或已删除: roleId={}, roleCode={}", roleId, role.getRoleCode());
                throw new BizException(ResultCode.NOT_FOUND, "角色不存在或已删除: " + roleId);
            }
            auditService.success("ROLE_DELETE", SecurityAuditResourceType.ROLE, roleId, before, null);
            log.info("删除后台角色: roleId={}, roleCode={}", roleId, role.getRoleCode());
        }
    }

    /**
     * 覆盖角色菜单和按钮授权。
     *
     * @param roleId  角色 ID
     * @param request 授权请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, RoleGrantRequest request) {
        SysRole role = requireRole(roleId);
        replaceGrants(roleId, request == null ? List.of() : request.menu());
        auditService.success("ROLE_ASSIGN", SecurityAuditResourceType.ROLE, roleId, null, role);
        log.info("分配角色权限: roleId={}, roleCode={}, menuGrantCount={}",
                roleId, role.getRoleCode(), request == null || request.menu() == null ? 0 : request.menu().size());
    }

    /**
     * 根据 ID 查询角色，不存在时抛出业务异常。
     *
     * @param roleId 角色 ID
     * @return 角色实体
     */
    @Override
    public SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectNonDeletedById(roleId);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在: " + roleId);
        }
        return role;
    }

    private void replaceGrants(Long roleId, List<RoleMenuGrant> grants) {
        roleMenuPermissionMapper.deleteByRoleId(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
        if (grants == null || grants.isEmpty()) {
            return;
        }
        Set<Long> menuIds = grants.stream()
                .map(grant -> SystemAdminSupport.parseId(grant.id(), "菜单ID"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        validateMenus(menuIds);
        Map<Long, Map<String, SysMenuPermission>> permissionByMenuAndAction = enabledPermissionsByMenuAndAction();
        for (Long menuId : menuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
        for (RoleMenuGrant grant : grants) {
            Long menuId = SystemAdminSupport.parseId(grant.id(), "菜单ID");
            List<String> actions = grant.meta() == null || grant.meta().permission() == null
                    ? List.of()
                    : grant.meta().permission();
            for (String action : actions) {
                SysMenuPermission permission = permissionByMenuAndAction
                        .getOrDefault(menuId, Map.of())
                        .get(action);
                if (permission == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "菜单按钮权限不存在: menuId=" + menuId + ", action=" + action);
                }
                SysRoleMenuPermission relation = new SysRoleMenuPermission();
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                relation.setMenuPermissionId(permission.getId());
                roleMenuPermissionMapper.insert(relation);
            }
        }
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(
                String.valueOf(role.getId()),
                role.getRoleCode(),
                role.getRoleName(),
                SysStatus.toApi(role.getStatus()),
                SystemAdminSupport.format(role.getCreateTime()),
                role.getRemark(),
                grantsForRole(role.getId()));
    }

    private List<RoleMenuGrant> grantsForRole(Long roleId) {
        List<SysRoleMenu> menus = roleMenuMapper.selectByRoleId(roleId);
        if (menus.isEmpty()) {
            return List.of();
        }
        List<SysRoleMenuPermission> permissions = roleMenuPermissionMapper.selectByRoleId(roleId);
        Map<Long, List<Long>> permissionIdsByMenu = permissions.stream()
                .collect(Collectors.groupingBy(SysRoleMenuPermission::getMenuId,
                        Collectors.mapping(SysRoleMenuPermission::getMenuPermissionId, Collectors.toList())));
        List<Long> permissionIds = permissions.stream().map(SysRoleMenuPermission::getMenuPermissionId).toList();
        Map<Long, SysMenuPermission> permissionMap = permissionIds.isEmpty()
                ? Map.of()
                : menuPermissionMapper.selectNonDeletedByIds(permissionIds)
                .stream()
                .collect(Collectors.toMap(SysMenuPermission::getId, item -> item));
        List<RoleMenuGrant> result = new ArrayList<>();
        for (SysRoleMenu menu : menus) {
            List<String> actions = permissionIdsByMenu.getOrDefault(menu.getMenuId(), List.of()).stream()
                    .map(permissionMap::get)
                    .filter(Objects::nonNull)
                    .map(SysMenuPermission::getActionValue)
                    .toList();
            result.add(new RoleMenuGrant(
                    String.valueOf(menu.getMenuId()),
                    new RouteMeta(null, null, null, null, null, null, null, null, null, null, actions)));
        }
        return result;
    }

    private void validate(RoleRequest request) {
        if (request == null || !StringUtils.hasText(request.roleCode()) || !StringUtils.hasText(request.roleName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色编码和角色名称不能为空");
        }
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludedId) {
        SysRole existing = roleMapper.selectByRoleCodeIncludeDeleted(roleCode);
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BizException(ResultCode.ADMIN_ROLE_CODE_CONFLICT);
        }
    }

    private void validateMenus(Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        long count = menuMapper.countEnabledByIds(menuIds, SysStatus.ENABLED.name());
        if (count != menuIds.size()) {
            throw new BizException(ResultCode.PARAM_ERROR, "只能分配存在且启用的菜单");
        }
    }

    private Map<Long, Map<String, SysMenuPermission>> enabledPermissionsByMenuAndAction() {
        return menuPermissionMapper.selectEnabledOrdered(SysStatus.ENABLED.name())
                .stream()
                .collect(Collectors.groupingBy(SysMenuPermission::getMenuId,
                        Collectors.toMap(SysMenuPermission::getActionValue, item -> item, (a, b) -> a)));
    }

    private SysRole copy(SysRole source) {
        SysRole target = new SysRole();
        target.setId(source.getId());
        target.setRoleCode(source.getRoleCode());
        target.setRoleName(source.getRoleName());
        target.setRoleType(source.getRoleType());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        return target;
    }
}
