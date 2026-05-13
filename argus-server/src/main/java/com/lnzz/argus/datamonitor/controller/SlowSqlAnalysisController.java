package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowSqlAnalysisService;
import com.lnzz.argus.datamonitor.service.SlowSqlAnalysisService.SlowSqlAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 慢 SQL 根因分析内部 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/internal/data-monitor/slow-sql")
@RequiredArgsConstructor
public class SlowSqlAnalysisController {

    private final SlowSqlAnalysisService slowSqlAnalysisService;

    @PostMapping("/analyze")
    public Result<List<SlowSqlAnalysisResult>> analyzePending(@RequestParam(required = false) Integer limit) {
        return Result.success(slowSqlAnalysisService.analyzePending(limit));
    }

    @PostMapping("/events/{eventId}/analyze")
    public Result<SlowSqlAnalysisResult> analyzeEvent(@PathVariable Long eventId) {
        return Result.success(slowSqlAnalysisService.analyzeEvent(eventId));
    }
}
