package com.lnzz.argus.notification.service;

import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.common.enums.NotificationStatus;
import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SCM 通知分发器。
 *
 * <p>统一处理多平台通知发送、重试和通知记录持久化。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScmNotificationDispatcher {

    private final WechatWebhookClient wechatClient;
    private final FeishuWebhookClient feishuClient;
    private final DingTalkWebhookClient dingTalkClient;
    private final NotificationRecordMapper recordMapper;
    private final NotificationProperties properties;
    private final ScmReviewConfigSupport scmReviewConfigSupport;

    public DispatchResult dispatchMarkdown(ScmConfig scmConfig,
                                           List<String> preferredPlatforms,
                                           String logicalChannel,
                                           String title,
                                           String content,
                                           String type,
                                           Long refId,
                                           String refType,
                                           ReviewConfig.NotificationRetryConfig retryConfig) {
        if (scmConfig == null) {
            return DispatchResult.skipped("SCM 配置不存在");
        }
        if (!properties.isEnabled()) {
            return DispatchResult.skipped("通知总开关已关闭");
        }

        ReviewConfig reviewConfig = scmReviewConfigSupport.resolveReviewConfig(scmConfig);
        ReviewConfig.NotificationConfig notificationConfig = reviewConfig.getNotification();
        List<String> targetPlatforms = resolveTargetPlatforms(preferredPlatforms, notificationConfig);
        log.info("开始分发 SCM 通知: scmConfigId={}, type={}, refId={}, channel={}, preferredPlatforms={}, targetPlatforms={}",
                scmConfig.getId(), type, refId, logicalChannel, preferredPlatforms, targetPlatforms);
        if (targetPlatforms.isEmpty()) {
            return DispatchResult.skipped("未配置通知平台");
        }

        ReviewConfig.NotificationRetryConfig effectiveRetry = retryConfig != null
                ? retryConfig
                : ReviewConfig.defaults().getNotification().getRetry();
        int attempted = 0;
        int success = 0;
        List<String> skipped = new ArrayList<>();

        for (String platform : targetPlatforms) {
            ReviewConfig.NotificationPlatformConfig platformConfig =
                    notificationConfig.getPlatforms().get(platform);
            if (platformConfig == null || !platformConfig.isEnabled()) {
                skipped.add(displayName(platform) + "未启用");
                log.debug("跳过通知平台: platform={}, reason=disabled", platform);
                continue;
            }
            if (!StringUtils.hasText(platformConfig.getWebhook())) {
                skipped.add(displayName(platform) + "未配置 Webhook");
                log.debug("跳过通知平台: platform={}, reason=missingWebhook", platform);
                continue;
            }

            attempted++;
            boolean sent = sendWithRetry(platform, logicalChannel, title, content,
                    type, refId, refType, platformConfig.getWebhook(), effectiveRetry);
            if (sent) {
                success++;
            }
        }

        if (attempted == 0) {
            log.info("SCM 通知未实际发送: scmConfigId={}, type={}, refId={}, reason={}",
                    scmConfig.getId(), type, refId, String.join("；", skipped));
            return DispatchResult.skipped(String.join("；", skipped));
        }
        log.info("SCM 通知分发完成: scmConfigId={}, type={}, refId={}, attempted={}, success={}, skipped={}",
                scmConfig.getId(), type, refId, attempted, success, skipped);
        return new DispatchResult(success > 0, attempted, success,
                success > 0 ? "发送成功 " + success + "/" + attempted : "发送失败");
    }

    public record DispatchResult(boolean success, int attemptedCount, int successCount, String message) {
        public static DispatchResult skipped(String message) {
            return new DispatchResult(false, 0, 0, message);
        }
    }

    private boolean sendWithRetry(String platform,
                                  String logicalChannel,
                                  String title,
                                  String content,
                                  String type,
                                  Long refId,
                                  String refType,
                                  String webhookUrl,
                                  ReviewConfig.NotificationRetryConfig retryConfig) {
        int maxRetries = Math.max(0, retryConfig.getMaxRetries());
        List<Integer> backoffSeconds = retryConfig.getBackoffSeconds() != null
                ? retryConfig.getBackoffSeconds()
                : ReviewConfig.defaults().getNotification().getRetry().getBackoffSeconds();
        int timeoutSec = Math.max(1, retryConfig.getTimeoutSec());
        long deadlineMillis = System.currentTimeMillis() + timeoutSec * 1000L;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                int delay = attempt <= backoffSeconds.size()
                        ? Math.max(0, backoffSeconds.get(attempt - 1))
                        : 300;
                if (System.currentTimeMillis() + delay * 1000L > deadlineMillis) {
                    log.warn("通知重试超时窗口不足，停止重试: platform={}, type={}, refId={}, attempt={}, timeoutSec={}",
                            platform, type, refId, attempt, timeoutSec);
                    break;
                }
                log.info("通知重试: platform={}, attempt={}/{}, delay={}s, timeoutSec={}",
                        platform, attempt, maxRetries, delay, timeoutSec);
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            try {
                boolean success = sendOnce(platform, logicalChannel, title, content, webhookUrl);
                if (success) {
                    saveRecord(type, refId, refType, platform, content,
                            NotificationStatus.SENT.getCode(), attempt, null, LocalDateTime.now());
                    return true;
                }
            } catch (Exception e) {
                log.warn("通知发送异常: platform={}, attempt={}, type={}, refId={}",
                        platform, attempt, type, refId, e);
            }
        }

        saveRecord(type, refId, refType, platform, content,
                NotificationStatus.FAILED.getCode(), maxRetries, "重试后仍发送失败", null);
        return false;
    }

    private boolean sendOnce(String platform,
                             String logicalChannel,
                             String title,
                             String content,
                             String webhookUrl) {
        return switch (normalizePlatform(platform)) {
            case NotificationConstants.PLATFORM_WECHAT ->
                    wechatClient.sendMarkdown(logicalChannel, content, webhookUrl);
            case NotificationConstants.PLATFORM_FEISHU ->
                    feishuClient.sendInteractive(title, content, webhookUrl);
            case NotificationConstants.PLATFORM_DINGTALK ->
                    dingTalkClient.sendMarkdown(title, content, webhookUrl);
            default -> {
                log.warn("未知通知平台，跳过发送: platform={}", platform);
                yield false;
            }
        };
    }

    private List<String> resolveTargetPlatforms(List<String> preferredPlatforms,
                                                ReviewConfig.NotificationConfig notificationConfig) {
        Set<String> platforms = new LinkedHashSet<>();
        if (preferredPlatforms != null) {
            preferredPlatforms.stream()
                    .filter(StringUtils::hasText)
                    .map(this::normalizePlatform)
                    .forEach(platforms::add);
        }
        // 评审通知可以只发到选中的平台；错误/监控告警未指定时默认遍历当前 SCM 已配置的平台集合。
        if (platforms.isEmpty() && notificationConfig.getPlatforms() != null) {
            notificationConfig.getPlatforms().keySet().stream()
                    .map(this::normalizePlatform)
                    .forEach(platforms::add);
        }
        return new ArrayList<>(platforms);
    }

    private void saveRecord(String type,
                            Long refId,
                            String refType,
                            String platform,
                            String content,
                            String status,
                            int retryCount,
                            String errorMessage,
                            LocalDateTime sentAt) {
        try {
            NotificationRecord record = new NotificationRecord();
            record.setType(type);
            record.setChannel(normalizePlatform(platform).toUpperCase(Locale.ROOT));
            record.setRefId(refId);
            record.setRefType(refType);
            record.setContentSummary(abbreviate(content, 500));
            record.setStatus(status);
            record.setRetryCount(retryCount);
            record.setErrorMessage(abbreviate(errorMessage, 500));
            record.setSentAt(sentAt);
            recordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存通知记录失败: type={}, refId={}, platform={}", type, refId, platform, e);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return NotificationConstants.PLATFORM_WECHAT;
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private String displayName(String platform) {
        return switch (normalizePlatform(platform)) {
            case NotificationConstants.PLATFORM_WECHAT -> "企业微信";
            case NotificationConstants.PLATFORM_FEISHU -> "飞书";
            case NotificationConstants.PLATFORM_DINGTALK -> "钉钉";
            default -> platform;
        };
    }
}
