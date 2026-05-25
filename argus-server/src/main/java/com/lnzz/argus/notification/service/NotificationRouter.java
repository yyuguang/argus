package com.lnzz.argus.notification.service;

import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.review.config.ReviewConfig;
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
public class NotificationRouter {

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
        return route(event, ReviewConfig.defaults().getNotification());
    }

    /**
     * 使用前端 SCM 配置中的错误告警路由策略匹配通知通道。
     */
    public RouteResult route(ErrorEvent event, ReviewConfig.NotificationConfig notificationConfig) {
        String severity = event.getSeverity();
        ReviewConfig.NotificationConfig effectiveConfig = notificationConfig != null
                ? notificationConfig
                : ReviewConfig.defaults().getNotification();
        ReviewConfig.ErrorAlertRouteConfig route = resolveRouteConfig(severity, effectiveConfig);
        boolean shouldNotify = route.isEnabled() && !"suppress".equalsIgnoreCase(route.getPriority());
        log.debug("SCM 通知路由匹配: severity={}, channel={}, priority={}, shouldNotify={}",
                severity, route.getChannel(), route.getPriority(), shouldNotify);
        return new RouteResult(defaultIfBlank(route.getChannel(), NotificationConstants.CHANNEL_DEFAULT),
                defaultIfBlank(route.getPriority(), NotificationConstants.PRIORITY_NORMAL), shouldNotify);
    }

    private ReviewConfig.ErrorAlertRouteConfig resolveRouteConfig(String severity,
                                                                  ReviewConfig.NotificationConfig notificationConfig) {
        String normalizedSeverity = defaultIfBlank(severity, "DEFAULT").toUpperCase();
        if (notificationConfig.getErrorAlertRoutes() != null) {
            ReviewConfig.ErrorAlertRouteConfig route = notificationConfig.getErrorAlertRoutes().get(normalizedSeverity);
            if (route != null) {
                return route;
            }
        }

        return defaultRoute(normalizedSeverity);
    }

    private ReviewConfig.ErrorAlertRouteConfig defaultRoute(String severity) {
        return switch (severity) {
            case "P0", "P1" -> new ReviewConfig.ErrorAlertRouteConfig(true,
                    NotificationConstants.CHANNEL_CRITICAL, NotificationConstants.PRIORITY_URGENT);
            case "P2" -> new ReviewConfig.ErrorAlertRouteConfig(true,
                    NotificationConstants.CHANNEL_DEFAULT, NotificationConstants.PRIORITY_NORMAL);
            case "P3" -> new ReviewConfig.ErrorAlertRouteConfig(false,
                    NotificationConstants.CHANNEL_DEFAULT, NotificationConstants.PRIORITY_LOW);
            default -> new ReviewConfig.ErrorAlertRouteConfig(true,
                    NotificationConstants.CHANNEL_DEFAULT, NotificationConstants.PRIORITY_NORMAL);
        };
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
