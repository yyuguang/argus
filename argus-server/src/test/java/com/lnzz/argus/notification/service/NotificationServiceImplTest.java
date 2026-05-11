package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.impl.NotificationServiceImpl;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
                templateBuilder
        );
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(templateBuilder.buildReviewAlert(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyBoolean(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn("review-content");
        when(wechatClient.sendMarkdown(anyString(), anyString(), any())).thenReturn(true);
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

        notificationService.sendReviewNotification(task, score, null, reviewConfig);

        verify(wechatClient).sendMarkdown(eq("default"), eq("review-content"), isNull());
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
}
