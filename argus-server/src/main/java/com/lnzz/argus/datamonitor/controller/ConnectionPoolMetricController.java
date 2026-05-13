package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricRequest;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 连接池指标内部接入 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/data-monitor/pools")
@RequiredArgsConstructor
public class ConnectionPoolMetricController {

    @Value("${argus.internal.token:argus-internal-token-change-me}")
    private String internalToken;

    private final ConnectionPoolMetricService connectionPoolMetricService;

    @PostMapping("/metrics")
    public Result<PoolMetricResponse> ingest(@RequestHeader("X-Argus-Token") String token,
                                             @RequestBody PoolMetricRequest request) {
        validateToken(token);
        return Result.success("accepted", connectionPoolMetricService.ingest(request));
    }

    private void validateToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            log.warn("连接池指标内部 API Token 校验失败");
            throw new BizException(ResultCode.UNAUTHORIZED, "内部 API Token 无效");
        }
    }
}
