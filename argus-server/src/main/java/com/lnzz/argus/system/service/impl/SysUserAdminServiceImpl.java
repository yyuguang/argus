package com.lnzz.argus.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.common.constant.SecurityConstants;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.entity.SysDepartment;
import com.lnzz.argus.system.entity.SysRole;
import com.lnzz.argus.system.entity.SysUser;
import com.lnzz.argus.system.entity.SysUserRole;
import com.lnzz.argus.system.mapper.SysDepartmentMapper;
import com.lnzz.argus.system.mapper.SysRoleMapper;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.mapper.SysUserRoleMapper;
import com.lnzz.argus.system.model.DepartmentRef;
import com.lnzz.argus.system.model.DepartmentOption;
import com.lnzz.argus.system.model.ResetPasswordRequest;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.system.model.UserImportError;
import com.lnzz.argus.system.model.UserImportResult;
import com.lnzz.argus.system.model.UserRequest;
import com.lnzz.argus.system.model.UserResponse;
import com.lnzz.argus.system.model.UserStatusRequest;
import com.lnzz.argus.system.service.PasswordService;
import com.lnzz.argus.system.service.SecurityAuditService;
import com.lnzz.argus.system.service.SessionService;
import com.lnzz.argus.system.service.SysUserAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户管理服务。
 * <p>封装用户 CRUD、启停、重置密码、角色覆盖写入和 CSV 导入导出。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserAdminServiceImpl implements SysUserAdminService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDepartmentMapper departmentMapper;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final SecurityAuditService auditService;

    /**
     * 分页查询后台用户。
     *
     * @param departmentId 部门 ID
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @param username     显示名称
     * @param account      登录账号
     * @param status       用户状态
     * @param roleId       角色 ID
     * @return 用户分页结果
     */
    @Override
    public PageResult<UserResponse> page(String departmentId, Integer pageNo, Integer pageSize,
                                        String username, String account, Integer status, String roleId) {
        int normalizedPageNo = SystemAdminSupport.pageNo(pageNo);
        int normalizedPageSize = SystemAdminSupport.pageSize(pageSize);
        List<Long> filterUserIds = userIdsByRole(roleId);
        if (roleId != null && filterUserIds.isEmpty()) {
            return PageResult.of(List.of(), normalizedPageNo, normalizedPageSize, 0);
        }
        Page<SysUser> page = userMapper.selectAdminPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                SystemAdminSupport.parseId(departmentId, "部门ID"),
                username,
                account,
                status == null ? null : SysStatus.fromApi(status),
                filterUserIds);
        return PageResult.of(toResponses(page.getRecords()), page.getCurrent(), page.getSize(), page.getTotal());
    }

    /**
     * 创建后台用户。
     *
     * @param request 用户请求
     * @return 新用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse create(UserRequest request) {
        validateUser(request, true);
        ensureAccountUnique(request.account(), null);
        ensureEmailUnique(request.email(), null);
        SysUser user = new SysUser();
        applyUserRequest(user, request, true);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setFailedLoginCount(0);
        user.setInitialPasswordRequired(true);
        user.setIsDeleted(SystemAdminSupport.NOT_DELETED);
        user.setVersion(0);
        userMapper.insert(user);
        replaceRoles(user.getId(), parseRoleIds(request.role()));
        auditService.success("USER_CREATE", SecurityAuditResourceType.USER, user.getId(), null, toSafeUser(user));
        log.info("创建后台用户: userId={}, account={}, displayName={}, departmentId={}, roleIds={}",
                user.getId(), user.getUsername(), user.getDisplayName(), user.getDepartmentId(), request.role());
        return toResponse(user);
    }

    /**
     * 更新后台用户资料、状态和角色。
     *
     * @param userId  用户 ID
     * @param request 用户请求
     * @return 更新后用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse update(Long userId, UserRequest request) {
        SysUser user = requireUser(userId);
        validateUser(request, false);
        ensureAccountUnique(request.account(), userId);
        ensureEmailUnique(request.email(), userId);
        List<Long> nextRoleIds = parseRoleIds(request.role());
        protectLastSuperAdminRoleChange(user, nextRoleIds);
        SysUser before = toSafeUser(user);
        applyUserRequest(user, request, false);
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordService.hash(request.password()));
            sessionService.revokeByUser(userId, "用户密码被管理员修改");
        }
        userMapper.updateById(user);
        replaceRoles(userId, nextRoleIds);
        auditService.success("USER_UPDATE", SecurityAuditResourceType.USER, userId, before, toSafeUser(user));
        log.info("更新后台用户: userId={}, account={}, status={}, roleIds={}, passwordChanged={}",
                userId, user.getUsername(), user.getStatus(), nextRoleIds, StringUtils.hasText(request.password()));
        return toResponse(user);
    }

    /**
     * 批量删除后台用户。
     *
     * @param userIds 用户 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        for (Long userId : userIds) {
            SysUser user = requireUser(userId);
            protectLastSuperAdminMutation(user, "删除");
            SysUser before = toSafeUser(user);
            int affected = userMapper.softDeleteById(userId, LoginUtil.currentUsernameOrSystem());
            if (affected == 0) {
                throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
            }
            sessionService.revokeByUser(userId, "用户被删除");
            auditService.success("USER_DELETE", SecurityAuditResourceType.USER, userId, before, null);
            log.info("删除后台用户: userId={}, account={}", userId, user.getUsername());
        }
    }

    /**
     * 批量更新后台用户状态。
     *
     * @param request 状态更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(UserStatusRequest request) {
        List<Long> ids = SystemAdminSupport.parseIds(request == null ? null : request.ids(), "用户ID");
        if (ids.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        String nextStatus = SysStatus.fromApi(request.status());
        for (Long userId : ids) {
            SysUser user = requireUser(userId);
            if (!SysStatus.enabled(nextStatus)) {
                protectLastSuperAdminMutation(user, "禁用");
            }
            SysUser before = toSafeUser(user);
            user.setStatus(nextStatus);
            userMapper.updateById(user);
            if (!SysStatus.enabled(nextStatus)) {
                sessionService.revokeByUser(userId, "用户被禁用: " + (request.reason() == null ? "" : request.reason()));
            }
            auditService.success("USER_STATUS_UPDATE", SecurityAuditResourceType.USER, userId, before, toSafeUser(user));
            log.info("更新后台用户状态: userId={}, account={}, nextStatus={}, reason={}",
                    userId, user.getUsername(), nextStatus, request.reason());
        }
    }

    /**
     * 重置用户密码并撤销其历史会话。
     *
     * @param userId  用户 ID
     * @param request 重置密码请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.password()) || request.password().length() < 8) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能为空且长度至少 8 位");
        }
        SysUser user = requireUser(userId);
        SysUser before = toSafeUser(user);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setInitialPasswordRequired(true);
        userMapper.updateById(user);
        sessionService.revokeByUser(userId, "管理员重置密码");
        auditService.success("USER_RESET_PASSWORD", SecurityAuditResourceType.USER, userId, before, toSafeUser(user));
        log.info("重置后台用户密码: userId={}, account={}", userId, user.getUsername());
    }

    /**
     * CSV 导入用户。首行字段：
     * account,username,email,phone,departmentId,roleIds,status,password
     *
     * @param file CSV 文件
     * @return 导入结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResult importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.ADMIN_IMPORT_ERROR, "导入文件不能为空");
        }
        long success = 0;
        List<UserImportError> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int rowNo = 0;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                if (rowNo == 1 && line.toLowerCase().contains("account")) {
                    continue;
                }
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                try {
                    String[] parts = line.split(",", -1);
                    UserRequest request = new UserRequest(
                            part(parts, 1),
                            part(parts, 0),
                            part(parts, 2),
                            part(parts, 3),
                            part(parts, 7),
                            "0".equals(part(parts, 6)) ? 0 : 1,
                            new DepartmentRef(part(parts, 4)),
                            parseCsvRoles(part(parts, 5)));
                    create(request);
                    success++;
                } catch (Exception ex) {
                    errors.add(new UserImportError(rowNo, ex.getMessage()));
                    log.warn("导入后台用户失败: rowNo={}, reason={}", rowNo, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            throw new BizException(ResultCode.ADMIN_IMPORT_ERROR, "读取导入文件失败: " + ex.getMessage());
        }
        log.info("导入后台用户完成: success={}, failed={}", success, errors.size());
        return new UserImportResult(success, errors.size(), errors);
    }

    /**
     * 导出后台用户为 CSV。
     *
     * @return CSV 字节内容
     */
    @Override
    public byte[] exportUsers() {
        StringBuilder builder = new StringBuilder();
        builder.append("account,username,email,phone,departmentId,roleIds,status,createTime\n");
        for (UserResponse user : toResponses(userMapper.selectNonDeletedOrdered())) {
            builder.append(csv(user.account())).append(',')
                    .append(csv(user.username())).append(',')
                    .append(csv(user.email())).append(',')
                    .append(csv(user.phone())).append(',')
                    .append(user.department() == null ? "" : user.department().id()).append(',')
                    .append(csv(String.join("|", user.role()))).append(',')
                    .append(user.status()).append(',')
                    .append(csv(user.createTime())).append('\n');
        }
        byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);
        log.info("导出后台用户: bytes={}", content.length);
        return content;
    }

    /**
     * 根据 ID 查询用户，不存在时抛出业务异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    @Override
    public SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectNonDeletedById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        return user;
    }

    private List<UserResponse> toResponses(List<SysUser> users) {
        return users.stream().map(this::toResponse).toList();
    }

    private UserResponse toResponse(SysUser user) {
        SysDepartment department = user.getDepartmentId() == null
                ? null
                : departmentMapper.selectNonDeletedById(user.getDepartmentId());
        List<SysRole> roles = rolesByUser(user.getId());
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                SysStatus.toApi(user.getStatus()),
                SystemAdminSupport.format(user.getCreateTime()),
                roles.stream().map(SysRole::getId).map(String::valueOf).toList(),
                roles.stream().map(SysRole::getRoleName).toList(),
                department == null ? null : new DepartmentOption(String.valueOf(department.getId()), department.getDepartmentName()));
    }

    private void validateUser(UserRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (!StringUtils.hasText(request.username())) {
            throw new BizException(ResultCode.PARAM_ERROR, "显示名称不能为空");
        }
        if (!StringUtils.hasText(request.account())) {
            throw new BizException(ResultCode.PARAM_ERROR, "登录账号不能为空");
        }
        if (creating && (!StringUtils.hasText(request.password()) || request.password().length() < 8)) {
            throw new BizException(ResultCode.PARAM_ERROR, "初始密码不能为空且长度至少 8 位");
        }
        Long departmentId = request.department() == null ? null : SystemAdminSupport.parseId(request.department().id(), "部门ID");
        boolean departmentMissing = departmentId != null && departmentMapper.selectNonDeletedById(departmentId) == null;
        if (departmentMissing) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在: " + departmentId);
        }
        validateRoles(parseRoleIds(request.role()));
    }

    private void applyUserRequest(SysUser user, UserRequest request, boolean creating) {
        user.setDisplayName(request.username().trim());
        user.setUsername(request.account().trim());
        user.setEmail(SystemAdminSupport.trimToNull(request.email()));
        user.setPhone(SystemAdminSupport.trimToNull(request.phone()));
        user.setDepartmentId(request.department() == null ? null : SystemAdminSupport.parseId(request.department().id(), "部门ID"));
        user.setStatus(SysStatus.fromApi(request.status()));
        if (creating) {
            user.setInitialPasswordRequired(true);
        }
    }

    private void ensureAccountUnique(String account, Long excludedId) {
        SysUser existing = userMapper.selectByUsernameIncludeDeleted(account);
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BizException(ResultCode.ADMIN_ACCOUNT_CONFLICT);
        }
    }

    private void ensureEmailUnique(String email, Long excludedId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        SysUser existing = userMapper.selectByEmailIncludeDeleted(email);
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "邮箱已存在");
        }
    }

    private List<Long> parseRoleIds(List<String> roles) {
        return SystemAdminSupport.parseIds(roles, "角色ID");
    }

    private void validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        long count = roleMapper.countNonDeletedByIds(roleIds);
        if (count != new LinkedHashSet<>(roleIds).size()) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    private List<SysRole> rolesByUser(Long userId) {
        return roleMapper.selectNonDeletedByIds(userRoleMapper.selectRoleIdsByUserId(userId));
    }

    private List<Long> userIdsByRole(String roleId) {
        Long parsed = SystemAdminSupport.parseId(roleId, "角色ID");
        if (parsed == null) {
            return List.of();
        }
        return userRoleMapper.selectUserIdsByRoleId(parsed);
    }

    private void protectLastSuperAdminMutation(SysUser user, String actionName) {
        if (SysStatus.enabled(user.getStatus()) && userHasSuperAdmin(user.getId()) && countActiveSuperAdmins() <= 1) {
            log.warn("超级管理员保护触发: userId={}, account={}, action={}",
                    user.getId(), user.getUsername(), actionName);
            throw new BizException(ResultCode.ADMIN_SUPER_ADMIN_REQUIRED,
                    "不能" + actionName + "最后一个启用的超级管理员");
        }
    }

    private void protectLastSuperAdminRoleChange(SysUser user, List<Long> nextRoleIds) {
        if (!SysStatus.enabled(user.getStatus()) || !userHasSuperAdmin(user.getId()) || countActiveSuperAdmins() > 1) {
            return;
        }
        SysRole superRole = roleMapper.selectActiveSuperAdminRole(SecurityConstants.SUPER_ADMIN_ROLE);
        if (superRole != null && (nextRoleIds == null || !nextRoleIds.contains(superRole.getId()))) {
            log.warn("超级管理员角色保护触发: userId={}, account={}, nextRoleIds={}",
                    user.getId(), user.getUsername(), nextRoleIds);
            throw new BizException(ResultCode.ADMIN_SUPER_ADMIN_REQUIRED, "不能移除最后一个启用超级管理员的角色");
        }
    }

    private boolean userHasSuperAdmin(Long userId) {
        SysRole superRole = roleMapper.selectActiveSuperAdminRole(SecurityConstants.SUPER_ADMIN_ROLE);
        if (superRole == null) {
            return false;
        }
        return userRoleMapper.countByUserIdAndRoleId(userId, superRole.getId()) > 0;
    }

    private long countActiveSuperAdmins() {
        SysRole superRole = roleMapper.selectActiveSuperAdminRole(SecurityConstants.SUPER_ADMIN_ROLE);
        if (superRole == null) {
            return 0;
        }
        return userMapper.countEnabledNonDeletedByIds(
                userRoleMapper.selectUserIdsByRoleId(superRole.getId()), SysStatus.ENABLED.name());
    }

    private SysUser toSafeUser(SysUser source) {
        SysUser safe = new SysUser();
        safe.setId(source.getId());
        safe.setDepartmentId(source.getDepartmentId());
        safe.setUsername(source.getUsername());
        safe.setDisplayName(source.getDisplayName());
        safe.setEmail(source.getEmail());
        safe.setPhone(source.getPhone());
        safe.setStatus(source.getStatus());
        safe.setInitialPasswordRequired(source.getInitialPasswordRequired());
        return safe;
    }

    private String part(String[] parts, int index) {
        return index < parts.length ? SystemAdminSupport.trimToNull(parts[index]) : null;
    }

    private List<String> parseCsvRoles(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("\\|"));
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }
}
