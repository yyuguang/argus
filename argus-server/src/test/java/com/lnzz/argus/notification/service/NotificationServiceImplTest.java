package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.impl.NotificationServiceImpl;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl - 仓库级评审通知控制")
class NotificationServiceImplTest {

    @Mock
    private WechatWebhookClient wechatClient;
    @Mock
    private FeishuWebhookClient feishuClient;
    @Mock
    private DingTalkWebhookClient dingTalkWebhookClient;
    @Mock
    private NotificationRecordMapper recordMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private NotificationRouter router;
    @Mock
    private AlertTemplateBuilder templateBuilder;
    @Mock
    private ProjectMappingMapper projectMappingMapper;
    @Mock
    private ScmConfigMapper scmConfigMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationProperties properties;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        notificationService = new NotificationServiceImpl(
                wechatClient,
                feishuClient,
                dingTalkWebhookClient,
                recordMapper,
                redisTemplate,
                properties,
                router,
                templateBuilder,
                projectMappingMapper,
                scmConfigMapper
        );
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(templateBuilder.buildReviewAlert(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyBoolean(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn("review-content");
        lenient().when(templateBuilder.buildDetailedAlert(any(), any())).thenReturn("error-content");
        lenient().when(wechatClient.sendMarkdown(anyString(), anyString(), any())).thenReturn(true);
        lenient().when(router.route(any())).thenReturn(new NotificationRouter.RouteResult("critical", "urgent", true));
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

        verify(wechatClient).sendMarkdown(eq("critical"), eq("review-content"), eq("https://custom-webhook"));
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

        verify(wechatClient).sendMarkdown(eq("default"), eq("review-content"), eq("https://custom-webhook"));
    }

    @Test
    @DisplayName("通知总开关关闭时不发送评审通知")
    void globalNotificationDisabledSkipsReviewNotification() {
        properties.setEnabled(false);
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(65, true);

        boolean sent = notificationService.sendReviewNotification(task, score, null, ReviewConfig.defaults());

        assertFalse(sent);
        verify(wechatClient, never()).sendMarkdown(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("SCM 未配置 webhook 时不发送评审通知")
    void missingScmWebhookSkipsReviewNotification() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore(65, true);
        ScmConfig scmConfig = new ScmConfig();

        boolean sent = notificationService.sendReviewNotification(task, score, scmConfig, ReviewConfig.defaults());

        assertFalse(sent);
        verify(wechatClient, never()).sendMarkdown(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("SCM 关闭企微通知时错误告警不发送")
    void scmDisabledSkipsErrorAlert() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(0, "https://custom-webhook");
        mockProjectMappingAndScm(scmConfig);

        notificationService.sendErrorAlert(event, new ErrorAnalysis());

        verify(wechatClient, never()).sendMarkdown(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("错误告警使用 appName 映射到 SCM webhook")
    void errorAlertUsesScmWebhookByAppName() {
        ErrorEvent event = createErrorEvent();
        ScmConfig scmConfig = createScmConfig(1, "https://custom-webhook");
        mockProjectMappingAndScm(scmConfig);

        notificationService.sendErrorAlert(event, new ErrorAnalysis());

        verify(wechatClient).sendMarkdown(eq("critical"), eq("error-content"), eq("https://custom-webhook"));
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
