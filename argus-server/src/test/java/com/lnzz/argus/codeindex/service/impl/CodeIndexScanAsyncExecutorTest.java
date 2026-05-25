package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import com.lnzz.argus.codeindex.dao.mapper.CodeIndexScanTaskMapper;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexScanService;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskExecutor;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.CodeIndexProperties;
import com.lnzz.argus.codeindex.support.CodeIndexScanExecutionContext;
import com.lnzz.argus.codeindex.support.CodeIndexScanProgressCallback;
import com.lnzz.argus.codeindex.support.CodeIndexScanProgressReporter;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CodeIndexScanAsyncExecutor - 源码索引异步扫描执行器")
class CodeIndexScanAsyncExecutorTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUpContextRunner() {
        contextRunner = new ApplicationContextRunner()
                .withBean(CodeIndexScanTaskMapper.class, () -> mock(CodeIndexScanTaskMapper.class))
                .withBean(ScmConfigService.class, () -> mock(ScmConfigService.class))
                .withBean(CodeIndexScanService.class, () -> mock(CodeIndexScanService.class))
                .withBean(CodeIndexScanProgressCallback.class, () -> mock(CodeIndexScanProgressCallback.class))
                .withBean(CodeIndexProperties.class, CodeIndexProperties::new)
                .withBean(CodeIndexScanAsyncExecutor.class);
    }

    @Test
    @DisplayName("Spring 容器应能创建生产异步执行器 Bean")
    void springContextShouldCreateProductionExecutorBean() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(CodeIndexScanAsyncExecutor.class);
            assertThat(context).hasSingleBean(CodeIndexScanTaskExecutor.class);
        });
    }

    @Test
    @DisplayName("提交 PENDING 任务应标记 RUNNING 并通过进度回调写入成功终态")
    void submitShouldRunPendingTaskAndReportSuccess() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper, new CodeIndexProperties(), () -> 1_000L);
        CodeIndexScanAsyncExecutor executor = executor(mapper, scmConfigService, scanService, reporter);
        when(mapper.selectById(1001L)).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.PENDING));
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);
        when(scanService.scanFull(any(ScmConfig.class), any(CodeIndexScanReqDTO.class),
                any(CodeIndexScanExecutionContext.class))).thenAnswer(invocation -> {
                    CodeIndexScanExecutionContext context = invocation.getArgument(2);
                    context.getProgressCallback().onSuccess(context.getTaskId(), 900L, 12, 5, 1);
                    return summary(CodeIndexConstants.ScanStatus.SUCCESS, 900L);
                });

        assertTrue(executor.submit(1001L));

        ArgumentCaptor<CodeIndexScanTask> updateCaptor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).selectById(1001L);
        verify(scanService).scanFull(any(ScmConfig.class), any(CodeIndexScanReqDTO.class),
                any(CodeIndexScanExecutionContext.class));
        verify(mapper, org.mockito.Mockito.atLeast(2)).updateById(updateCaptor.capture());
        List<CodeIndexScanTask> updates = updateCaptor.getAllValues();
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, updates.get(0).getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.SCM_READING, updates.get(0).getScanStage());
        assertNotNull(updates.get(0).getStartedAt());
        CodeIndexScanTask successUpdate = updates.get(updates.size() - 1);
        assertEquals(CodeIndexConstants.ScanTaskStatus.SUCCESS, successUpdate.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.COMPLETED, successUpdate.getScanStage());
        assertEquals(900L, successUpdate.getResultIndexId());
        assertEquals(100, successUpdate.getProgressPercent());
    }

    @Test
    @DisplayName("扫描服务抛异常时应捕获并写入 FAILED 终态")
    void submitShouldMarkFailedWhenScanThrowsException() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper, new CodeIndexProperties(), () -> 1_000L);
        CodeIndexScanAsyncExecutor executor = executor(mapper, scmConfigService, scanService, reporter);
        when(mapper.selectById(1001L)).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.PENDING));
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);
        when(scanService.scanFull(any(ScmConfig.class), any(CodeIndexScanReqDTO.class),
                any(CodeIndexScanExecutionContext.class))).thenThrow(new IllegalStateException("SCM 读取失败"));

        assertTrue(executor.submit(1001L));

        ArgumentCaptor<CodeIndexScanTask> updateCaptor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper, org.mockito.Mockito.atLeast(2)).updateById(updateCaptor.capture());
        CodeIndexScanTask failedUpdate = updateCaptor.getAllValues().get(updateCaptor.getAllValues().size() - 1);
        assertEquals(CodeIndexConstants.ScanTaskStatus.FAILED, failedUpdate.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.FAILED, failedUpdate.getScanStage());
        assertEquals("SCM 读取失败", failedUpdate.getLatestErrorMessage());
        assertNotNull(failedUpdate.getFinishedAt());
    }

    @Test
    @DisplayName("扫描服务返回失败摘要时应写入 FAILED 终态")
    void submitShouldMarkFailedWhenScanReturnsFailedSummary() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper, new CodeIndexProperties(), () -> 1_000L);
        CodeIndexScanAsyncExecutor executor = executor(mapper, scmConfigService, scanService, reporter);
        CodeIndexSummaryResDTO failedSummary = summary(CodeIndexConstants.ScanStatus.FAILED, null);
        failedSummary.setLatestErrorMessage("未读取到可扫描文件");
        when(mapper.selectById(1001L)).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.PENDING));
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);
        when(scanService.scanFull(any(ScmConfig.class), any(CodeIndexScanReqDTO.class),
                any(CodeIndexScanExecutionContext.class))).thenReturn(failedSummary);

        assertTrue(executor.submit(1001L));

        ArgumentCaptor<CodeIndexScanTask> updateCaptor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper, org.mockito.Mockito.atLeast(2)).updateById(updateCaptor.capture());
        CodeIndexScanTask failedUpdate = updateCaptor.getAllValues().get(updateCaptor.getAllValues().size() - 1);
        assertEquals(CodeIndexConstants.ScanTaskStatus.FAILED, failedUpdate.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.FAILED, failedUpdate.getScanStage());
        assertEquals("未读取到可扫描文件", failedUpdate.getLatestErrorMessage());
    }

    @Test
    @DisplayName("终态任务重复提交时不应再次执行扫描")
    void submitShouldSkipTerminalTask() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper, new CodeIndexProperties(), () -> 1_000L);
        CodeIndexScanAsyncExecutor executor = executor(mapper, scmConfigService, scanService, reporter);
        when(mapper.selectById(1001L)).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.SUCCESS));

        assertFalse(executor.submit(1001L));

        verify(scanService, never()).scanFull(any(), any(), any());
        verify(mapper, never()).updateById(any(CodeIndexScanTask.class));
    }

    private CodeIndexScanAsyncExecutor executor(CodeIndexScanTaskMapper mapper,
                                                ScmConfigService scmConfigService,
                                                CodeIndexScanService scanService,
                                                CodeIndexScanProgressCallback reporter) {
        return new CodeIndexScanAsyncExecutor(mapper, scmConfigService, scanService,
                reporter, new CodeIndexProperties(), Runnable::run);
    }

    private CodeIndexScanTask scanTask(String taskStatus) {
        CodeIndexScanTask task = new CodeIndexScanTask();
        task.setId(1001L);
        task.setTaskNo("CI-20260525-1001");
        task.setScmConfigId(1L);
        task.setBranchName("master");
        task.setCommitSha("abc123");
        task.setScanType(CodeIndexConstants.ScanType.FULL);
        task.setForceRebuild(false);
        task.setReason("SCM 配置页手动刷新");
        task.setTaskStatus(taskStatus);
        task.setScanStage(CodeIndexConstants.ScanStage.WAITING);
        task.setProgressPercent(0);
        task.setIsDeleted(false);
        task.setCreateTime(LocalDateTime.of(2026, 5, 25, 10, 0));
        return task;
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        scmConfig.setProjectId(10086L);
        scmConfig.setRepoName("argus");
        return scmConfig;
    }

    private CodeIndexSummaryResDTO summary(String scanStatus, Long indexId) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setScanStatus(scanStatus);
        response.setIndexId(indexId);
        return response;
    }
}
