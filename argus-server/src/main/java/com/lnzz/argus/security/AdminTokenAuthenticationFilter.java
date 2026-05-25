package com.lnzz.argus.security;

import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.common.constant.SecurityConstants;
import com.lnzz.argus.system.entity.SysSession;
import com.lnzz.argus.system.entity.SysUser;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.service.PermissionDecisionService;
import com.lnzz.argus.system.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Portal 后台 Token 认证过滤器。
 * <p>
 * 该过滤器属于 Spring Security 过滤器链，负责校验 Bearer Token、
 * 写入 SecurityContext 和 CurrentUserContext。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";
    private static final String RULES_PATH_PREFIX = "/api/v1/rules/";
    private static final String LOGIN_PATH = "/api/v1/admin/auth/sessions";

    private final SessionService sessionService;
    private final SysUserMapper userMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final ArgusSecurityResponseWriter responseWriter;

    /**
     * 非后台接口、登录接口和预检请求不进入后台 Token 校验。
     *
     * @param request HTTP 请求
     * @return true 表示跳过当前过滤器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || (!uri.startsWith(ADMIN_PATH_PREFIX) && !uri.startsWith(RULES_PATH_PREFIX))
                || LOGIN_PATH.equals(uri);
    }

    /**
     * 校验后台 Token 并写入 Spring Security 认证上下文。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request.getHeader(SecurityConstants.AUTHORIZATION_HEADER));
            if (token == null) {
                log.warn("后台认证失败: uri={}, reason=缺少 Authorization", request.getRequestURI());
                responseWriter.write(response, ResultCode.ADMIN_UNAUTHENTICATED);
                return;
            }
            SysSession session = sessionService.findValid(token).orElse(null);
            if (session == null) {
                log.warn("后台认证失败: uri={}, reason=Token 无效或过期", request.getRequestURI());
                responseWriter.write(response, ResultCode.ADMIN_TOKEN_EXPIRED);
                return;
            }
            SysUser user = userMapper.selectNonDeletedById(session.getUserId());
            if (user == null || !SysStatus.enabled(user.getStatus())) {
                log.warn("后台认证失败: uri={}, userId={}, reason=账号不存在、已删除或未启用",
                        request.getRequestURI(), session.getUserId());
                responseWriter.write(response, ResultCode.ADMIN_ACCOUNT_DISABLED);
                return;
            }
            PermissionDecisionService.AuthorizationSnapshot snapshot =
                    permissionDecisionService.authorizationFor(user.getId(), request.getRemoteAddr(), LocalDateTime.now());
            CurrentUser currentUser = new CurrentUser(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    session.getId(),
                    snapshot.roleCodes());
            CurrentUserContext.set(currentUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    currentUser,
                    token,
                    authorities(snapshot));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("后台 Spring Security 认证通过: uri={}, userId={}, sessionId={}, roleCodes={}",
                    request.getRequestURI(), user.getId(), session.getId(), snapshot.roleCodes());
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private List<SimpleGrantedAuthority> authorities(PermissionDecisionService.AuthorizationSnapshot snapshot) {
        Stream<SimpleGrantedAuthority> roles = snapshot.roleCodes().stream()
                .map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode));
        Stream<SimpleGrantedAuthority> permissions = snapshot.permissionCodes().stream()
                .map(SimpleGrantedAuthority::new);
        return Stream.concat(roles, permissions).toList();
    }

    private String extractToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, SecurityConstants.BEARER_PREFIX, 0, SecurityConstants.BEARER_PREFIX.length())) {
            return value.substring(SecurityConstants.BEARER_PREFIX.length()).trim();
        }
        return value;
    }
}
