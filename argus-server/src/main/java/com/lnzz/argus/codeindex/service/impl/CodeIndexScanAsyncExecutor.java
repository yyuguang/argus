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
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @classname: CodeIndexScanAsyncExecutor
 * @author: Fantasy
 * @date: 2026/05/25 09:45
 * @description: 源码索引异步扫描执行器，负责提交扫描任务、驱动阶段回调并保护失败任务不影响主请求线程。
 */
@Slf4j
@Component
public class CodeIndexScanAsyncExecutor implements CodeIndexScanTaskExecutor {

    private static final String THREAD_NAME_PREFIX = "code-index-scan-";
    private static final String RUNNING_STAGE_MESSAGE = "开始读取 SCM 源码";
    private static final String EMPTY_RESULT_MESSAGE = "源码索引扫描未返回结果";
    private static final List<String> TERMINAL_TASK_STATUSES = List.of(
            CodeIndexConstants.ScanTaskStatus.SUCCESS,
            CodeIndexConstants.ScanTaskStatus.FAILED,
            CodeIndexConstants.ScanTaskStatus.CANCELED,
            CodeIndexConstants.ScanTaskStatus.REUSED);

    private final CodeIndexScanTaskMapper scanTaskMapper;
    private final ScmConfigService scmConfigService;
    private final CodeIndexScanService scanService;
    private final CodeIndexScanProgressCallback progressCallback;
    private final CodeIndexProperties codeIndexProperties;
    private final Executor executor;

