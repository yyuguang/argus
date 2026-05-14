package com.lnzz.argus.common.enums;

/**
 * 知识条目状态枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum KnowledgeEntryStatus {

    DRAFT("DRAFT", "草稿"),
    CONFIRMED("CONFIRMED", "已确认"),
    WHITELIST("WHITELIST", "白名单"),
    FALSE_POSITIVE("FALSE_POSITIVE", "误报"),
    OUTDATED("OUTDATED", "已过时");

    private final String code;
    private final String name;

    KnowledgeEntryStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public static KnowledgeEntryStatus fromCode(String code) {
        if (code == null) return DRAFT;
        for (KnowledgeEntryStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) return status;
        }
        return DRAFT;
    }
}
