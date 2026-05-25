package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.system.model.BatchIdsRequest;
import com.lnzz.argus.system.model.ResetPasswordRequest;
import com.lnzz.argus.system.model.UserImportResult;
import com.lnzz.argus.system.model.UserPageRequest;
import com.lnzz.argus.system.model.UserRequest;
import com.lnzz.argus.system.model.UserResponse;
import com.lnzz.argus.system.model.UserStatusRequest;
import com.lnzz.argus.system.service.SysUserAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserAdminService userService;

    /**
     * 分页查询后台用户。
     *
     * @param request 用户分页查询请求
     * @return 用户分页结果
     */
    @PostMapping("/page")
    @RequirePermission(SystemPermissionCodes.USER_VIEW)
    public Result<PageResult<UserResponse>> page(@RequestBody(required = false) UserPageRequest request) {
        UserPageRequest safeRequest = request == null ? new UserPageRequest() : request;
        return Result.success(userService.page(
                safeRequest.getId(),
                safeRequest.effectivePageNo(),
                safeRequest.getPageSize(),
                safeRequest.getUsername(),
                safeRequest.getAccount(),
                safeRequest.getStatus(),
                safeRequest.getRoleId()));
    }

    @PostMapping
    @RequirePermission(SystemPermissionCodes.USER_CREATE)
    public Result<UserResponse> create(@RequestBody UserRequest request) {
        return Result.success("用户创建成功", userService.create(request));
    }

    @PutMapping("/{userId}")
    @RequirePermission(SystemPermissionCodes.USER_UPDATE)
    public Result<UserResponse> update(@PathVariable Long userId,
                                       @RequestBody UserRequest request) {
        return Result.success("用户更新成功", userService.update(userId, request));
    }

    @DeleteMapping
    @RequirePermission(SystemPermissionCodes.USER_DELETE)
    public Result<Void> delete(@RequestBody BatchIdsRequest request) {
        userService.delete(SystemAdminSupport.parseIds(request == null ? null : request.ids(), "用户ID"));
        return Result.success();
    }

    @PatchMapping("/status")
    @RequirePermission(SystemPermissionCodes.USER_DISABLE)
    public Result<Void> updateStatus(@RequestBody UserStatusRequest request) {
        userService.updateStatus(request);
        return Result.success();
    }

    @PatchMapping("/{userId}/password")
    @RequirePermission(SystemPermissionCodes.USER_RESET_PASSWORD)
    public Result<Void> resetPassword(@PathVariable Long userId,
                                      @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(userId, request);
        return Result.success();
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(SystemPermissionCodes.USER_IMPORT)
    public Result<UserImportResult> imports(@RequestPart("file") MultipartFile file) {
        return Result.success(userService.importUsers(file));
    }

    @GetMapping("/exports")
    @RequirePermission(SystemPermissionCodes.USER_EXPORT)
    public ResponseEntity<byte[]> exports() {
        byte[] body = userService.exportUsers();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("argus-users.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
