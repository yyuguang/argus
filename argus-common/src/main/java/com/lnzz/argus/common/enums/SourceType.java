package com.lnzz.argus.common.enums;

/**
 * 错误事件来源类型枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum SourceType {

    AGENT("AGENT", "Agent采集"),
    NGINX("NGINX", "Nginx入口");

    private final String code;
    private final String name;

    SourceType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
