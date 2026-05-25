package com.lnzz.argus.security;

import com.lnzz.argus.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 内部采集接口 Token 认证过滤器。
 * <p>
 * 统一校验 /api/v1/internal/** 请求头中的 X-Argus-Token，
 * 将散落在 Controller 内的内部 Token 判断收敛到 Spring Security 链路。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
    private static final String INTERNAL_TOKEN_HEADER = "X-Argus-Token";
    private static final String INTERNAL_PRINCIPAL = "argus-internal";
    private static final List<SimpleGrantedAuthority> INTERNAL_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"));

    private final ArgusSecurityResponseWriter responseWriter;

    @Value("${argus.internal.token:argustest}")
    private String internalToken;

    /**
     * 非内部接口和预检请求不进入内部 Token 校验。
     *
     * @param request HTTP 请求
     * @return true 表示跳过当前过滤器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    /**
     * 校验内部 Token 并写入 Spring Security 认证上下文。
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
            String providedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
            if (!tokenMatches(providedToken)) {
                log.warn("内部 API 认证失败: uri={}, clientIp={}", request.getRequestURI(), request.getRemoteAddr());
                responseWriter.write(response, ResultCode.UNAUTHORIZED);
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    INTERNAL_PRINCIPAL,
                    null,
                    INTERNAL_AUTHORITIES);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("内部 API Spring Security 认证通过: uri={}, clientIp={}",
                    request.getRequestURI(), request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean tokenMatches(String providedToken) {
        if (internalToken == null || internalToken.isBlank() || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
