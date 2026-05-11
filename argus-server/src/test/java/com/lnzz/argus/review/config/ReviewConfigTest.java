package com.lnzz.argus.review.config;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReviewConfig - 默认值 + 合并")
class ReviewConfigTest {

    @Test
    @DisplayName("defaults() 返回全默认值实例")
    void defaultsReturnsAllDefaults() {
        ReviewConfig config = ReviewConfig.defaults();

        // Trigger
        assertTrue(config.getTrigger().isEnabled());
        assertEquals("TARGET_ONLY", config.getTrigger().getBranchMode());
        assertTrue(config.getTrigger().getTargetBranches().contains("test"));
        assertTrue(config.getTrigger().getEventTypes().contains("update"));

        // Vector
        assertTrue(config.getVector().isEnabled());
        assertEquals(5, config.getVector().getReviewSearchTopk());
        assertEquals(0.7, config.getVector().getMinSimilarity(), 0.001);

        // Profile
        assertFalse(config.getProfile().isInjectEnabled());
        assertEquals(30, config.getProfile().getLookbackDays());
        assertEquals(3, config.getProfile().getInjectTopk());

        // FileFilter
        assertEquals(500, config.getFileFilter().getMaxDiffLinesPerFile());
        assertFalse(config.getFileFilter().getExcludeFilePatterns().isEmpty());

        // Token
        assertEquals(16000, config.getToken().getMaxContextTokens());
        assertEquals(0.8, config.getToken().getNewFilePenalty(), 0.001);
        assertEquals(1.2, config.getToken().getCoreModuleBonus(), 0.001);

        // Async
        assertEquals(120, config.getAsync().getScoreTimeoutSec());
        assertTrue(config.getAsync().isProgressCommentEnabled());

        // Scoring
        assertEquals(0.6, config.getScoring().getAiWeight(), 0.001);
        assertEquals(0.4, config.getScoring().getRuleWeight(), 0.001);
        assertEquals(20, config.getScoring().getCriticalDeduction());
        assertEquals(60, config.getScoring().getBlockThreshold());
        assertNotNull(config.getScoring().getDimensions());
        assertEquals(25, config.getScoring().getDimensions().getCompliance());
        assertEquals(5, config.getScoring().getScoreLevels().size());
        assertEquals(4, config.getScoring().getSeverityDefinitions().size());

        // Notification
        assertEquals(60, config.getNotification().getScoreAlertThreshold());
        assertTrue(config.getNotification().getScoreAlertChannels().contains("wechat"));
        assertTrue(config.getNotification().isWechatNotifyEnabled());
    }

    @Test
    @DisplayName("merge() 顶层字段覆盖默认值")
    void mergeTopLevelOverride() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig override = new ReviewConfig();

        override.getTrigger().setBranchMode("SOURCE_AND_TARGET");
        override.getTrigger().setTargetBranches(java.util.List.of("develop"));
        override.getScoring().setBlockThreshold(70);
        override.getScoring().setAiWeight(0.7);
        override.getVector().setMinSimilarity(0.65);

        ReviewConfig merged = defaults.merge(override);

        // 覆盖生效
        assertEquals("SOURCE_AND_TARGET", merged.getTrigger().getBranchMode());
        assertEquals("develop", merged.getTrigger().getTargetBranches().get(0));
        assertEquals(70, merged.getScoring().getBlockThreshold());
        assertEquals(0.7, merged.getScoring().getAiWeight(), 0.001);
        assertEquals(0.65, merged.getVector().getMinSimilarity(), 0.001);

        // 未覆盖的保持默认
        assertEquals(0.4, merged.getScoring().getRuleWeight(), 0.001);
        assertEquals(20, merged.getScoring().getCriticalDeduction());
        assertEquals(5, merged.getVector().getReviewSearchTopk());
    }

    @Test
    @DisplayName("merge() override=null 返回原实例")
    void mergeNullReturnsSame() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig merged = defaults.merge(null);
        assertSame(defaults, merged);
    }

    @Test
    @DisplayName("merge() 嵌套 dimensions 部分覆盖")
    void mergeNestedDimensionsPartial() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig override = new ReviewConfig();

        // 只改 compliance，其他维度保持默认
        override.getScoring().setDimensions(new ReviewConfig.DimensionsConfig());
        override.getScoring().getDimensions().setCompliance(40);

        ReviewConfig merged = defaults.merge(override);

        assertEquals(40, merged.getScoring().getDimensions().getCompliance());
        assertEquals(25, merged.getScoring().getDimensions().getCorrectness()); // 默认未改
        assertEquals(20, merged.getScoring().getDimensions().getDataIntegrity());
    }

    @Test
    @DisplayName("merge() scoreLevels 自定义覆盖")
    void mergeCustomScoreLevels() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig override = new ReviewConfig();

        override.getScoring().getScoreLevels().clear();
        override.getScoring().getScoreLevels().put("S", new ReviewConfig.ScoreLevelConfig(90, "卓越", "直接合并"));

        ReviewConfig merged = defaults.merge(override);

        assertEquals(1, merged.getScoring().getScoreLevels().size());
        assertEquals(90, merged.getScoring().getScoreLevels().get("S").getMinScore());
        assertEquals("卓越", merged.getScoring().getScoreLevels().get("S").getLabel());
    }

    @Test
    @DisplayName("merge() notification 字段覆盖")
    void mergeNotification() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig override = new ReviewConfig();
        override.getNotification().setWechatNotifyEnabled(false);
        override.getNotification().setScoreAlertThreshold(80);

        ReviewConfig merged = defaults.merge(override);

        assertFalse(merged.getNotification().isWechatNotifyEnabled());
        assertEquals(80, merged.getNotification().getScoreAlertThreshold());
    }

    @Test
    @DisplayName("JSON 序列化/反序列化往返一致")
    void jsonRoundTrip() {
        ReviewConfig original = ReviewConfig.defaults();
        original.getScoring().setBlockThreshold(75);
        original.getTrigger().setSourceBranches(java.util.List.of("feature/*"));

        String json = JSON.toJSONString(original);
        ReviewConfig parsed = JSON.parseObject(json, ReviewConfig.class);

        assertEquals("feature/*", parsed.getTrigger().getSourceBranches().get(0));
        assertEquals(original.getScoring().getBlockThreshold(), parsed.getScoring().getBlockThreshold());
        assertEquals(original.getVector().getMinSimilarity(), parsed.getVector().getMinSimilarity(), 0.001);
        assertEquals(original.getToken().getNewFilePenalty(), parsed.getToken().getNewFilePenalty(), 0.001);
        assertEquals(5, parsed.getScoring().getScoreLevels().size());
    }

    @Test
    @DisplayName("fileFilter merge 覆盖 excludeFilePatterns")
    void mergeFileFilterPatterns() {
        ReviewConfig defaults = ReviewConfig.defaults();
        ReviewConfig override = new ReviewConfig();
        override.getFileFilter().setMaxDiffLinesPerFile(1000);
        override.getFileFilter().setExcludeFilePatterns(java.util.List.of("**/generated/**"));

        ReviewConfig merged = defaults.merge(override);

        assertEquals(1000, merged.getFileFilter().getMaxDiffLinesPerFile());
        assertEquals(1, merged.getFileFilter().getExcludeFilePatterns().size());
        assertEquals("**/generated/**", merged.getFileFilter().getExcludeFilePatterns().get(0));
        // 默认依然保留（因为 override 覆盖了）
    }
}
