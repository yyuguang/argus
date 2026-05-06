package com.lnzz.argus.notification.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * M7-01: 企业微信 Webhook 客户端
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatWebhookClient {

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送 Markdown 消息
     *
     * @param channel 通道名（对应配置中的 webhooks key）
     * @param content Markdown 内容
     * @return 是否成功
     */
    public boolean sendMarkdown(String channel, String content) {
        String webhookUrl = getWebhookUrl(channel);
        if (webhookUrl == null) {
            log.error("企微通道不存在: channel={}", channel);
            return false;
        }

        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", content)
        );

        return doSend(webhookUrl, body);
    }

    /**
     * 发送文本消息（支持 @人员）
     *
     * @param channel      通道名
     * @param content      文本内容
     * @param mentionUsers @的用户ID列表（企微用户ID）
     * @return 是否成功
     */
    public boolean sendText(String channel, String content, java.util.List<String> mentionUsers) {
        String webhookUrl = getWebhookUrl(channel);
        if (webhookUrl == null) {
            log.error("企微通道不存在: channel={}", channel);
            return false;
        }

        Map<String, Object> textMap = new java.util.HashMap<>();
        textMap.put("content", content);
        if (mentionUsers != null && !mentionUsers.isEmpty()) {
            textMap.put("mentioned_list", mentionUsers);
        }

        Map<String, Object> body = Map.of(
                "msgtype", "text",
                "text", textMap
        );

        return doSend(webhookUrl, body);
    }

    private boolean doSend(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("企微消息发送成功");
                return true;
            }
            log.warn("企微消息发送失败: status={}, body={}", response.getStatusCode(), response.getBody());
            return false;
        } catch (Exception e) {
            log.error("企微消息发送异常", e);
            return false;
        }
    }

    private String getWebhookUrl(String channel) {
        Map<String, String> webhooks = properties.getWechat().getWebhooks();
        if (webhooks == null) {
            return null;
        }
        return webhooks.getOrDefault(channel, webhooks.get("default"));
    }
}
