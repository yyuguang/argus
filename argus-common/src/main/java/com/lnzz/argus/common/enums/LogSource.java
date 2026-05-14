package com.lnzz.argus.common.enums;

/**
 * 日志来源枚举
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum LogSource {

    APP_LOG("APP_LOG", "应用日志"),
    NGINX_ACCESS("NGINX_ACCESS", "Nginx访问日志"),
    NGINX_ERROR("NGINX_ERROR", "Nginx错误日志");

    private final String code;
    private final String name;

    LogSource(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public static LogSource fromCode(String code) {
        if (code == null) return APP_LOG;
        for (LogSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) return source;
        }
        return APP_LOG;
    }

    public boolean isNginx() {
        return this == NGINX_ACCESS || this == NGINX_ERROR;
    }
}
