package com.lnzz.argus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InternalTokenAuthenticationFilter - 内部接口 Spring Security Token 认证")
class InternalTokenAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("内部 Token 正确时放行请求")
    void continuesWhenInternalTokenValid() throws Exception {
        InternalTokenAuthenticationFilter filter = filter("secret");
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Argus-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("内部 Token 错误时写回统一鉴权失败结构")
    void rejectsWhenInternalTokenInvalid() throws Exception {
        InternalTokenAuthenticationFilter filter = filter("secret");
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Argus-Token", "bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertTrue(response.getContentAsString().contains("\"code\":10003"));
    }

    private InternalTokenAuthenticationFilter filter(String internalToken) {
        InternalTokenAuthenticationFilter filter = new InternalTokenAuthenticationFilter(
                new ArgusSecurityResponseWriter(new ObjectMapper()));
        ReflectionTestUtils.setField(filter, "internalToken", internalToken);
        return filter;
    }

    private MockHttpServletRequest internalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/error-logs");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
