package com.lnzz.argus.common.enums;

/**
 * 条件权限类型。
 * <p>首版只实现时间段与 IP CIDR，保留 JSON 配置结构便于后续扩展。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum PermissionConditionType {

    /** 按每天的时间窗口限制，例如 09:00:00 到 18:00:00。 */
    TIME_RANGE,

    /** 按客户端 IP 或 CIDR 网段限制。 */
    IP_CIDR
}
