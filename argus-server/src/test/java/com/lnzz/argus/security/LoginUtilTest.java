package com.lnzz.argus.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoginUtil - 当前登录用户工具")
class LoginUtilTest {

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("无登录态时返回系统和匿名兜底操作者")
    void returnsFallbackOperatorWhenContextMissing() {
        assertTrue(LoginUtil.currentUser().isEmpty());
        assertNull(LoginUtil.currentUserIdOrNull());
        assertEquals(LoginUtil.SYSTEM_OPERATOR, LoginUtil.currentUsernameOrSystem());
        assertEquals(LoginUtil.ANONYMOUS_OPERATOR, LoginUtil.currentUsernameOrAnonymous());
    }

    @Test
    @DisplayName("有登录态时返回当前用户信息")
    void returnsCurrentUserFromThreadContext() {
        CurrentUserContext.set(new CurrentUser(
                1001L,
                "admin",
                "系统管理员",
                "127.0.0.1",
                "JUnit",
                2001L,
                List.of("SUPER_ADMIN")));

        assertEquals(1001L, LoginUtil.currentUserIdOrNull());
        assertEquals("admin", LoginUtil.currentUsernameOrSystem());
        assertEquals("admin", LoginUtil.currentUsernameOrAnonymous());
    }
}
