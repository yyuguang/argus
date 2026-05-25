package com.lnzz.argus.review.config;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.NotificationConstants;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码评审配置 — 纯 POJO，从 ScmConfig.review_config JSON 反序列化。
 * 所有字段均提供默认值，JSON 中未配置的字段走默认值。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class ReviewConfig {

    // ==================== 顶层配置域 ====================

    private TriggerConfig trigger = new TriggerConfig();
    private VectorConfig vector = new VectorConfig();
    private ProfileConfig profile = new ProfileConfig();
    private FileFilterConfig fileFilter = new FileFilterConfig();
    private TokenConfig token = new TokenConfig();
    private AsyncConfig async = new AsyncConfig();
    private ScoringConfig scoring = new ScoringConfig();
    private NotificationConfig notification = new NotificationConfig();
    private RuleConfig rule = new RuleConfig();

    // ==================== 工厂方法 ====================

    /** 返回全默认值实例 */
    public static ReviewConfig defaults() {
        return new ReviewConfig();
    }

    /**
     * 合并：override 中非 null 的顶层字段覆盖当前实例的默认值。
     * 嵌套对象内部也是非 null 字段覆盖，null 保留原值。
     */
    public ReviewConfig merge(ReviewConfig override) {
        if (override == null) return this;
        ReviewConfig merged = JSON.parseObject(JSON.toJSONBytes(this), ReviewConfig.class);
        byte[] overrideBytes = JSON.toJSONBytes(override);
        // fastjson2 的 JSON.parseObject 对嵌套对象是浅合并，逐域处理
        ReviewConfig overrideParsed = JSON.parseObject(overrideBytes, ReviewConfig.class);

        if (overrideParsed.trigger != null) merged.trigger = merged.trigger.merge(overrideParsed.trigger);
        if (overrideParsed.vector != null) merged.vector = merged.vector.merge(overrideParsed.vector);
        if (overrideParsed.profile != null) merged.profile = merged.profile.merge(overrideParsed.profile);
        if (overrideParsed.fileFilter != null) merged.fileFilter = merged.fileFilter.merge(overrideParsed.fileFilter);
        if (overrideParsed.token != null) merged.token = merged.token.merge(overrideParsed.token);
        if (overrideParsed.async != null) merged.async = merged.async.merge(overrideParsed.async);
        if (overrideParsed.scoring != null) merged.scoring = merged.scoring.merge(overrideParsed.scoring);
        if (overrideParsed.notification != null) merged.notification = merged.notification.merge(overrideParsed.notification);
        if (overrideParsed.rule != null) merged.rule = merged.rule.merge(overrideParsed.rule);

        return merged;
    }

    // ==================== 0. 触发规则配置 ====================

    @Data
    public static class TriggerConfig {
        private boolean enabled = true;
        private List<String> eventTypes = new ArrayList<>();
        private String branchMode = "TARGET_ONLY";
        private List<String> targetBranches = new ArrayList<>();
        private List<String> sourceBranches = new ArrayList<>();

        {
            eventTypes.addAll(List.of("open", "opened", "update", "synchronize", "reopen", "reopened"));
            targetBranches.add("test");
        }

        TriggerConfig merge(TriggerConfig o) {
            if (o == null) return this;
            this.enabled = o.enabled;
            if (o.eventTypes != null && !o.eventTypes.isEmpty()) {
                this.eventTypes = o.eventTypes;
            }
            if (o.branchMode != null && !o.branchMode.isBlank()) {
                this.branchMode = o.branchMode;
            }
            if (o.targetBranches != null && !o.targetBranches.isEmpty()) {
                this.targetBranches = o.targetBranches;
            }
            if (o.sourceBranches != null && !o.sourceBranches.isEmpty()) {
                this.sourceBranches = o.sourceBranches;
            }
            return this;
        }
    }

    // ==================== 1. 向量知识库配置 ====================

    @Data
    public static class VectorConfig {
        private boolean enabled = true;
        private int reviewSearchTopk = 5;
        private int errorSearchTopk = 5;
        private double minSimilarity = 0.7;
        private double knowledgeMinScore = 0.7;
        private int embeddingTimeoutSec = 30;

        VectorConfig merge(VectorConfig o) {
            if (o == null) return this;
            if (o.reviewSearchTopk != 5 || o.enabled != true) this.enabled = o.enabled;
            this.reviewSearchTopk = o.reviewSearchTopk;
            this.errorSearchTopk = o.errorSearchTopk;
            this.minSimilarity = o.minSimilarity;
            this.knowledgeMinScore = o.knowledgeMinScore;
            this.embeddingTimeoutSec = o.embeddingTimeoutSec;
            return this;
        }
    }

    // ==================== 2. 个人画像配置 ====================

    @Data
    public static class ProfileConfig {
        private boolean injectEnabled = false;   // 默认关闭，团队稳定后开启
        private int lookbackDays = 30;
        private int clusterTopk = 10;
        private int injectTopk = 3;
        private int recentReviewCount = 5;
        private int scoreTrendCount = 10;

        ProfileConfig merge(ProfileConfig o) {
            if (o == null) return this;
            this.injectEnabled = o.injectEnabled;
            this.lookbackDays = o.lookbackDays;
            this.clusterTopk = o.clusterTopk;
            this.injectTopk = o.injectTopk;
            this.recentReviewCount = o.recentReviewCount;
            this.scoreTrendCount = o.scoreTrendCount;
            return this;
        }
    }

    // ==================== 3. 文件过滤配置 ====================

    @Data
    public static class FileFilterConfig {
        private int maxDiffLinesPerFile = 500;
        private int maxTotalDiffLines = 3000;
        private int maxReviewFiles = 15;
        private List<String> excludeFilePatterns = new ArrayList<>();
        private List<String> binaryExtensions = new ArrayList<>();

        {
            // 默认排除模式
            excludeFilePatterns.add("**/package-lock.json");
            excludeFilePatterns.add("**/yarn.lock");
            excludeFilePatterns.add("**/pnpm-lock.yaml");
            excludeFilePatterns.add("**/go.sum");
            excludeFilePatterns.add("**/*.min.js");
            excludeFilePatterns.add("**/*.min.css");
            excludeFilePatterns.add("**/sql/*.sql");

            // 默认二进制扩展名
            binaryExtensions.addAll(List.of(
                    ".jar", ".war", ".ear",
                    ".png", ".jpg", ".gif", ".ico", ".svg",
                    ".pdf", ".doc", ".docx", ".xlsx",
                    ".ttf", ".woff", ".woff2", ".eot"
            ));
        }

        FileFilterConfig merge(FileFilterConfig o) {
            if (o == null) return this;
            this.maxDiffLinesPerFile = o.maxDiffLinesPerFile;
            this.maxTotalDiffLines = o.maxTotalDiffLines;
            this.maxReviewFiles = o.maxReviewFiles;
            if (o.excludeFilePatterns != null && !o.excludeFilePatterns.isEmpty())
                this.excludeFilePatterns = o.excludeFilePatterns;
            if (o.binaryExtensions != null && !o.binaryExtensions.isEmpty())
                this.binaryExtensions = o.binaryExtensions;
            return this;
        }
    }

    // ==================== 4. Token 预算配置 ====================

    @Data
    public static class TokenConfig {
        private int maxContextTokens = 16000;
        private int templateReserveTokens = 2000;
        private int relatedClassTokens = 1000;
        private double newFilePenalty = 0.8;
        private double coreModuleBonus = 1.2;
        private int minTokenPerFile = 800;

        TokenConfig merge(TokenConfig o) {
            if (o == null) return this;
            this.maxContextTokens = o.maxContextTokens;
            this.templateReserveTokens = o.templateReserveTokens;
            this.relatedClassTokens = o.relatedClassTokens;
            this.newFilePenalty = o.newFilePenalty;
            this.coreModuleBonus = o.coreModuleBonus;
            this.minTokenPerFile = o.minTokenPerFile;
            return this;
        }
    }

    // ==================== 5. 异步流程配置 ====================

    @Data
    public static class AsyncConfig {
        private int scoreTimeoutSec = 120;
        private int aiTimeoutSec = 180;
        private int threadPoolSize = 4;
        private boolean progressCommentEnabled = true;
        private int scoreRetryMax = 2;
        private int scoreRetryDelayMs = 5000;

        AsyncConfig merge(AsyncConfig o) {
            if (o == null) return this;
            this.scoreTimeoutSec = o.scoreTimeoutSec;
            this.aiTimeoutSec = o.aiTimeoutSec;
            this.threadPoolSize = o.threadPoolSize;
            this.progressCommentEnabled = o.progressCommentEnabled;
            this.scoreRetryMax = o.scoreRetryMax;
            this.scoreRetryDelayMs = o.scoreRetryDelayMs;
            return this;
        }
    }

    // ==================== 6. 评分规则配置 ====================

    @Data
    public static class ScoringConfig {
        private double aiWeight = 0.6;
        private double ruleWeight = 0.4;
        private int criticalDeduction = 20;
        private int majorDeduction = 10;
        private int minorDeduction = 3;
        private int suggestionDeduction = 0;
        private int blockThreshold = 60;
        private BlockingRuleConfig blockingRules = new BlockingRuleConfig();
        private DimensionsConfig dimensions = new DimensionsConfig();
        private Map<String, ScoreLevelConfig> scoreLevels = new LinkedHashMap<>();
        private Map<String, SeverityDefConfig> severityDefinitions = new LinkedHashMap<>();

        {
            // 默认等级线
            scoreLevels.put("A", new ScoreLevelConfig(85, "优秀", "可直接合并"));
            scoreLevels.put("B", new ScoreLevelConfig(70, "良好", "建议修复 MAJOR 以上问题后合并"));
            scoreLevels.put("C", new ScoreLevelConfig(60, "一般", "必须修复所有 CRITICAL 问题"));
            scoreLevels.put("D", new ScoreLevelConfig(50, "较差", "建议重构，需人工 Review"));
            scoreLevels.put("F", new ScoreLevelConfig(0, "不合格", "阻止合并，必须整改"));

            // 默认严重度定义
            severityDefinitions.put("CRITICAL", new SeverityDefConfig(20, "致命",
                    List.of("SQL注入", "硬编码密钥", "死锁风险", "数据丢失风险")));
            severityDefinitions.put("MAJOR", new SeverityDefConfig(10, "严重",
                    List.of("未处理异常", "N+1查询", "空指针风险", "事务缺失")));
            severityDefinitions.put("MINOR", new SeverityDefConfig(3, "一般",
                    List.of("魔法数字", "过长方法", "无效import", "命名不规范")));
            severityDefinitions.put("SUGGESTION", new SeverityDefConfig(0, "建议",
                    List.of("Optional替代null检查", "日志补充")));
        }

        ScoringConfig merge(ScoringConfig o) {
            if (o == null) return this;
            this.aiWeight = o.aiWeight;
            this.ruleWeight = o.ruleWeight;
            this.criticalDeduction = o.criticalDeduction;
            this.majorDeduction = o.majorDeduction;
            this.minorDeduction = o.minorDeduction;
            this.suggestionDeduction = o.suggestionDeduction;
            this.blockThreshold = o.blockThreshold;
            if (o.blockingRules != null) this.blockingRules = this.blockingRules.merge(o.blockingRules);
            if (o.dimensions != null) this.dimensions = this.dimensions.merge(o.dimensions);
            if (o.scoreLevels != null && !o.scoreLevels.isEmpty()) this.scoreLevels = o.scoreLevels;
            if (o.severityDefinitions != null && !o.severityDefinitions.isEmpty()) this.severityDefinitions = o.severityDefinitions;
            return this;
        }
    }

    @Data
    public static class BlockingRuleConfig {
        private Boolean criticalDirectBlock = true;
        private Integer majorBlockThreshold = null;
        private Boolean suggestionOnlyBlockEnabled = false;

        BlockingRuleConfig merge(BlockingRuleConfig o) {
            if (o == null) return this;
            if (o.criticalDirectBlock != null) this.criticalDirectBlock = o.criticalDirectBlock;
            if (o.majorBlockThreshold != null || this.majorBlockThreshold != null) this.majorBlockThreshold = o.majorBlockThreshold;
            if (o.suggestionOnlyBlockEnabled != null) this.suggestionOnlyBlockEnabled = o.suggestionOnlyBlockEnabled;
            return this;
        }
    }

    // ==================== 6a. 评分维度权重 ====================

    @Data
    public static class DimensionsConfig {
        private int compliance = 25;
        private int correctness = 25;
        private int dataIntegrity = 20;
        private int performance = 15;
        private int maintainability = 15;

        DimensionsConfig merge(DimensionsConfig o) {
            if (o == null) return this;
            this.compliance = o.compliance;
            this.correctness = o.correctness;
            this.dataIntegrity = o.dataIntegrity;
            this.performance = o.performance;
            this.maintainability = o.maintainability;
            return this;
        }
    }

    // ==================== 6b. 评分等级 ====================

    @Data
    public static class ScoreLevelConfig {
        private int minScore;
        private String label;
        private String suggestion;

        public ScoreLevelConfig() {}

        public ScoreLevelConfig(int minScore, String label, String suggestion) {
            this.minScore = minScore;
            this.label = label;
            this.suggestion = suggestion;
        }
    }

    // ==================== 6c. 严重度定义 ====================

    @Data
    public static class SeverityDefConfig {
        private int deduction;
        private String label;
        private List<String> examples = new ArrayList<>();

        public SeverityDefConfig() {}

        public SeverityDefConfig(int deduction, String label, List<String> examples) {
            this.deduction = deduction;
            this.label = label;
            this.examples = examples;
        }
    }

    // ==================== 7. 通知配置 ====================

    @Data
    public static class NotificationConfig {
        private int scoreAlertThreshold = 60;
        private List<String> scoreAlertChannels = new ArrayList<>();
        /**
         * 兼容旧配置：历史版本通过 notification.wechatNotifyEnabled 控制仓库级企微通知。
         * 新版本统一使用 platforms.wechat.enabled。
         */
        private boolean wechatNotifyEnabled = true;
        private Map<String, NotificationPlatformConfig> platforms = defaultPlatforms();
        private Map<String, ErrorAlertRouteConfig> errorAlertRoutes = defaultErrorAlertRoutes();
        private NotificationRetryConfig retry = new NotificationRetryConfig();

        {
            scoreAlertChannels.add(NotificationConstants.PLATFORM_WECHAT);
        }

        NotificationConfig merge(NotificationConfig o) {
            if (o == null) return this;
            this.scoreAlertThreshold = o.scoreAlertThreshold;
            if (o.scoreAlertChannels != null && !o.scoreAlertChannels.isEmpty())
                this.scoreAlertChannels = o.scoreAlertChannels;
            this.wechatNotifyEnabled = o.wechatNotifyEnabled;
            this.platforms = mergePlatforms(this.platforms, o.platforms, o.wechatNotifyEnabled);
            this.errorAlertRoutes = mergeErrorAlertRoutes(this.errorAlertRoutes, o.errorAlertRoutes);
            this.retry = this.retry.merge(o.retry);
            return this;
        }

        private static Map<String, NotificationPlatformConfig> defaultPlatforms() {
            Map<String, NotificationPlatformConfig> platforms = new LinkedHashMap<>();
            platforms.put(NotificationConstants.PLATFORM_WECHAT, new NotificationPlatformConfig(true, null));
            platforms.put(NotificationConstants.PLATFORM_FEISHU, new NotificationPlatformConfig(false, null));
            platforms.put(NotificationConstants.PLATFORM_DINGTALK, new NotificationPlatformConfig(false, null));
            return platforms;
        }

        private static Map<String, NotificationPlatformConfig> mergePlatforms(
                Map<String, NotificationPlatformConfig> base,
                Map<String, NotificationPlatformConfig> override,
                boolean legacyWechatEnabled) {
            Map<String, NotificationPlatformConfig> merged = new LinkedHashMap<>();
            if (base != null) {
                base.forEach((platform, config) -> merged.put(platform, config.copy()));
            }
            if (override != null) {
                override.forEach((platform, config) -> {
                    if (platform != null && config != null) {
                        NotificationPlatformConfig current = merged.getOrDefault(platform, new NotificationPlatformConfig());
                        merged.put(platform, current.merge(config));
                    }
                });
            }
            NotificationPlatformConfig wechatConfig = merged.getOrDefault(
                    NotificationConstants.PLATFORM_WECHAT,
                    new NotificationPlatformConfig());
            wechatConfig.setEnabled(legacyWechatEnabled && wechatConfig.isEnabled());
            merged.put(NotificationConstants.PLATFORM_WECHAT, wechatConfig);
            merged.putIfAbsent(NotificationConstants.PLATFORM_FEISHU, new NotificationPlatformConfig(false, null));
            merged.putIfAbsent(NotificationConstants.PLATFORM_DINGTALK, new NotificationPlatformConfig(false, null));
            return merged;
        }

        private static Map<String, ErrorAlertRouteConfig> defaultErrorAlertRoutes() {
            Map<String, ErrorAlertRouteConfig> routes = new LinkedHashMap<>();
            routes.put("P0", new ErrorAlertRouteConfig(true, "critical", "urgent"));
            routes.put("P1", new ErrorAlertRouteConfig(true, "critical", "urgent"));
            routes.put("P2", new ErrorAlertRouteConfig(true, "default", "normal"));
            routes.put("P3", new ErrorAlertRouteConfig(false, "default", "low"));
            return routes;
        }

        private static Map<String, ErrorAlertRouteConfig> mergeErrorAlertRoutes(
                Map<String, ErrorAlertRouteConfig> base,
                Map<String, ErrorAlertRouteConfig> override) {
            Map<String, ErrorAlertRouteConfig> merged = new LinkedHashMap<>();
            if (base != null) {
                base.forEach((severity, route) -> merged.put(severity, route.copy()));
            }
            if (override != null) {
                override.forEach((severity, route) -> {
                    if (severity != null && route != null) {
                        ErrorAlertRouteConfig current = merged.getOrDefault(severity, new ErrorAlertRouteConfig());
                        merged.put(severity, current.merge(route));
                    }
                });
            }
            return merged;
        }
    }

    @Data
    public static class NotificationPlatformConfig {
        private boolean enabled;
        private String webhook;

        public NotificationPlatformConfig() {
        }

        public NotificationPlatformConfig(boolean enabled, String webhook) {
            this.enabled = enabled;
            this.webhook = webhook;
        }

        NotificationPlatformConfig merge(NotificationPlatformConfig o) {
            if (o == null) return this;
            this.enabled = o.enabled;
            if (o.webhook != null) {
                this.webhook = o.webhook;
            }
            return this;
        }

        NotificationPlatformConfig copy() {
            return new NotificationPlatformConfig(enabled, webhook);
        }
    }

    @Data
    public static class ErrorAlertRouteConfig {
        private boolean enabled = true;
        private String channel = "default";
        private String priority = "normal";

        public ErrorAlertRouteConfig() {
        }

        public ErrorAlertRouteConfig(boolean enabled, String channel, String priority) {
            this.enabled = enabled;
            this.channel = channel;
            this.priority = priority;
        }

        ErrorAlertRouteConfig merge(ErrorAlertRouteConfig o) {
            if (o == null) return this;
            this.enabled = o.enabled;
            if (o.channel != null && !o.channel.isBlank()) {
                this.channel = o.channel;
            }
            if (o.priority != null && !o.priority.isBlank()) {
                this.priority = o.priority;
            }
            return this;
        }

        ErrorAlertRouteConfig copy() {
            return new ErrorAlertRouteConfig(enabled, channel, priority);
        }
    }

    @Data
    public static class NotificationRetryConfig {
        private int maxRetries = 3;
        private List<Integer> backoffSeconds = new ArrayList<>(List.of(30, 120, 300));
        private int timeoutSec = 600;

        NotificationRetryConfig merge(NotificationRetryConfig o) {
            if (o == null) return this;
            if (o.maxRetries >= 0) {
                this.maxRetries = o.maxRetries;
            }
            if (o.backoffSeconds != null && !o.backoffSeconds.isEmpty()) {
                this.backoffSeconds = o.backoffSeconds;
            }
            if (o.timeoutSec > 0) {
                this.timeoutSec = o.timeoutSec;
            }
            return this;
        }
    }

    // ==================== 8. 规则管理配置 ====================

    @Data
    public static class RuleConfig {
        private List<String> standardCategories = new ArrayList<>(List.of(
                "CODING", "API", "DATABASE", "SECURITY", "CUSTOM"));
        private ReviewFocusConfig reviewFocus = new ReviewFocusConfig();

        RuleConfig merge(RuleConfig o) {
            if (o == null) return this;
            if (o.standardCategories != null && !o.standardCategories.isEmpty()) {
                this.standardCategories = o.standardCategories;
            }
            if (o.reviewFocus != null) {
                this.reviewFocus = this.reviewFocus.merge(o.reviewFocus);
            }
            return this;
        }
    }

    @Data
    public static class ReviewFocusConfig {
        private Map<String, String> focusByLanguage = defaultFocusByLanguage();

        ReviewFocusConfig merge(ReviewFocusConfig o) {
            if (o == null) return this;
            if (o.focusByLanguage != null && !o.focusByLanguage.isEmpty()) {
                this.focusByLanguage = new LinkedHashMap<>(this.focusByLanguage);
                this.focusByLanguage.putAll(o.focusByLanguage);
            }
            return this;
        }

        private static Map<String, String> defaultFocusByLanguage() {
            Map<String, String> focusMap = new LinkedHashMap<>();
            focusMap.put("default", "变更是否引入逻辑风险、配置风险、可维护性问题或发布风险");
            focusMap.put("java", "重点检查事务边界、空指针风险、集合遍历副作用、数据库访问和并发安全");
            focusMap.put("javascript", "重点检查状态同步、空值兼容、异步流程、类型约束和界面交互边界");
            focusMap.put("typescript", "重点检查状态同步、空值兼容、异步流程、类型约束和界面交互边界");
            focusMap.put("vue", "重点检查状态同步、空值兼容、异步流程、类型约束和界面交互边界");
            focusMap.put("sql", "重点检查 where 条件、索引命中、锁风险、兼容性和数据变更安全");
            return focusMap;
        }
    }
}
