package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.config.NotificationProperties.RouteRule;
import com.lnzz.argus.error.entity.ErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * M7-A01: 通知路由规则匹配器
 * <p>根据严重度、错误类型、来源类型匹配目标通道和优先级</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRouter {

    private final NotificationProperties properties;

    /**
     * 路由结果
     */
    public record RouteResult(String channel, String priority, boolean shouldNotify) {
        public static final RouteResult SUPPRESS = new RouteResult(null, "suppress", false);
    }

    /**
     * 匹配 ErrorEvent 到通知通道
     *
     * @return RouteResult 含目标通道、优先级、是否发送
     */
    public RouteResult route(ErrorEvent event) {
        if (!properties.isEnabled()) {
            return RouteResult.SUPPRESS;
        }

        String severity = event.getSeverity();
        String errorType = event.getErrorType();
        String sourceType = event.getSourceType();

        // 1. 精确匹配路由规则
        if (properties.getRouteRules() != null) {
            for (RouteRule rule : properties.getRouteRules()) {
                if (matches(rule, severity, errorType, sourceType)) {
                    log.debug("路由规则匹配: severity={}, errorType={}, channel={}, priority={}",
                            severity, errorType, rule.getChannel(), rule.getPriority());
                    return new RouteResult(
                            rule.getChannel() != null ? rule.getChannel() : properties.getDefaultChannel(),
                            rule.getPriority() != null ? rule.getPriority() : "normal",
                            !"suppress".equals(rule.getPriority())
                    );
                }
            }
        }

        // 2. 默认路由: 按严重度
        return defaultRoute(severity);
    }

    private RouteResult defaultRoute(String severity) {
        if (severity == null) {
            return new RouteResult(properties.getDefaultChannel(), "normal", true);
        }
        return switch (severity) {
            case "P0", "P1" -> new RouteResult("critical", "urgent", true);
            case "P2" -> new RouteResult(properties.getDefaultChannel(), "normal", true);
            case "P3" -> new RouteResult(properties.getDefaultChannel(), "low", false);
            default -> new RouteResult(properties.getDefaultChannel(), "normal", true);
        };
    }

    private boolean matches(RouteRule rule, String severity, String errorType, String sourceType) {
        if (rule.getSeverity() != null && !rule.getSeverity().isEmpty()) {
            if (!rule.getSeverity().equalsIgnoreCase(severity)) return false;
        }
        if (rule.getErrorType() != null && !rule.getErrorType().isEmpty()) {
            if (errorType == null || !errorType.contains(rule.getErrorType())) return false;
        }
        if (rule.getSourceType() != null && !rule.getSourceType().isEmpty()) {
            if (sourceType == null || !sourceType.equalsIgnoreCase(rule.getSourceType())) return false;
        }
        return true;
    }
}
