package com.lnzz.argus.error.parse;

/**
 * AI 分析触发决策枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum AnalysisDecision {

    MUST_ANALYZE("MUST_ANALYZE", "必须分析"),
    CONDITIONAL_ANALYZE("CONDITIONAL_ANALYZE", "条件分析"),
    AGGREGATE_ONLY("AGGREGATE_ONLY", "仅聚合");

    private final String code;
    private final String name;

    AnalysisDecision(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
