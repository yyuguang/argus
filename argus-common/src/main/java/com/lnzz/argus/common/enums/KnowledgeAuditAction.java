package com.lnzz.argus.common.enums;

/**
 * 知识条目审计操作枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum KnowledgeAuditAction {

    CONFIRM("CONFIRM", "确认"),
    MARK_FALSE_POSITIVE("MARK_FALSE_POSITIVE", "标记误报"),
    IGNORE("IGNORE", "忽略"),
    PROMOTE_WHITELIST("PROMOTE_WHITELIST", "提升白名单"),
    DEMOTE_WHITELIST("DEMOTE_WHITELIST", "降级白名单");

    private final String code;
    private final String name;

    KnowledgeAuditAction(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
