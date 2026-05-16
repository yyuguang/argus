package com.lnzz.argus.notification.service;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.common.enums.SeverityLevel;
import com.lnzz.argus.review.config.ReviewConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationRouter - 通知路由")
class NotificationRouterTest {

    private NotificationRouter router;
    private ReviewConfig.NotificationConfig notificationConfig;

    @BeforeEach
    void setUp() {
        router = new NotificationRouter();
        notificationConfig = ReviewConfig.defaults().getNotification();
    }

    @Test
    @DisplayName("P0 → channel=critical, priority=urgent, shouldNotify=true")
    void p0RouteToCritical() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());
        event.setErrorType("NULL_POINTER");

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("critical", result.channel());
        assertEquals("urgent", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("P1 → channel=critical, priority=urgent")
    void p1RouteToCritical() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P1.getCode());
        event.setErrorType("SQL_EXCEPTION");

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("critical", result.channel());
        assertEquals("urgent", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("P2 → channel=default, priority=normal")
    void p2RouteToDefault() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P2.getCode());
        event.setErrorType("TIMEOUT");

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("default", result.channel());
        assertEquals("normal", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("P3 → channel=default, priority=low, shouldNotify=false")
    void p3RouteSuppressed() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P3.getCode());
        event.setErrorType("BIZ_EXCEPTION");

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("default", result.channel());
        assertEquals("low", result.priority());
        assertFalse(result.shouldNotify());
    }

    @Test
    @DisplayName("未知严重度 → 默认路由（normal）")
    void unknownSeverityDefaultRoute() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(null);

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("default", result.channel());
        assertEquals("normal", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("前端 SCM 配置开启 P3 时允许通知")
    void scmConfigCanEnableP3Notify() {
        notificationConfig.getErrorAlertRoutes().put("P3",
                new ReviewConfig.ErrorAlertRouteConfig(true, "default", "normal"));
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P3.getCode());

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("default", result.channel());
        assertEquals("normal", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("前端 SCM 配置可覆盖 P0 通道")
    void scmConfigCanOverrideP0Channel() {
        notificationConfig.getErrorAlertRoutes().put("P0",
                new ReviewConfig.ErrorAlertRouteConfig(true, "dingtalk", "urgent"));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("dingtalk", result.channel());
        assertEquals("urgent", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("前端 SCM 配置可抑制 P1")
    void scmConfigCanSuppressP1() {
        notificationConfig.getErrorAlertRoutes().put("P1",
                new ReviewConfig.ErrorAlertRouteConfig(false, "critical", "urgent"));
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P1.getCode());

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertFalse(result.shouldNotify());
        assertEquals("critical", result.channel());
    }

    @Test
    @DisplayName("SCM 配置缺少对应严重度时走代码默认路由兜底")
    void missingScmSeverityFallsBackToDefault() {
        notificationConfig.getErrorAlertRoutes().remove("P0");
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertEquals("critical", result.channel());
        assertEquals("urgent", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("SCM 配置 priority=suppress 时 shouldNotify=false")
    void ruleSuppressDisablesNotify() {
        notificationConfig.getErrorAlertRoutes().put("P3",
                new ReviewConfig.ErrorAlertRouteConfig(true, "default", "suppress"));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P3.getCode());

        NotificationRouter.RouteResult result = router.route(event, notificationConfig);

        assertFalse(result.shouldNotify());
    }
}
