package com.lnzz.argus.error.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    /**
     * 严重度判定结果
     */
    public record SeverityResult(
            SeverityLevel severity,
            String reason,
            AnalysisDecision analysisDecision,
            double confidence
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
        ErrorType type;
        try {
            type = ErrorType.valueOf(errorTypeName);
        } catch (IllegalArgumentException e) {
            type = ErrorType.UNKNOWN;
        }

        // 1. 获取默认严重度
        SeverityLevel severity = DEFAULT_SEVERITY.getOrDefault(type, SeverityLevel.P3);
        StringBuilder reason = new StringBuilder("错误类型: ").append(type.getDescription());

        // 2. 环境因子调整: PROD 环境升级
        boolean isProd = "PROD".equalsIgnoreCase(environment) || "PRODUCTION".equalsIgnoreCase(environment);
        if (isProd && severity == SeverityLevel.P2) {
            if (type == ErrorType.SQL_EXCEPTION || type == ErrorType.CONNECTION_REFUSED
                    || type == ErrorType.TIMEOUT) {
                severity = SeverityLevel.P1;
                reason.append(" | PROD环境升级");
            }
        }

        // 3. UNKNOWN + 新指纹 → P1（未知新异常需重点关注）
        if (type == ErrorType.UNKNOWN && isNewFingerprint) {
            severity = SeverityLevel.P1;
            reason.append(" | 未知新异常指纹，高优分析");
        }

        // 4. BIZ_EXCEPTION: 保持 P3，但需根据频率后续升级
        if (type == ErrorType.BIZ_EXCEPTION) {
            reason.append(" | 业务异常，需结合频率/链路判断");
        }

        // 5. 分析决策
        AnalysisDecision decision = determineAnalysisDecision(severity, type, isNewFingerprint);
        reason.append(" | 决策: ").append(decision.getName());

        // 6. 置信度：规则初判默认 0.80
        double confidence = 0.80;

        log.debug("严重度初判: type={}, env={}, severity={}, decision={}, confidence={}",
                errorTypeName, environment, severity.getCode(), decision.getCode(), confidence);

        return new SeverityResult(severity, reason.toString(), decision, confidence);
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

        if (isNewFingerprint) {
            return AnalysisDecision.MUST_ANALYZE;
        }

        if (severity == SeverityLevel.P2) {
            return AnalysisDecision.CONDITIONAL_ANALYZE;
        }

        if (type == ErrorType.BIZ_EXCEPTION) {
            return AnalysisDecision.CONDITIONAL_ANALYZE;
        }

        return AnalysisDecision.AGGREGATE_ONLY;
    }
}
