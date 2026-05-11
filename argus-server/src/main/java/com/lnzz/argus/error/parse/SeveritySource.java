package com.lnzz.argus.error.parse;

/**
 * 严重度来源枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum SeveritySource {

    RULE("RULE", "规则初判"),
    AI("AI", "AI校准"),
    MANUAL("MANUAL", "人工修正");

    private final String code;
    private final String name;

    SeveritySource(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
