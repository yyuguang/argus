package com.lnzz.argus.scm.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SCM 评审配置支撑组件。
 *
 * <p>负责 reviewConfig 的解析、兼容旧版企微字段、Webhook 脱敏以及保存前的密钥保留。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ScmReviewConfigSupport {

    public ReviewConfig resolveReviewConfig(ScmConfig scmConfig) {
        if (scmConfig == null) {
            return ReviewConfig.defaults();
        }
        return resolveReviewConfig(
                scmConfig.getReviewConfig(),
                scmConfig.getWechatNotifyEnabled(),
                scmConfig.getWechatNotifyWebhook(),
                scmConfig.getFeishuNotifyEnabled(),
                scmConfig.getFeishuNotifyWebhook(),
                scmConfig.getDingtalkNotifyEnabled(),
                scmConfig.getDingtalkNotifyWebhook());
    }

    public ReviewConfig resolveReviewConfig(String rawReviewConfig,
                                            Integer legacyWechatEnabled,
                                            String legacyWechatWebhook) {
        return resolveReviewConfig(rawReviewConfig, legacyWechatEnabled, legacyWechatWebhook,
                null, null, null, null);
    }

    public ReviewConfig resolveReviewConfig(String rawReviewConfig,
                                            Integer legacyWechatEnabled,
                                            String legacyWechatWebhook,
                                            Integer feishuEnabled,
                                            String feishuWebhook,
                                            Integer dingtalkEnabled,
                                            String dingtalkWebhook) {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig reviewConfig = defaults;
        if (StringUtils.hasText(rawReviewConfig)) {
            try {
                reviewConfig = defaults.merge(JSON.parseObject(rawReviewConfig, ReviewConfig.class));
            } catch (Exception e) {
                log.warn("SCM reviewConfig 解析失败，回退默认配置: error={}", e.getMessage());
            }
        }
        log.debug("解析 SCM reviewConfig: hasRaw={}, wechatEnabled={}, feishuEnabled={}, dingtalkEnabled={}",
                StringUtils.hasText(rawReviewConfig), legacyWechatEnabled, feishuEnabled, dingtalkEnabled);
        applyLegacyWechatConfig(reviewConfig, legacyWechatEnabled, legacyWechatWebhook);
        applyPlatformConfig(reviewConfig, NotificationConstants.PLATFORM_FEISHU, feishuEnabled, feishuWebhook);
        applyPlatformConfig(reviewConfig, NotificationConstants.PLATFORM_DINGTALK, dingtalkEnabled, dingtalkWebhook);
        return reviewConfig;
    }

    public String mergeReviewConfigForPersist(String incomingReviewConfig,
                                              String existingReviewConfig,
                                              Integer legacyWechatEnabled,
                                              String legacyWechatWebhook) {
        return mergeReviewConfigForPersist(incomingReviewConfig, existingReviewConfig,
                legacyWechatEnabled, legacyWechatWebhook, null, null, null, null);
    }

    public String mergeReviewConfigForPersist(String incomingReviewConfig,
                                              String existingReviewConfig,
                                              Integer legacyWechatEnabled,
                                              String legacyWechatWebhook,
                                              Integer feishuEnabled,
                                              String feishuWebhook,
                                              Integer dingtalkEnabled,
                                              String dingtalkWebhook) {
        ReviewConfig existing = resolveReviewConfig(existingReviewConfig, legacyWechatEnabled, legacyWechatWebhook,
                feishuEnabled, feishuWebhook, dingtalkEnabled, dingtalkWebhook);
        if (!StringUtils.hasText(incomingReviewConfig)) {
            log.debug("保存 SCM reviewConfig 时未传入新配置，直接沿用现有配置");
            return JSON.toJSONString(existing);
        }
        ReviewConfig incoming = resolveReviewConfig(incomingReviewConfig, legacyWechatEnabled, legacyWechatWebhook,
                feishuEnabled, feishuWebhook, dingtalkEnabled, dingtalkWebhook);

        // 编辑态 webhook 留空表示“保留原值”，这里按平台逐一补回。
        preserveBlankWebhook(incoming, existing, NotificationConstants.PLATFORM_WECHAT);
        preserveBlankWebhook(incoming, existing, NotificationConstants.PLATFORM_FEISHU);
        preserveBlankWebhook(incoming, existing, NotificationConstants.PLATFORM_DINGTALK);

        log.debug("合并 SCM reviewConfig 完成: hasExisting={}, hasIncoming={}, platforms={}",
                StringUtils.hasText(existingReviewConfig),
                StringUtils.hasText(incomingReviewConfig),
                incoming.getNotification().getPlatforms().keySet());
        return JSON.toJSONString(incoming);
    }

    public String maskReviewConfigSecrets(String reviewConfig) {
        if (!StringUtils.hasText(reviewConfig)) {
            return reviewConfig;
        }
        ReviewConfig resolved = resolveReviewConfig(reviewConfig, null, null);
        return maskReviewConfigSecrets(resolved);
    }

    public String maskReviewConfigSecrets(ScmConfig scmConfig) {
        if (scmConfig == null) {
            return null;
        }
        return maskReviewConfigSecrets(resolveReviewConfig(scmConfig));
    }

    public String maskReviewConfigSecrets(ReviewConfig resolved) {
        if (resolved == null) {
            return null;
        }
        maskWebhook(resolved, NotificationConstants.PLATFORM_WECHAT);
        maskWebhook(resolved, NotificationConstants.PLATFORM_FEISHU);
        maskWebhook(resolved, NotificationConstants.PLATFORM_DINGTALK);
        return JSON.toJSONString(resolved);
    }

    public Integer resolveLegacyWechatEnabled(ReviewConfig reviewConfig) {
        return resolvePlatformEnabled(reviewConfig, NotificationConstants.PLATFORM_WECHAT);
    }

    public String resolveLegacyWechatWebhook(ReviewConfig reviewConfig) {
        return resolvePlatformWebhook(reviewConfig, NotificationConstants.PLATFORM_WECHAT);
    }

    public Integer resolvePlatformEnabled(ReviewConfig reviewConfig, String platform) {
        ReviewConfig.NotificationPlatformConfig platformConfig = getPlatformConfig(reviewConfig, platform);
        return platformConfig != null && platformConfig.isEnabled() ? 1 : 0;
    }

    public String resolvePlatformWebhook(ReviewConfig reviewConfig, String platform) {
        ReviewConfig.NotificationPlatformConfig platformConfig = getPlatformConfig(reviewConfig, platform);
        if (platformConfig == null || !StringUtils.hasText(platformConfig.getWebhook())) {
            return null;
        }
        return platformConfig.getWebhook().trim();
    }

    private void preserveBlankWebhook(ReviewConfig incoming, ReviewConfig existing, String platform) {
        ReviewConfig.NotificationPlatformConfig incomingConfig = getPlatformConfig(incoming, platform);
        ReviewConfig.NotificationPlatformConfig existingConfig = getPlatformConfig(existing, platform);
        if (incomingConfig == null || existingConfig == null) {
            return;
        }
        if (!StringUtils.hasText(incomingConfig.getWebhook()) && StringUtils.hasText(existingConfig.getWebhook())) {
            incomingConfig.setWebhook(existingConfig.getWebhook());
            log.debug("SCM reviewConfig 保留平台 webhook 原值: platform={}", platform);
        }
    }

    private void applyLegacyWechatConfig(ReviewConfig reviewConfig,
                                         Integer legacyWechatEnabled,
                                         String legacyWechatWebhook) {
        // 旧字段仍保留在表结构里，统一回填到新结构的 platforms.wechat，保证历史配置平滑升级。
        ReviewConfig.NotificationPlatformConfig wechatConfig =
                getOrCreatePlatformConfig(reviewConfig, NotificationConstants.PLATFORM_WECHAT);
        if (legacyWechatEnabled != null) {
            wechatConfig.setEnabled(legacyWechatEnabled == 1);
        } else if (!reviewConfig.getNotification().isWechatNotifyEnabled()) {
            wechatConfig.setEnabled(false);
        }
        if (!StringUtils.hasText(wechatConfig.getWebhook()) && StringUtils.hasText(legacyWechatWebhook)) {
            wechatConfig.setWebhook(legacyWechatWebhook.trim());
            log.debug("旧版企业微信 webhook 已回填到新配置模型");
        }
    }

    private void applyPlatformConfig(ReviewConfig reviewConfig,
                                     String platform,
                                     Integer enabled,
                                     String webhook) {
        ReviewConfig.NotificationPlatformConfig platformConfig = getOrCreatePlatformConfig(reviewConfig, platform);
        if (enabled != null) {
            platformConfig.setEnabled(enabled == 1);
        }
        if (!StringUtils.hasText(platformConfig.getWebhook()) && StringUtils.hasText(webhook)) {
            platformConfig.setWebhook(webhook.trim());
            log.debug("SCM 平台 webhook 已回填到新配置模型: platform={}", platform);
        }
    }

    private void maskWebhook(ReviewConfig reviewConfig, String platform) {
        ReviewConfig.NotificationPlatformConfig platformConfig = getPlatformConfig(reviewConfig, platform);
        if (platformConfig == null || !StringUtils.hasText(platformConfig.getWebhook())) {
            return;
        }
        platformConfig.setWebhook(maskSecret(platformConfig.getWebhook()));
    }

    private ReviewConfig.NotificationPlatformConfig getPlatformConfig(ReviewConfig reviewConfig, String platform) {
        if (reviewConfig == null
                || reviewConfig.getNotification() == null
                || reviewConfig.getNotification().getPlatforms() == null) {
            return null;
        }
        return reviewConfig.getNotification().getPlatforms().get(platform);
    }

    private ReviewConfig.NotificationPlatformConfig getOrCreatePlatformConfig(ReviewConfig reviewConfig, String platform) {
        ReviewConfig.NotificationPlatformConfig platformConfig = getPlatformConfig(reviewConfig, platform);
        if (platformConfig != null) {
            return platformConfig;
        }
        ReviewConfig.NotificationPlatformConfig created = new ReviewConfig.NotificationPlatformConfig();
        reviewConfig.getNotification().getPlatforms().put(platform, created);
        return created;
    }

    private String maskSecret(String secret) {
        String trimmed = secret.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, 4) + "********" + trimmed.substring(trimmed.length() - 4);
    }
}
