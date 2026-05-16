package com.lnzz.argus.error.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorAnalysisTask;
import com.lnzz.argus.error.entity.ErrorContextLog;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.mapper.ErrorAnalysisMapper;
import com.lnzz.argus.error.mapper.ErrorAnalysisTaskMapper;
import com.lnzz.argus.error.mapper.ErrorContextLogMapper;
import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.common.enums.ProcessingStatus;
import com.lnzz.argus.error.service.ErrorAnalysisService;
import com.lnzz.argus.error.service.ErrorManagementService;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误诊断管理台服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ErrorManagementServiceImpl implements ErrorManagementService {

    private final ErrorEventMapper errorEventMapper;
    private final ErrorAnalysisMapper errorAnalysisMapper;
    private final ErrorAnalysisTaskMapper errorAnalysisTaskMapper;
    private final ErrorContextLogMapper contextLogMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final ErrorAnalysisService errorAnalysisService;
    private final NotificationService notificationService;
    private final KnowledgeService knowledgeService;

    @Override
    public Page<ErrorEvent> queryEvents(long pageNo, long pageSize, String appName, String environment,
                                        String severity, String status, String keyword) {
        LambdaQueryWrapper<ErrorEvent> wrapper = new LambdaQueryWrapper<ErrorEvent>()
                .eq(hasText(appName), ErrorEvent::getAppName, appName)
                .eq(hasText(environment), ErrorEvent::getEnvironment, environment)
                .eq(hasText(severity), ErrorEvent::getSeverity, severity)
                .eq(hasText(status), ErrorEvent::getProcessingStatus, status)
                .and(hasText(keyword), query -> query
                        .like(ErrorEvent::getErrorMessage, keyword)
                        .or()
                        .like(ErrorEvent::getErrorFingerprint, keyword)
                        .or()
                        .like(ErrorEvent::getClassName, keyword)
                        .or()
                        .like(ErrorEvent::getInterfaceRef, keyword))
                .orderByDesc(ErrorEvent::getLastOccurredAt)
                .orderByDesc(ErrorEvent::getOccurredAt)
                .orderByDesc(ErrorEvent::getId);
        return errorEventMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Long eventId) {
        ErrorEvent event = requireEvent(eventId);
        ErrorAnalysis analysis = errorAnalysisMapper.selectOne(new LambdaQueryWrapper<ErrorAnalysis>()
                .eq(ErrorAnalysis::getErrorEventId, eventId)
                .orderByDesc(ErrorAnalysis::getCreateTime)
                .last("LIMIT 1"));
        List<ErrorContextLog> contextLogs = contextLogMapper.selectList(new LambdaQueryWrapper<ErrorContextLog>()
                .eq(ErrorContextLog::getErrorEventId, eventId)
                .orderByAsc(ErrorContextLog::getLogTime)
                .last("LIMIT 50"));
        List<NotificationRecord> notifications = notificationRecordMapper.selectList(
                new LambdaQueryWrapper<NotificationRecord>()
                        .eq(NotificationRecord::getRefType, "ERROR_EVENT")
                        .eq(NotificationRecord::getRefId, eventId)
                        .orderByDesc(NotificationRecord::getCreateTime)
                        .last("LIMIT 20"));
        List<KnowledgeEntry> knowledgeMatches = knowledgeService.findSimilar(event, 5);
        List<ErrorAnalysisTask> analysisTasks = listAnalysisTasks(eventId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event", event);
        detail.put("analysis", analysis);
        detail.put("analysisTasks", analysisTasks);
        detail.put("contextLogs", contextLogs);
        detail.put("notifications", notifications);
        detail.put("knowledgeMatches", knowledgeMatches);
        detail.put("sourceLocation", buildSourceLocationSummary(event));
        return detail;
    }

    @Override
    public List<ErrorAnalysisTask> listAnalysisTasks(Long eventId) {
        return errorAnalysisTaskMapper.selectList(new LambdaQueryWrapper<ErrorAnalysisTask>()
                .eq(ErrorAnalysisTask::getErrorEventId, eventId)
                .orderByDesc(ErrorAnalysisTask::getCreateTime)
                .orderByDesc(ErrorAnalysisTask::getId));
    }

    @Override
    public List<ErrorEvent> listByFingerprint(String fingerprint) {
        return errorEventMapper.selectList(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getErrorFingerprint, fingerprint)
                .orderByDesc(ErrorEvent::getLastOccurredAt)
                .orderByDesc(ErrorEvent::getOccurredAt)
                .last("LIMIT 100"));
    }

    @Override
    public Map<String, Object> getStats() {
        long total = errorEventMapper.selectCount(null);
        long p0 = countBySeverity("P0");
        long p1 = countBySeverity("P1");
        long p2 = countBySeverity("P2");
        long p3 = countBySeverity("P3");
        long unanalyzed = errorEventMapper.selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .and(query -> query.eq(ErrorEvent::getAnalyzed, false).or().isNull(ErrorEvent::getAnalyzed)));
        long ignored = errorEventMapper.selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getProcessingStatus, "IGNORED"));
        long falsePositive = errorEventMapper.selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getProcessingStatus, "FALSE_POSITIVE"));

        Map<String, Object> severityCounts = new LinkedHashMap<>();
        severityCounts.put("P0", p0);
        severityCounts.put("P1", p1);
        severityCounts.put("P2", p2);
        severityCounts.put("P3", p3);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("unanalyzed", unanalyzed);
        stats.put("ignored", ignored);
        stats.put("falsePositive", falsePositive);
        stats.put("severityCounts", severityCounts);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyze(Long eventId, boolean resetAnalyzed) {
        ErrorEvent event = requireEvent(eventId);
        if (resetAnalyzed) {
            errorEventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                    .eq(ErrorEvent::getId, eventId)
                    .set(ErrorEvent::getAnalyzed, false)
                    .set(ErrorEvent::getProcessingStatus, ProcessingStatus.ANALYZING.getCode()));
        }
        errorAnalysisService.analyzeEvent(eventId, resetAnalyzed ? "MANUAL_RETRY" : "MANUAL");
        return Map.of("eventId", event.getId(), "status", "ANALYSIS_SUBMITTED");
    }

    @Override
    public Map<String, Object> retryNotify(Long eventId) {
        ErrorEvent event = requireEvent(eventId);
        ErrorAnalysis analysis = errorAnalysisMapper.selectOne(new LambdaQueryWrapper<ErrorAnalysis>()
                .eq(ErrorAnalysis::getErrorEventId, eventId)
                .orderByDesc(ErrorAnalysis::getCreateTime)
                .last("LIMIT 1"));
        boolean sent = notificationService.sendErrorAlert(event, analysis);
        errorEventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getNotified, sent));
        return Map.of("eventId", eventId, "status", sent ? "NOTIFIED" : "NOTIFY_SKIPPED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErrorEvent ignore(Long eventId, String operator, String reason) {
        ErrorEvent event = requireEvent(eventId);
        String message = actionReason("人工忽略", operator, reason);
        updateEventStatus(eventId, ProcessingStatus.IGNORED.getCode(), message);
        event.setProcessingStatus(ProcessingStatus.IGNORED.getCode());
        event.setSeverityReason(message);
        return event;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErrorEvent markFalsePositive(Long eventId, String operator, String reason) {
        ErrorEvent event = requireEvent(eventId);
        String message = actionReason("人工标记误报", operator, reason);
        errorEventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getProcessingStatus, ProcessingStatus.FALSE_POSITIVE.getCode())
                .set(ErrorEvent::getAnalysisDecision, "AGGREGATE_ONLY")
                .set(ErrorEvent::getSeverityReason, message));
        event.setProcessingStatus(ProcessingStatus.FALSE_POSITIVE.getCode());
        event.setAnalysisDecision("AGGREGATE_ONLY");
        event.setSeverityReason(message);
        return event;
    }

    @Override
    public ErrorEvent adjustSeverity(Long eventId, String severity, String reason) {
        return errorAnalysisService.adjustSeverity(eventId, severity, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> manualConclusion(Long eventId, String rootCause, String severity,
                                                String fixDescription, String preventionAdvice) {
        ErrorAnalysis analysis = errorAnalysisService.supplementManual(
                eventId, rootCause, severity, fixDescription, preventionAdvice);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("analysis", analysis);
        result.put("status", "MANUAL_CONCLUSION_SAVED");
        return result;
    }

    private ErrorEvent requireEvent(Long eventId) {
        ErrorEvent event = errorEventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "错误事件不存在: " + eventId);
        }
        return event;
    }

    private long countBySeverity(String severity) {
        return errorEventMapper.selectCount(new LambdaQueryWrapper<ErrorEvent>()
                .eq(ErrorEvent::getSeverity, severity));
    }

    private Map<String, Object> buildSourceLocationSummary(ErrorEvent event) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("appName", event.getAppName());
        source.put("className", event.getClassName());
        source.put("methodName", event.getMethodName());
        source.put("filePath", event.getFilePath());
        source.put("lineNumber", event.getLineNumber());
        source.put("interfaceRef", event.getInterfaceRef());
        source.put("sourceType", event.getSourceType());
        return source;
    }

    private void updateEventStatus(Long eventId, String status, String reason) {
        errorEventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, eventId)
                .set(ErrorEvent::getProcessingStatus, status)
                .set(ErrorEvent::getSeverityReason, reason));
    }

    private String actionReason(String action, String operator, String reason) {
        StringBuilder builder = new StringBuilder(action);
        if (hasText(operator)) {
            builder.append("[").append(operator.trim()).append("]");
        }
        if (hasText(reason)) {
            builder.append(": ").append(reason.trim());
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
