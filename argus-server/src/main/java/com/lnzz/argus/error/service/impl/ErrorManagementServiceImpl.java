package com.lnzz.argus.error.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.common.enums.AnalysisDecision;
import com.lnzz.argus.common.enums.ProcessingStatus;
import com.lnzz.argus.common.enums.SeverityLevel;
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
import com.lnzz.argus.error.service.ErrorAnalysisService;
import com.lnzz.argus.error.service.ErrorManagementService;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        log.debug("查询错误事件: pageNo={}, pageSize={}, appName={}, environment={}, severity={}, status={}, keyword={}",
                pageNo, pageSize, appName, environment, severity, status, keyword);
        return errorEventMapper.queryEvents(pageNo, pageSize, appName, environment, severity, status, keyword);
    }

    @Override
    public Map<String, Object> getDetail(Long eventId) {
        ErrorEvent event = requireEvent(eventId);
        ErrorAnalysis analysis = errorAnalysisMapper.findLatestByEventId(eventId);
        List<ErrorContextLog> contextLogs = contextLogMapper.findByEventId(eventId, 50);
        List<NotificationRecord> notifications = notificationRecordMapper.findByRef(
                NotificationConstants.REF_TYPE_ERROR_EVENT, eventId, 20);
        List<KnowledgeEntry> knowledgeMatches = knowledgeService.findSimilar(event, 5);
        List<ErrorAnalysisTask> analysisTasks = listAnalysisTasks(eventId);
        log.debug("查询错误详情: eventId={}, contextLogCount={}, notificationCount={}, taskCount={}",
                eventId, contextLogs.size(), notifications.size(), analysisTasks.size());

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
        return errorAnalysisTaskMapper.findByEventId(eventId);
    }

    @Override
    public List<ErrorEvent> listByFingerprint(String fingerprint) {
        log.debug("按指纹查询错误事件: fingerprint={}", fingerprint);
        return errorEventMapper.findByFingerprint(fingerprint, 100);
    }

    @Override
    public Map<String, Object> getStats() {
        long total = errorEventMapper.countAll();
        long p0 = countBySeverity(SeverityLevel.P0.getCode());
        long p1 = countBySeverity(SeverityLevel.P1.getCode());
        long p2 = countBySeverity(SeverityLevel.P2.getCode());
        long p3 = countBySeverity(SeverityLevel.P3.getCode());
        long unanalyzed = errorEventMapper.countUnanalyzed();
        long ignored = errorEventMapper.countByProcessingStatus(ProcessingStatus.IGNORED.getCode());
        long falsePositive = errorEventMapper.countByProcessingStatus(ProcessingStatus.FALSE_POSITIVE.getCode());

        Map<String, Object> severityCounts = new LinkedHashMap<>();
        severityCounts.put(SeverityLevel.P0.getCode(), p0);
        severityCounts.put(SeverityLevel.P1.getCode(), p1);
        severityCounts.put(SeverityLevel.P2.getCode(), p2);
        severityCounts.put(SeverityLevel.P3.getCode(), p3);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("unanalyzed", unanalyzed);
        stats.put("ignored", ignored);
        stats.put("falsePositive", falsePositive);
        stats.put("severityCounts", severityCounts);
        log.debug("错误事件统计: total={}, unanalyzed={}, ignored={}, falsePositive={}",
                total, unanalyzed, ignored, falsePositive);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyze(Long eventId, boolean resetAnalyzed) {
        ErrorEvent event = requireEvent(eventId);
        if (resetAnalyzed) {
            errorEventMapper.updateAnalysisState(eventId, false, ProcessingStatus.ANALYZING.getCode());
        }
        errorAnalysisService.analyzeEvent(eventId, resetAnalyzed ? "MANUAL_RETRY" : "MANUAL");
        log.info("手动提交错误分析: eventId={}, resetAnalyzed={}", event.getId(), resetAnalyzed);
        return Map.of("eventId", event.getId(), "status", "ANALYSIS_SUBMITTED");
    }

    @Override
    public Map<String, Object> retryNotify(Long eventId) {
        ErrorEvent event = requireEvent(eventId);
        ErrorAnalysis analysis = errorAnalysisMapper.findLatestByEventId(eventId);
        boolean sent = notificationService.sendErrorAlert(event, analysis);
        errorEventMapper.updateNotified(eventId, sent);
        log.info("手动重试错误通知: eventId={}, sent={}", eventId, sent);
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
        log.info("人工忽略错误事件: eventId={}, operator={}", eventId, operator);
        return event;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErrorEvent markFalsePositive(Long eventId, String operator, String reason) {
        ErrorEvent event = requireEvent(eventId);
        String message = actionReason("人工标记误报", operator, reason);
        errorEventMapper.markFalsePositive(eventId, ProcessingStatus.FALSE_POSITIVE.getCode(),
                AnalysisDecision.AGGREGATE_ONLY.getCode(), message);
        event.setProcessingStatus(ProcessingStatus.FALSE_POSITIVE.getCode());
        event.setAnalysisDecision(AnalysisDecision.AGGREGATE_ONLY.getCode());
        event.setSeverityReason(message);
        log.info("人工标记错误事件为误报: eventId={}, operator={}", eventId, operator);
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
        log.info("人工补充错误分析结论: eventId={}", eventId);
        return result;
    }

    private ErrorEvent requireEvent(Long eventId) {
        ErrorEvent event = errorEventMapper.findById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "错误事件不存在: " + eventId);
        }
        return event;
    }

    private long countBySeverity(String severity) {
        return errorEventMapper.countBySeverity(severity);
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
        errorEventMapper.updateStatus(eventId, status, reason);
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
