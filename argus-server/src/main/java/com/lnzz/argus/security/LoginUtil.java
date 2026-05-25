package com.lnzz.argus.security;

import java.util.Optional;

/**
 * 后台登录用户工具类。
 * <p>
 * 该工具只读取认证拦截器写入的线程上下文，不持有业务状态。
 * MyBatis 自动填充、审计日志和业务服务可通过它统一获取当前操作者。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class LoginUtil {

    public static final String SYSTEM_OPERATOR = "system";
    public static final String ANONYMOUS_OPERATOR = "anonymous";

    private LoginUtil() {
    }

    /**
     * 获取当前登录用户。
     *
     * @return 当前登录用户；未认证请求返回 Optional.empty()
     */
    public static Optional<CurrentUser> currentUser() {
        return CurrentUserContext.get();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID；无登录态时返回 null
     */
    public static Long currentUserIdOrNull() {
        return currentUser().map(CurrentUser::userId).orElse(null);
    }

    /**
     * 获取当前登录账号，未登录时使用系统操作者兜底。
     *
     * @return 当前登录账号或 system
     */
    public static String currentUsernameOrSystem() {
        return currentUsernameOrDefault(SYSTEM_OPERATOR);
    }

    /**
     * 获取当前登录账号，未登录时使用匿名操作者兜底。
     *
     * @return 当前登录账号或 anonymous
     */
    public static String currentUsernameOrAnonymous() {
        return currentUsernameOrDefault(ANONYMOUS_OPERATOR);
    }

    /**
     * 获取当前登录账号，未登录时使用指定默认值。
     *
     * @param defaultUsername 默认操作者
     * @return 当前登录账号或默认操作者
     */
    public static String currentUsernameOrDefault(String defaultUsername) {
        return currentUser()
                .map(CurrentUser::username)
                .filter(username -> username != null && !username.isBlank())
                .orElse(defaultUsername);
    }
}
