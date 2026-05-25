package com.lnzz.argus.codeindex.service.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import com.lnzz.argus.codeindex.dao.mapper.CodeIndexScanTaskMapper;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.CodeIndexEnums;
import com.lnzz.argus.codeindex.support.CodeIndexProperties;
import com.lnzz.argus.codeindex.support.CodeIndexScanProgressCallback;
import com.lnzz.argus.codeindex.support.CodeIndexScanProgressReporter;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CodeIndexScanTaskServiceImpl - 源码索引扫描任务持久化模型")
class CodeIndexScanTaskServiceImplTest {

    @Test
    @DisplayName("扫描任务实体应映射扫描任务表并覆盖进度字段")
    void scanTaskEntityShouldMapTaskTableAndProgressFields() {
        CodeIndexScanTask task = new CodeIndexScanTask();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);

        task.setTaskNo("CI-20260525-0001");
        task.setScmConfigId(1L);
        task.setScmProvider("GITLAB");
        task.setScmProjectId("10086");
        task.setRepoName("argus");
        task.setBranchName("master");
        task.setCommitSha("abc123");
        task.setScanType("FULL");
        task.setTriggerType("MANUAL");
        task.setForceRebuild(false);
        task.setTaskStatus("RUNNING");
        task.setScanStage("JAVA_PARSING");
        task.setProgressPercent(55);
        task.setStageMessage("正在解析 Java 文件");
        task.setLoadedFileCount(120);
        task.setTotalJavaFileCount(100);
        task.setParsedFileCount(55);
        task.setFailedFileCount(1);
        task.setClassCount(300);
        task.setPackageCount(80);
        task.setWarningCount(2);
        task.setResultIndexId(10L);
        task.setReusedIndexId(9L);
        task.setLatestErrorMessage("parse warning");
        task.setRequestedBy("Fantasy");
        task.setReason("SCM 配置页手动刷新");
        task.setStartedAt(now.minusMinutes(1));
        task.setFinishedAt(now);
        task.setLastHeartbeatAt(now);
        task.setIsDeleted(false);
        task.setVersion(1);

        TableName tableName = CodeIndexScanTask.class.getAnnotation(TableName.class);

