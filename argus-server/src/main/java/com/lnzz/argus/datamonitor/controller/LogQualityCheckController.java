package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService.LogQualityCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接口日志表质量巡检内部 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/internal/data-monitor/log-quality")
@RequiredArgsConstructor
public class LogQualityCheckController {

    private final LogQualityCheckService checkService;

    @PostMapping("/check")
    public Result<List<LogQualityCheckResponse>> checkAllEnabled() {
        return Result.success(checkService.checkAllEnabled());
    }

    @PostMapping("/configs/{configId}/check")
    public Result<LogQualityCheckResponse> checkConfig(@PathVariable Long configId) {
        return Result.success(checkService.checkConfig(configId));
    }
}
