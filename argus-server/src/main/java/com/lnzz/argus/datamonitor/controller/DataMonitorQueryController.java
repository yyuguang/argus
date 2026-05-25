package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.model.DataMonitorDashboardResponse;
import com.lnzz.argus.datamonitor.model.LogQualityIssueView;
import com.lnzz.argus.datamonitor.model.PoolRiskView;
import com.lnzz.argus.datamonitor.model.SlowSqlEventView;
import com.lnzz.argus.datamonitor.service.DataMonitorQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据监控工作台查询 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/data-monitor")
@RequiredArgsConstructor
public class DataMonitorQueryController {

    private final DataMonitorQueryService dataMonitorQueryService;

    @GetMapping("/dashboard")
    public Result<DataMonitorDashboardResponse> dashboard(@RequestParam(required = false) String appName,
                                                          @RequestParam(required = false) String environment,
                                                          @RequestParam(required = false) String window) {
        return Result.success(dataMonitorQueryService.getDashboard(appName, environment, window));
    }

    @GetMapping("/slow-sql")
    public Result<List<SlowSqlEventView>> slowSql(@RequestParam(required = false) String appName,
                                                  @RequestParam(required = false) String environment,
                                                  @RequestParam(required = false) String window) {
        return Result.success(dataMonitorQueryService.listSlowSql(appName, environment, window));
    }

    @GetMapping("/pools/risks")
    public Result<List<PoolRiskView>> poolRisks(@RequestParam(required = false) String appName,
                                                @RequestParam(required = false) String environment,
                                                @RequestParam(required = false) String window) {
        return Result.success(dataMonitorQueryService.listPoolRisks(appName, environment, window));
    }

    @GetMapping("/log-quality/issues")
    public Result<List<LogQualityIssueView>> logQualityIssues(@RequestParam(required = false) String appName,
                                                              @RequestParam(required = false) String environment,
                                                              @RequestParam(required = false) String window) {
        return Result.success(dataMonitorQueryService.listLogQualityIssues(appName, environment, window));
    }
}
