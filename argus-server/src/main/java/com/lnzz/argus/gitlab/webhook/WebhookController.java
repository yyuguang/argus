package com.lnzz.argus.gitlab.webhook;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.config.GitLabProperties;
import com.lnzz.argus.gitlab.model.MergeRequestEvent;
import com.lnzz.argus.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GitLab Webhook 接收控制器
 * <p>M1-01~05: 接收 MR 事件 → 签名校验 → 事件解析 → 异步分发</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final GitLabProperties gitLabProperties;
    private final WebhookEventParser eventParser;
    private final ReviewService reviewService;

    /**
     * 接收 GitLab Webhook
     *
     * @param gitlabToken Webhook 签名 Token
     * @param payload     原始请求体
     * @return 处理结果
     */
    @PostMapping("/gitlab")
    public Result<Map<String, Object>> receiveGitLabWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestBody String payload) {

        // M1-03: 签名校验
        if (!verifyWebhookToken(gitlabToken)) {
            log.warn("Webhook签名校验失败");
            return Result.fail(401, "Webhook Token 无效");
        }

        // M1-01: 解析 MR 事件
        MergeRequestEvent event = eventParser.parseMergeRequestEvent(payload);
        if (event == null) {
            return Result.success("非MR事件，已忽略", Map.of("action", "ignored"));
        }

        // 仅处理 dev → test 的 open/update 事件
        if (!event.isReviewable()) {
            log.info("非评审目标MR，忽略: {}→{}, state={}", event.getSourceBranch(), event.getTargetBranch(), event.getMrState());
            return Result.success("非评审目标MR，已忽略", Map.of("action", "skipped"));
        }

        // M1-05: 异步触发评审
        Long taskId = reviewService.triggerReview(event);
        log.info("评审任务已创建: taskId={}, project={}, mrIid={}", taskId, event.getProjectName(), event.getMrIid());

        return Result.success("评审任务已创建", Map.of(
                "taskId", taskId,
                "status", "PENDING"
        ));
    }

    /**
     * M1-03: 校验 Webhook Token
     */
    private boolean verifyWebhookToken(String token) {
        String expected = gitLabProperties.getWebhookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(token);
    }
}
