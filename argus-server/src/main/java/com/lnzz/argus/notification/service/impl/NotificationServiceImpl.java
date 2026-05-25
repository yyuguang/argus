package com.lnzz.argus.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.notification.service.AlertTemplateBuilder;
import com.lnzz.argus.notification.service.NotificationRouter;
import com.lnzz.argus.notification.service.ScmNotificationDispatcher;
import com.lnzz.argus.notification.service.NotificationService;
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
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
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

    private final NotificationRecordMapper recordMapper;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties properties;
    private final NotificationRouter router;
    private final ScmNotificationDispatcher scmNotificationDispatcher;
    private final AlertTemplateBuilder templateBuilder;
    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigMapper scmConfigMapper;
    private final ScmReviewConfigSupport scmReviewConfigSupport;

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

        String content = templateBuilder.buildReviewAlert(
                task.getProjectName(), task.getMrIid(), task.getMrTitle(),
                task.getAuthorName(), task.getSourceBranch(), task.getTargetBranch(),
                score.getTotalScore(), score.getScoreLevel(), score.isPassed(),
                score.getCriticalCount(), score.getMajorCount(), score.getMinorCount(),
                task.getMrUrl());

        int threshold = reviewConfig != null
                ? reviewConfig.getNotification().getScoreAlertThreshold()
                : ReviewConfig.defaults().getNotification().getScoreAlertThreshold();
        String channel = (!score.isPassed() || score.getTotalScore() <= threshold)
                ? NotificationConstants.CHANNEL_CRITICAL
                : NotificationConstants.CHANNEL_DEFAULT;
        ReviewConfig effectiveReviewConfig = reviewConfig != null
                ? reviewConfig
                : scmReviewConfigSupport.resolveReviewConfig(scmConfig);
        ScmNotificationDispatcher.DispatchResult dispatchResult = scmNotificationDispatcher.dispatchMarkdown(
                scmConfig,
                effectiveReviewConfig.getNotification().getScoreAlertChannels(),
                channel,
                "Argus 评审通知",
                content,
                "REVIEW",
                task.getId(),
                "REVIEW_TASK",
                resolveRetryConfig(effectiveReviewConfig));
        if (!dispatchResult.success()) {
            log.info("评审通知未发送: taskId={}, scmConfigId={}, reason={}",
                    task.getId(), scmConfig.getId(), dispatchResult.message());
        }
        return dispatchResult.success();
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
        boolean isDetailed = NotificationConstants.PRIORITY_URGENT.equals(route.priority());
        String content = isDetailed
                ? templateBuilder.buildDetailedAlert(event, analysis)
                : templateBuilder.buildBriefAlert(event, analysis);

        String title = "[" + event.getSeverity() + "] " + event.getAppName() + " - " + event.getErrorType();

        ScmNotificationDispatcher.DispatchResult dispatchResult = scmNotificationDispatcher.dispatchMarkdown(
                scmConfig,
                null,
                route.channel(),
                title,
                content,
                NotificationConstants.TYPE_ERROR_ALERT,
                event.getId(),
                NotificationConstants.REF_TYPE_ERROR_EVENT,
                resolveRetryConfig(reviewConfig));
        if (!dispatchResult.success() && dispatchResult.attemptedCount() == 0) {
            log.info("错误告警无可用通知平台，跳过发送: eventId={}, scmConfigId={}, reason={}",
                    event.getId(), scmConfig.getId(), dispatchResult.message());
            saveErrorAlertSkip(event, dispatchResult.message());
            return false;
        }

        if (dispatchResult.success()) {
            markSilenced(event);
        }
        log.info("错误告警通知完成: eventId={}, channel={}, detailed={}, sent={}, attempted={}, successCount={}",
                event.getId(), route.channel(), isDetailed, dispatchResult.success(),
                dispatchResult.attemptedCount(), dispatchResult.successCount());
        return dispatchResult.success();
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
        return scmReviewConfigSupport.resolveReviewConfig(scmConfig);
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
            key = NotificationConstants.SILENCE_KEY_PREFIX + "p3:" + event.getAppName();
            interval = silence.getP3Interval();
        } else {
            String fingerprint = event.getErrorFingerprint();
            if (fingerprint == null || fingerprint.isEmpty()) {
                return false;
            }
            key = NotificationConstants.SILENCE_KEY_PREFIX + fingerprint;
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
            key = NotificationConstants.SILENCE_KEY_PREFIX + "p3:" + event.getAppName();
            interval = silence.getP3Interval();
        } else {
            String fingerprint = event.getErrorFingerprint();
            if (fingerprint == null || fingerprint.isEmpty()) return;
            key = NotificationConstants.SILENCE_KEY_PREFIX + fingerprint;
            interval = silence.getFingerprintInterval();
        }
        redisTemplate.opsForValue().set(key, "1", interval, TimeUnit.SECONDS);
    }

    private boolean checkGlobalRate() {
        int maxPerHour = properties.getSilence().getGlobalMaxPerHour();
        Long count = redisTemplate.opsForValue().increment(NotificationConstants.GLOBAL_COUNT_KEY);
        if (count == 1) {
            redisTemplate.expire(NotificationConstants.GLOBAL_COUNT_KEY, 1, TimeUnit.HOURS);
        }
        return count == null || count <= maxPerHour;
    }

    // ======================== 基础工具 ========================

    private boolean isDuplicate(String key, int ttlSeconds) {
        String redisKey = NotificationConstants.RATE_KEY_PREFIX + key;
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
        saveRecord(NotificationConstants.TYPE_ERROR_ALERT, eventId, NotificationConstants.REF_TYPE_ERROR_EVENT, summary,
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
