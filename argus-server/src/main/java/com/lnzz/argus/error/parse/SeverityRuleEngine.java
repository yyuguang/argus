package com.lnzz.argus.error.parse;

import com.lnzz.argus.common.enums.AnalysisDecision;
import com.lnzz.argus.common.enums.ErrorType;
import com.lnzz.argus.common.enums.SeverityLevel;
import com.lnzz.argus.common.enums.SeveritySource;
import com.lnzz.argus.config.ErrorProcessingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 严重度规则初判引擎（M4-B05）
 * <p>规则驱动的 initialSeverity 判定 + analysisDecision 触发决策</p>
 * <p>不依赖 AI，纯规则快速分流</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class SeverityRuleEngine {

    private static final Map<ErrorType, SeverityLevel> DEFAULT_SEVERITY = Map.ofEntries(
            Map.entry(ErrorType.SQL_EXCEPTION, SeverityLevel.P1),
            Map.entry(ErrorType.CONNECTION_REFUSED, SeverityLevel.P1),
            Map.entry(ErrorType.NGINX_502, SeverityLevel.P1),
            Map.entry(ErrorType.NGINX_503, SeverityLevel.P1),
            Map.entry(ErrorType.NGINX_504, SeverityLevel.P1),
            Map.entry(ErrorType.NGINX_499, SeverityLevel.P2),
            Map.entry(ErrorType.NGINX_5XX, SeverityLevel.P1),
            Map.entry(ErrorType.NGINX_4XX, SeverityLevel.P3),
            Map.entry(ErrorType.TIMEOUT, SeverityLevel.P2),
            Map.entry(ErrorType.NULL_POINTER, SeverityLevel.P2),
            Map.entry(ErrorType.CLASS_CAST, SeverityLevel.P2),
            Map.entry(ErrorType.INDEX_OUT_OF_BOUNDS, SeverityLevel.P2),
            Map.entry(ErrorType.IO_EXCEPTION, SeverityLevel.P2),
            Map.entry(ErrorType.HTTP_ERROR, SeverityLevel.P2),
            Map.entry(ErrorType.MQ_ERROR, SeverityLevel.P2),
            Map.entry(ErrorType.SERIALIZATION_ERROR, SeverityLevel.P2),
            Map.entry(ErrorType.BIZ_EXCEPTION, SeverityLevel.P3),
            Map.entry(ErrorType.UNKNOWN, SeverityLevel.P2)
    );

    private final ErrorProcessingProperties properties;

    public SeverityRuleEngine() {
        this(new ErrorProcessingProperties());
    }

    @Autowired
    public SeverityRuleEngine(ErrorProcessingProperties properties) {
        this.properties = properties != null ? properties : new ErrorProcessingProperties();
    }

    /**
     * 严重度判定结果
     */
    public record SeverityResult(
            SeverityLevel severity,
            SeverityLevel initialSeverity,
            SeverityLevel finalSeverity,
            SeveritySource severitySource,
            String reason,
            AnalysisDecision analysisDecision,
            double confidence
    ) {}

    /**
     * 严重度判定上下文。
     */
    public record SeverityContext(
            String errorTypeName,
            String environment,
            boolean newFingerprint,
            Integer occurrenceCount,
            String interfaceRef,
            String requestUri,
            String ownerTeam,
            String appName,
            String knowledgeStatus,
            String errorMessage
    ) {}

    /**
     * 根据错误类型和环境判定 initialSeverity + analysisDecision
     *
     * @param errorType   已识别的错误类型
     * @param environment 环境（PROD/UAT/TEST）
     * @param isNewFingerprint 是否为新指纹（首次出现）
     * @return 判定结果
     */
    public SeverityResult evaluate(String errorTypeName, String environment,
                                    boolean isNewFingerprint) {
        return evaluate(new SeverityContext(errorTypeName, environment, isNewFingerprint,
                null, null, null, null, null, null, null));
    }

    /**
     * 根据错误类型、运行环境、聚合频率和业务上下文判定 initialSeverity/finalSeverity。
     *
     * @param context 严重度判定上下文
     * @return 判定结果
     */
    public SeverityResult evaluate(SeverityContext context) {
        ErrorType type;
        try {
            type = ErrorType.valueOf(context.errorTypeName());
        } catch (IllegalArgumentException | NullPointerException e) {
            type = ErrorType.UNKNOWN;
        }

        // 1. 获取默认严重度
        SeverityLevel initialSeverity = defaultSeverity(type);
        SeverityLevel severity = initialSeverity;
        StringBuilder reason = new StringBuilder("错误类型: ").append(type.getDescription());

        // 2. 环境因子调整: PROD 环境升级
        ErrorProcessingProperties.EnvironmentUpgrade environmentPolicy =
                properties.getSeverityPolicy().getEnvironmentUpgrade();
        if (environmentPolicy.isEnabled()
                && containsIgnoreCase(environmentPolicy.getEnvironments(), context.environment())
                && containsIgnoreCase(environmentPolicy.getErrorTypes(), type.name())
                && severity == SeverityLevel.fromCode(environmentPolicy.getFrom())) {
            severity = moreSevere(severity, SeverityLevel.fromCode(environmentPolicy.getTo()));
            reason.append(" | ").append(context.environment()).append("环境升级");
        }

        // 3. UNKNOWN + 新指纹 → P1（未知新异常需重点关注）
        if (type == ErrorType.UNKNOWN && context.newFingerprint()) {
            severity = SeverityLevel.P1;
            reason.append(" | 未知新异常指纹，高优分析");
        }

        // 4. 高频聚合升级：低等级错误频繁出现时提高处理优先级
        ErrorProcessingProperties.FrequencyUpgrade frequencyPolicy =
                properties.getSeverityPolicy().getFrequencyUpgrade();
        int occurrenceCount = context.occurrenceCount() == null ? 0 : context.occurrenceCount();
        if (frequencyPolicy.isEnabled() && occurrenceCount >= frequencyPolicy.getHighThreshold()) {
            SeverityLevel target = SeverityLevel.fromCode(frequencyPolicy.getHighTo());
            SeverityLevel upgraded = moreSevere(severity, target);
            if (upgraded != severity) {
                severity = upgraded;
                reason.append(" | 高频异常达到").append(occurrenceCount).append("次，升级为").append(severity.getCode());
            }
        } else if (frequencyPolicy.isEnabled() && occurrenceCount >= frequencyPolicy.getThreshold()) {
            SeverityLevel target = SeverityLevel.fromCode(frequencyPolicy.getTo());
            SeverityLevel upgraded = moreSevere(severity, target);
            if (upgraded != severity) {
                severity = upgraded;
                reason.append(" | 重复出现").append(occurrenceCount).append("次，升级为").append(severity.getCode());
            }
        }

        // 5. 核心链路/核心应用/责任团队升级
        ErrorProcessingProperties.CoreLinkUpgrade corePolicy =
                properties.getSeverityPolicy().getCoreLinkUpgrade();
        if (corePolicy.isEnabled() && matchesCoreLink(corePolicy, context)) {
            SeverityLevel target = SeverityLevel.fromCode(corePolicy.getTo());
            SeverityLevel upgraded = moreSevere(severity, target);
            if (upgraded != severity) {
                severity = upgraded;
                reason.append(" | 命中核心链路策略，升级为").append(severity.getCode());
            }
        }

        // 6. 数据一致性风险升级
        ErrorProcessingProperties.DataConsistencyUpgrade consistencyPolicy =
                properties.getSeverityPolicy().getDataConsistencyUpgrade();
        if (consistencyPolicy.isEnabled() && containsAnyIgnoreCase(
                joinContext(context.interfaceRef(), context.requestUri(), context.errorMessage()),
                consistencyPolicy.getKeywords())) {
            SeverityLevel target = SeverityLevel.fromCode(consistencyPolicy.getTo());
            SeverityLevel upgraded = moreSevere(severity, target);
            if (upgraded != severity) {
                severity = upgraded;
                reason.append(" | 命中数据一致性风险关键词，升级为").append(severity.getCode());
            }
        }

        // 7. 知识库白名单/低风险命中降级
        ErrorProcessingProperties.KnowledgeDowngrade knowledgePolicy =
                properties.getSeverityPolicy().getKnowledgeDowngrade();
        boolean protectUnknownNewFingerprint = type == ErrorType.UNKNOWN && context.newFingerprint();
        if (!protectUnknownNewFingerprint
                && knowledgePolicy.isEnabled()
                && containsIgnoreCase(knowledgePolicy.getWhitelistStatuses(), context.knowledgeStatus())) {
            severity = SeverityLevel.fromCode(knowledgePolicy.getWhitelistTo());
            reason.append(" | 命中白名单知识，降级为").append(severity.getCode());
        } else if (!protectUnknownNewFingerprint
                && knowledgePolicy.isEnabled()
                && containsIgnoreCase(knowledgePolicy.getLowRiskStatuses(), context.knowledgeStatus())) {
            severity = SeverityLevel.fromCode(knowledgePolicy.getLowRiskTo());
            reason.append(" | 命中已知低风险知识，降级为").append(severity.getCode());
        }

        // 8. BIZ_EXCEPTION: 默认保持 P3，但允许被频率/核心链路升级
        if (type == ErrorType.BIZ_EXCEPTION) {
            reason.append(" | 业务异常，需结合频率/链路判断");
        }

        // 9. 分析决策
        AnalysisDecision decision = determineAnalysisDecision(severity, type, context.newFingerprint());
        reason.append(" | 决策: ").append(decision.getName());

        // 10. 置信度：规则初判默认 0.80
        double confidence = 0.80;

        log.debug("严重度初判: type={}, env={}, severity={}, decision={}, confidence={}",
                context.errorTypeName(), context.environment(), severity.getCode(), decision.getCode(), confidence);

        return new SeverityResult(severity, initialSeverity, severity, SeveritySource.RULE,
                reason.toString(), decision, confidence);
    }

    /**
     * 根据严重度和类型确定分析决策
     */
    private AnalysisDecision determineAnalysisDecision(SeverityLevel severity, ErrorType type, boolean isNewFingerprint) {
        if (severity == SeverityLevel.P0 || severity == SeverityLevel.P1) {
            return AnalysisDecision.MUST_ANALYZE;
        }

        if (type == ErrorType.UNKNOWN) {
            return AnalysisDecision.MUST_ANALYZE;
        }

        if (isNewFingerprint && severity != SeverityLevel.P3) {
            return AnalysisDecision.MUST_ANALYZE;
        }

        if (severity == SeverityLevel.P2) {
            return properties.getAnalysis().isAnalyzeP2()
                    ? AnalysisDecision.CONDITIONAL_ANALYZE
                    : AnalysisDecision.AGGREGATE_ONLY;
        }

        if (type == ErrorType.BIZ_EXCEPTION) {
            return properties.getAnalysis().isAnalyzeBizException()
                    ? AnalysisDecision.CONDITIONAL_ANALYZE
                    : AnalysisDecision.AGGREGATE_ONLY;
        }

        if (severity == SeverityLevel.P3 && properties.getAnalysis().isAnalyzeP3()) {
            return AnalysisDecision.CONDITIONAL_ANALYZE;
        }

        return AnalysisDecision.AGGREGATE_ONLY;
    }

    private SeverityLevel defaultSeverity(ErrorType type) {
        String configured = properties.getSeverityPolicy().getDefaultLevels().get(type.name());
        if (configured != null && !configured.isBlank()) {
            return SeverityLevel.fromCode(configured);
        }
        return DEFAULT_SEVERITY.getOrDefault(type, SeverityLevel.P3);
    }

    private SeverityLevel moreSevere(SeverityLevel current, SeverityLevel target) {
        return rank(target) < rank(current) ? target : current;
    }

    private int rank(SeverityLevel level) {
        return switch (level) {
            case P0 -> 0;
            case P1 -> 1;
            case P2 -> 2;
            case P3 -> 3;
        };
    }

    private boolean matchesCoreLink(ErrorProcessingProperties.CoreLinkUpgrade policy, SeverityContext context) {
        return containsAnyIgnoreCase(context.interfaceRef(), policy.getInterfaceRefs())
                || containsAnyIgnoreCase(context.requestUri(), policy.getRequestUris())
                || containsIgnoreCase(policy.getOwnerTeams(), context.ownerTeam())
                || containsIgnoreCase(policy.getAppNames(), context.appName());
    }

    private boolean containsIgnoreCase(Iterable<String> values, String target) {
        if (target == null || values == null) {
            return false;
        }
        for (String value : values) {
            if (target.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyIgnoreCase(String text, Iterable<String> keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String joinContext(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (Objects.nonNull(value) && !value.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }
}
