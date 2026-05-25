package com.lnzz.argus.config;

import com.lnzz.argus.security.AdminPermissionAuthorizationFilter;
import com.lnzz.argus.security.AdminTokenAuthenticationFilter;
import com.lnzz.argus.security.ArgusSecurityResponseWriter;
import com.lnzz.argus.security.InternalTokenAuthenticationFilter;
import com.lnzz.argus.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Argus 项目级 Spring Security 配置。
 * <p>
 * 统一接管后端 API 安全入口：Portal 后台接口使用 Bearer Token，内部采集接口使用
 * X-Argus-Token，暂未纳入认证的公开接口显式放行，避免 Spring Security 默认策略误伤既有功能。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityWebConfig {

    private final AdminTokenAuthenticationFilter tokenAuthenticationFilter;
    private final AdminPermissionAuthorizationFilter permissionAuthorizationFilter;
    private final InternalTokenAuthenticationFilter internalTokenAuthenticationFilter;
    private final ArgusSecurityResponseWriter responseWriter;

    /**
     * Portal 后台安全过滤链。
     *
     * @param http Spring Security HTTP 配置器
     * @return 后台管理接口专用过滤链
     * @throws Exception 安全配置构建异常
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/admin/**", "/api/v1/rules/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                responseWriter.write(response, ResultCode.ADMIN_UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                responseWriter.write(response, ResultCode.ADMIN_FORBIDDEN)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/api/v1/admin/**", "/api/v1/rules/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/sessions").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(permissionAuthorizationFilter, AdminTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * 内部采集接口安全过滤链。
     *
     * @param http Spring Security HTTP 配置器
     * @return 内部接口专用过滤链
     * @throws Exception 安全配置构建异常
     */
    @Bean
    @Order(2)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                responseWriter.write(response, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                responseWriter.write(response, ResultCode.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/api/v1/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(internalTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 公开 API 兼容过滤链。
     *
     * @param http Spring Security HTTP 配置器
     * @return 暂未接入认证的 API 放行过滤链
     * @throws Exception 安全配置构建异常
     */
    @Bean
    @Order(3)
    public SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
