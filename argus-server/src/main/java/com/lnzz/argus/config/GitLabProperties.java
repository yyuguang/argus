package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitLab 配置属性
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.gitlab")
public class GitLabProperties {

    /** GitLab 地址 */
    private String url;

    /** API Token */
    private String token;

    /** Webhook 签名密钥 */
    private String webhookSecret;

    /** API 超时时间(ms) */
    private int apiTimeout = 10000;

    /** 最大文件大小(bytes) */
    private int maxFileSize = 1048576;

    /** 重试配置 */
    private Retry retry = new Retry();

    @Data
    public static class Retry {
        /** 最大重试次数 */
        private int maxAttempts = 3;
        /** 退避倍数 */
        private int backoffMultiplier = 2;
    }
}
