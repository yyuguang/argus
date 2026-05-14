package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SCM 平台配置属性
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.scm")
public class ScmProperties {

    private Platform gitlab = new Platform();

    private Platform github = new Platform();

    private Platform gitee = new Platform();

    @Data
    public static class Platform {
        /** 是否启用 */
        private boolean enabled = true;
        /** API 基础地址 */
        private String apiBaseUrl;
        /** Web 基础地址 */
        private String webBaseUrl;
        /** API Token */
        private String token;
        /** Webhook 密钥 */
        private String webhookSecret;
        /** API 超时时间(ms) */
        private int apiTimeout = 10000;
        /** 最大文件大小(bytes) */
        private int maxFileSize = 1048576;
        /** 重试配置 */
        private Retry retry = new Retry();
    }

    @Data
    public static class Retry {
        /** 最大重试次数 */
        private int maxAttempts = 3;
        /** 退避倍数 */
        private int backoffMultiplier = 2;
    }
}
