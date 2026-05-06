package com.lnzz.argus.notification.service;

import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.entity.ReviewTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * M7: 通知服务
 * <p>统一通知入口，支持评审通知、错误告警、报告推送</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WechatWebhookClient wechatClient;
    private final NotificationRecordMapper recordMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "argus:notify:rate:";

    /**
     * M7-02: 发送评审结果通知
     */
    public boolean sendReviewNotification(ReviewTask task, ScoreCalculator.ScoreResult score) {
        String dedupKey = "REVIEW:" + task.getId();
        if (isDuplicate(dedupKey)) {
            log.info("评审通知已发送过, taskId={}", task.getId());
            return true;
        }

        String emoji = score.isPassed() ? "✅" : "❌";
        String status = score.isPassed() ? "通过" : "不通过（阻止合并）";

        StringBuilder content = new StringBuilder();
        content.append("## ").append(emoji).append(" AI 代码评审通知\n\n");
        content.append("> **项目**: ").append(task.getProjectName()).append("\n");
        content.append("> **MR**: #").append(task.getMrIid()).append(" ").append(task.getMrTitle()).append("\n");
        content.append("> **提交者**: ").append(task.getAuthorName()).append("\n");
        content.append("> **分支**: `").append(task.getSourceBranch()).append("` → `").append(task.getTargetBranch()).append("`\n\n");
        content.append("**评分**: ").append(score.getTotalScore()).append("/100（等级 ").append(score.getScoreLevel()).append("）\n");
        content.append("**结果**: ").append(status).append("\n\n");

        if (score.getCriticalCount() > 0 || score.getMajorCount() > 0) {
            content.append("**问题统计**: 🔴致命 ").append(score.getCriticalCount())
                    .append(" / 🟡严重 ").append(score.getMajorCount())
                    .append(" / 🔵一般 ").append(score.getMinorCount()).append("\n\n");
        }

        if (task.getMrUrl() != null) {
            content.append("[查看详情](").append(task.getMrUrl()).append(")\n");
        }

        // 选择通知通道
        String channel = score.isPassed() ? "default" : "critical";
        boolean success;
        try {
            success = wechatClient.sendMarkdown(channel, content.toString());
        } catch (Exception e) {
            log.error("发送评审通知失败, taskId={}", task.getId(), e);
            success = false;
        }

        // 记录通知
        saveRecord("REVIEW", task.getId(), "REVIEW_TASK",
                content.substring(0, Math.min(content.length(), 500)), success);
        return success;
    }

    /**
     * M7-04: 发送错误告警通知
     */
    @Async
    public void sendErrorAlertNotification(String appName, String errorType, String errorMessage,
                                            String severity, String rootCause) {
        String dedupKey = "ERROR:" + appName + ":" + errorType;
        if (isDuplicate(dedupKey)) {
            log.info("错误告警已发送过, app={}, type={}", appName, errorType);
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("## 🚨 生产错误 AI 诊断告警\n\n");
        content.append("> **应用**: ").append(appName).append("\n");
        content.append("> **严重度**: ").append(severity).append("\n");
        content.append("> **错误类型**: ").append(errorType).append("\n\n");
        content.append("**错误信息**: ").append(errorMessage).append("\n\n");
        if (rootCause != null) {
            content.append("**AI 分析**: ").append(rootCause).append("\n");
        }

        wechatClient.sendMarkdown("critical", content.toString());
    }

    /**
     * M7-06: 频率控制（同一类型通知 60 秒内不重复发送）
     */
    private boolean isDuplicate(String key) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            return true;
        }
        redisTemplate.opsForValue().set(redisKey, "1", 60, TimeUnit.SECONDS);
        return false;
    }

    /**
     * M7-07: 保存通知记录
     */
    private void saveRecord(String type, Long refId, String refType, String summary, boolean success) {
        NotificationRecord record = new NotificationRecord();
        record.setType(type);
        record.setChannel("WECHAT");
        record.setRefId(refId);
        record.setRefType(refType);
        record.setContentSummary(summary);
        record.setStatus(success ? "SENT" : "FAILED");
        record.setRetryCount(0);
        record.setSentAt(success ? LocalDateTime.now() : null);
        recordMapper.insert(record);
    }
}
