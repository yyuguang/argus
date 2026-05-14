package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.entity.DataMonitorReport;
import com.lnzz.argus.datamonitor.service.DataMonitorAlertService;
import com.lnzz.argus.datamonitor.service.DataMonitorAlertService.DataMonitorAlertResult;
import com.lnzz.argus.datamonitor.service.DataMonitorReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据监控报告与告警内部 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/internal/data-monitor")
@RequiredArgsConstructor
public class DataMonitorReportController {

    private final DataMonitorReportService reportService;
    private final DataMonitorAlertService alertService;

    @PostMapping("/reports/daily")
    public Result<List<DataMonitorReport>> generateDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(reportService.generateDaily(date));
    }

    @PostMapping("/alerts")
    public Result<List<DataMonitorAlertResult>> alertPending() {
        return Result.success(alertService.alertPending());
    }
}
