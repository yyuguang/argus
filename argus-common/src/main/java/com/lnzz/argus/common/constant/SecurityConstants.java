package com.lnzz.argus.common.constant;

/**
 * 通用安全常量。
 * <p>
 * 该类只定义认证、授权流程中的跨模块公共约定，避免在过滤器、服务层和数据初始化逻辑中散落魔法值。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class SecurityConstants {

    /** HTTP Authorization 请求头名称。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer Token 请求头前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 后台管理会话 Token 类型。 */
    public static final String ADMIN_TOKEN_TYPE = "ADMIN";

    /** 超级管理员角色编码。 */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /** 通配权限码，表示拥有全部权限。 */
    public static final String ALL_PERMISSION = "*:*:*";

    private SecurityConstants() {
    }
}
