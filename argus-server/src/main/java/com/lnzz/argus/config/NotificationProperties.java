package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通知配置属性（M7-A01/A04/A05）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.notification")
public class NotificationProperties {

    /** 是否启用通知 */
    private boolean enabled = true;

    /** 企业微信配置 */
    private WechatConfig wechat = new WechatConfig();

    /** 飞书配置（预留） */
    private FeishuConfig feishu = new FeishuConfig();

    /** 钉钉配置（预留） */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    /** 静默配置 */
    private SilenceConfig silence = new SilenceConfig();

    @Data
    public static class WechatConfig {
        private RateLimit rateLimit = new RateLimit();
    }

    @Data
    public static class FeishuConfig {
        private boolean enabled = false;
        private Map<String, String> webhooks;
    }

    @Data
    public static class DingTalkConfig {
        private boolean enabled = false;
        private Map<String, String> webhooks;
    }

    @Data
    public static class RateLimit {
        private int sameTypeInterval = 60;
        private int maxPerHour = 30;
    }

    /**
     * M7-A05: 静默控制
     */
    @Data
    public static class SilenceConfig {
        /** P0/P1 永不禁用 */
        private boolean alwaysNotifyP0P1 = true;
        /** 同指纹通知间隔(秒) */
        private int fingerprintInterval = 300;
        /** P3 通知间隔(秒) */
        private int p3Interval = 3600;
        /** 全局每小时上限 */
        private int globalMaxPerHour = 30;
    }

}
