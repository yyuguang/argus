package com.lnzz.argus.security;

import java.util.Optional;

/**
 * 当前用户线程上下文。
 * <p>认证拦截器在请求进入时写入，业务服务和审计服务可读取操作者信息。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static Optional<CurrentUser> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static Long userIdOrNull() {
        return get().map(CurrentUser::userId).orElse(null);
    }

    public static String usernameOrSystem() {
        return get().map(CurrentUser::username).orElse(LoginUtil.SYSTEM_OPERATOR);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
