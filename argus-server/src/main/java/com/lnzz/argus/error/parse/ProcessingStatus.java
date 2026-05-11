package com.lnzz.argus.error.parse;

/**
 * 错误事件处理状态枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum ProcessingStatus {

    RECEIVED("RECEIVED", "已接收"),
    PARSED("PARSED", "已解析");

    private final String code;
    private final String name;

    ProcessingStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
