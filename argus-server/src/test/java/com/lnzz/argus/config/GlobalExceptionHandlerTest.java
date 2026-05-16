package com.lnzz.argus.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GlobalExceptionHandler - 全局异常日志")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("兜底异常返回 errorId 并记录完整 Throwable")
    void fallbackExceptionReturnsErrorIdAndLogsThrowable() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/demo");
            request.setQueryString("id=1");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("User-Agent", "JUnit");
            request.addHeader("X-Trace-Id", "trace-001");
            RuntimeException exception = new RuntimeException(
                    "outer failure",
                    new IllegalStateException("root failure"));

            Result<Void> result = handler.handleException(exception, request);

            assertEquals(ResultCode.SYSTEM_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("errorId="));

            List<ILoggingEvent> events = appender.list;
            assertEquals(1, events.size());
            ILoggingEvent event = events.get(0);
            assertEquals(Level.ERROR, event.getLevel());
            assertNotNull(event.getThrowableProxy());
            assertEquals(RuntimeException.class.getName(), event.getThrowableProxy().getClassName());
            assertTrue(event.getFormattedMessage().contains("GET /api/v1/demo?id=1"));
            assertTrue(event.getFormattedMessage().contains("traceId=trace-001"));
            assertTrue(event.getFormattedMessage().contains("rootCauseType=java.lang.IllegalStateException"));
            assertTrue(event.getFormattedMessage().contains("rootCauseMessage=root failure"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
