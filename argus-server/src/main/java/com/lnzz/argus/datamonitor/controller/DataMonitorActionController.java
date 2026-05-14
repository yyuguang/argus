package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService.ActionRequest;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService.SlowSqlConfirmRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据监控人工处理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/data-monitor")
@RequiredArgsConstructor
public class DataMonitorActionController {

    private final DataMonitorActionService actionService;

    @PostMapping("/slow-sql/{id}/ignore")
    public Result<SlowSqlEvent> ignoreSlowSql(@PathVariable Long id, @RequestBody ActionRequest request) {
        return Result.success(actionService.ignoreSlowSql(id, request));
    }

    @PostMapping("/slow-sql/{id}/confirm")
    public Result<SlowSqlEvent> confirmSlowSql(@PathVariable Long id, @RequestBody SlowSqlConfirmRequest request) {
        return Result.success(actionService.confirmSlowSql(id, request));
    }

    @PostMapping("/log-quality/issues/{id}/ignore")
    public Result<LogQualityIssue> ignoreLogQualityIssue(@PathVariable Long id, @RequestBody ActionRequest request) {
        return Result.success(actionService.ignoreLogQualityIssue(id, request));
    }
}
