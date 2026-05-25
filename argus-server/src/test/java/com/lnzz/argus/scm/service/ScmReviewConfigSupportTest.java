package com.lnzz.argus.scm.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ScmReviewConfigSupport - SCM 评审配置兼容")
class ScmReviewConfigSupportTest {

    private final ScmReviewConfigSupport support = new ScmReviewConfigSupport();

    @Test
    @DisplayName("解析配置时会把飞书和钉钉独立字段回填到 reviewConfig 平台模型")
    void resolveReviewConfigShouldApplyFeishuAndDingTalkColumns() {
        ScmConfig config = new ScmConfig();
        config.setFeishuNotifyEnabled(1);
        config.setFeishuNotifyWebhook("https://open.feishu.cn/open-apis/bot/v2/hook/feishu-token");
        config.setDingtalkNotifyEnabled(1);
        config.setDingtalkNotifyWebhook("https://oapi.dingtalk.com/robot/send?access_token=dingtalk-token");

        ReviewConfig resolved = support.resolveReviewConfig(config);

        ReviewConfig.NotificationPlatformConfig feishu =
                resolved.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU);
        ReviewConfig.NotificationPlatformConfig dingtalk =
                resolved.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK);
        assertTrue(feishu.isEnabled());
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/feishu-token", feishu.getWebhook());
        assertTrue(dingtalk.isEnabled());
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=dingtalk-token", dingtalk.getWebhook());
    }

    @Test
    @DisplayName("合并配置时多平台 Webhook 留空会保留原值")
    void mergeReviewConfigShouldPreserveBlankMultiPlatformWebhook() {
        ReviewConfig existing = ReviewConfig.defaults();
        existing.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU).setEnabled(true);
        existing.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU)
                .setWebhook("https://open.feishu.cn/open-apis/bot/v2/hook/old-feishu");
        existing.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK).setEnabled(true);
        existing.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK)
                .setWebhook("https://oapi.dingtalk.com/robot/send?access_token=old-dingtalk");

        ReviewConfig incoming = ReviewConfig.defaults();
        incoming.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU).setEnabled(false);
        incoming.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU).setWebhook("");
        incoming.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK).setEnabled(true);
        incoming.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK).setWebhook("");

        String mergedJson = support.mergeReviewConfigForPersist(
                JSON.toJSONString(incoming), JSON.toJSONString(existing),
                null, null, null, null, null, null);
        ReviewConfig merged = support.resolveReviewConfig(mergedJson, null, null);

        ReviewConfig.NotificationPlatformConfig feishu =
                merged.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_FEISHU);
        ReviewConfig.NotificationPlatformConfig dingtalk =
                merged.getNotification().getPlatforms().get(NotificationConstants.PLATFORM_DINGTALK);
        assertEquals(0, support.resolvePlatformEnabled(merged, NotificationConstants.PLATFORM_FEISHU));
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/old-feishu", feishu.getWebhook());
        assertEquals(1, support.resolvePlatformEnabled(merged, NotificationConstants.PLATFORM_DINGTALK));
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=old-dingtalk", dingtalk.getWebhook());
    }
}
