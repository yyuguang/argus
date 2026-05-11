package com.lnzz.argus.gitlab.webhook;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.service.ReviewTriggerRuleEvaluator;
import com.lnzz.argus.review.service.ReviewService;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.LinkedHashMap;
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

    private final ScmPlatformServiceFactory scmPlatformServiceFactory;
    private final ScmConfigService scmConfigService;
    private final ReviewTriggerRuleEvaluator reviewTriggerRuleEvaluator;
    private final ReviewService reviewService;

    /**
     * 接收 SCM Webhook
     *
     * @param provider SCM 平台
     * @param payload     原始请求体
     * @return 处理结果
     */
    @PostMapping("/{provider}")
    public Result<Map<String, Object>> receiveWebhook(
            @PathVariable String provider,
            HttpServletRequest request,
            @RequestBody String payload) {
        ScmPlatformService scmService = scmPlatformServiceFactory.getRequired(provider);
        Map<String, String> headers = extractHeaders(request);

        PullRequestEvent event = scmService.parseWebhookEvent(headers, payload);
        if (event == null) {
            return Result.success("非PR/MR事件，已忽略", Map.of("action", "ignored"));
        }

        ScmConfig config = scmConfigService.resolveConfig(
                event.getScmProvider(), event.getProjectId(), event.getRepoOwner(), event.getRepoName());
        if (config == null) {
            log.warn("未找到匹配的 SCM 配置: provider={}, projectId={}, repo={}/{}",
                    event.getScmProvider(), event.getProjectId(), event.getRepoOwner(), event.getRepoName());
            return Result.fail(404, "未找到匹配的 SCM 配置");
        }

        if (!scmService.verifyWebhookSignature(config, headers, payload)) {
            log.warn("Webhook签名校验失败: provider={}, project={}", provider, event.getProjectName());
            return Result.fail(401, "Webhook 签名无效");
        }

        ReviewTriggerRuleEvaluator.TriggerDecision decision = reviewTriggerRuleEvaluator.evaluate(event, config);
        if (!decision.shouldReview()) {
            log.info("PR/MR 未命中评审规则，忽略: provider={}, {}→{}, state={}, reason={}",
                    provider, event.getSourceBranch(), event.getTargetBranch(), event.getMrState(), decision.reason());
            return Result.success("非评审目标PR/MR，已忽略", Map.of(
                    "action", "skipped",
                    "reason", decision.reason()
            ));
        }

        // M1-05: 异步触发评审
        Long taskId = reviewService.triggerReview(event, config);
        log.info("评审任务已创建: provider={}, taskId={}, project={}, mrIid={}",
                provider, taskId, event.getProjectName(), event.getMrIid());

        return Result.success("评审任务已创建", Map.of(
                "taskId", taskId,
                "status", "PENDING"
        ));
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
