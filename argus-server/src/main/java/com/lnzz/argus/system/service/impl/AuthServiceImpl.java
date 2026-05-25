package com.lnzz.argus.system.service.impl;

import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.common.constant.SecurityConstants;
import com.lnzz.argus.system.entity.SysDepartment;
import com.lnzz.argus.system.entity.SysUser;
import com.lnzz.argus.system.mapper.SysDepartmentMapper;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.model.CurrentUserResponse;
import com.lnzz.argus.system.model.DepartmentOption;
import com.lnzz.argus.system.model.LoginRequest;
import com.lnzz.argus.system.model.LoginResponse;
import com.lnzz.argus.system.model.RouteRecord;
import com.lnzz.argus.security.CurrentUser;
import com.lnzz.argus.security.CurrentUserContext;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.service.AuthService;
import com.lnzz.argus.system.service.DynamicRouteService;
import com.lnzz.argus.system.service.PasswordService;
import com.lnzz.argus.system.service.PermissionDecisionService;
import com.lnzz.argus.system.service.SecurityAuditService;
import com.lnzz.argus.system.service.SessionService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Portal 认证服务。
 * <p>负责登录、退出、当前用户信息和动态路由入口编排。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String ACTION_LOGIN = "AUTH_LOGIN";
    private static final String ACTION_LOGOUT = "AUTH_LOGOUT";

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final PermissionDecisionService permissionDecisionService;
    private final DynamicRouteService dynamicRouteService;
    private final SecurityAuditService auditService;

    /**
     * 登录并签发服务端可撤销 Token。
     *
     * @param request        登录请求
     * @param servletRequest 当前 HTTP 请求
     * @return 登录结果，包含 Token、角色和按钮权限摘要
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名和密码不能为空");
        }
        SysUser user = userMapper.selectNonDeletedByUsername(request.username());
        if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            log.warn("后台登录失败: username={}, reason=账号或密码错误, clientIp={}",
                    request.username(), servletRequest == null ? null : servletRequest.getRemoteAddr());
            auditService.failed(ACTION_LOGIN, SecurityAuditResourceType.SESSION, request.username(),
                    "用户名或密码错误", null, null);
            throw new BizException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (!SysStatus.enabled(user.getStatus())) {
            log.warn("后台登录被拒绝: userId={}, username={}, status={}, clientIp={}",
                    user.getId(), user.getUsername(), user.getStatus(),
                    servletRequest == null ? null : servletRequest.getRemoteAddr());
            auditService.failed(ACTION_LOGIN, SecurityAuditResourceType.SESSION, user.getId(),
                    "账号不可用", null, null);
            throw new BizException(ResultCode.ADMIN_ACCOUNT_DISABLED);
        }

        SessionService.IssuedSession issued = sessionService.issue(
                user.getId(),
                servletRequest == null ? null : servletRequest.getRemoteAddr(),
                servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(servletRequest == null ? null : servletRequest.getRemoteAddr());
        user.setFailedLoginCount(0);
        userMapper.updateById(user);

        PermissionDecisionService.AuthorizationSnapshot snapshot =
                permissionDecisionService.authorizationFor(user.getId(), servletRequest == null ? null : servletRequest.getRemoteAddr(), LocalDateTime.now());
        auditService.success(ACTION_LOGIN, SecurityAuditResourceType.SESSION, issued.sessionId(), null, user.getUsername());
        String primaryRoleCode = snapshot.roleCodes().isEmpty() ? null : snapshot.roleCodes().get(0);
        String primaryRoleId = snapshot.roleIds().isEmpty() ? null : String.valueOf(snapshot.roleIds().get(0));
        log.info("后台登录成功: userId={}, username={}, sessionId={}, roleCodes={}, clientIp={}",
                user.getId(), user.getUsername(), issued.sessionId(), snapshot.roleCodes(),
                servletRequest == null ? null : servletRequest.getRemoteAddr());
        return new LoginResponse(
                String.valueOf(user.getId()),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                primaryRoleCode,
                primaryRoleId,
                snapshot.roleCodes(),
                snapshot.permissionsSummary(),
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.BEARER_PREFIX + issued.token(),
                SystemAdminSupport.format(issued.expiresAt()));
    }

    /**
     * 注销当前登录用户，并撤销当前会话。
     */
    @Override
    public void logout() {
        CurrentUserContext.get().ifPresent(user -> {
            sessionService.revoke(user.sessionId(), "用户退出登录");
            auditService.success(ACTION_LOGOUT, SecurityAuditResourceType.SESSION, user.sessionId(), null, null);
            log.info("后台用户退出: userId={}, username={}, sessionId={}",
                    user.userId(), user.username(), user.sessionId());
        });
    }

    /**
     * 查询当前用户资料、部门、角色和权限摘要。
     *
     * @param clientIp 客户端 IP，用于条件权限裁决
     * @return 当前用户信息
     */
    @Override
    public CurrentUserResponse currentUser(String clientIp) {
        CurrentUser current = CurrentUserContext.get()
                .orElseThrow(() -> new BizException(ResultCode.ADMIN_UNAUTHENTICATED));
        SysUser user = userMapper.selectNonDeletedById(current.userId());
        if (user == null) {
            throw new BizException(ResultCode.ADMIN_TOKEN_EXPIRED);
        }
        PermissionDecisionService.AuthorizationSnapshot snapshot =
                permissionDecisionService.authorizationFor(user.getId(), clientIp, LocalDateTime.now());
        SysDepartment department = user.getDepartmentId() == null
                ? null
                : departmentMapper.selectNonDeletedById(user.getDepartmentId());
        return new CurrentUserResponse(
                String.valueOf(user.getId()),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                SysStatus.toApi(user.getStatus()),
                department == null ? null : new DepartmentOption(String.valueOf(department.getId()), department.getDepartmentName()),
                snapshot.roleCodes(),
                snapshot.permissionsSummary());
    }

    /**
     * 查询当前用户可访问的 Vue Admin 动态路由。
     *
     * @param clientIp 客户端 IP，用于条件权限裁决
     * @return 动态路由树
     */
    @Override
    public List<RouteRecord> routes(String clientIp) {
        Long userId = LoginUtil.currentUserIdOrNull();
        if (userId == null) {
            throw new BizException(ResultCode.ADMIN_UNAUTHENTICATED);
        }
        List<RouteRecord> routes = dynamicRouteService.routesForUser(userId, clientIp);
        log.debug("查询后台动态路由: userId={}, routeCount={}, clientIp={}", userId, routes.size(), clientIp);
        return routes;
    }
}
