package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.system.model.BatchIdsRequest;
import com.lnzz.argus.system.model.MenuOrderRequest;
import com.lnzz.argus.system.model.MenuRequest;
import com.lnzz.argus.system.model.MenuResponse;
import com.lnzz.argus.system.model.MenuStatusRequest;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.system.service.SysMenuAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 菜单与按钮权限管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final SysMenuAdminService menuService;

    @GetMapping("/tree")
    @RequirePermission(SystemPermissionCodes.MENU_VIEW)
    public Result<Map<String, List<MenuResponse>>> tree() {
        return Result.success(Map.of("list", menuService.tree()));
    }

    @PostMapping
    @RequirePermission(SystemPermissionCodes.MENU_CREATE)
    public Result<MenuResponse> create(@RequestBody MenuRequest request) {
        return Result.success("菜单创建成功", menuService.create(request));
    }

    @PutMapping("/{menuId}")
    @RequirePermission(SystemPermissionCodes.MENU_UPDATE)
    public Result<MenuResponse> update(@PathVariable Long menuId,
                                       @RequestBody MenuRequest request) {
        return Result.success("菜单更新成功", menuService.update(menuId, request));
    }

    @DeleteMapping
    @RequirePermission(SystemPermissionCodes.MENU_DELETE)
    public Result<Void> delete(@RequestBody BatchIdsRequest request) {
        menuService.delete(SystemAdminSupport.parseIds(request == null ? null : request.ids(), "菜单ID"));
        return Result.success();
    }

    @PatchMapping("/status")
    @RequirePermission(SystemPermissionCodes.MENU_UPDATE)
    public Result<Void> updateStatus(@RequestBody MenuStatusRequest request) {
        menuService.updateStatus(request);
        return Result.success();
    }

    @PatchMapping("/order")
    @RequirePermission(SystemPermissionCodes.MENU_UPDATE)
    public Result<Void> updateOrder(@RequestBody MenuOrderRequest request) {
        menuService.updateOrder(request);
        return Result.success();
    }
}
