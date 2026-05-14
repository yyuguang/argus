package com.lnzz.argus.common.enums;

/**
 * 错误事件处理状态枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum ProcessingStatus {

    RECEIVED("RECEIVED", "已接收"),
    PARSED("PARSED", "已解析"),
    AGGREGATED("AGGREGATED", "已聚合"),
    ANALYZING("ANALYZING", "分析中"),
    ANALYZED("ANALYZED", "已分析"),
    AI_DEGRADED("AI_DEGRADED", "AI降级"),
    NOTIFY_FAILED("NOTIFY_FAILED", "通知失败"),
    IGNORED("IGNORED", "已忽略"),
    FALSE_POSITIVE("FALSE_POSITIVE", "误报");

    private final String code;
    private final String name;

    ProcessingStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
