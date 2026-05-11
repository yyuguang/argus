package com.lnzz.argus.notification.entity;

/**
 * 通知发送状态枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum NotificationStatus {

    SENT("SENT", "已发送"),
    FAILED("FAILED", "发送失败");

    private final String code;
    private final String name;

    NotificationStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
