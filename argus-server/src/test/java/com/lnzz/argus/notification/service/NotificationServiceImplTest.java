package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.common.enums.NotificationStatus;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.impl.NotificationServiceImpl;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl - 仓库级评审通知控制")
class NotificationServiceImplTest {

    private final ScmReviewConfigSupport realScmReviewConfigSupport = new ScmReviewConfigSupport();

    @Mock
    private NotificationRecordMapper recordMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private NotificationRouter router;
    @Mock
    private ScmNotificationDispatcher scmNotificationDispatcher;
    @Mock
    private AlertTemplateBuilder templateBuilder;
    @Mock
    private ProjectMappingMapper projectMappingMapper;
    @Mock
    private ScmConfigMapper scmConfigMapper;
    @Mock
    private ScmReviewConfigSupport scmReviewConfigSupport;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationProperties properties;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        notificationService = new NotificationServiceImpl(
                recordMapper,
                redisTemplate,
                properties,
                router,
                scmNotificationDispatcher,
                templateBuilder,
                projectMappingMapper,
                scmConfigMapper,
                scmReviewConfigSupport
        );
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(templateBuilder.buildReviewAlert(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyBoolean(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn("review-content");
        lenient().when(templateBuilder.buildDetailedAlert(any(), any())).thenReturn("error-content");
        lenient().when(scmNotificationDispatcher.dispatchMarkdown(any(), any(), anyString(), anyString(), anyString(),
                        anyString(), any(), anyString(), any()))
                .thenReturn(new ScmNotificationDispatcher.DispatchResult(true, 1, 1, "发送成功"));
        lenient().when(router.route(any(ErrorEvent.class), any(ReviewConfig.NotificationConfig.class)))
                .thenReturn(new NotificationRouter.RouteResult("critical", "urgent", true));
        lenient().when(scmReviewConfigSupport.resolveReviewConfig(any(ScmConfig.class)))
                .thenAnswer(invocation -> realScmReviewConfigSupport.resolveReviewConfig(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("低于阈值时走 critical 通道并使用仓库级 webhook")
    void lowScoreUsesCriticalChannelAndRepoWebhook() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(65, true);
        ReviewConfig reviewConfig = ReviewConfig.defaults();
        reviewConfig.getNotification().setScoreAlertThreshold(70);
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setWechatNotifyWebhook("https://custom-webhook");

        notificationService.sendReviewNotification(task, score, scmConfig, reviewConfig);

        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), any(), eq("critical"),
                eq("Argus 评审通知"), eq("review-content"), eq("REVIEW"),
                eq(task.getId()), eq("REVIEW_TASK"), any());
    }

    @Test
    @DisplayName("高于阈值且通过时走 default 通道")
    void passedReviewUsesDefaultChannel() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(85, true);
        ReviewConfig reviewConfig = ReviewConfig.defaults();
        reviewConfig.getNotification().setScoreAlertThreshold(70);
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setWechatNotifyWebhook("https://custom-webhook");

        notificationService.sendReviewNotification(task, score, scmConfig, reviewConfig);

        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), any(), eq("default"),
                eq("Argus 评审通知"), eq("review-content"), eq("REVIEW"),
                eq(task.getId()), eq("REVIEW_TASK"), any());
    }

    @Test
    @DisplayName("通知总开关关闭时不发送评审通知")
    void globalNotificationDisabledSkipsReviewNotification() {
        properties.setEnabled(false);
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(65, true);

        boolean sent = notificationService.sendReviewNotification(task, score, null, ReviewConfig.defaults());

        assertFalse(sent);
        verify(scmNotificationDispatcher, never()).dispatchMarkdown(any(), any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("SCM 未配置 webhook 时不发送评审通知")
    void missingScmWebhookSkipsReviewNotification() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(65, true);
        ScmConfig scmConfig = new ScmConfig();
        when(scmNotificationDispatcher.dispatchMarkdown(eq(scmConfig), any(), anyString(), anyString(), anyString(),
                        anyString(), any(), anyString(), any()))
                .thenReturn(ScmNotificationDispatcher.DispatchResult.skipped("企业微信未配置 Webhook"));

        boolean sent = notificationService.sendReviewNotification(task, score, scmConfig, ReviewConfig.defaults());

        assertFalse(sent);
        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), any(), eq("default"),
                eq("Argus 评审通知"), eq("review-content"), eq("REVIEW"),
                eq(task.getId()), eq("REVIEW_TASK"), any());
    }

    @Test
    @DisplayName("SCM 关闭企微通知时错误告警不发送")
    void scmDisabledSkipsErrorAlert() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(0, "https://custom-webhook");
        mockProjectMappingAndScm(scmConfig);

        when(scmNotificationDispatcher.dispatchMarkdown(eq(scmConfig), isNull(), anyString(), anyString(), anyString(),
                        eq("ERROR_ALERT"), eq(event.getId()), eq("ERROR_EVENT"), any()))
                .thenReturn(ScmNotificationDispatcher.DispatchResult.skipped("企业微信未启用"));

        boolean sent = notificationService.sendErrorAlert(event, new ErrorAnalysis());

        assertFalse(sent);
        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), isNull(), eq("critical"),
                anyString(), eq("error-content"), eq("ERROR_ALERT"),
                eq(event.getId()), eq("ERROR_EVENT"), any());
    }

    @Test
    @DisplayName("错误告警被路由抑制时写入跳过通知记录")
    void suppressedErrorAlertWritesSkippedRecord() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(1, "https://custom-webhook");
        mockProjectMappingAndScm(scmConfig);
        when(router.route(any(ErrorEvent.class), any(ReviewConfig.NotificationConfig.class)))
                .thenReturn(new NotificationRouter.RouteResult("default", "silent", false));

        boolean sent = notificationService.sendErrorAlert(event, new ErrorAnalysis());

        assertFalse(sent);
        verify(scmNotificationDispatcher, never()).dispatchMarkdown(any(), any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        NotificationRecord record = recordCaptor.getValue();
        assertEquals("ERROR_ALERT", record.getType());
        assertEquals("ERROR_EVENT", record.getRefType());
        assertEquals(event.getId(), record.getRefId());
        assertEquals(NotificationStatus.SKIPPED.getCode(), record.getStatus());
    }

    @Test
    @DisplayName("错误告警使用 appName 映射到 SCM webhook")
    void errorAlertUsesScmWebhookByAppName() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(1, "https://custom-webhook");
        mockProjectMappingAndScm(scmConfig);

        notificationService.sendErrorAlert(event, new ErrorAnalysis());

        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), any(), eq("critical"),
                anyString(), eq("error-content"), eq("ERROR_ALERT"),
                eq(event.getId()), eq("ERROR_EVENT"), any());
    }

    @Test
    @DisplayName("错误告警路由使用 SCM reviewConfig 中的前端配置")
    void errorAlertRouteUsesScmReviewConfig() {
        ErrorEvent event = createErrorEvent();
        event.setSeverity("P3");
        ScmConfig scmConfig = createScmConfig(1, "https://custom-webhook");
        scmConfig.setReviewConfig("""
                {
                  "notification": {
                    "errorAlertRoutes": {
                      "P3": { "enabled": true, "channel": "default", "priority": "normal" }
                    }
                  }
                }
                """);
        mockProjectMappingAndScm(scmConfig);

        notificationService.sendErrorAlert(event, new ErrorAnalysis());

        ArgumentCaptor<ReviewConfig.NotificationConfig> configCaptor =
                ArgumentCaptor.forClass(ReviewConfig.NotificationConfig.class);
        verify(router).route(eq(event), configCaptor.capture());
        ReviewConfig.ErrorAlertRouteConfig p3Route = configCaptor.getValue().getErrorAlertRoutes().get("P3");
        assertEquals(true, p3Route.isEnabled());
        assertEquals("default", p3Route.getChannel());
        assertEquals("normal", p3Route.getPriority());
    }

    @Test
    @DisplayName("错误告警重试策略使用 SCM reviewConfig 中的前端配置")
    void errorAlertRetryUsesScmReviewConfig() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(1, "https://custom-webhook");
        scmConfig.setReviewConfig("""
                {
                  "notification": {
                    "retry": {
                      "maxRetries": 2,
                      "backoffSeconds": [0, 0],
                      "timeoutSec": 60
                    }
                  }
                }
                """);
        mockProjectMappingAndScm(scmConfig);

        notificationService.sendErrorAlert(event, new ErrorAnalysis());

        ArgumentCaptor<ReviewConfig.NotificationRetryConfig> retryCaptor =
                ArgumentCaptor.forClass(ReviewConfig.NotificationRetryConfig.class);
        verify(scmNotificationDispatcher).dispatchMarkdown(eq(scmConfig), any(), eq("critical"),
                anyString(), eq("error-content"), eq("ERROR_ALERT"),
                eq(event.getId()), eq("ERROR_EVENT"), retryCaptor.capture());
        assertEquals(2, retryCaptor.getValue().getMaxRetries());
        assertEquals(60, retryCaptor.getValue().getTimeoutSec());
    }

    private ReviewTask createTask() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setProjectName("demo");
        task.setMrIid(42L);
        task.setMrTitle("feat: notification");
        task.setAuthorName("yyuguang");
        task.setSourceBranch("feature/e09");
        task.setTargetBranch("test");
        task.setMrUrl("https://example.com/mr/42");
        return task;
    }

    private ScoreCalculator.ScoreResult createScore(int totalScore, boolean passed) {
        ScoreCalculator.ScoreResult score = new ScoreCalculator.ScoreResult();
        score.setTotalScore(totalScore);
        score.setScoreLevel(totalScore >= 85 ? "A" : "B");
        score.setPassed(passed);
        score.setCriticalCount(0);
        score.setMajorCount(1);
        score.setMinorCount(2);
        return score;
    }

    private ErrorEvent createErrorEvent() {
        ErrorEvent event = new ErrorEvent();
        event.setId(10L);
        event.setAppName("order-service");
        event.setSeverity("P1");
        event.setErrorType("NULL_POINTER");
        event.setErrorFingerprint("fp-1");
        return event;
    }

    private ScmConfig createScmConfig(Integer wechatEnabled, String webhook) {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(99L);
        scmConfig.setProjectId(100L);
        scmConfig.setScmProvider("github");
        scmConfig.setEnabled(true);
        scmConfig.setWechatNotifyEnabled(wechatEnabled);
        scmConfig.setWechatNotifyWebhook(webhook);
        return scmConfig;
    }

    private void mockProjectMappingAndScm(ScmConfig scmConfig) {
        ProjectMapping mapping = new ProjectMapping();
        mapping.setAppName("order-service");
        mapping.setScmProjectId(100L);
        mapping.setScmProvider("github");
        when(projectMappingMapper.selectOne(any())).thenReturn(mapping);
        when(scmConfigMapper.selectOne(any())).thenReturn(scmConfig);
    }
}
