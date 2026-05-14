package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("WechatWebhookClient - webhook 配置校验")
class WechatWebhookClientTest {

    @Test
    @DisplayName("未传入 SCM webhook 时不从全局配置兜底")
    void missingScmWebhookReturnsNull() {
        NotificationProperties properties = new NotificationProperties();

        WechatWebhookClient client = new WechatWebhookClient(properties);

        assertNull(client.getWebhookUrl("default", null));
    }

    @Test
    @DisplayName("空 webhook 不应被当成 URL 发送")
    void blankWebhookReturnsNull() {
        NotificationProperties properties = new NotificationProperties();

        WechatWebhookClient client = new WechatWebhookClient(properties);

        assertNull(client.getWebhookUrl("default", ""));
    }

    @Test
    @DisplayName("非绝对 URL 不应被当成 webhook 发送")
    void relativeWebhookReturnsNull() {
        NotificationProperties properties = new NotificationProperties();

        WechatWebhookClient client = new WechatWebhookClient(properties);

        assertNull(client.getWebhookUrl("default", "not-a-url"));
    }

    @Test
    @DisplayName("合法 https webhook 正常返回")
    void validWebhookReturnsTrimmedUrl() {
        NotificationProperties properties = new NotificationProperties();

        WechatWebhookClient client = new WechatWebhookClient(properties);

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc",
                client.getWebhookUrl("default", " https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc "));
    }
}
