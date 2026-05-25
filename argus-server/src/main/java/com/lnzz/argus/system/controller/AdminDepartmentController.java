package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.system.model.BatchIdsRequest;
import com.lnzz.argus.system.model.DepartmentPageRequest;
import com.lnzz.argus.system.model.DepartmentRequest;
import com.lnzz.argus.system.model.DepartmentResponse;
import com.lnzz.argus.system.service.SysDepartmentAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 部门管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/departments")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final SysDepartmentAdminService departmentService;

    @GetMapping("/tree")
    @RequirePermission(SystemPermissionCodes.DEPARTMENT_VIEW)
    public Result<Map<String, List<DepartmentResponse>>> tree() {
        return Result.success(Map.of("list", departmentService.tree()));
    }

    /**
     * 分页查询部门。
     *
     * @param request 部门分页查询请求
     * @return 部门分页结果
     */
    @PostMapping("/page")
    @RequirePermission(SystemPermissionCodes.DEPARTMENT_VIEW)
    public Result<PageResult<DepartmentResponse>> page(@RequestBody(required = false) DepartmentPageRequest request) {
        DepartmentPageRequest safeRequest = request == null ? new DepartmentPageRequest() : request;
        return Result.success(departmentService.page(
                safeRequest.effectivePageNo(),
                safeRequest.getPageSize(),
                safeRequest.getDepartmentName(),
                safeRequest.getStatus()));
    }

    @PostMapping
    @RequirePermission(SystemPermissionCodes.DEPARTMENT_CREATE)
    public Result<DepartmentResponse> create(@RequestBody DepartmentRequest request) {
        return Result.success("部门创建成功", departmentService.create(request));
    }

    @PutMapping("/{departmentId}")
    @RequirePermission(SystemPermissionCodes.DEPARTMENT_UPDATE)
    public Result<DepartmentResponse> update(@PathVariable Long departmentId,
                                             @RequestBody DepartmentRequest request) {
        return Result.success("部门更新成功", departmentService.update(departmentId, request));
    }

    @DeleteMapping
    @RequirePermission(SystemPermissionCodes.DEPARTMENT_DELETE)
    public Result<Void> delete(@RequestBody BatchIdsRequest request) {
        departmentService.delete(SystemAdminSupport.parseIds(request == null ? null : request.ids(), "部门ID"));
        return Result.success();
    }
}
