package com.lnzz.argus.error.controller;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2 日志诊断自测接口。
 *
 * <p>该接口只用于本地/联调验证“服务异常 -> 监测脚本捕获 -> 错误日志接入 -> AI 分析 -> 企业微信通知”
 * 全链路。必须携带内部 Token 才能触发，避免普通页面误点制造异常。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/self-test/phase2")
public class Phase2SelfTestController {

    @Value("${argus.internal.token:argus-internal-token-change-me}")
    private String internalToken;

    @GetMapping("/bug")
    public void triggerNullPointerBug(@RequestHeader("X-Argus-Token") String token,
                                      @RequestParam(defaultValue = "phase2-chain-test") String businessKey) {
        validateToken(token);
        log.info("Phase2 自测故障触发: businessKey={}", businessKey);
        String nullableMarker = null;
        nullableMarker.length();
    }

    private void validateToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            log.warn("Phase2 自测故障接口 Token 校验失败");
            throw new BizException(ResultCode.UNAUTHORIZED, "内部 API Token 无效");
        }
    }
}
