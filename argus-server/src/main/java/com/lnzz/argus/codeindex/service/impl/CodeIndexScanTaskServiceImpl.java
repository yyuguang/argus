package com.lnzz.argus.codeindex.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import com.lnzz.argus.codeindex.dao.mapper.CodeIndexScanTaskMapper;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.service.CodeIndexScanTaskService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.security.LoginUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @classname: CodeIndexScanTaskServiceImpl
 * @author: Fantasy
 * @date: 2026/05/25 08:50
 * @description: 源码索引扫描任务服务实现，负责扫描任务查询和状态流转编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeIndexScanTaskServiceImpl implements CodeIndexScanTaskService {

    private static final DateTimeFormatter TASK_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String WAITING_STAGE_MESSAGE = "等待扫描任务执行";
    private static final String REUSED_STAGE_MESSAGE = "已复用同提交成功源码索引";
    private static final String RUNNING_STAGE_MESSAGE = "开始读取 SCM 源码";
    private static final String SUCCESS_STAGE_MESSAGE = "源码索引扫描完成";
    private static final String FAILED_STAGE_MESSAGE = "源码索引扫描失败";
    private static final String PENDING_RESPONSE_MESSAGE = "源码索引扫描任务已创建";
    private static final String RUNNING_RESPONSE_MESSAGE = "已存在运行中的源码索引扫描任务";
    private static final String REUSED_RESPONSE_MESSAGE = "当前提交已有成功源码索引，已复用";

    private static final List<String> RUNNING_TASK_STATUSES = List.of(
            CodeIndexConstants.ScanTaskStatus.PENDING,
            CodeIndexConstants.ScanTaskStatus.RUNNING);

    private final CodeIndexScanTaskMapper scanTaskMapper;
    private final CodeIndexService codeIndexService;
    private final ScmConfigService scmConfigService;

    /**
     * 创建扫描任务，按普通刷新复用、运行中任务去重、新建 PENDING 任务的顺序编排。
     *
     * @param scmConfigId SCM 配置 ID
     * @param requestDTO 创建任务请求
     * @return 扫描任务响应，可能是 REUSED、RUNNING 或 PENDING
     * @author Fantasy
     * @date 2026/05/25 09:05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeIndexScanTaskResDTO createTask(Long scmConfigId, CodeIndexScanTaskCreateReqDTO requestDTO) {
        requireValidScmConfigId(scmConfigId);
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        CodeIndexScanTaskCreateReqDTO effectiveRequest = requestDTO == null
                ? new CodeIndexScanTaskCreateReqDTO()
                : requestDTO;
        String branchName = normalizeBranchName(effectiveRequest.getBranchName());
        String commitSha = trimToNull(effectiveRequest.getCommitSha());
        String scanType = normalizeScanType(effectiveRequest.getScanType());
        boolean forceRebuild = resolveForceRebuild(effectiveRequest, scanType);

        if (!forceRebuild && hasText(commitSha)) {
            CodeIndexSummaryResDTO successfulIndex = codeIndexService.getSuccessfulIndexByCommit(scmConfigId, commitSha);
            if (successfulIndex != null) {
                return createReusedTask(scmConfig, effectiveRequest, branchName, commitSha, scanType, successfulIndex);
            }
        }

        CodeIndexScanTask runningTask = findRunningTaskEntity(scmConfigId, branchName, commitSha);
        if (runningTask != null) {
            log.info("复用运行中的源码索引扫描任务, scmConfigId={}, branchName={}, commitSha={}, taskId={}, taskNo={}",
                    scmConfigId, branchName, commitSha, runningTask.getId(), runningTask.getTaskNo());
            return toResponse(runningTask, RUNNING_RESPONSE_MESSAGE);
        }

        CodeIndexScanTask pendingTask = buildBaseTask(scmConfig, effectiveRequest, branchName, commitSha, scanType,
                forceRebuild, CodeIndexConstants.ScanTaskStatus.PENDING, CodeIndexConstants.ScanStage.WAITING,
                0, WAITING_STAGE_MESSAGE);
        insertTask(pendingTask);
        log.info("创建源码索引扫描任务, scmConfigId={}, branchName={}, commitSha={}, scanType={}, forceRebuild={}, taskId={}, taskNo={}",
                scmConfigId, branchName, commitSha, scanType, forceRebuild, pendingTask.getId(), pendingTask.getTaskNo());
        return toResponse(pendingTask, PENDING_RESPONSE_MESSAGE);
    }

    @Override
    public CodeIndexScanTaskResDTO getTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            return null;
        }
        return toResponse(activeTask(scanTaskMapper.selectById(taskId)));
    }

    @Override
    public CodeIndexScanTaskResDTO findRunningTask(Long scmConfigId, String branchName) {
        if (scmConfigId == null || !hasText(branchName)) {
            return null;
        }
        CodeIndexScanTask task = scanTaskMapper.selectOne(new LambdaQueryWrapper<CodeIndexScanTask>()
                .eq(CodeIndexScanTask::getScmConfigId, scmConfigId)
                .eq(CodeIndexScanTask::getBranchName, branchName)
                .in(CodeIndexScanTask::getTaskStatus, RUNNING_TASK_STATUSES)
                .eq(CodeIndexScanTask::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(CodeIndexScanTask::getCreateTime)
                .orderByDesc(CodeIndexScanTask::getId)
                .last("limit 1"));
        task = activeTask(task);
        if (task == null || !RUNNING_TASK_STATUSES.contains(task.getTaskStatus())) {
            return null;
        }
        return toResponse(task);
    }

    /**
     * 将扫描任务切换为运行中状态，并初始化开始时间和心跳时间。
     *
     * @param taskId 扫描任务 ID
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRunning(Long taskId) {
        requireValidTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        CodeIndexScanTask update = new CodeIndexScanTask();
        update.setId(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.RUNNING);
        update.setScanStage(CodeIndexConstants.ScanStage.SCM_READING);
        update.setProgressPercent(CodeIndexConstants.ScanTask.SCM_READING_PROGRESS_START);
        update.setStageMessage(RUNNING_STAGE_MESSAGE);
        update.setStartedAt(now);
        update.setLastHeartbeatAt(now);
        updateTaskStatus(update, "标记运行中");
    }

    /**
     * 将扫描任务切换为成功终态，并写入最终关联的源码索引 ID。
     *
     * @param taskId 扫描任务 ID
     * @param resultIndexId 成功后关联的源码索引 ID
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long taskId, Long resultIndexId) {
        requireValidTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        CodeIndexScanTask update = new CodeIndexScanTask();
        update.setId(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.SUCCESS);
        update.setScanStage(CodeIndexConstants.ScanStage.COMPLETED);
        update.setProgressPercent(CodeIndexConstants.ScanTask.COMPLETED_PROGRESS);
        update.setStageMessage(SUCCESS_STAGE_MESSAGE);
        update.setResultIndexId(resultIndexId);
        update.setFinishedAt(now);
        update.setLastHeartbeatAt(now);
        updateTaskStatus(update, "标记成功");
    }

    /**
     * 将扫描任务切换为失败终态，并按任务表长度限制保存失败摘要。
     *
     * @param taskId 扫描任务 ID
     * @param errorMessage 失败原因摘要
     * @author Fantasy
     * @date 2026/05/25 09:45
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long taskId, String errorMessage) {
        requireValidTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        CodeIndexScanTask update = new CodeIndexScanTask();
        update.setId(taskId);
        update.setTaskStatus(CodeIndexConstants.ScanTaskStatus.FAILED);
        update.setScanStage(CodeIndexConstants.ScanStage.FAILED);
        update.setStageMessage(FAILED_STAGE_MESSAGE);
        update.setLatestErrorMessage(truncateErrorMessage(errorMessage));
        update.setFinishedAt(now);
        update.setLastHeartbeatAt(now);
        updateTaskStatus(update, "标记失败");
    }

    private CodeIndexScanTask activeTask(CodeIndexScanTask task) {
        if (task == null || Boolean.TRUE.equals(task.getIsDeleted())) {
            return null;
        }
        return task;
    }

    private CodeIndexScanTaskResDTO toResponse(CodeIndexScanTask task) {
        return toResponse(task, null);
    }

    private CodeIndexScanTaskResDTO toResponse(CodeIndexScanTask task, String message) {
        if (task == null) {
            return null;
        }
        CodeIndexScanTaskResDTO response = new CodeIndexScanTaskResDTO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setTaskStatus(task.getTaskStatus());
        response.setScanStage(task.getScanStage());
        response.setProgressPercent(task.getProgressPercent());
        response.setStageMessage(task.getStageMessage());
        response.setLoadedFileCount(task.getLoadedFileCount());
        response.setTotalJavaFileCount(task.getTotalJavaFileCount());
        response.setParsedFileCount(task.getParsedFileCount());
        response.setFailedFileCount(task.getFailedFileCount());
        response.setClassCount(task.getClassCount());
        response.setPackageCount(task.getPackageCount());
        response.setWarningCount(task.getWarningCount());
        response.setResultIndexId(task.getResultIndexId());
        response.setReusedIndexId(task.getReusedIndexId());
        response.setLatestErrorMessage(task.getLatestErrorMessage());
        response.setMessage(message);
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setLastHeartbeatAt(task.getLastHeartbeatAt());
        return response;
    }

    private CodeIndexScanTaskResDTO createReusedTask(ScmConfig scmConfig,
                                                     CodeIndexScanTaskCreateReqDTO requestDTO,
                                                     String branchName,
                                                     String commitSha,
                                                     String scanType,
                                                     CodeIndexSummaryResDTO successfulIndex) {
        CodeIndexScanTask reusedTask = buildBaseTask(scmConfig, requestDTO, branchName, commitSha, scanType,
                false, CodeIndexConstants.ScanTaskStatus.REUSED, CodeIndexConstants.ScanStage.COMPLETED,
                CodeIndexConstants.ScanTask.COMPLETED_PROGRESS, REUSED_STAGE_MESSAGE);
        LocalDateTime now = LocalDateTime.now();
        reusedTask.setStartedAt(now);
        reusedTask.setFinishedAt(now);
        reusedTask.setLastHeartbeatAt(now);
        reusedTask.setReusedIndexId(successfulIndex.getIndexId());
        reusedTask.setClassCount(defaultInt(successfulIndex.getClassCount()));
        reusedTask.setPackageCount(defaultInt(successfulIndex.getPackageCount()));
        reusedTask.setWarningCount(defaultInt(successfulIndex.getWarningCount()));
        insertTask(reusedTask);
        log.info("源码索引扫描任务复用成功索引, scmConfigId={}, branchName={}, commitSha={}, reusedIndexId={}, taskId={}, taskNo={}",
                scmConfig.getId(), branchName, commitSha, successfulIndex.getIndexId(),
                reusedTask.getId(), reusedTask.getTaskNo());
        return toResponse(reusedTask, REUSED_RESPONSE_MESSAGE);
    }

    private CodeIndexScanTask findRunningTaskEntity(Long scmConfigId, String branchName, String commitSha) {
        CodeIndexScanTask task = scanTaskMapper.selectOne(new LambdaQueryWrapper<CodeIndexScanTask>()
                .eq(CodeIndexScanTask::getScmConfigId, scmConfigId)
                .eq(CodeIndexScanTask::getBranchName, branchName)
                .eq(hasText(commitSha), CodeIndexScanTask::getCommitSha, commitSha)
                .in(CodeIndexScanTask::getTaskStatus, RUNNING_TASK_STATUSES)
                .eq(CodeIndexScanTask::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(CodeIndexScanTask::getCreateTime)
                .orderByDesc(CodeIndexScanTask::getId)
                .last("limit 1"));
        task = activeTask(task);
        if (task == null || !RUNNING_TASK_STATUSES.contains(task.getTaskStatus())) {
            return null;
        }
        return task;
    }

    private CodeIndexScanTask buildBaseTask(ScmConfig scmConfig,
                                            CodeIndexScanTaskCreateReqDTO requestDTO,
                                            String branchName,
                                            String commitSha,
                                            String scanType,
                                            boolean forceRebuild,
                                            String taskStatus,
                                            String scanStage,
                                            Integer progressPercent,
                                            String stageMessage) {
        CodeIndexScanTask task = new CodeIndexScanTask();
        task.setTaskNo(generateTaskNo());
        task.setScmConfigId(scmConfig.getId());
        task.setScmProvider(scmConfig.getScmProvider());
        task.setScmProjectId(scmConfig.getProjectId() == null ? null : String.valueOf(scmConfig.getProjectId()));
        task.setRepoName(scmConfig.getRepoName());
        task.setBranchName(branchName);
        task.setCommitSha(commitSha);
        task.setScanType(scanType);
        task.setTriggerType(CodeIndexConstants.ScanTriggerType.MANUAL);
        task.setForceRebuild(forceRebuild);
        task.setTaskStatus(taskStatus);
        task.setScanStage(scanStage);
        task.setProgressPercent(progressPercent);
        task.setStageMessage(stageMessage);
        task.setLoadedFileCount(0);
        task.setTotalJavaFileCount(0);
        task.setParsedFileCount(0);
        task.setFailedFileCount(0);
        task.setClassCount(0);
        task.setPackageCount(0);
        task.setWarningCount(0);
        task.setRequestedBy(LoginUtil.currentUsernameOrSystem());
        task.setReason(trimToNull(requestDTO.getReason()));
        task.setLastHeartbeatAt(LocalDateTime.now());
        task.setIsDeleted(SystemDataConstants.NOT_DELETED);
        task.setVersion(0);
        return task;
    }

    private void insertTask(CodeIndexScanTask task) {
        int affectedRows = scanTaskMapper.insert(task);
        if (affectedRows <= 0) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "源码索引扫描任务创建失败");
        }
    }

    private String generateTaskNo() {
        String datePart = LocalDateTime.now().format(TASK_NO_DATE_FORMATTER);
        String sequencePart = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(Locale.ROOT);
        return CodeIndexConstants.ScanTask.TASK_NO_PREFIX + datePart + "-" + sequencePart;
    }

    private boolean resolveForceRebuild(CodeIndexScanTaskCreateReqDTO requestDTO, String scanType) {
        return Boolean.TRUE.equals(requestDTO.getForceRebuild())
                || CodeIndexConstants.ScanType.REBUILD.equals(scanType);
    }

    private String normalizeBranchName(String branchName) {
        return hasText(branchName) ? branchName.trim() : CodeIndexConstants.DEFAULT_BRANCH;
    }

    private String normalizeScanType(String scanType) {
        return hasText(scanType) ? scanType.trim() : CodeIndexConstants.ScanType.FULL;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireValidScmConfigId(Long scmConfigId) {
        if (scmConfigId == null || scmConfigId <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "scmConfigId 不能为空");
        }
    }

    private void requireValidTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "taskId 不能为空");
        }
    }

    private void updateTaskStatus(CodeIndexScanTask update, String operation) {
        int affectedRows = scanTaskMapper.updateById(update);
        if (affectedRows <= 0) {
            log.warn("源码索引扫描任务状态更新未命中任务, operation={}, taskId={}, status={}, stage={}",
                    operation, update.getId(), update.getTaskStatus(), update.getScanStage());
            throw new BizException(ResultCode.SYSTEM_ERROR, "源码索引扫描任务状态更新失败");
        }
        log.info("源码索引扫描任务状态更新成功, operation={}, taskId={}, status={}, stage={}",
                operation, update.getId(), update.getTaskStatus(), update.getScanStage());
    }

    private String truncateErrorMessage(String errorMessage) {
        String effectiveMessage = hasText(errorMessage) ? errorMessage : FAILED_STAGE_MESSAGE;
        if (effectiveMessage.length() <= CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH) {
            return effectiveMessage;
        }
        return effectiveMessage.substring(0, CodeIndexConstants.ScanTask.MAX_ERROR_MESSAGE_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
