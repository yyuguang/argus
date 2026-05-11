package com.lnzz.argus.error.parse;

/**
 * 严重度等级枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum SeverityLevel {

    P0("P0", "紧急"),
    P1("P1", "高"),
    P2("P2", "中"),
    P3("P3", "低");

    private final String code;
    private final String name;

    SeverityLevel(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public static SeverityLevel fromCode(String code) {
        if (code == null) return P3;
        String s = code.trim().toUpperCase();
        for (SeverityLevel level : values()) {
            if (level.code.equals(s)) return level;
        }
        if (s.startsWith("P0")) return P0;
        if (s.startsWith("P1")) return P1;
        if (s.startsWith("P2")) return P2;
        return P3;
    }
}
