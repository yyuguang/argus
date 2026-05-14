package com.lnzz.argus.review.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.PullRequestEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReviewTriggerRuleEvaluator - 分支规则评估")
class ReviewTriggerRuleEvaluatorTest {

    private final ReviewTriggerRuleEvaluator evaluator = new ReviewTriggerRuleEvaluator();

    @Test
    @DisplayName("默认配置时 target=test 命中评审")
    void shouldAllowByDefaultTargetBranch() {
        PullRequestEvent event = createEvent("dev", "test", "update", "opened");
        ScmConfig config = createConfig(null);

        ReviewTriggerRuleEvaluator.TriggerDecision decision = evaluator.evaluate(event, config);

        assertTrue(decision.shouldReview());
        assertEquals("ALLOW", decision.reason());
    }

    @Test
    @DisplayName("SOURCE_AND_TARGET 模式同时命中源目标分支")
    void shouldAllowBySourceAndTargetRule() {
        ReviewConfig reviewConfig = ReviewConfig.defaults();
        reviewConfig.getTrigger().setBranchMode("SOURCE_AND_TARGET");
        reviewConfig.getTrigger().setTargetBranches(java.util.List.of("develop"));
        reviewConfig.getTrigger().setSourceBranches(java.util.List.of("feature/*", "bugfix/*"));

        PullRequestEvent event = createEvent("feature/order-create", "develop", "synchronize", "opened");
        ScmConfig config = createConfig(JSON.toJSONString(reviewConfig));

        ReviewTriggerRuleEvaluator.TriggerDecision decision = evaluator.evaluate(event, config);

        assertTrue(decision.shouldReview());
    }

    @Test
    @DisplayName("源分支不匹配时返回 SOURCE_BRANCH_NOT_MATCHED")
    void shouldRejectWhenSourceBranchNotMatched() {
        ReviewConfig reviewConfig = ReviewConfig.defaults();
        reviewConfig.getTrigger().setBranchMode("SOURCE_AND_TARGET");
        reviewConfig.getTrigger().setTargetBranches(java.util.List.of("develop"));
        reviewConfig.getTrigger().setSourceBranches(java.util.List.of("feature/*"));

        PullRequestEvent event = createEvent("hotfix/payment", "develop", "update", "opened");
        ScmConfig config = createConfig(JSON.toJSONString(reviewConfig));

        ReviewTriggerRuleEvaluator.TriggerDecision decision = evaluator.evaluate(event, config);

        assertFalse(decision.shouldReview());
        assertEquals("SOURCE_BRANCH_NOT_MATCHED", decision.reason());
    }

    @Test
    @DisplayName("事件状态不是 opened 时跳过")
    void shouldRejectWhenStateNotOpened() {
        PullRequestEvent event = createEvent("dev", "test", "update", "merged");
        ScmConfig config = createConfig(null);

        ReviewTriggerRuleEvaluator.TriggerDecision decision = evaluator.evaluate(event, config);

        assertFalse(decision.shouldReview());
        assertEquals("MR_STATE_NOT_OPENED", decision.reason());
    }

    private PullRequestEvent createEvent(String sourceBranch, String targetBranch, String eventType, String mrState) {
        PullRequestEvent event = new PullRequestEvent();
        event.setScmProvider("github");
        event.setSourceBranch(sourceBranch);
        event.setTargetBranch(targetBranch);
        event.setEventType(eventType);
        event.setMrState(mrState);
        return event;
    }

    private ScmConfig createConfig(String reviewConfig) {
        ScmConfig config = new ScmConfig();
        config.setEnabled(true);
        config.setReviewConfig(reviewConfig);
        return config;
    }
}
