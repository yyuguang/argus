package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogIngestResult;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogPushRequest;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogRawPushRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * slow log 内部接入 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/internal/data-monitor/slow-logs")
@RequiredArgsConstructor
public class SlowLogIngestController {

    private final SlowLogIngestService slowLogIngestService;

    @PostMapping
    public Result<SlowLogIngestResult> ingest(@RequestBody SlowLogPushRequest request) {
        return Result.success(slowLogIngestService.ingest(request));
    }

    @PostMapping("/raw")
    public Result<SlowLogIngestResult> ingestRaw(@RequestBody SlowLogRawPushRequest request) {
        return Result.success(slowLogIngestService.ingestRaw(request));
    }
}