    /**
     * 创建生产环境异步扫描执行器，并初始化源码索引扫描专用有界线程池。
     *
     * @param scanTaskMapper 扫描任务 Mapper
     * @param scmConfigService SCM 配置服务
     * @param scanService 源码索引扫描服务
     * @param progressCallback 扫描进度回调
     * @param codeIndexProperties 源码索引配置
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    @Autowired
    public CodeIndexScanAsyncExecutor(CodeIndexScanTaskMapper scanTaskMapper,
                                      ScmConfigService scmConfigService,
                                      CodeIndexScanService scanService,
                                      CodeIndexScanProgressCallback progressCallback,
                                      CodeIndexProperties codeIndexProperties) {
        this(scanTaskMapper, scmConfigService, scanService, progressCallback, codeIndexProperties,
                createExecutor(codeIndexProperties));
    }

    CodeIndexScanAsyncExecutor(CodeIndexScanTaskMapper scanTaskMapper,
                               ScmConfigService scmConfigService,
                               CodeIndexScanService scanService,
                               CodeIndexScanProgressCallback progressCallback,
                               CodeIndexProperties codeIndexProperties,
                               Executor executor) {
        this.scanTaskMapper = scanTaskMapper;
        this.scmConfigService = scmConfigService;
        this.scanService = scanService;
        this.progressCallback = progressCallback;
        this.codeIndexProperties = codeIndexProperties == null ? new CodeIndexProperties() : codeIndexProperties;
        this.executor = executor == null ? Runnable::run : executor;
    }

    /**
     * 提交源码索引扫描任务。仅 PENDING 任务会进入执行链，终态或已删除任务直接跳过。
     *
     * @param taskId 扫描任务 ID
     * @return true 表示任务已提交执行；false 表示任务不存在、不可执行或线程池拒绝
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    public boolean submit(Long taskId) {
        if (!validTaskId(taskId)) {
            log.warn("源码索引扫描任务提交被拒绝, reason=invalid_task_id, taskId={}", taskId);
            return false;
        }
        if (!codeIndexProperties.isAsyncScanEnabled()) {
            log.warn("源码索引扫描任务提交被拒绝, reason=async_disabled, taskId={}", taskId);
            return false;
        }
        CodeIndexScanTask task = scanTaskMapper.selectById(taskId);
        if (!executableTask(task)) {
            log.warn("源码索引扫描任务提交被跳过, taskId={}, status={}, deleted={}",
                    taskId, task == null ? null : task.getTaskStatus(), task == null ? null : task.getIsDeleted());
            return false;
        }
        try {
            executor.execute(() -> executeTask(task));
            return true;
        } catch (RuntimeException ex) {
            log.error("源码索引扫描任务提交线程池失败, taskId={}, status={}", taskId, task.getTaskStatus(), ex);
            reportFailure(taskId, ex.getMessage());
            return false;
        }
    }

    /**
     * 关闭生产环境内部线程池，避免应用停止时残留扫描线程。
     *
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    @PreDestroy
    public void destroy() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdown();
        }
    }

    private void executeTask(CodeIndexScanTask task) {
        Long taskId = task.getId();
        long startedAt = System.currentTimeMillis();
        try {
            if (!markRunning(taskId)) {
                return;
            }
            ScmConfig scmConfig = scmConfigService.requireById(task.getScmConfigId());
            CodeIndexScanReqDTO requestDTO = buildRequest(task);
            CodeIndexScanExecutionContext context = CodeIndexScanExecutionContext.progress(taskId, progressCallback);
            CodeIndexSummaryResDTO response = scanService.scanFull(scmConfig, requestDTO, context);
            if (response == null) {
                reportFailure(taskId, EMPTY_RESULT_MESSAGE);
                return;
            }
            if (CodeIndexConstants.ScanStatus.FAILED.equals(response.getScanStatus())) {
                reportFailure(taskId, response.getLatestErrorMessage());
                return;
            }
            log.info("源码索引异步扫描任务执行完成, taskId={}, scmConfigId={}, branchName={}, commitSha={}, resultIndexId={}, scanStatus={}, costMs={}",
                    taskId, task.getScmConfigId(), task.getBranchName(), task.getCommitSha(), response.getIndexId(),
                    response.getScanStatus(), System.currentTimeMillis() - startedAt);
        } catch (RuntimeException ex) {
            log.error("源码索引异步扫描任务执行失败, taskId={}, scmConfigId={}, branchName={}, commitSha={}",
                    taskId, task.getScmConfigId(), task.getBranchName(), task.getCommitSha(), ex);
            reportFailure(taskId, ex.getMessage());
        }
    }

    private boolean markRunning(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        CodeIndexScanTask update = new CodeIndexScanTask();
        update.setId(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.RUNNING);
        update.setScanStage(CodeIndexConstants.ScanStage.SCM_READING);
        update.setProgressPercent(CodeIndexConstants.ScanTask.SCM_READING_PROGRESS_START);
        update.setStageMessage(RUNNING_STAGE_MESSAGE);
        update.setStartedAt(now);
        update.setLastHeartbeatAt(now);
        int affectedRows = scanTaskMapper.updateById(update);
        if (affectedRows <= 0) {
            log.warn("源码索引扫描任务运行中状态更新未命中任务, taskId={}", taskId);
            return false;
        }
        log.info("源码索引扫描任务进入异步执行, taskId={}", taskId);
        return true;
    }

    private void reportFailure(Long taskId, String errorMessage) {
        if (progressCallback == null) {
            log.warn("源码索引扫描任务失败状态无法上报, reason=missing_progress_callback, taskId={}", taskId);
            return;
        }
        try {
            progressCallback.onFailure(taskId, errorMessage);
        } catch (RuntimeException ex) {
            log.error("源码索引扫描任务失败状态上报异常, taskId={}", taskId, ex);
        }
    }

    private CodeIndexScanReqDTO buildRequest(CodeIndexScanTask task) {
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setBranchName(firstText(task.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH));
        requestDTO.setCommitSha(trimToNull(task.getCommitSha()));
        requestDTO.setScanType(firstText(task.getScanType(), CodeIndexConstants.ScanType.FULL));
        requestDTO.setForceRebuild(Boolean.TRUE.equals(task.getForceRebuild()));
        requestDTO.setReason(trimToNull(task.getReason()));
        return requestDTO;
    }

    private boolean executableTask(CodeIndexScanTask task) {
        if (task == null || Boolean.TRUE.equals(task.getIsDeleted())) {
            return false;
        }
        return CodeIndexConstants.ScanTaskStatus.PENDING.equals(task.getTaskStatus())
                && !TERMINAL_TASK_STATUSES.contains(task.getTaskStatus());
    }

    private boolean validTaskId(Long taskId) {
        return taskId != null && taskId > 0;
    }

    private String firstText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Executor createExecutor(CodeIndexProperties properties) {
        CodeIndexProperties effectiveProperties = properties == null ? new CodeIndexProperties() : properties;
        CodeIndexProperties.Parser parser = effectiveProperties.getParser() == null
                ? new CodeIndexProperties.Parser()
                : effectiveProperties.getParser();
        int poolSize = Math.max(1, parser.getParallelism());
        int queueCapacity = Math.max(1, parser.getQueueSize());
        AtomicInteger threadNo = new AtomicInteger(1);
        return new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + threadNo.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
