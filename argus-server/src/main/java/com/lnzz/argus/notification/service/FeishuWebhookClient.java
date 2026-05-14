package com.lnzz.argus.notification.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * M7-A04: 飞书 Webhook 客户端（预留适配器）
 * <p>当前为占位实现，后续接入飞书开放平台后可激活</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuWebhookClient {

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送飞书消息（Markdown 格式，预留）
     */
    public boolean sendInteractive(String channel, String title, String content) {
        if (!properties.getFeishu().isEnabled()) {
            log.debug("飞书通知未启用, channel={}", channel);
            return false;
        }

        String webhookUrl = resolveWebhook(channel);
        if (webhookUrl == null) {
            log.warn("飞书 Webhook 未配置: channel={}", channel);
            return false;
        }

        try {
            Map<String, Object> body = Map.of(
                    "msg_type", "interactive",
                    "card", Map.of(
                            "header", Map.of(
                                    "title", Map.of("content", title),
                                    "template", "red"
                            ),
                            "elements", List.of(
                                    Map.of("tag", "markdown", "content", content)
                            )
                    )
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("飞书消息发送成功: channel={}", channel);
                return true;
            }
            log.warn("飞书消息发送失败: status={}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("飞书消息发送异常", e);
            return false;
        }
    }

    private String resolveWebhook(String channel) {
        Map<String, String> webhooks = properties.getFeishu().getWebhooks();
        if (webhooks == null) return null;
        return webhooks.getOrDefault(channel, webhooks.get("default"));
    }
}
