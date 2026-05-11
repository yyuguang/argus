package com.lnzz.argus.review.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 评审触发规则评估器。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class ReviewTriggerRuleEvaluator {

    /**
     * 基于仓库配置评估当前事件是否应触发评审。
     *
     * @param event  PR/MR 事件
     * @param config SCM 配置
     * @return 评估结果
     */
    public TriggerDecision evaluate(PullRequestEvent event, ScmConfig config) {
        if (event == null || config == null) {
            return TriggerDecision.skip("INVALID_INPUT");
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return TriggerDecision.skip("SCM_CONFIG_DISABLED");
        }

        ReviewConfig reviewConfig = resolveReviewConfig(config);
        ReviewConfig.TriggerConfig trigger = reviewConfig.getTrigger();
        if (trigger == null || !trigger.isEnabled()) {
            return TriggerDecision.skip("TRIGGER_DISABLED");
        }
        if (!isOpenedState(event.getMrState())) {
            return TriggerDecision.skip("MR_STATE_NOT_OPENED");
        }
        if (!matchesAny(normalize(event.getEventType()), normalizeAll(trigger.getEventTypes()))) {
            return TriggerDecision.skip("EVENT_NOT_ALLOWED");
        }
        if (!matchBranch(event.getTargetBranch(), trigger.getTargetBranches())) {
            return TriggerDecision.skip("TARGET_BRANCH_NOT_MATCHED");
        }

        String branchMode = normalizeBranchMode(trigger.getBranchMode());
        if ("SOURCE_AND_TARGET".equals(branchMode) && !matchBranch(event.getSourceBranch(), trigger.getSourceBranches())) {
            return TriggerDecision.skip("SOURCE_BRANCH_NOT_MATCHED");
        }

        return TriggerDecision.allow();
    }

    ReviewConfig resolveReviewConfig(ScmConfig scmConfig) {
        ReviewConfig defaults = ReviewConfig.defaults();
        if (scmConfig == null || !StringUtils.hasText(scmConfig.getReviewConfig())) {
            return defaults;
        }
        try {
            ReviewConfig override = JSON.parseObject(scmConfig.getReviewConfig(), ReviewConfig.class);
            return defaults.merge(override);
        } catch (Exception ignore) {
            return defaults;
        }
    }

    private boolean isOpenedState(String state) {
        String normalized = normalize(state);
        return "opened".equals(normalized) || "open".equals(normalized);
    }

    private boolean matchesAny(String value, List<String> allowedValues) {
        return allowedValues.stream().anyMatch(item -> item.equals(value));
    }

    private boolean matchBranch(String branch, List<String> patterns) {
        if (!StringUtils.hasText(branch) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> branchMatches(branch, pattern));
    }

    private boolean branchMatches(String branch, String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        String regex = toWildcardRegex(pattern.trim());
        return branch.matches(regex);
    }

    private String toWildcardRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return regex.toString();
    }

    private List<String> normalizeAll(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalize).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBranchMode(String branchMode) {
        if (!StringUtils.hasText(branchMode)) {
            return "TARGET_ONLY";
        }
        return branchMode.trim().toUpperCase(Locale.ROOT);
    }

    public record TriggerDecision(boolean shouldReview, String reason) {
        public static TriggerDecision allow() {
            return new TriggerDecision(true, "ALLOW");
        }

        public static TriggerDecision skip(String reason) {
            return new TriggerDecision(false, reason);
        }
    }
}
