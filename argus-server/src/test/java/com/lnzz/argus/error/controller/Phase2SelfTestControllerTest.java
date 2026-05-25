package com.lnzz.argus.error.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Phase2SelfTestController - Phase 2 全链路自测故障接口")
class Phase2SelfTestControllerTest {

    @Test
    @DisplayName("内部 Token 正确时触发可控空指针异常")
    void triggerNullPointerBugThrowsNpeWithValidToken() {
        Phase2SelfTestController controller = new Phase2SelfTestController();

        assertThrows(NullPointerException.class,
                () -> controller.triggerNullPointerBug("phase2-chain-test"));
    }
}
