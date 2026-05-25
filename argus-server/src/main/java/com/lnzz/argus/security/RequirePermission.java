package com.lnzz.argus.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台管理接口权限声明。
 * <p>声明在 Controller 方法上，由 Spring Security 权限过滤器统一执行后端强校验。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 完整权限编码，例如 system:security:user:create。
     */
    String value();
}
