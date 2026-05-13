package com.lnzz.argus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误日志处理策略配置。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "argus.error")
public class ErrorProcessingProperties {

    /** 指纹去重聚合配置 */
    private Dedup dedup = new Dedup();

    /** AI 分析闸门配置 */
    private Analysis analysis = new Analysis();

    /** 源码拉取缓存与预算配置 */
    private Source source = new Source();

    /** 周期汇总配置占位，由 application.yml 暴露 cron 表达式 */
    private Summary summary = new Summary();

    /** 严重度等级判定策略 */
    private SeverityPolicy severityPolicy = new SeverityPolicy();

    @Data
    public static class Dedup {
        /** 同 app + environment + fingerprint 聚合窗口（秒） */
        private long windowSeconds = 60;

        /** 预留：窗口内重复达到该次数后可升级分析/通知 */
        private int repeatUpgradeThreshold = 10;
    }

    @Data
    public static class Analysis {
        /** P2 事件是否允许进入 AI 分析，默认保持现有条件分析行为 */
        private boolean analyzeP2 = true;

        /** P3 事件是否允许进入 AI 分析，默认只聚合不分析 */
        private boolean analyzeP3 = false;

        /** 业务异常是否允许进入条件分析，默认保持现有行为 */
        private boolean analyzeBizException = true;

        /** 已确认知识/白名单精确命中时是否跳过 AI 分析 */
        private boolean skipKnownKnowledge = true;
    }

    @Data
    public static class Source {
        /** SCM 单文件源码缓存 TTL（秒） */
        private long cacheTtlSeconds = 900;

        /** 单文件最大字符数，超出后截断进入 Prompt */
        private int maxFileChars = 20000;

        /** 关联上下文文件最大数量 */
        private int maxRelatedFiles = 3;

        /** 单次错误分析 Prompt 源码总字符预算 */
        private int maxPromptSourceChars = 30000;
    }

    @Data
    public static class Summary {
        private String hourlyCron = "0 0 * * * *";
        private String dailyCron = "0 10 0 * * *";
        private String weeklyCron = "0 30 0 * * MON";
    }

    @Data
    public static class SeverityPolicy {
        /** 错误类型默认等级矩阵，key 为 ErrorType 枚举名，value 为 P0/P1/P2/P3 */
        private Map<String, String> defaultLevels = new LinkedHashMap<>();

        /** 生产环境升级策略 */
        private EnvironmentUpgrade environmentUpgrade = new EnvironmentUpgrade();

        /** 高频错误升级策略 */
        private FrequencyUpgrade frequencyUpgrade = new FrequencyUpgrade();

        /** 核心链路/核心系统升级策略 */
        private CoreLinkUpgrade coreLinkUpgrade = new CoreLinkUpgrade();

        /** 数据一致性风险升级策略 */
        private DataConsistencyUpgrade dataConsistencyUpgrade = new DataConsistencyUpgrade();

        /** 知识命中后的低风险降级策略 */
        private KnowledgeDowngrade knowledgeDowngrade = new KnowledgeDowngrade();

        /** AI 校准边界策略 */
        private AiCalibration aiCalibration = new AiCalibration();
    }

    @Data
    public static class EnvironmentUpgrade {
        private boolean enabled = true;
        private List<String> environments = new ArrayList<>(List.of("PROD", "PRODUCTION"));
        private List<String> errorTypes = new ArrayList<>(List.of("SQL_EXCEPTION", "CONNECTION_REFUSED", "TIMEOUT"));
        private String from = "P2";
        private String to = "P1";
    }

    @Data
    public static class FrequencyUpgrade {
        private boolean enabled = true;
        private int threshold = 10;
        private String to = "P2";
        private int highThreshold = 50;
        private String highTo = "P1";
    }

    @Data
    public static class CoreLinkUpgrade {
        private boolean enabled = true;
        private List<String> interfaceRefs = new ArrayList<>();
        private List<String> requestUris = new ArrayList<>();
        private List<String> ownerTeams = new ArrayList<>();
        private List<String> appNames = new ArrayList<>();
        private String to = "P1";
    }

    @Data
    public static class DataConsistencyUpgrade {
        private boolean enabled = true;
        private List<String> keywords = new ArrayList<>(List.of(
                "amount", "balance", "stock", "inventory", "payment", "settlement",
                "金额", "余额", "库存", "支付", "结算"));
        private String to = "P1";
    }

    @Data
    public static class KnowledgeDowngrade {
        private boolean enabled = true;
        private List<String> whitelistStatuses = new ArrayList<>(List.of("WHITELIST"));
        private List<String> lowRiskStatuses = new ArrayList<>(List.of("KNOWN_LOW_RISK", "LOW_RISK"));
        private String whitelistTo = "P3";
        private String lowRiskTo = "P3";
    }

    @Data
    public static class AiCalibration {
        /** AI 不允许直接把 P0/P1 降到 P2/P3，需人工确认 */
        private boolean protectHighSeverityDowngrade = true;
    }
}
