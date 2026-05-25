package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricRequest;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final ConnectionPoolMetricService connectionPoolMetricService;

    @PostMapping("/metrics")
    public Result<PoolMetricResponse> ingest(@RequestBody PoolMetricRequest request) {
        return Result.success("accepted", connectionPoolMetricService.ingest(request));
    }
}
