package com.lnzz.argus.common.enums;

/**
 * AI 分析来源枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum AnalysisSource {

    AI("AI", "AI分析"),
    AI_DEGRADED("AI_DEGRADED", "AI降级"),
    MANUAL("MANUAL", "人工分析"),
    HYBRID("HYBRID", "混合分析");

    private final String code;
    private final String name;

    AnalysisSource(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
