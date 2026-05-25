package com.lnzz.argus.error.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.error.entity.ErrorAnalysisTask;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.model.ErrorEventPageRequest;
import com.lnzz.argus.error.service.ErrorManagementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误诊断管理台 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/errors")
@RequiredArgsConstructor
public class ErrorManagementController {

    private final ErrorManagementService errorManagementService;

    /**
     * 分页查询错误事件。
     *
     * @param request 错误事件分页查询请求
     * @return 错误事件分页结果
     */
    @PostMapping("/page")
    public Result<Map<String, Object>> listErrors(@RequestBody(required = false) ErrorEventPageRequest request) {
        ErrorEventPageRequest safeRequest = request == null ? new ErrorEventPageRequest() : request;
        Page<ErrorEvent> page = errorManagementService.queryEvents(
                safeRequest.normalizedPageNo(),
                safeRequest.normalizedPageSize(),
                safeRequest.getAppName(),
                safeRequest.getEnvironment(),
                safeRequest.getSeverity(),
                safeRequest.getStatus(),
                safeRequest.getKeyword());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNo", page.getCurrent());
        data.put("pageSize", page.getSize());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getErrorDetail(@PathVariable Long id) {
        return Result.success(errorManagementService.getDetail(id));
    }

    @GetMapping("/{id}/analysis-tasks")
    public Result<List<ErrorAnalysisTask>> listAnalysisTasks(@PathVariable Long id) {
        return Result.success(errorManagementService.listAnalysisTasks(id));
    }

    @GetMapping("/fingerprints/{fingerprint}")
    public Result<List<ErrorEvent>> listByFingerprint(@PathVariable String fingerprint) {
        return Result.success(errorManagementService.listByFingerprint(fingerprint));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(errorManagementService.getStats());
    }

    @PostMapping("/{id}/analyze")
    public Result<Map<String, Object>> analyze(@PathVariable Long id) {
        return Result.success("分析任务已提交", errorManagementService.analyze(id, false));
    }

    @PostMapping("/{id}/retry")
    public Result<Map<String, Object>> retryAnalyze(@PathVariable Long id) {
        return Result.success("重试分析任务已提交", errorManagementService.analyze(id, true));
    }

    @PostMapping("/{id}/retry-notify")
    public Result<Map<String, Object>> retryNotify(@PathVariable Long id) {
        return Result.success("重发通知任务已提交", errorManagementService.retryNotify(id));
    }

    @PostMapping("/{id}/ignore")
    public Result<ErrorEvent> ignore(@PathVariable Long id,
                                     @RequestBody(required = false) ManualActionRequest request) {
        ManualActionRequest safeRequest = request != null ? request : new ManualActionRequest();
        return Result.success("错误事件已忽略",
                errorManagementService.ignore(id, safeRequest.getOperator(), safeRequest.getReason()));
    }

    @PostMapping("/{id}/mark-false-positive")
    public Result<ErrorEvent> markFalsePositive(@PathVariable Long id,
                                                @RequestBody(required = false) ManualActionRequest request) {
        ManualActionRequest safeRequest = request != null ? request : new ManualActionRequest();
        return Result.success("错误事件已标记为误报",
                errorManagementService.markFalsePositive(id, safeRequest.getOperator(), safeRequest.getReason()));
    }

    @PostMapping("/{id}/adjust-severity")
    public Result<ErrorEvent> adjustSeverity(@PathVariable Long id,
                                             @RequestBody AdjustSeverityRequest request) {
        return Result.success("严重度已人工调整",
                errorManagementService.adjustSeverity(id, request.getSeverity(), request.getReason()));
    }

    @PostMapping("/{id}/manual-conclusion")
    public Result<Map<String, Object>> manualConclusion(@PathVariable Long id,
                                                        @RequestBody ManualConclusionRequest request) {
        return Result.success("人工结论已保存",
                errorManagementService.manualConclusion(id, request.getRootCause(), request.getSeverity(),
                        request.getFixDescription(), request.getPreventionAdvice()));
    }

    @Data
    public static class ManualActionRequest {
        private String operator;
        private String reason;
    }

    @Data
    public static class AdjustSeverityRequest {
        private String severity;
        private String reason;
    }

    @Data
    public static class ManualConclusionRequest {
        private String rootCause;
        private String severity;
        private String fixDescription;
        private String preventionAdvice;
    }
}
