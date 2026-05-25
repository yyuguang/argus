package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskExecutor;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @classname: CodeIndexScanTaskController
 * @author: Fantasy
 * @date: 2026/05/25 10:40
 * @description: 源码索引扫描任务 API，提供异步扫描任务创建、任务进度查询和运行中任务恢复入口。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/code-indexes")
@RequiredArgsConstructor
public class CodeIndexScanTaskController {

    private final CodeIndexScanTaskService scanTaskService;
    private final CodeIndexScanTaskExecutor scanTaskExecutor;

    /**
     * 创建源码索引异步扫描任务，并在任务可执行时提交后台执行器。
     *
     * @param scmConfigId SCM 配置 ID
     * @param requestDTO 扫描任务创建请求
     * @return 扫描任务状态与进度响应
     * @author Fantasy
     * @date 2026/05/25 10:40
     */
    @PostMapping("/scm/{scmConfigId}/scan-tasks")
    public Result<CodeIndexScanTaskResDTO> createTask(@PathVariable Long scmConfigId,
                                                      @RequestBody(required = false) CodeIndexScanTaskCreateReqDTO requestDTO) {
        requireId(scmConfigId, "scmConfigId");
        CodeIndexScanTaskCreateReqDTO effectiveRequest = requestDTO == null
                ? new CodeIndexScanTaskCreateReqDTO()
                : requestDTO;
        CodeIndexScanTaskResDTO task = scanTaskService.createTask(scmConfigId, effectiveRequest);
        if (task == null) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "源码索引扫描任务创建失败");
        }
        submitIfPending(task);
        return Result.success(resolveMessage(task, "源码索引扫描任务已创建"), task);
    }

    /**
     * 按任务 ID 查询源码索引扫描任务进度。
     *
     * @param taskId 扫描任务 ID
     * @return 扫描任务状态与进度响应
     * @author Fantasy
     * @date 2026/05/25 10:40
     */
    @GetMapping("/scan-tasks/{taskId}")
    public Result<CodeIndexScanTaskResDTO> getTask(@PathVariable Long taskId) {
        requireId(taskId, "taskId");
        CodeIndexScanTaskResDTO task = scanTaskService.getTask(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "源码索引扫描任务不存在: " + taskId);
        }
        return Result.success(task);
    }

    /**
     * 查询仓库指定分支最近的运行中源码索引扫描任务，用于页面恢复轮询。
     *
     * @param scmConfigId SCM 配置 ID
     * @param branchName 分支名称，未传时使用默认分支
     * @return 运行中扫描任务；不存在时 data 为 null
     * @author Fantasy
     * @date 2026/05/25 10:40
     */
    @GetMapping("/scm/{scmConfigId}/scan-tasks/running")
    public Result<CodeIndexScanTaskResDTO> getRunningTask(@PathVariable Long scmConfigId,
                                                          @RequestParam(required = false) String branchName) {
        requireId(scmConfigId, "scmConfigId");
        String effectiveBranchName = hasText(branchName) ? branchName.trim() : CodeIndexConstants.DEFAULT_BRANCH;
        return Result.success(scanTaskService.findRunningTask(scmConfigId, effectiveBranchName));
    }

    private void submitIfPending(CodeIndexScanTaskResDTO task) {
        if (!CodeIndexConstants.ScanTaskStatus.PENDING.equals(task.getTaskStatus())) {
            return;
        }
        boolean submitted = scanTaskExecutor.submit(task.getTaskId());
        if (!submitted) {
            log.warn("源码索引扫描任务创建后提交失败, taskId={}, status={}",
                    task.getTaskId(), task.getTaskStatus());
            return;
        }
        log.info("源码索引扫描任务创建后已提交异步执行, taskId={}", task.getTaskId());
    }

    private String resolveMessage(CodeIndexScanTaskResDTO task, String fallback) {
        return hasText(task.getMessage()) ? task.getMessage() : fallback;
    }

    private void requireId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, fieldName + " 不能为空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
