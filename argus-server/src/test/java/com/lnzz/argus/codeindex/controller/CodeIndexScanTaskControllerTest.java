package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskExecutor;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CodeIndexScanTaskController - 源码索引扫描任务接口")
class CodeIndexScanTaskControllerTest {

    @Test
    @DisplayName("创建 PENDING 扫描任务后应提交异步执行器")
    void createTaskShouldSubmitPendingTask() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskExecutor taskExecutor = mock(CodeIndexScanTaskExecutor.class);
        CodeIndexScanTaskController controller = controller(taskService, taskExecutor);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest();
        CodeIndexScanTaskResDTO responseDTO = taskResponse(CodeIndexConstants.ScanTaskStatus.PENDING);
        when(taskService.createTask(1L, requestDTO)).thenReturn(responseDTO);
        when(taskExecutor.submit(1001L)).thenReturn(true);

        Result<CodeIndexScanTaskResDTO> result = controller.createTask(1L, requestDTO);

        assertEquals("源码索引扫描任务已创建", result.getMessage());
        assertEquals(1001L, result.getData().getTaskId());
        verify(taskService).createTask(1L, requestDTO);
        verify(taskExecutor).submit(1001L);
    }

    @Test
    @DisplayName("创建 REUSED 任务时不应重复提交异步执行器")
    void createTaskShouldNotSubmitReusedTask() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskExecutor taskExecutor = mock(CodeIndexScanTaskExecutor.class);
        CodeIndexScanTaskController controller = controller(taskService, taskExecutor);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest();
        CodeIndexScanTaskResDTO responseDTO = taskResponse(CodeIndexConstants.ScanTaskStatus.REUSED);
        when(taskService.createTask(1L, requestDTO)).thenReturn(responseDTO);

        Result<CodeIndexScanTaskResDTO> result = controller.createTask(1L, requestDTO);

        assertEquals(CodeIndexConstants.ScanTaskStatus.REUSED, result.getData().getTaskStatus());
        verify(taskExecutor, never()).submit(1001L);
    }

    @Test
    @DisplayName("按任务 ID 查询应返回任务进度")
    void getTaskShouldReturnTaskProgress() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskController controller = controller(taskService, mock(CodeIndexScanTaskExecutor.class));
        CodeIndexScanTaskResDTO responseDTO = taskResponse(CodeIndexConstants.ScanTaskStatus.RUNNING);
        when(taskService.getTask(1001L)).thenReturn(responseDTO);

        Result<CodeIndexScanTaskResDTO> result = controller.getTask(1001L);

        assertEquals(0, result.getCode());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, result.getData().getTaskStatus());
        verify(taskService).getTask(1001L);
    }

    @Test
    @DisplayName("查询不存在的扫描任务应抛出 NOT_FOUND")
    void getTaskShouldThrowWhenTaskNotFound() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskController controller = controller(taskService, mock(CodeIndexScanTaskExecutor.class));
        when(taskService.getTask(404L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> controller.getTask(404L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals("源码索引扫描任务不存在: 404", exception.getMessage());
    }

    @Test
    @DisplayName("查询仓库运行中任务为空时应返回成功空数据")
    void getRunningTaskShouldReturnNullWhenAbsent() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskController controller = controller(taskService, mock(CodeIndexScanTaskExecutor.class));
        when(taskService.findRunningTask(1L, "master")).thenReturn(null);

        Result<CodeIndexScanTaskResDTO> result = controller.getRunningTask(1L, "master");

        assertEquals(0, result.getCode());
        assertNull(result.getData());
        verify(taskService).findRunningTask(1L, "master");
    }

    @Test
    @DisplayName("查询仓库运行中任务存在时应返回任务进度")
    void getRunningTaskShouldReturnRunningTask() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskController controller = controller(taskService, mock(CodeIndexScanTaskExecutor.class));
        CodeIndexScanTaskResDTO responseDTO = taskResponse(CodeIndexConstants.ScanTaskStatus.RUNNING);
        when(taskService.findRunningTask(1L, "master")).thenReturn(responseDTO);

        Result<CodeIndexScanTaskResDTO> result = controller.getRunningTask(1L, "master");

        assertEquals(1001L, result.getData().getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, result.getData().getTaskStatus());
        verify(taskService).findRunningTask(1L, "master");
    }

    @Test
    @DisplayName("缺失 SCM 配置 ID 时不应调用服务层")
    void createTaskShouldRejectInvalidScmConfigId() {
        CodeIndexScanTaskService taskService = mock(CodeIndexScanTaskService.class);
        CodeIndexScanTaskExecutor taskExecutor = mock(CodeIndexScanTaskExecutor.class);
        CodeIndexScanTaskController controller = controller(taskService, taskExecutor);

        BizException exception = assertThrows(BizException.class,
                () -> controller.createTask(0L, createRequest()));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(taskService, taskExecutor);
    }

    @Test
    @DisplayName("创建扫描任务请求 DTO 应覆盖异步扫描入参")
    void createTaskRequestShouldExposeAsyncScanFields() {
        CodeIndexScanTaskCreateReqDTO requestDTO = new CodeIndexScanTaskCreateReqDTO();

        requestDTO.setBranchName("master");
        requestDTO.setCommitSha("abc123");
        requestDTO.setScanType("FULL");
        requestDTO.setForceRebuild(false);
        requestDTO.setReason("SCM 配置页手动刷新");

        assertEquals("master", requestDTO.getBranchName());
        assertEquals("abc123", requestDTO.getCommitSha());
        assertEquals("FULL", requestDTO.getScanType());
        assertEquals(false, requestDTO.getForceRebuild());
        assertEquals("SCM 配置页手动刷新", requestDTO.getReason());
    }

    @Test
    @DisplayName("扫描任务响应 DTO 应覆盖阶段进度和结果索引字段")
    void taskResponseShouldExposeProgressAndResultFields() {
        CodeIndexScanTaskResDTO response = new CodeIndexScanTaskResDTO();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 30);

        response.setTaskId(1001L);
        response.setTaskNo("CI-20260525-1001");
        response.setTaskStatus("RUNNING");
        response.setScanStage("JAVA_PARSING");
        response.setProgressPercent(66);
        response.setStageMessage("正在解析 Java 文件");
        response.setLoadedFileCount(200);
        response.setTotalJavaFileCount(180);
        response.setParsedFileCount(120);
        response.setFailedFileCount(2);
        response.setClassCount(400);
        response.setPackageCount(90);
        response.setWarningCount(3);
        response.setResultIndexId(10L);
        response.setReusedIndexId(9L);
        response.setLatestErrorMessage("parse warning");
        response.setStartedAt(now.minusMinutes(3));
        response.setFinishedAt(now);
        response.setLastHeartbeatAt(now.minusSeconds(10));

        assertEquals(1001L, response.getTaskId());
        assertEquals("CI-20260525-1001", response.getTaskNo());
        assertEquals("RUNNING", response.getTaskStatus());
        assertEquals("JAVA_PARSING", response.getScanStage());
        assertEquals(66, response.getProgressPercent());
        assertEquals("正在解析 Java 文件", response.getStageMessage());
        assertEquals(200, response.getLoadedFileCount());
        assertEquals(180, response.getTotalJavaFileCount());
        assertEquals(120, response.getParsedFileCount());
        assertEquals(2, response.getFailedFileCount());
        assertEquals(400, response.getClassCount());
        assertEquals(90, response.getPackageCount());
        assertEquals(3, response.getWarningCount());
        assertEquals(10L, response.getResultIndexId());
        assertEquals(9L, response.getReusedIndexId());
        assertEquals("parse warning", response.getLatestErrorMessage());
        assertEquals(now.minusMinutes(3), response.getStartedAt());
        assertEquals(now, response.getFinishedAt());
        assertEquals(now.minusSeconds(10), response.getLastHeartbeatAt());
    }

    private CodeIndexScanTaskController controller(CodeIndexScanTaskService taskService,
                                                   CodeIndexScanTaskExecutor taskExecutor) {
        return new CodeIndexScanTaskController(taskService, taskExecutor);
    }

    private CodeIndexScanTaskCreateReqDTO createRequest() {
        CodeIndexScanTaskCreateReqDTO requestDTO = new CodeIndexScanTaskCreateReqDTO();
        requestDTO.setBranchName("master");
        requestDTO.setCommitSha("abc123");
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        requestDTO.setForceRebuild(false);
        requestDTO.setReason("SCM 配置页手动刷新");
        return requestDTO;
    }

    private CodeIndexScanTaskResDTO taskResponse(String taskStatus) {
        CodeIndexScanTaskResDTO responseDTO = new CodeIndexScanTaskResDTO();
        responseDTO.setTaskId(1001L);
        responseDTO.setTaskNo("CI-20260525-1001");
        responseDTO.setTaskStatus(taskStatus);
        responseDTO.setScanStage(CodeIndexConstants.ScanStage.WAITING);
        responseDTO.setProgressPercent(0);
        responseDTO.setMessage("源码索引扫描任务已创建");
        return responseDTO;
    }
}
