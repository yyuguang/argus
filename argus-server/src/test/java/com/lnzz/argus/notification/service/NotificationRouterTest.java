package com.lnzz.argus.notification.service;

import com.lnzz.argus.config.NotificationProperties;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.parse.SeverityLevel;
import com.lnzz.argus.error.parse.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationRouter - 通知路由")
class NotificationRouterTest {

    private NotificationProperties properties;
    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setDefaultChannel("wechat");

        NotificationProperties.SilenceConfig silence = new NotificationProperties.SilenceConfig();
        silence.setAlwaysNotifyP0P1(true);
        silence.setFingerprintInterval(300);
        silence.setP3Interval(3600);
        silence.setGlobalMaxPerHour(30);
        properties.setSilence(silence);

        properties.setRetry(new NotificationProperties.RetryConfig());

        router = new NotificationRouter(properties);
    }

    @Test
    @DisplayName("P0 → channel=critical, priority=urgent, shouldNotify=true")
    void p0RouteToCritical() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());
        event.setErrorType("NULL_POINTER");

        NotificationRouter.RouteResult result = router.route(event);

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

        NotificationRouter.RouteResult result = router.route(event);

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

        NotificationRouter.RouteResult result = router.route(event);

        assertEquals("wechat", result.channel());
        assertEquals("normal", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("P3 → channel=default, priority=low, shouldNotify=false")
    void p3RouteSuppressed() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P3.getCode());
        event.setErrorType("BIZ_EXCEPTION");

        NotificationRouter.RouteResult result = router.route(event);

        assertEquals("wechat", result.channel());
        assertEquals("low", result.priority());
        assertFalse(result.shouldNotify());
    }

    @Test
    @DisplayName("未知严重度 → 默认路由（normal）")
    void unknownSeverityDefaultRoute() {
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(null);

        NotificationRouter.RouteResult result = router.route(event);

        assertEquals("wechat", result.channel());
        assertEquals("normal", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("全局禁用通知 → SUPPRESS")
    void globallyDisabledReturnsSuppress() {
        properties.setEnabled(false);
        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());

        NotificationRouter.RouteResult result = router.route(event);

        assertFalse(result.shouldNotify());
        assertEquals("suppress", result.priority());
    }

    @Test
    @DisplayName("精确路由规则匹配: severity=P0 → 自定义 channel")
    void exactRuleMatch() {
        NotificationProperties.RouteRule rule = new NotificationProperties.RouteRule();
        rule.setSeverity("P0");
        rule.setChannel("dingtalk");
        rule.setPriority("urgent");
        properties.setRouteRules(List.of(rule));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());

        NotificationRouter.RouteResult result = router.route(event);

        assertEquals("dingtalk", result.channel());
        assertEquals("urgent", result.priority());
        assertTrue(result.shouldNotify());
    }

    @Test
    @DisplayName("规则匹配: severity + sourceType 组合")
    void ruleMatchWithSourceType() {
        NotificationProperties.RouteRule rule = new NotificationProperties.RouteRule();
        rule.setSeverity("P2");
        rule.setSourceType("NGINX");
        rule.setChannel("feishu");
        rule.setPriority("normal");
        properties.setRouteRules(List.of(rule));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P2.getCode());
        event.setSourceType(SourceType.NGINX.getCode());

        NotificationRouter.RouteResult result = router.route(event);

        assertEquals("feishu", result.channel());
    }

    @Test
    @DisplayName("规则不匹配时走默认路由")
    void ruleNotMatchFallsBackToDefault() {
        NotificationProperties.RouteRule rule = new NotificationProperties.RouteRule();
        rule.setSeverity("P0");
        rule.setSourceType("NGINX");
        rule.setChannel("dingtalk");
        properties.setRouteRules(List.of(rule));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P0.getCode());
        event.setSourceType(SourceType.AGENT.getCode());  // 不匹配 sourceType

        NotificationRouter.RouteResult result = router.route(event);

        // 走默认路由：P0 → critical/urgent
        assertEquals("critical", result.channel());
    }

    @Test
    @DisplayName("规则 priority=suppress 时 shouldNotify=false")
    void ruleSuppressDisablesNotify() {
        NotificationProperties.RouteRule rule = new NotificationProperties.RouteRule();
        rule.setSeverity("P3");
        rule.setPriority("suppress");
        properties.setRouteRules(List.of(rule));

        ErrorEvent event = new ErrorEvent();
        event.setSeverity(SeverityLevel.P3.getCode());

        NotificationRouter.RouteResult result = router.route(event);

        assertFalse(result.shouldNotify());
    }
}
