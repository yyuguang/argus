package com.lnzz.argus.error.controller;

import com.lnzz.argus.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Phase2SelfTestController - Phase 2 全链路自测故障接口")
class Phase2SelfTestControllerTest {

    @Test
    @DisplayName("内部 Token 正确时触发可控空指针异常")
    void triggerNullPointerBugThrowsNpeWithValidToken() {
        Phase2SelfTestController controller = new Phase2SelfTestController();
        ReflectionTestUtils.setField(controller, "internalToken", "token");

        assertThrows(NullPointerException.class,
                () -> controller.triggerNullPointerBug("token", "phase2-chain-test"));
    }

    @Test
    @DisplayName("内部 Token 错误时拒绝触发自测故障")
    void triggerNullPointerBugRejectsInvalidToken() {
        Phase2SelfTestController controller = new Phase2SelfTestController();
        ReflectionTestUtils.setField(controller, "internalToken", "token");

        assertThrows(BizException.class,
                () -> controller.triggerNullPointerBug("bad-token", "phase2-chain-test"));
    }
}
