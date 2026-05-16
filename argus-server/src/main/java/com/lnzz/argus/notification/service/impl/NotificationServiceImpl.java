package com.lnzz.argus.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.notification.service.AlertTemplateBuilder;
import com.lnzz.argus.notification.service.DingTalkWebhookClient;
import com.lnzz.argus.notification.service.FeishuWebhookClient;
import com.lnzz.argus.notification.service.NotificationRouter;
import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.notification.service.WechatWebhookClient;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.common.enums.SourceType;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.common.enums.NotificationStatus;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 通知服务实现
 * <p>统一通知入口，支持评审通知、错误告警、多渠道分发、重试与静默控制</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final WechatWebhookClient wechatClient;
    private final FeishuWebhookClient feishuClient;
    private final DingTalkWebhookClient dingtalkClient;
    private final NotificationRecordMapper recordMapper;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties properties;
    private final NotificationRouter router;
    private final AlertTemplateBuilder templateBuilder;
    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigMapper scmConfigMapper;

    private static final String RATE_KEY_PREFIX = "argus:notify:rate:";
    private static final String SILENCE_KEY_PREFIX = "argus:notify:silence:";
    private static final String GLOBAL_COUNT_KEY = "argus:notify:global:count";

    // ======================== 评审通知 ========================

    @Override
    public boolean sendReviewNotification(ReviewTask task, ScoreCalculator.ScoreResult score) {
        return sendReviewNotification(task, score, null, ReviewConfig.defaults());
    }

    @Override
    public boolean sendReviewNotification(ReviewTask task, ScoreCalculator.ScoreResult score,
                                          ScmConfig scmConfig, ReviewConfig reviewConfig) {
        if (!properties.isEnabled()) {
            log.info("通知总开关已关闭，跳过评审通知: taskId={}", task.getId());
            return false;
        }
        String dedupKey = "REVIEW:" + task.getId();
        if (isDuplicate(dedupKey, 60)) {
            log.info("评审通知已发送过, taskId={}", task.getId());
            return true;
        }
        if (scmConfig == null) {
            log.info("缺少 SCM 配置，跳过评审通知: taskId={}", task.getId());
            return false;
        }
        if (!scmConfig.isWechatNotificationEnabled()) {
            log.info("SCM 配置已关闭企业微信通知，跳过评审通知: taskId={}, scmConfigId={}",
                    task.getId(), scmConfig.getId());
            return false;
        }
        if (isBlank(scmConfig.getWechatNotifyWebhook())) {
            log.info("SCM 配置未配置企业微信 webhook，跳过评审通知: taskId={}, scmConfigId={}",
                    task.getId(), scmConfig.getId());
            return false;
        }

        String content = templateBuilder.buildReviewAlert(
                task.getProjectName(), task.getMrIid(), task.getMrTitle(),
                task.getAuthorName(), task.getSourceBranch(), task.getTargetBranch(),
                score.getTotalScore(), score.getScoreLevel(), score.isPassed(),
                score.getCriticalCount(), score.getMajorCount(), score.getMinorCount(),
                task.getMrUrl());

        int threshold = reviewConfig != null
                ? reviewConfig.getNotification().getScoreAlertThreshold()
                : ReviewConfig.defaults().getNotification().getScoreAlertThreshold();
        String channel = (!score.isPassed() || score.getTotalScore() <= threshold) ? "critical" : "default";
        return sendWithRetry(channel, content, "REVIEW", task.getId(), "REVIEW_TASK",
                scmConfig.getWechatNotifyWebhook(), resolveRetryConfig(reviewConfig));
    }

    // ======================== 错误告警 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendErrorAlert(ErrorEvent event, ErrorAnalysis analysis) {
        if (!properties.isEnabled()) {
            log.info("通知总开关已关闭，跳过错误告警: eventId={}", event.getId());
            saveErrorAlertSkip(event, "通知总开关已关闭");
            return false;
        }
        ScmConfig scmConfig = resolveScmConfigForError(event);
        if (scmConfig == null) {
            log.info("错误告警未找到 SCM 配置，跳过企微通知: eventId={}, appName={}",
                    event.getId(), event.getAppName());
            saveErrorAlertSkip(event, "未找到可用 SCM 配置: appName=" + event.getAppName());
            return false;
        }
        ReviewConfig reviewConfig = resolveReviewConfig(scmConfig);
        NotificationRouter.RouteResult route = router.route(event, reviewConfig.getNotification());
        if (!route.shouldNotify()) {
            log.info("SCM 通知路由抑制: eventId={}, appName={}, scmConfigId={}, severity={}, priority={}",
                    event.getId(), event.getAppName(), scmConfig.getId(), event.getSeverity(), route.priority());
            saveErrorAlertSkip(event, "SCM 通知路由抑制: scmConfigId=" + scmConfig.getId()
                    + ", severity=" + event.getSeverity()
                    + ", priority=" + route.priority());
            return false;
        }
        if (!scmConfig.isWechatNotificationEnabled()) {
            log.info("SCM 配置已关闭企业微信通知，跳过错误告警: eventId={}, appName={}, scmConfigId={}",
                    event.getId(), event.getAppName(), scmConfig.getId());
            saveErrorAlertSkip(event, "SCM 配置已关闭企业微信通知: scmConfigId=" + scmConfig.getId());
            return false;
        }
        if (isBlank(scmConfig.getWechatNotifyWebhook())) {
            log.info("SCM 配置未配置企业微信 webhook，跳过错误告警: eventId={}, appName={}, scmConfigId={}",
                    event.getId(), event.getAppName(), scmConfig.getId());
            saveErrorAlertSkip(event, "SCM 配置未配置企业微信 webhook: scmConfigId=" + scmConfig.getId());
            return false;
        }

        // 静默检查
        if (isSilenced(event)) {
            log.info("通知已静默抑制: eventId={}, fingerprint={}", event.getId(), event.getErrorFingerprint());
            saveErrorAlertSkip(event, "通知静默抑制: fingerprint=" + event.getErrorFingerprint());
            return false;
        }
        if (!checkGlobalRate()) {
            log.warn("全局通知频率超限，延迟通知: eventId={}", event.getId());
            saveErrorAlertSkip(event, "全局通知频率超限");
            return false;
        }

        // 构建告警内容
        boolean isDetailed = "urgent".equals(route.priority());
        String content = isDetailed
                ? templateBuilder.buildDetailedAlert(event, analysis)
                : templateBuilder.buildBriefAlert(event, analysis);

        String title = "[" + event.getSeverity() + "] " + event.getAppName() + " - " + event.getErrorType();

        // 企微通道
        boolean wechatOk = sendWithRetry(route.channel(), content, "ERROR_ALERT",
                event.getId(), "ERROR_EVENT", scmConfig.getWechatNotifyWebhook(),
                resolveRetryConfig(reviewConfig));

        // 飞书通道（预留）
        if (properties.getFeishu().isEnabled()) {
            try {
                feishuClient.sendInteractive(route.channel(), title, content);
            } catch (Exception e) {
                log.warn("飞书通知发送失败: eventId={}", event.getId(), e);
            }
        }

        // 钉钉通道（预留）
        if (properties.getDingtalk().isEnabled()) {
            try {
                dingtalkClient.sendMarkdown(route.channel(), title, content);
            } catch (Exception e) {
                log.warn("钉钉通知发送失败: eventId={}", event.getId(), e);
            }
        }

        if (wechatOk) {
            markSilenced(event);
        }
        log.info("错误告警通知完成: eventId={}, channel={}, detailed={}, sent={}",
                event.getId(), route.channel(), isDetailed, wechatOk);
        return wechatOk;
    }

    @Override
    @Async
    public void sendErrorAlertLegacy(String appName, String errorType, String errorMessage,
                                      String severity, String rootCause) {
        ErrorEvent dummyEvent = new ErrorEvent();
        dummyEvent.setAppName(appName);
        dummyEvent.setErrorType(errorType);
        dummyEvent.setErrorMessage(errorMessage);
        dummyEvent.setSeverity(severity);
        dummyEvent.setSourceType(SourceType.AGENT.getCode());

        ErrorAnalysis dummyAnalysis = new ErrorAnalysis();
        dummyAnalysis.setRootCause(rootCause);

        sendErrorAlert(dummyEvent, dummyAnalysis);
    }

    // ======================== 重试机制 ========================

    private boolean sendWithRetry(String channel, String content,
                                   String type, Long refId, String refType,
                                   String customWebhookUrl,
                                   ReviewConfig.NotificationRetryConfig retryConfig) {
        if (!properties.isEnabled()) {
            log.info("通知总开关已关闭，跳过发送: type={}, refId={}", type, refId);
            saveRecord(type, refId, refType, "通知总开关已关闭",
                    NotificationStatus.SKIPPED.getCode(), 0, "通知总开关已关闭", null);
            return false;
        }
        if (isBlank(customWebhookUrl)) {
            log.info("未配置 SCM 企业微信 webhook，跳过发送: type={}, refId={}, channel={}", type, refId, channel);
            saveRecord(type, refId, refType, "未配置 SCM 企业微信 webhook",
                    NotificationStatus.SKIPPED.getCode(), 0, "未配置 SCM 企业微信 webhook", null);
            return false;
        }
        ReviewConfig.NotificationRetryConfig effectiveRetry = retryConfig != null
                ? retryConfig
                : ReviewConfig.defaults().getNotification().getRetry();
        int maxRetries = Math.max(0, effectiveRetry.getMaxRetries());
        var backoffSeconds = effectiveRetry.getBackoffSeconds() != null
                ? effectiveRetry.getBackoffSeconds()
                : ReviewConfig.defaults().getNotification().getRetry().getBackoffSeconds();
        int timeoutSec = Math.max(1, effectiveRetry.getTimeoutSec());
        long deadlineMillis = System.currentTimeMillis() + timeoutSec * 1000L;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                int delay = attempt <= backoffSeconds.size()
                        ? Math.max(0, backoffSeconds.get(attempt - 1)) : 300;
                if (System.currentTimeMillis() + delay * 1000L > deadlineMillis) {
                    log.warn("通知重试超时窗口不足，停止重试: type={}, refId={}, attempt={}, timeoutSec={}",
                            type, refId, attempt, timeoutSec);
                    break;
                }
                log.info("通知重试: attempt={}/{}, delay={}s, timeoutSec={}", attempt, maxRetries, delay, timeoutSec);
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            try {
                boolean success = wechatClient.sendMarkdown(channel, content, customWebhookUrl);
                if (success) {
                    saveRecord(type, refId, refType, content, true, attempt);
                    return true;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("通知发送失败(attempt={}): channel={}, error={}",
                        attempt, channel, e.getMessage());
            }
        }

        log.error("通知发送全部重试失败: type={}, refId={}", type, refId, lastException);
        saveRecord(type, refId, refType, content, false, maxRetries);
        return false;
    }

    private ReviewConfig.NotificationRetryConfig resolveRetryConfig(ReviewConfig reviewConfig) {
        ReviewConfig.NotificationConfig notificationConfig = reviewConfig != null
                ? reviewConfig.getNotification()
                : ReviewConfig.defaults().getNotification();
        return notificationConfig != null
                ? notificationConfig.getRetry()
                : ReviewConfig.defaults().getNotification().getRetry();
    }

    private ScmConfig resolveScmConfigForError(ErrorEvent event) {
        if (event == null || isBlank(event.getAppName())) {
            return null;
        }
        ProjectMapping mapping = projectMappingMapper.selectOne(new LambdaQueryWrapper<ProjectMapping>()
                .eq(ProjectMapping::getAppName, event.getAppName())
                .last("LIMIT 1"));
        if (mapping == null) {
            return null;
        }
        return scmConfigMapper.selectOne(new LambdaQueryWrapper<ScmConfig>()
                .eq(ScmConfig::getProjectId, mapping.getScmProjectId())
                .eq(ScmConfig::getScmProvider, mapping.getScmProvider())
                .eq(ScmConfig::getEnabled, true)
                .last("LIMIT 1"));
    }

    private ReviewConfig resolveReviewConfig(ScmConfig scmConfig) {
        ReviewConfig defaults = ReviewConfig.defaults();
        if (scmConfig == null || isBlank(scmConfig.getReviewConfig())) {
            return defaults;
        }
        try {
            return defaults.merge(JSON.parseObject(scmConfig.getReviewConfig(), ReviewConfig.class));
        } catch (Exception e) {
            log.warn("SCM reviewConfig 解析失败，使用默认通知路由: scmConfigId={}, error={}",
                    scmConfig.getId(), e.getMessage());
            return defaults;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ======================== 静默控制 ========================

    private boolean isSilenced(ErrorEvent event) {
        NotificationProperties.SilenceConfig silence = properties.getSilence();

        if (silence.isAlwaysNotifyP0P1()) {
            String severity = event.getSeverity();
            if ("P0".equals(severity) || "P1".equals(severity)) {
                return false;
            }
        }

        String key;
        int interval;
        if ("P3".equals(event.getSeverity())) {
            key = SILENCE_KEY_PREFIX + "p3:" + event.getAppName();
            interval = silence.getP3Interval();
        } else {
            String fingerprint = event.getErrorFingerprint();
            if (fingerprint == null || fingerprint.isEmpty()) {
                return false;
            }
            key = SILENCE_KEY_PREFIX + fingerprint;
            interval = silence.getFingerprintInterval();
        }

        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    private void markSilenced(ErrorEvent event) {
        NotificationProperties.SilenceConfig silence = properties.getSilence();
        String key;
        int interval;
        if ("P3".equals(event.getSeverity())) {
            key = SILENCE_KEY_PREFIX + "p3:" + event.getAppName();
            interval = silence.getP3Interval();
        } else {
            String fingerprint = event.getErrorFingerprint();
            if (fingerprint == null || fingerprint.isEmpty()) return;
            key = SILENCE_KEY_PREFIX + fingerprint;
            interval = silence.getFingerprintInterval();
        }
        redisTemplate.opsForValue().set(key, "1", interval, TimeUnit.SECONDS);
    }

    private boolean checkGlobalRate() {
        int maxPerHour = properties.getSilence().getGlobalMaxPerHour();
        Long count = redisTemplate.opsForValue().increment(GLOBAL_COUNT_KEY);
        if (count == 1) {
            redisTemplate.expire(GLOBAL_COUNT_KEY, 1, TimeUnit.HOURS);
        }
        return count == null || count <= maxPerHour;
    }

    // ======================== 基础工具 ========================

    private boolean isDuplicate(String key, int ttlSeconds) {
        String redisKey = RATE_KEY_PREFIX + key;
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            return true;
        }
        redisTemplate.opsForValue().set(redisKey, "1", ttlSeconds, TimeUnit.SECONDS);
        return false;
    }

    private void saveRecord(String type, Long refId, String refType,
                             String content, boolean success, int retryCount) {
        saveRecord(type, refId, refType, content,
                success ? NotificationStatus.SENT.getCode() : NotificationStatus.FAILED.getCode(),
                retryCount,
                !success && retryCount > 0 ? "重试 " + retryCount + " 次后失败" : null,
                success ? LocalDateTime.now() : null);
    }

    private void saveErrorAlertSkip(ErrorEvent event, String reason) {
        Long eventId = event == null ? null : event.getId();
        String summary = buildErrorAlertSkipSummary(event, reason);
        saveRecord("ERROR_ALERT", eventId, "ERROR_EVENT", summary,
                NotificationStatus.SKIPPED.getCode(), 0, reason, null);
    }

    private String buildErrorAlertSkipSummary(ErrorEvent event, String reason) {
        if (event == null) {
            return "错误告警通知跳过: " + reason;
        }
        return "错误告警通知跳过: eventId=" + event.getId()
                + ", appName=" + event.getAppName()
                + ", severity=" + event.getSeverity()
                + ", errorType=" + event.getErrorType()
                + ", reason=" + reason;
    }

    private void saveRecord(String type, Long refId, String refType,
                             String content, String status, int retryCount,
                             String errorMessage, LocalDateTime sentAt) {
        try {
            NotificationRecord record = new NotificationRecord();
            record.setType(type);
            record.setChannel("WECHAT");
            record.setRefId(refId);
            record.setRefType(refType);
            record.setContentSummary(abbreviate(content, 500));
            record.setStatus(status);
            record.setRetryCount(retryCount);
            record.setErrorMessage(abbreviate(errorMessage, 500));
            record.setSentAt(sentAt);
            recordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存通知记录失败: type={}, refId={}", type, refId, e);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
