package com.lnzz.argus.notification.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.notification.service.NotificationService;
import com.lnzz.argus.notification.service.WechatWebhookClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 通知测试与手动推送入口
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final WechatWebhookClient wechatClient;
    private final NotificationService notificationService;

    // ======================== 快速连通性测试 ========================

    /**
     * 最简连通性测试：GET 请求，发一条默认消息到企微，验证 webhook 是否通
     */
    @GetMapping("/test/ping")
    public Result<Map<String, Object>> ping(
            @RequestParam(defaultValue = "default") String channel) {
        String content = "## ✅ Argus 企微通知连通测试\n"
                + "> 如果你能看到这条消息，说明 webhook 配置正确\n\n"
                + "- **通道**：`" + channel + "`\n"
                + "- **时间**：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("企微连通测试: channel={}", channel);
        boolean ok = wechatClient.sendMarkdown(channel, content);
        return Result.success(Map.of("channel", channel, "success", ok, "msg", ok ? "发送成功" : "发送失败，请检查 webhook 配置"));
    }

    // ======================== 自定义内容推送 ========================

    /**
     * 推送自定义 Markdown 消息到企微
     * <p>用法：POST /test/markdown?channel=default，body 里放 markdown 文本（Content-Type: text/plain）</p>
     * <p>也支持 GET 快捷方式：/test/markdown?content=xxx</p>
     */
    @RequestMapping(value = "/test/markdown", method = {RequestMethod.POST, RequestMethod.GET})
    public Result<Map<String, Object>> testMarkdown(
            @RequestParam(defaultValue = "default") String channel,
            @RequestParam(required = false) String content,
            @RequestBody(required = false) String body) {
        // POST body 优先，其次 GET content 参数
        String md = (body != null && !body.isEmpty()) ? body : content;
        if (md == null || md.isEmpty()) {
            return Result.fail("请提供 content 参数或 POST body");
        }
        log.info("手动测试企微 Markdown 推送: channel={}, len={}", channel, md.length());
        boolean ok = wechatClient.sendMarkdown(channel, md);
        return Result.success(Map.of("channel", channel, "success", ok));
    }

    /**
     * 推送文本消息到企微（支持 @人）
     */
    @GetMapping("/test/text")
    public Result<Map<String, Object>> testText(
            @RequestParam(defaultValue = "default") String channel,
            @RequestParam(defaultValue = "企微通知测试") String content,
            @RequestParam(required = false) List<String> atUsers) {
        String text = "【Argus 通知测试】\n" + content + "\n时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("手动测试企微文本推送: channel={}, content={}", channel, content);
        boolean ok = wechatClient.sendText(channel, text, atUsers);
        return Result.success(Map.of("channel", channel, "success", ok));
    }

    // ======================== 完整链路模拟 ========================

    /**
     * 模拟一条错误告警通知（完整链路：路由 → 模板 → 静默 → 发送）
     */
    @GetMapping("/test/error-alert")
    public Result<Map<String, Object>> testErrorAlert(
            @RequestParam(defaultValue = "test-app") String appName,
            @RequestParam(defaultValue = "NULL_POINTER") String errorType,
            @RequestParam(defaultValue = "java.lang.NullPointerException: 测试异常") String errorMessage,
            @RequestParam(defaultValue = "P2") String severity,
            @RequestParam(defaultValue = "模拟根因：未做空值判断") String rootCause) {
        log.info("手动测试错误告警: app={}, type={}, severity={}", appName, errorType, severity);
        notificationService.sendErrorAlertLegacy(appName, errorType, errorMessage, severity, rootCause);
        return Result.success(Map.of("appName", appName, "severity", severity, "sent", true,
                "msg", "通知已异步发送，请注意企微消息"));
    }

    /**
     * 模拟一条评审通知
     */
    @GetMapping("/test/review-alert")
    public Result<Map<String, Object>> testReviewAlert(
            @RequestParam(defaultValue = "demo-project") String projectName,
            @RequestParam(defaultValue = "42") Long mrIid,
            @RequestParam(defaultValue = "feat: 测试评审通知") String mrTitle,
            @RequestParam(defaultValue = "开发者") String authorName,
            @RequestParam(defaultValue = "feature/test") String sourceBranch,
            @RequestParam(defaultValue = "dev") String targetBranch,
            @RequestParam(defaultValue = "https://example.com/mr/42") String mrUrl,
            @RequestParam(defaultValue = "85") int totalScore,
            @RequestParam(defaultValue = "B") String scoreLevel,
            @RequestParam(defaultValue = "true") boolean passed,
            @RequestParam(defaultValue = "0") int criticalCount,
            @RequestParam(defaultValue = "2") int majorCount,
            @RequestParam(defaultValue = "5") int minorCount) {

        com.lnzz.argus.review.entity.ReviewTask task = new com.lnzz.argus.review.entity.ReviewTask();
        task.setId(System.currentTimeMillis());
        task.setProjectName(projectName);
        task.setMrIid(mrIid);
        task.setMrTitle(mrTitle);
        task.setAuthorName(authorName);
        task.setSourceBranch(sourceBranch);
        task.setTargetBranch(targetBranch);
        task.setMrUrl(mrUrl);

        com.lnzz.argus.review.ai.ScoreCalculator.ScoreResult score =
                new com.lnzz.argus.review.ai.ScoreCalculator.ScoreResult();
        score.setTotalScore(totalScore);
        score.setScoreLevel(scoreLevel);
        score.setPassed(passed);
        score.setCriticalCount(criticalCount);
        score.setMajorCount(majorCount);
        score.setMinorCount(minorCount);

        log.info("手动测试评审通知: project={}, score={}/{}, passed={}", projectName, totalScore, scoreLevel, passed);
        boolean ok = notificationService.sendReviewNotification(task, score);
        return Result.success(Map.of("projectName", projectName, "score", totalScore, "success", ok,
                "msg", ok ? "发送成功" : "发送失败，请检查 webhook 配置"));
    }
}
