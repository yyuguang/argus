package com.lnzz.argus.notification.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.net.URI;
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

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送飞书交互卡片消息。
     */
    public boolean sendInteractive(String title, String content, String webhookUrl) {
        String normalizedWebhook = normalizeWebhook(webhookUrl);
        if (!StringUtils.hasText(normalizedWebhook)) {
            log.warn("飞书 Webhook 未配置或非法");
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
            ResponseEntity<String> response = restTemplate.postForEntity(normalizedWebhook, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("飞书消息发送成功");
                return true;
            }
            log.warn("飞书消息发送失败: status={}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("飞书消息发送异常", e);
            return false;
        }
    }

    private String normalizeWebhook(String webhookUrl) {
        if (!StringUtils.hasText(webhookUrl)) {
            return null;
        }
        String trimmed = webhookUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            if (!uri.isAbsolute() || uri.getScheme() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                log.warn("飞书 webhook 地址非法，已跳过发送: {}", maskWebhook(trimmed));
                return null;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            log.warn("飞书 webhook 地址解析失败，已跳过发送: {}", maskWebhook(trimmed));
            return null;
        }
    }

    private String maskWebhook(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.length() <= 16) {
            return "****";
        }
        return webhookUrl.substring(0, 12) + "****";
    }
}