        assertNotNull(tableName);
        assertEquals("argus_code_index_scan_task", tableName.value());
        assertEquals("CI-20260525-0001", task.getTaskNo());
        assertEquals(1L, task.getScmConfigId());
        assertEquals("GITLAB", task.getScmProvider());
        assertEquals("10086", task.getScmProjectId());
        assertEquals("argus", task.getRepoName());
        assertEquals("master", task.getBranchName());
        assertEquals("abc123", task.getCommitSha());
        assertEquals("FULL", task.getScanType());
        assertEquals("MANUAL", task.getTriggerType());
        assertEquals(false, task.getForceRebuild());
        assertEquals("RUNNING", task.getTaskStatus());
        assertEquals("JAVA_PARSING", task.getScanStage());
        assertEquals(55, task.getProgressPercent());
        assertEquals("正在解析 Java 文件", task.getStageMessage());
        assertEquals(120, task.getLoadedFileCount());
        assertEquals(100, task.getTotalJavaFileCount());
        assertEquals(55, task.getParsedFileCount());
        assertEquals(1, task.getFailedFileCount());
        assertEquals(300, task.getClassCount());
        assertEquals(80, task.getPackageCount());
        assertEquals(2, task.getWarningCount());
        assertEquals(10L, task.getResultIndexId());
        assertEquals(9L, task.getReusedIndexId());
        assertEquals("parse warning", task.getLatestErrorMessage());
        assertEquals("Fantasy", task.getRequestedBy());
        assertEquals("SCM 配置页手动刷新", task.getReason());
        assertEquals(now.minusMinutes(1), task.getStartedAt());
        assertEquals(now, task.getFinishedAt());
        assertEquals(now, task.getLastHeartbeatAt());
        assertEquals(false, task.getIsDeleted());
        assertEquals(1, task.getVersion());
    }

    @Test
    @DisplayName("扫描任务 Mapper 应继承 MyBatis-Plus BaseMapper")
    void scanTaskMapperShouldExtendBaseMapper() {
        assertTrue(BaseMapper.class.isAssignableFrom(CodeIndexScanTaskMapper.class));
    }

    @Test
    @DisplayName("扫描任务状态枚举应覆盖异步任务状态机")
    void scanTaskStatusEnumsShouldMatchDesign() {
        assertEquals("PENDING", CodeIndexEnums.ScanTaskStatus.PENDING.getCode());
        assertEquals("RUNNING", CodeIndexEnums.ScanTaskStatus.RUNNING.getCode());
        assertEquals("SUCCESS", CodeIndexEnums.ScanTaskStatus.SUCCESS.getCode());
        assertEquals("FAILED", CodeIndexEnums.ScanTaskStatus.FAILED.getCode());
        assertEquals("CANCELED", CodeIndexEnums.ScanTaskStatus.CANCELED.getCode());
        assertEquals("REUSED", CodeIndexEnums.ScanTaskStatus.REUSED.getCode());
        assertTrue(CodeIndexEnums.ScanTaskStatus.SUCCESS.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.FAILED.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.CANCELED.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.REUSED.terminal());
    }

    @Test
    @DisplayName("扫描阶段和进度常量应覆盖任务进度展示")
    void scanStageAndProgressConstantsShouldMatchDesign() {
        assertEquals("WAITING", CodeIndexEnums.ScanStage.WAITING.getCode());
        assertEquals("SCM_READING", CodeIndexEnums.ScanStage.SCM_READING.getCode());
        assertEquals("MODULE_SCANNING", CodeIndexEnums.ScanStage.MODULE_SCANNING.getCode());
        assertEquals("SOURCE_ROOT_DISCOVERING", CodeIndexEnums.ScanStage.SOURCE_ROOT_DISCOVERING.getCode());
        assertEquals("JAVA_PARSING", CodeIndexEnums.ScanStage.JAVA_PARSING.getCode());
        assertEquals("INDEX_AGGREGATING", CodeIndexEnums.ScanStage.INDEX_AGGREGATING.getCode());
        assertEquals("INDEX_PERSISTING", CodeIndexEnums.ScanStage.INDEX_PERSISTING.getCode());
        assertEquals("COMPLETED", CodeIndexEnums.ScanStage.COMPLETED.getCode());
        assertEquals("FAILED", CodeIndexEnums.ScanStage.FAILED.getCode());
        assertEquals(30, CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START);
        assertEquals(80, CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_END);
        assertEquals(500, CodeIndexConstants.ScanTask.DEFAULT_PROGRESS_INTERVAL);
        assertEquals("CI-", CodeIndexConstants.ScanTask.TASK_NO_PREFIX);
    }

    @Test
    @DisplayName("扫描任务触发类型应与管理端异步扫描设计一致")
    void scanTriggerTypeShouldMatchAsyncScanDesign() {
        assertEquals("MANUAL", CodeIndexEnums.ScanTriggerType.MANUAL.getCode());
        assertEquals("WEBHOOK", CodeIndexEnums.ScanTriggerType.WEBHOOK.getCode());
        assertEquals("DEPLOY_CALLBACK", CodeIndexEnums.ScanTriggerType.DEPLOY_CALLBACK.getCode());
        assertEquals("SCHEDULED", CodeIndexEnums.ScanTriggerType.SCHEDULED.getCode());
    }

    @Test
    @DisplayName("源码索引配置应绑定 argus.code-index 并提供保守默认值")
    void codeIndexPropertiesShouldExposeConservativeDefaults() {
        ConfigurationProperties annotation = CodeIndexProperties.class.getAnnotation(ConfigurationProperties.class);
        CodeIndexProperties properties = new CodeIndexProperties();

        assertNotNull(annotation);
        assertEquals("argus.code-index", annotation.prefix());
        assertTrue(properties.isAsyncScanEnabled());
        assertNotNull(properties.getParser());
        assertEquals(false, properties.getParser().isParallelEnabled());
        assertEquals(2, properties.getParser().getParallelism());
        assertEquals(1000, properties.getParser().getQueueSize());
        assertEquals(500, properties.getParser().getProgressInterval());
    }

    @Test
    @DisplayName("按任务 ID 查询应返回扫描任务响应 DTO")
    void getTaskShouldReturnTaskResponse() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        CodeIndexScanTask task = scanTask(CodeIndexConstants.ScanTaskStatus.RUNNING);
        when(mapper.selectById(1001L)).thenReturn(task);

        CodeIndexScanTaskResDTO response = service.getTask(1001L);

        assertNotNull(response);
        assertEquals(1001L, response.getTaskId());
        assertEquals("CI-20260525-1001", response.getTaskNo());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, response.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.JAVA_PARSING, response.getScanStage());
        assertEquals(66, response.getProgressPercent());
        assertEquals(10L, response.getResultIndexId());
        assertEquals(9L, response.getReusedIndexId());
        verify(mapper).selectById(1001L);
    }

    @Test
    @DisplayName("按任务 ID 查询不存在或已删除任务时返回空")
    void getTaskShouldReturnNullWhenMissingOrDeleted() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        CodeIndexScanTask deletedTask = scanTask(CodeIndexConstants.ScanTaskStatus.RUNNING);
        deletedTask.setIsDeleted(true);
        when(mapper.selectById(404L)).thenReturn(null);
        when(mapper.selectById(1001L)).thenReturn(deletedTask);

        assertNull(service.getTask(null));
        assertNull(service.getTask(0L));
        assertNull(service.getTask(404L));
        assertNull(service.getTask(1001L));
    }

    @Test
    @DisplayName("查询运行中任务只返回 PENDING/RUNNING 任务")
    void findRunningTaskShouldReturnPendingOrRunningTask() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        when(mapper.selectOne(any())).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.PENDING));

        CodeIndexScanTaskResDTO response = service.findRunningTask(1L, "master");

        assertNotNull(response);
        assertEquals(CodeIndexConstants.ScanTaskStatus.PENDING, response.getTaskStatus());
        verify(mapper).selectOne(any());
    }

    @Test
    @DisplayName("查询运行中任务应过滤 SUCCESS/FAILED/CANCELED/REUSED 终态")
    void findRunningTaskShouldIgnoreTerminalTask() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        when(mapper.selectOne(any())).thenReturn(scanTask(CodeIndexConstants.ScanTaskStatus.SUCCESS));

        CodeIndexScanTaskResDTO response = service.findRunningTask(1L, "master");

        assertNull(response);
    }

    @Test
    @DisplayName("查询运行中任务参数无效时不访问 Mapper")
    void findRunningTaskShouldSkipInvalidParameters() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);

        assertNull(service.findRunningTask(null, "master"));
        assertNull(service.findRunningTask(1L, ""));
        assertNull(service.findRunningTask(1L, " "));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("普通刷新命中同 commit 成功索引时应创建 REUSED 任务并写入 reusedIndexId")
    void createTaskShouldReuseSuccessfulIndexForNormalRefresh() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanTaskService service = service(mapper, codeIndexService, scmConfigService);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest("master", "abc123", false);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(codeIndexService.getSuccessfulIndexByCommit(1L, "abc123")).thenReturn(successfulIndex(900L));
        when(mapper.insert(any(CodeIndexScanTask.class))).thenAnswer(invocation -> {
            CodeIndexScanTask task = invocation.getArgument(0);
            task.setId(2001L);
            return 1;
        });

        CodeIndexScanTaskResDTO response = service.createTask(1L, requestDTO);

        assertNotNull(response);
        assertEquals(2001L, response.getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.REUSED, response.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.COMPLETED, response.getScanStage());
        assertEquals(100, response.getProgressPercent());
        assertEquals(900L, response.getReusedIndexId());
        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).insert(captor.capture());
        assertEquals(CodeIndexConstants.ScanTaskStatus.REUSED, captor.getValue().getTaskStatus());
        assertEquals(900L, captor.getValue().getReusedIndexId());
        assertEquals("abc123", captor.getValue().getCommitSha());
    }

    @Test
    @DisplayName("强制重建命中同 commit 成功索引时不应复用旧索引")
    void createTaskShouldNotReuseSuccessfulIndexWhenForceRebuild() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanTaskService service = service(mapper, codeIndexService, scmConfigService);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest("master", "abc123", true);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(mapper.insert(any(CodeIndexScanTask.class))).thenAnswer(invocation -> {
            CodeIndexScanTask task = invocation.getArgument(0);
            task.setId(2002L);
            return 1;
        });

        CodeIndexScanTaskResDTO response = service.createTask(1L, requestDTO);

        assertNotNull(response);
        assertEquals(2002L, response.getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.PENDING, response.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.WAITING, response.getScanStage());
        assertEquals(0, response.getProgressPercent());
        assertNull(response.getReusedIndexId());
        verify(codeIndexService, never()).getSuccessfulIndexByCommit(1L, "abc123");
    }

    @Test
    @DisplayName("存在同仓库同分支同 commit 运行中任务时应返回现有任务")
    void createTaskShouldReturnExistingRunningTaskForSameTarget() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanTaskService service = service(mapper, codeIndexService, scmConfigService);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest("master", "abc123", false);
        CodeIndexScanTask runningTask = scanTask(CodeIndexConstants.ScanTaskStatus.RUNNING);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(codeIndexService.getSuccessfulIndexByCommit(1L, "abc123")).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(runningTask);

        CodeIndexScanTaskResDTO response = service.createTask(1L, requestDTO);

        assertNotNull(response);
        assertEquals(1001L, response.getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, response.getTaskStatus());
        verify(mapper, never()).insert(any(CodeIndexScanTask.class));
    }

    @Test
    @DisplayName("存在同仓库同分支同 commit 待执行任务时应返回现有任务")
    void createTaskShouldReturnExistingPendingTaskForSameTarget() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanTaskService service = service(mapper, codeIndexService, scmConfigService);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest("master", "abc123", false);
        CodeIndexScanTask pendingTask = scanTask(CodeIndexConstants.ScanTaskStatus.PENDING);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(codeIndexService.getSuccessfulIndexByCommit(1L, "abc123")).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(pendingTask);

        CodeIndexScanTaskResDTO response = service.createTask(1L, requestDTO);

        assertNotNull(response);
        assertEquals(1001L, response.getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.PENDING, response.getTaskStatus());
        verify(mapper, never()).insert(any(CodeIndexScanTask.class));
    }

    @Test
    @DisplayName("首次创建扫描任务应写入 PENDING/WAITING/0% 默认状态")
    void createTaskShouldInsertPendingTaskForFirstRefresh() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexScanTaskService service = service(mapper, codeIndexService, scmConfigService);
        CodeIndexScanTaskCreateReqDTO requestDTO = createRequest("master", "abc123", false);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig());
        when(codeIndexService.getSuccessfulIndexByCommit(1L, "abc123")).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(CodeIndexScanTask.class))).thenAnswer(invocation -> {
            CodeIndexScanTask task = invocation.getArgument(0);
            task.setId(2003L);
            return 1;
        });

        CodeIndexScanTaskResDTO response = service.createTask(1L, requestDTO);

        assertNotNull(response);
        assertEquals(2003L, response.getTaskId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.PENDING, response.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.WAITING, response.getScanStage());
        assertEquals(0, response.getProgressPercent());
        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).insert(captor.capture());
        CodeIndexScanTask insertedTask = captor.getValue();
        assertTrue(insertedTask.getTaskNo().startsWith(CodeIndexConstants.ScanTask.TASK_NO_PREFIX));
        assertEquals(1L, insertedTask.getScmConfigId());
        assertEquals("master", insertedTask.getBranchName());
        assertEquals("abc123", insertedTask.getCommitSha());
        assertEquals(CodeIndexConstants.ScanTriggerType.MANUAL, insertedTask.getTriggerType());
        assertEquals(CodeIndexConstants.ScanTaskStatus.PENDING, insertedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.WAITING, insertedTask.getScanStage());
        assertEquals(0, insertedTask.getProgressPercent());
        assertEquals(false, insertedTask.getIsDeleted());
        assertEquals(0, insertedTask.getVersion());
    }

    @Test
    @DisplayName("进度 Reporter 阶段开始应按阶段权重写入起始进度")
    void progressReporterShouldUpdateStageStartProgress() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper,
                new CodeIndexProperties(),
                () -> 1_000L);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);

        reporter.onStageStart(1001L, CodeIndexConstants.ScanStage.JAVA_PARSING, "开始解析 Java 文件");

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(1001L, updatedTask.getId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.JAVA_PARSING, updatedTask.getScanStage());
        assertEquals(CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START, updatedTask.getProgressPercent());
        assertEquals("开始解析 Java 文件", updatedTask.getStageMessage());
        assertNotNull(updatedTask.getLastHeartbeatAt());
    }

    @Test
    @DisplayName("进度 Reporter 应按 Java 解析比例折算 30-80 区间并节流写库")
    void progressReporterShouldThrottleJavaParseProgressUpdates() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexProperties properties = new CodeIndexProperties();
        AtomicLong nowMillis = new AtomicLong(1_000L);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper,
                properties,
                nowMillis::get);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);

        reporter.onJavaParseProgress(1001L, 50, 100, 2);
        nowMillis.set(1_100L);
        reporter.onJavaParseProgress(1001L, 60, 100, 2);
        nowMillis.set(1_501L);
        reporter.onJavaParseProgress(1001L, 80, 100, 3);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper, times(2)).updateById(captor.capture());
        CodeIndexScanTask firstUpdate = captor.getAllValues().get(0);
        CodeIndexScanTask secondUpdate = captor.getAllValues().get(1);
        assertEquals(55, firstUpdate.getProgressPercent());
        assertEquals(50, firstUpdate.getParsedFileCount());
        assertEquals(100, firstUpdate.getTotalJavaFileCount());
        assertEquals(2, firstUpdate.getFailedFileCount());
        assertEquals(70, secondUpdate.getProgressPercent());
        assertEquals(80, secondUpdate.getParsedFileCount());
        assertEquals(3, secondUpdate.getFailedFileCount());
    }

    @Test
    @DisplayName("进度 Reporter 成功时应写入 SUCCESS/COMPLETED/100% 和结果索引")
    void progressReporterShouldMarkSuccess() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper,
                new CodeIndexProperties(),
                () -> 1_000L);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);

        reporter.onSuccess(1001L, 900L, 4504, 2166, 1);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(CodeIndexConstants.ScanTaskStatus.SUCCESS, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.COMPLETED, updatedTask.getScanStage());
        assertEquals(100, updatedTask.getProgressPercent());
        assertEquals(900L, updatedTask.getResultIndexId());
        assertEquals(4504, updatedTask.getClassCount());
        assertEquals(2166, updatedTask.getPackageCount());
        assertEquals(1, updatedTask.getWarningCount());
        assertNotNull(updatedTask.getFinishedAt());
    }

    @Test
    @DisplayName("进度 Reporter 失败时应截断错误信息并写入失败终态")
    void progressReporterShouldTruncateFailureMessage() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanProgressCallback reporter = new CodeIndexScanProgressReporter(
                mapper,
                new CodeIndexProperties(),
                () -> 1_000L);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);
        String longErrorMessage = "E".repeat(CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH + 20);

        reporter.onFailure(1001L, longErrorMessage);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(CodeIndexConstants.ScanTaskStatus.FAILED, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.FAILED, updatedTask.getScanStage());
        assertEquals(CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH,
                updatedTask.getLatestErrorMessage().length());
        assertEquals("E".repeat(CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH),
                updatedTask.getLatestErrorMessage());
        assertNotNull(updatedTask.getFinishedAt());
    }

    @Test
    @DisplayName("标记运行中应写入 RUNNING/SCM_READING 和开始时间")
    void markRunningShouldUpdateRunningStatus() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);

        service.markRunning(1001L);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(1001L, updatedTask.getId());
        assertEquals(CodeIndexConstants.ScanTaskStatus.RUNNING, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.SCM_READING, updatedTask.getScanStage());
        assertEquals(CodeIndexConstants.ScanTask.SCM_READING_PROGRESS_START, updatedTask.getProgressPercent());
        assertNotNull(updatedTask.getStartedAt());
        assertNotNull(updatedTask.getLastHeartbeatAt());
    }

    @Test
    @DisplayName("标记成功应写入 SUCCESS/COMPLETED/100% 和结果索引")
    void markSuccessShouldUpdateSuccessStatus() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);

        service.markSuccess(1001L, 900L);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(CodeIndexConstants.ScanTaskStatus.SUCCESS, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.COMPLETED, updatedTask.getScanStage());
        assertEquals(CodeIndexConstants.ScanTask.COMPLETED_PROGRESS, updatedTask.getProgressPercent());
        assertEquals(900L, updatedTask.getResultIndexId());
        assertNotNull(updatedTask.getFinishedAt());
    }

    @Test
    @DisplayName("标记失败应截断错误信息并写入 FAILED 终态")
    void markFailedShouldUpdateFailureStatus() {
        CodeIndexScanTaskMapper mapper = mock(CodeIndexScanTaskMapper.class);
        CodeIndexScanTaskService service = service(mapper);
        when(mapper.updateById(any(CodeIndexScanTask.class))).thenReturn(1);
        String longErrorMessage = "E".repeat(CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH + 20);

        service.markFailed(1001L, longErrorMessage);

        ArgumentCaptor<CodeIndexScanTask> captor = ArgumentCaptor.forClass(CodeIndexScanTask.class);
        verify(mapper).updateById(captor.capture());
        CodeIndexScanTask updatedTask = captor.getValue();
        assertEquals(CodeIndexConstants.ScanTaskStatus.FAILED, updatedTask.getTaskStatus());
        assertEquals(CodeIndexConstants.ScanStage.FAILED, updatedTask.getScanStage());
        assertEquals(CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH,
                updatedTask.getLatestErrorMessage().length());
        assertNotNull(updatedTask.getFinishedAt());
    }

    private CodeIndexScanTask scanTask(String taskStatus) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 30);
        CodeIndexScanTask task = new CodeIndexScanTask();
        task.setId(1001L);
        task.setTaskNo("CI-20260525-1001");
        task.setScmConfigId(1L);
        task.setScmProvider("GITLAB");
        task.setScmProjectId("10086");
        task.setRepoName("argus");
        task.setBranchName("master");
        task.setCommitSha("abc123");
        task.setScanType(CodeIndexConstants.ScanType.FULL);
        task.setTriggerType(CodeIndexConstants.ScanTriggerType.MANUAL);
        task.setForceRebuild(false);
        task.setTaskStatus(taskStatus);
        task.setScanStage(CodeIndexConstants.ScanStage.JAVA_PARSING);
        task.setProgressPercent(66);
        task.setStageMessage("正在解析 Java 文件");
        task.setLoadedFileCount(200);
        task.setTotalJavaFileCount(180);
        task.setParsedFileCount(120);
        task.setFailedFileCount(2);
        task.setClassCount(400);
        task.setPackageCount(90);
        task.setWarningCount(3);
        task.setResultIndexId(10L);
        task.setReusedIndexId(9L);
        task.setLatestErrorMessage("parse warning");
        task.setStartedAt(now.minusMinutes(3));
        task.setFinishedAt(now);
        task.setLastHeartbeatAt(now.minusSeconds(10));
        task.setIsDeleted(false);
        task.setVersion(1);
        return task;
    }

    private CodeIndexScanTaskService service(CodeIndexScanTaskMapper mapper) {
        return service(mapper, mock(CodeIndexService.class), mock(ScmConfigService.class));
    }

    private CodeIndexScanTaskService service(CodeIndexScanTaskMapper mapper,
                                             CodeIndexService codeIndexService,
                                             ScmConfigService scmConfigService) {
        return new CodeIndexScanTaskServiceImpl(mapper, codeIndexService, scmConfigService);
    }

    private CodeIndexScanTaskCreateReqDTO createRequest(String branchName, String commitSha, boolean forceRebuild) {
        CodeIndexScanTaskCreateReqDTO requestDTO = new CodeIndexScanTaskCreateReqDTO();
        requestDTO.setBranchName(branchName);
        requestDTO.setCommitSha(commitSha);
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        requestDTO.setForceRebuild(forceRebuild);
        requestDTO.setReason("SCM 配置页手动刷新");
        return requestDTO;
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        scmConfig.setProjectId(10086L);
        scmConfig.setRepoName("argus");
        return scmConfig;
    }

    private CodeIndexSummaryResDTO successfulIndex(Long indexId) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setIndexId(indexId);
        response.setScmConfigId(1L);
        response.setBranchName("master");
        response.setCommitSha("abc123");
        response.setScanStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return response;
    }
}
