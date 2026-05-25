package com.lnzz.argus.codeindex.support;

import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import com.lnzz.argus.codeindex.dao.mapper.CodeIndexScanTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * @classname: CodeIndexScanProgressReporter
 * @author: Fantasy
 * @date: 2026/05/25 09:15
 * @description: 源码索引扫描进度上报器，统一处理阶段进度计算、写库节流和终态更新。
 */
@Slf4j
@Component
public class CodeIndexScanProgressReporter implements CodeIndexScanProgressCallback {

    private static final String DEFAULT_FAILURE_MESSAGE = "源码索引扫描失败";

    private final CodeIndexScanTaskMapper scanTaskMapper;
    private final CodeIndexProperties codeIndexProperties;
    private final LongSupplier currentTimeMillisSupplier;
    private final Map<Long, Long> lastProgressUpdateMillis = new ConcurrentHashMap<>();
    private final Map<Long, Integer> lastProgressPercent = new ConcurrentHashMap<>();

    /**
     * 创建生产环境进度上报器。
     *
     * @param scanTaskMapper 扫描任务 Mapper
     * @param codeIndexProperties 源码索引配置
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    @Autowired
    public CodeIndexScanProgressReporter(CodeIndexScanTaskMapper scanTaskMapper,
                                         CodeIndexProperties codeIndexProperties) {
        this(scanTaskMapper, codeIndexProperties, System::currentTimeMillis);
    }

    /**
     * 创建可注入时间源的进度上报器，便于单元测试验证节流逻辑。
     *
     * @param scanTaskMapper 扫描任务 Mapper
     * @param codeIndexProperties 源码索引配置
     * @param currentTimeMillisSupplier 当前毫秒时间供应器
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    public CodeIndexScanProgressReporter(CodeIndexScanTaskMapper scanTaskMapper,
                                         CodeIndexProperties codeIndexProperties,
                                         LongSupplier currentTimeMillisSupplier) {
        this.scanTaskMapper = scanTaskMapper;
        this.codeIndexProperties = codeIndexProperties == null ? new CodeIndexProperties() : codeIndexProperties;
        this.currentTimeMillisSupplier = currentTimeMillisSupplier == null
                ? System::currentTimeMillis
                : currentTimeMillisSupplier;
    }

    @Override
    public void onStageStart(Long taskId, String scanStage, String stageMessage) {
        if (!validTaskId(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.RUNNING);
        update.setScanStage(scanStage);
        update.setProgressPercent(nextProgress(taskId, stageStartProgress(scanStage)));
        update.setStageMessage(stageMessage);
        updateTask(update);
    }

    @Override
    public void onFileLoaded(Long taskId, int loadedFileCount) {
        if (!validTaskId(taskId) || throttled(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setLoadedFileCount(nonNegative(loadedFileCount));
        update.setScanStage(CodeIndexConstants.ScanStage.SCM_READING);
        update.setProgressPercent(nextProgress(taskId, CodeIndexConstants.ScanTask.SCM_READING_PROGRESS_START));
        updateTask(update);
    }

    @Override
    public void onJavaParseProgress(Long taskId, int parsedFileCount, int totalJavaFileCount, int failedFileCount) {
        if (!validTaskId(taskId) || throttled(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setScanStage(CodeIndexConstants.ScanStage.JAVA_PARSING);
        update.setProgressPercent(nextProgress(taskId, javaParsingProgress(parsedFileCount, totalJavaFileCount)));
        update.setParsedFileCount(nonNegative(parsedFileCount));
        update.setTotalJavaFileCount(nonNegative(totalJavaFileCount));
        update.setFailedFileCount(nonNegative(failedFileCount));
        updateTask(update);
    }

    @Override
    public void onPersisting(Long taskId, int classCount, int packageCount, int warningCount) {
        if (!validTaskId(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setScanStage(CodeIndexConstants.ScanStage.INDEX_PERSISTING);
        update.setProgressPercent(nextProgress(taskId, CodeIndexConstants.ScanTask.INDEX_PERSISTING_PROGRESS_START));
        update.setClassCount(nonNegative(classCount));
        update.setPackageCount(nonNegative(packageCount));
        update.setWarningCount(nonNegative(warningCount));
        updateTask(update);
    }

    @Override
    public void onSuccess(Long taskId, Long resultIndexId, int classCount, int packageCount, int warningCount) {
        if (!validTaskId(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.SUCCESS);
        update.setScanStage(CodeIndexConstants.ScanStage.COMPLETED);
        update.setProgressPercent(nextProgress(taskId, CodeIndexConstants.ScanTask.COMPLETED_PROGRESS));
        update.setResultIndexId(resultIndexId);
        update.setClassCount(nonNegative(classCount));
        update.setPackageCount(nonNegative(packageCount));
        update.setWarningCount(nonNegative(warningCount));
        LocalDateTime now = LocalDateTime.now();
        update.setFinishedAt(now);
        update.setLastHeartbeatAt(now);
        updateTask(update);
    }

    @Override
    public void onFailure(Long taskId, String errorMessage) {
        if (!validTaskId(taskId)) {
            return;
        }
        CodeIndexScanTask update = baseUpdate(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.FAILED);
        update.setScanStage(CodeIndexConstants.ScanStage.FAILED);
        update.setLatestErrorMessage(truncateErrorMessage(errorMessage));
        LocalDateTime now = LocalDateTime.now();
        update.setFinishedAt(now);
        update.setLastHeartbeatAt(now);
        updateTask(update);
    }

    private CodeIndexScanTask baseUpdate(Long taskId) {
        CodeIndexScanTask update = new CodeIndexScanTask();
        update.setId(taskId);
        update.setLastHeartbeatAt(LocalDateTime.now());
        return update;
    }

    private void updateTask(CodeIndexScanTask update) {
        int affectedRows = scanTaskMapper.updateById(update);
        if (affectedRows <= 0) {
            log.warn("源码索引扫描任务进度更新未命中任务, taskId={}, stage={}, status={}",
                    update.getId(), update.getScanStage(), update.getTaskStatus());
        }
    }

    private boolean throttled(Long taskId) {
        int progressInterval = progressInterval();
        if (progressInterval <= 0) {
            recordProgressUpdateTime(taskId);
            return false;
        }
        long nowMillis = currentTimeMillisSupplier.getAsLong();
        Long lastMillis = lastProgressUpdateMillis.get(taskId);
        if (lastMillis != null && nowMillis - lastMillis < progressInterval) {
            return true;
        }
        lastProgressUpdateMillis.put(taskId, nowMillis);
        return false;
    }

    private void recordProgressUpdateTime(Long taskId) {
        lastProgressUpdateMillis.put(taskId, currentTimeMillisSupplier.getAsLong());
    }

    private int progressInterval() {
        if (codeIndexProperties.getParser() == null) {
            return CodeIndexConstants.ScanTask.DEFAULT_PROGRESS_INTERVAL;
        }
        return codeIndexProperties.getParser().getProgressInterval();
    }

    private int javaParsingProgress(int parsedFileCount, int totalJavaFileCount) {
        if (totalJavaFileCount <= 0) {
            return CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START;
        }
        int progressRange = CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_END
                - CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START;
        double ratio = Math.min(1D, Math.max(0D, parsedFileCount * 1D / totalJavaFileCount));
        return CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START + (int) Math.floor(progressRange * ratio);
    }

    private int stageStartProgress(String scanStage) {
        if (CodeIndexConstants.ScanStage.SCM_READING.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.SCM_READING_PROGRESS_START;
        }
        if (CodeIndexConstants.ScanStage.MODULE_SCANNING.equals(scanStage)
                || CodeIndexConstants.ScanStage.SOURCE_ROOT_DISCOVERING.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.MODULE_SCANNING_PROGRESS_START;
        }
        if (CodeIndexConstants.ScanStage.JAVA_PARSING.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START;
        }
        if (CodeIndexConstants.ScanStage.INDEX_AGGREGATING.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.INDEX_AGGREGATING_PROGRESS_START;
        }
        if (CodeIndexConstants.ScanStage.INDEX_PERSISTING.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.INDEX_PERSISTING_PROGRESS_START;
        }
        if (CodeIndexConstants.ScanStage.COMPLETED.equals(scanStage)) {
            return CodeIndexConstants.ScanTask.COMPLETED_PROGRESS;
        }
        return 0;
    }

    private int nextProgress(Long taskId, int progressPercent) {
        int normalizedProgress = Math.min(CodeIndexConstants.ScanTask.COMPLETED_PROGRESS, Math.max(0, progressPercent));
        return lastProgressPercent.merge(taskId, normalizedProgress, Math::max);
    }

    private String truncateErrorMessage(String errorMessage) {
        String effectiveMessage = hasText(errorMessage) ? errorMessage : DEFAULT_FAILURE_MESSAGE;
        if (effectiveMessage.length() <= CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH) {
            return effectiveMessage;
        }
        return effectiveMessage.substring(0, CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH);
    }

    private int nonNegative(int value) {
        return Math.max(0, value);
    }

    private boolean validTaskId(Long taskId) {
        return taskId != null && taskId > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
