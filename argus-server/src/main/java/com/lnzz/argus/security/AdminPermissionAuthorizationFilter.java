package com.lnzz.argus.security;

import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.system.service.PermissionDecisionService;
import com.lnzz.argus.system.service.SecurityAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

/**
 * Portal 后台接口权限过滤器。
 * <p>
 * 过滤器运行在 Spring Security 链路中，读取 Controller 方法上的
 * {@link RequirePermission} 并调用权限裁决服务完成后端强校验。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class AdminPermissionAuthorizationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";
    private static final String RULES_PATH_PREFIX = "/api/v1/rules/";
    private static final String LOGIN_PATH = "/api/v1/admin/auth/sessions";

    private final RequestMappingHandlerMapping handlerMapping;
    private final PermissionDecisionService permissionDecisionService;
    private final SecurityAuditService auditService;
    private final ArgusSecurityResponseWriter responseWriter;

    /**
     * 创建后台接口权限过滤器。
     * <p>
     * Spring Boot Actuator 会额外注册 `controllerEndpointHandlerMapping`，
     * 因此这里必须显式限定为 MVC Controller 的 `requestMappingHandlerMapping`，
     * 避免启动时按 `HandlerMapping` 候选 Bean 推断而出现歧义。
     * </p>
     *
     * @param handlerMapping            MVC Controller 请求映射处理器
     * @param permissionDecisionService 权限裁决服务
     * @param auditService              安全审计服务
     * @param responseWriter            安全响应写入器
     */
    public AdminPermissionAuthorizationFilter(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            PermissionDecisionService permissionDecisionService,
            SecurityAuditService auditService,
            ArgusSecurityResponseWriter responseWriter) {
        this.handlerMapping = handlerMapping;
        this.permissionDecisionService = permissionDecisionService;
        this.auditService = auditService;
        this.responseWriter = responseWriter;
    }

    /**
     * 非后台接口、登录接口和预检请求不进入接口权限校验。
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
     * 读取接口权限声明并执行授权校验。
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
        RequirePermission permission = resolvePermission(request);
        if (permission == null) {
            filterChain.doFilter(request, response);
            return;
        }
        CurrentUser currentUser = currentUser();
        boolean allowed = currentUser != null
                && permissionDecisionService.hasPermission(currentUser.userId(), permission.value(), request.getRemoteAddr());
        if (!allowed) {
            Long userId = currentUser == null ? null : currentUser.userId();
            log.warn("后台权限拒绝: userId={}, permissionCode={}, uri={}, clientIp={}",
                    userId, permission.value(), request.getRequestURI(), request.getRemoteAddr());
            auditService.failed("PERMISSION_DENIED", SecurityAuditResourceType.PERMISSION, permission.value(),
                    "缺少权限", null, request.getRequestURI());
            responseWriter.write(response, ResultCode.ADMIN_FORBIDDEN);
            return;
        }
        log.debug("后台权限通过: userId={}, permissionCode={}, uri={}",
                currentUser.userId(), permission.value(), request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    private RequirePermission resolvePermission(HttpServletRequest request) throws ServletException {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null || !(chain.getHandler() instanceof HandlerMethod method)) {
                return null;
            }
            return AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
        } catch (Exception ex) {
            throw new ServletException("解析后台接口权限声明失败", ex);
        }
    }

    private CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        return LoginUtil.currentUser().orElse(null);
    }
}
