package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.system.model.BatchIdsRequest;
import com.lnzz.argus.system.model.RoleGrantRequest;
import com.lnzz.argus.system.model.RolePageRequest;
import com.lnzz.argus.system.model.RoleRequest;
import com.lnzz.argus.system.model.RoleResponse;
import com.lnzz.argus.system.service.SysRoleAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final SysRoleAdminService roleService;

    /**
     * 分页查询角色。
     *
     * @param request 角色分页查询请求
     * @return 角色分页结果
     */
    @PostMapping("/page")
    @RequirePermission(SystemPermissionCodes.ROLE_VIEW)
    public Result<PageResult<RoleResponse>> page(@RequestBody(required = false) RolePageRequest request) {
        RolePageRequest safeRequest = request == null ? new RolePageRequest() : request;
        return Result.success(roleService.page(
                safeRequest.effectivePageNo(),
                safeRequest.getPageSize(),
                safeRequest.getRoleName(),
                safeRequest.getStatus()));
    }

    @PostMapping
    @RequirePermission(SystemPermissionCodes.ROLE_CREATE)
    public Result<RoleResponse> create(@RequestBody RoleRequest request) {
        return Result.success("角色创建成功", roleService.create(request));
    }

    @PutMapping("/{roleId}")
    @RequirePermission(SystemPermissionCodes.ROLE_UPDATE)
    public Result<RoleResponse> update(@PathVariable Long roleId,
                                       @RequestBody RoleRequest request) {
        return Result.success("角色更新成功", roleService.update(roleId, request));
    }

    @DeleteMapping
    @RequirePermission(SystemPermissionCodes.ROLE_DELETE)
    public Result<Void> delete(@RequestBody BatchIdsRequest request) {
        roleService.delete(SystemAdminSupport.parseIds(request == null ? null : request.ids(), "角色ID"));
        return Result.success();
    }

    @PutMapping("/{roleId}/menus")
    @RequirePermission(SystemPermissionCodes.ROLE_ASSIGN)
    public Result<Void> assignMenus(@PathVariable Long roleId,
                                    @RequestBody RoleGrantRequest request) {
        roleService.assignMenus(roleId, request);
        return Result.success();
    }
}
