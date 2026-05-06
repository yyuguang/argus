package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通知配置属性
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.notification")
public class NotificationProperties {

    /** 企业微信配置 */
    private WechatConfig wechat = new WechatConfig();

    @Data
    public static class WechatConfig {
        /** Webhook 地址映射（通道名→URL） */
        private Map<String, String> webhooks;

        /** 频率控制 */
        private RateLimit rateLimit = new RateLimit();
    }

    @Data
    public static class RateLimit {
        /** 同类型通知最小间隔(秒) */
        private int sameTypeInterval = 60;
        /** 每小时最大通知数 */
        private int maxPerHour = 30;
    }
}
