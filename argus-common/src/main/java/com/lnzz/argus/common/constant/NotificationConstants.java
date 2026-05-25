package com.lnzz.argus.common.constant;

/**
 * 通知领域常量。
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class NotificationConstants {

    /** 通知类型：错误告警。 */
    public static final String TYPE_ERROR_ALERT = "ERROR_ALERT";

    /** 通知关联类型：错误事件。 */
    public static final String REF_TYPE_ERROR_EVENT = "ERROR_EVENT";

    /** 默认通知通道。 */
    public static final String CHANNEL_DEFAULT = "default";

    /** 严重告警通知通道。 */
    public static final String CHANNEL_CRITICAL = "critical";

    /** 通知平台：企业微信。 */
    public static final String PLATFORM_WECHAT = "wechat";

    /** 通知平台：飞书。 */
    public static final String PLATFORM_FEISHU = "feishu";

    /** 通知平台：钉钉。 */
    public static final String PLATFORM_DINGTALK = "dingtalk";

    /** 通知优先级：紧急。 */
    public static final String PRIORITY_URGENT = "urgent";

    /** 通知优先级：普通。 */
    public static final String PRIORITY_NORMAL = "normal";

    /** 通知优先级：低。 */
    public static final String PRIORITY_LOW = "low";

    /** Redis 通知限流 key 前缀。 */
    public static final String RATE_KEY_PREFIX = "argus:notify:rate:";

    /** Redis 通知静默 key 前缀。 */
    public static final String SILENCE_KEY_PREFIX = "argus:notify:silence:";

    /** Redis 全局通知计数 key。 */
    public static final String GLOBAL_COUNT_KEY = "argus:notify:global:count";

    private NotificationConstants() {
    }
}
