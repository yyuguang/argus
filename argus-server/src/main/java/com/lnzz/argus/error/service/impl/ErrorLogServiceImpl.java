package com.lnzz.argus.error.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lnzz.argus.error.service.ErrorAnalysisService;
import com.lnzz.argus.error.service.ErrorLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.AgentPushBatch;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ErrorContextLog;
import com.lnzz.argus.error.mapper.AgentPushBatchMapper;
import com.lnzz.argus.error.mapper.ErrorContextLogMapper;
import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.error.metrics.ErrorLogMetrics;
import com.lnzz.argus.error.model.BatchLogRequest;
import com.lnzz.argus.error.model.ErrorLogEntry;
import com.lnzz.argus.error.parse.AnalysisDecision;
import com.lnzz.argus.error.parse.ErrorType;
import com.lnzz.argus.error.parse.ErrorTypeIdentifier;
import com.lnzz.argus.error.parse.FingerprintGenerator;
import com.lnzz.argus.error.parse.IdentifierExtractor;
import com.lnzz.argus.error.parse.LogSource;
import com.lnzz.argus.error.parse.ProcessingStatus;
import com.lnzz.argus.error.parse.SeverityLevel;
import com.lnzz.argus.error.parse.SeverityRuleEngine;
import com.lnzz.argus.error.parse.SeveritySource;
import com.lnzz.argus.error.parse.SourceType;
import com.lnzz.argus.error.parse.ParsedStackTrace;
import com.lnzz.argus.error.parse.StackFrame;
import com.lnzz.argus.error.parse.StackTraceParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 错误日志接收服务实现
 * <p>M4-A02/A04: 负责 Agent 推送日志的幂等接收、对象转换、持久化与批次审计</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
public class ErrorLogServiceImpl implements ErrorLogService {

    /** 同指纹去重窗口（秒） */
    private static final long DEDUP_WINDOW_SECONDS = 60;

    private final ErrorEventMapper errorEventMapper;
    private final AgentPushBatchMapper batchMapper;
    private final ObjectMapper objectMapper;
    private final ErrorLogMetrics metrics;
    private final ErrorTypeIdentifier errorTypeIdentifier;
    private final StackTraceParser stackTraceParser;
    private final IdentifierExtractor identifierExtractor;
    private final FingerprintGenerator fingerprintGenerator;
    private final SeverityRuleEngine severityRuleEngine;
    private final ErrorContextLogMapper contextLogMapper;
    private final ErrorAnalysisService analysisService;

    /** 自注入 —— 使批量循环内调用走 Spring AOP 代理 */
    @Lazy
    @Autowired
    private ErrorLogService self;

    public ErrorLogServiceImpl(ErrorEventMapper errorEventMapper,
                               AgentPushBatchMapper batchMapper,
                               ObjectMapper objectMapper,
                               ErrorLogMetrics metrics,
                               ErrorTypeIdentifier errorTypeIdentifier,
                               StackTraceParser stackTraceParser,
                               IdentifierExtractor identifierExtractor,
                               FingerprintGenerator fingerprintGenerator,
                               SeverityRuleEngine severityRuleEngine,
                               ErrorContextLogMapper contextLogMapper,
                               ErrorAnalysisService analysisService) {
        this.errorEventMapper = errorEventMapper;
        this.batchMapper = batchMapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.errorTypeIdentifier = errorTypeIdentifier;
        this.stackTraceParser = stackTraceParser;
        this.identifierExtractor = identifierExtractor;
        this.fingerprintGenerator = fingerprintGenerator;
        this.severityRuleEngine = severityRuleEngine;
        this.contextLogMapper = contextLogMapper;
        this.analysisService = analysisService;
    }

    /**
     * 接收单条日志，幂等写入
     *
     * @param entry 日志条目
     * @return 处理结果: accepted / duplicated
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveSingle(ErrorLogEntry entry) {
        // M4-A06: 按日志来源校验必填字段
        validateByLogSource(entry);

        Map<String, Object> result = new HashMap<>();
        result.put("logId", entry.getLogId());

        ErrorEvent event = toErrorEvent(entry);

        // M4-B04: 指纹去重 —— 同指纹 60s 内聚合而非新建
        ErrorEvent existing = errorEventMapper.findLatestByFingerprint(event.getErrorFingerprint());
        if (existing != null && isWithinDedupWindow(existing)) {
            errorEventMapper.aggregateOccurrence(existing.getId(), event.getOccurredAt(),
                    event.getBusinessKey(), event.getTraceId());
            result.put("status", "AGGREGATED");
            result.put("existingEventId", existing.getId());
            result.put("fingerprint", event.getErrorFingerprint());
            metrics.recordLogDuplicated(entry.getAppName());
            log.debug("同指纹聚合: appName={}, logId={}, fingerprint={}, existingId={}",
                    entry.getAppName(), entry.getLogId(),
                    event.getErrorFingerprint().substring(0, 16), existing.getId());
            return result;
        }

        try {
            errorEventMapper.insert(event);
            result.put("status", "ACCEPTED");
            result.put("errorEventId", event.getId());
            metrics.recordLogReceived(entry.getAppName(), entry.getLogSource(), entry.getLogLevel());

            // M4-B06: 上下文日志快照落库
            saveContextLogs(event.getId(), entry.getContextLogs());

            // 异步 AI 分析 → 等当前事务提交后再触发，避免异步线程读不到 event
            Long eventId = event.getId();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            analysisService.analyzeEvent(eventId);
                        }
                    });

            log.debug("日志已入库: appName={}, logId={}, errorEventId={}",
                    entry.getAppName(), entry.getLogId(), event.getId());
        } catch (DuplicateKeyException e) {
            result.put("status", "DUPLICATED");
            result.put("reason", "appName + logId 重复");
            metrics.recordLogDuplicated(entry.getAppName());
            log.debug("重复日志已忽略: appName={}, logId={}", entry.getAppName(), entry.getLogId());
        }

        return result;
    }

    /**
     * 批量接收日志，每条独立幂等，并生成推送批次审计记录
     *
     * @param request 批量推送请求
     * @return 汇总结果: batchId / acceptedCount / duplicatedCount / errorCount
     */
    @Override
    public Map<String, Object> receiveBatch(BatchLogRequest request) {
        String batchId = UUID.randomUUID().toString();
        List<Map<String, Object>> details = new ArrayList<>();
        int acceptedCount = 0;
        int duplicatedCount = 0;
        int errorCount = 0;

        for (ErrorLogEntry entry : request.getEntries()) {
            try {
                Map<String, Object> singleResult = self.receiveSingle(entry);
                String status = (String) singleResult.get("status");
                if ("ACCEPTED".equals(status)) {
                    acceptedCount++;
                } else {
                    duplicatedCount++;
                }
                details.add(singleResult);
            } catch (Exception e) {
                errorCount++;
                metrics.recordLogError(entry.getAppName() != null ? entry.getAppName() : "UNKNOWN");
                log.error("批量日志处理异常: agentId={}, logId={}", request.getAgentId(), entry.getLogId(), e);
                details.add(Map.of(
                        "logId", entry.getLogId(),
                        "status", "ERROR",
                        "reason", e.getMessage() != null ? e.getMessage() : "未知异常"
                ));
            }
        }

        log.info("批量日志处理完成: batchId={}, agentId={}, total={}, accepted={}, duplicated={}, error={}",
                batchId, request.getAgentId(), request.getEntries().size(), acceptedCount, duplicatedCount, errorCount);

        metrics.recordBatchSize(request.getEntries().size());

        // M4-A04: 记录推送批次审计
        recordBatch(batchId, request.getAgentId(), request.getEntries().size(), acceptedCount, duplicatedCount, errorCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("agentId", request.getAgentId());
        result.put("totalCount", request.getEntries().size());
        result.put("acceptedCount", acceptedCount);
        result.put("duplicatedCount", duplicatedCount);
        result.put("errorCount", errorCount);
        result.put("details", details);
        result.put("receivedAt", LocalDateTime.now().toString());
        return result;
    }

    /**
     * M4-A04: 记录推送批次审计
     */
    private void recordBatch(String batchId, String agentId, int totalCount,
                             int acceptedCount, int duplicatedCount, int errorCount) {
        try {
            AgentPushBatch batch = new AgentPushBatch();
            batch.setBatchId(batchId);
            batch.setAgentId(agentId);
            batch.setEntryCount(totalCount);
            batch.setAcceptedCount(acceptedCount);
            batch.setDuplicatedCount(duplicatedCount);
            batch.setErrorCount(errorCount);
            batch.setStatus("RECEIVED");
            batch.setReceivedAt(LocalDateTime.now());
            batchMapper.insert(batch);
            log.debug("推送批次记录已保存: batchId={}", batchId);
        } catch (Exception e) {
            log.error("推送批次记录保存失败: batchId={}", batchId, e);
        }
    }

    /**
     * 将 Agent 推送的日志条目转换为错误事件实体
     * <p>解析阶段（M4-B）字段暂留空或使用默认值</p>
     */
    private ErrorEvent toErrorEvent(ErrorLogEntry entry) {
        ErrorEvent event = new ErrorEvent();
        event.setLogId(entry.getLogId());
        event.setAppName(entry.getAppName());
        event.setErrorMessage(entry.getMessage());
        event.setOccurredAt(entry.getLogTime());

        // M4-B01: 错误类型识别
        ErrorType identifiedType = errorTypeIdentifier.identify(entry);

        // M4-B07: Nginx 入口异常识别 —— 基于 HTTP 状态码覆盖错误类型
        LogSource logSource = LogSource.fromCode(entry.getLogSource());
        boolean isNginxLog = logSource.isNginx();
        if (isNginxLog && entry.getHttpStatus() != null) {
            ErrorType nginxType = errorTypeIdentifier.identifyNginxError(entry.getHttpStatus());
            if (nginxType != null) {
                identifiedType = nginxType;
            }
        }
        event.setErrorType(identifiedType.name());

        // M4-B02: 异常栈解析 —— 提取 className/methodName/lineNumber/filePath
        ParsedStackTrace parsedStack = stackTraceParser.parse(entry.getStackTrace());
        if (parsedStack.isParsed()) {
            StackFrame topFrame = parsedStack.getTopFrame();
            // 优先使用栈帧中的业务类名（用于源码定位），而非异常类名
            event.setClassName(entry.getClassName() != null
                    ? entry.getClassName()
                    : (topFrame != null ? topFrame.getClassName() : parsedStack.getPrimaryExceptionClass()));
            if (topFrame != null) {
                event.setMethodName(entry.getMethodName() != null
                        ? entry.getMethodName() : topFrame.getMethodName());
                event.setLineNumber(entry.getLineNumber() != null
                        ? entry.getLineNumber() : topFrame.getLineNumber());
                if (topFrame.getFileName() != null && topFrame.getLineNumber() != null) {
                    event.setFilePath(topFrame.getClassName().replace('.', '/')
                            + ".java:" + topFrame.getLineNumber());
                } else if (topFrame.getFileName() != null) {
                    event.setFilePath(topFrame.getClassName().replace('.', '/') + ".java");
                }
            } else {
                event.setMethodName(entry.getMethodName());
                event.setLineNumber(entry.getLineNumber());
            }
        } else {
            event.setClassName(entry.getClassName());
            event.setMethodName(entry.getMethodName());
            event.setLineNumber(entry.getLineNumber());
        }
        // M4-B03: traceId/businessKey/interfaceRef 提取
        String traceId = identifierExtractor.extractTraceId(entry.getMessage(), entry.getTraceId());
        String businessKey = identifierExtractor.extractBusinessKey(entry.getMessage(), entry.getBusinessKey());
        String interfaceRef = identifierExtractor.inferInterfaceRef(entry.getInterfaceRef(),
                event.getClassName(), event.getMethodName(), entry.getRequestUri());
        event.setTraceId(traceId);
        event.setBusinessKey(businessKey);
        event.setInterfaceRef(interfaceRef);
        event.setRawStackTrace(entry.getStackTrace());

        // M4-B04: 错误指纹生成（SHA-256）
        String fingerprint = fingerprintGenerator.generate(
                event.getAppName(), event.getErrorType(),
                event.getClassName(), event.getMethodName(), event.getLineNumber(),
                parsedStack.isParsed() ? parsedStack.getRootCauseClass() : null);
        event.setErrorFingerprint(fingerprint);

        // JSON 字段序列化
        if (entry.getContextLogs() != null && !entry.getContextLogs().isEmpty()) {
            event.setContextLogs(toJsonQuietly(entry.getContextLogs()));
        }
        if (entry.getRequestInfo() != null && !entry.getRequestInfo().isEmpty()) {
            event.setRequestInfo(toJsonQuietly(entry.getRequestInfo()));
        }

        event.setAnalyzed(false);
        event.setNotified(false);

        // M4-B04: 聚合字段初始化
        event.setEnvironment(entry.getEnvironment());
        event.setHostName(entry.getHost());
        event.setOccurrenceCount(1);
        event.setFirstOccurredAt(entry.getLogTime());
        event.setLastOccurredAt(entry.getLogTime());
        event.setLastBusinessKey(businessKey);
        event.setLastTraceId(traceId);
        event.setProcessingStatus(ProcessingStatus.PARSED.getCode());

        // M4-B05: 规则初判严重度与触发决策
        boolean isNewFingerprint = true;
        SeverityRuleEngine.SeverityResult severityResult = severityRuleEngine.evaluate(
                event.getErrorType(), entry.getEnvironment(), isNewFingerprint);
        event.setInitialSeverity(severityResult.severity().getCode());
        event.setSeverity(severityResult.severity().getCode());
        event.setSeveritySource(SeveritySource.RULE.getCode());
        event.setSeverityReason(severityResult.reason());
        event.setSeverityConfidence(java.math.BigDecimal.valueOf(severityResult.confidence()));
        event.setAnalysisDecision(severityResult.analysisDecision().getCode());
        event.setSourceType(isNginxLog ? SourceType.NGINX.getCode() : SourceType.AGENT.getCode());

        // M4-B08: Nginx 字段解析 —— 结构化提取入口路由、upstream 等字段
        if (isNginxLog) {
            applyNginxFields(entry, event);
        }

        return event;
    }

    /**
     * M4-B06: 保存上下文日志快照
     */
    private void saveContextLogs(Long errorEventId, List<String> contextLogs) {
        if (contextLogs == null || contextLogs.isEmpty()) {
            return;
        }
        try {
            List<ErrorContextLog> logs = new ArrayList<>(contextLogs.size());
            for (int i = 0; i < contextLogs.size(); i++) {
                ParsedLogLine parsed = parseLogLine(contextLogs.get(i));
                ErrorContextLog ctxLog = new ErrorContextLog();
                ctxLog.setErrorEventId(errorEventId);
                ctxLog.setLogTime(parsed.logTime);
                ctxLog.setLogLevel(parsed.logLevel);
                ctxLog.setLoggerName(parsed.loggerName);
                ctxLog.setThreadName(parsed.threadName);
                ctxLog.setTraceId(parsed.traceId);
                ctxLog.setMessage(parsed.message);
                ctxLog.setSortOrder(i);
                logs.add(ctxLog);
            }
            for (ErrorContextLog ctxLog : logs) {
                contextLogMapper.insert(ctxLog);
            }
            log.debug("上下文日志快照已保存: eventId={}, count={}", errorEventId, logs.size());
        } catch (Exception e) {
            log.warn("上下文日志快照保存失败: eventId={}", errorEventId, e);
        }
    }

    /**
     * 解析单行应用日志
     */
    private ParsedLogLine parseLogLine(String line) {
        ParsedLogLine result = new ParsedLogLine();
        if (line == null || line.isBlank()) {
            return result;
        }
        try {
            if (line.length() >= 23) {
                String timeStr = line.substring(0, 23);
                try {
                    result.logTime = LocalDateTime.parse(timeStr,
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                } catch (Exception ignored) {
                }
            }
            int threadStart = line.indexOf('[');
            int threadEnd = line.indexOf(']', threadStart + 1);
            if (threadStart >= 0 && threadEnd > threadStart) {
                result.threadName = line.substring(threadStart + 1, threadEnd);
            }
            int afterBracket = threadEnd >= 0 ? threadEnd + 1 : 23;
            if (afterBracket < line.length()) {
                String rest = line.substring(afterBracket).trim();
                String[] parts = rest.split("\\s+", 3);
                if (parts.length >= 1) {
                    result.logLevel = parts[0];
                }
                if (parts.length >= 2) {
                    result.loggerName = parts[1];
                }
                if (parts.length >= 3) {
                    String msg = parts[2];
                    int tidx = msg.indexOf("traceId=");
                    if (tidx >= 0) {
                        int endIdx = msg.indexOf(',', tidx);
                        if (endIdx < 0) endIdx = msg.indexOf(' ', tidx);
                        if (endIdx < 0) endIdx = msg.indexOf(']', tidx);
                        if (endIdx < 0) endIdx = msg.length();
                        result.traceId = msg.substring(tidx + 8, endIdx);
                    }
                    result.message = msg;
                }
            }
        } catch (Exception e) {
            result.message = line;
        }
        return result;
    }

    private static class ParsedLogLine {
        LocalDateTime logTime;
        String logLevel;
        String loggerName;
        String threadName;
        String traceId;
        String message;
    }

    /**
     * M4-B08: 解析 Nginx 特有字段，结构化写入 ErrorEvent
     */
    private void applyNginxFields(ErrorLogEntry entry, ErrorEvent event) {
        Map<String, Object> nginxExtra = new LinkedHashMap<>();

        if (entry.getHttpStatus() != null) {
            nginxExtra.put("httpStatus", entry.getHttpStatus());
        }
        if (entry.getRequestMethod() != null) {
            nginxExtra.put("requestMethod", entry.getRequestMethod());
        }
        if (entry.getRemoteAddr() != null) {
            nginxExtra.put("remoteAddr", entry.getRemoteAddr());
        }
        if (entry.getRequestTime() != null) {
            nginxExtra.put("requestTime", entry.getRequestTime());
        }

        if (entry.getRequestUri() != null && !entry.getRequestUri().isEmpty()) {
            nginxExtra.put("requestUri", entry.getRequestUri());
            if (event.getInterfaceRef() == null || event.getInterfaceRef().isEmpty()) {
                event.setInterfaceRef(entry.getRequestUri());
            }
        }

        if (entry.getUpstreamAddr() != null) {
            nginxExtra.put("upstreamAddr", entry.getUpstreamAddr());
        }
        if (entry.getUpstreamStatus() != null) {
            nginxExtra.put("upstreamStatus", entry.getUpstreamStatus());
        }
        if (entry.getUpstreamConnectTime() != null) {
            nginxExtra.put("upstreamConnectTime", entry.getUpstreamConnectTime());
        }
        if (entry.getUpstreamResponseTime() != null) {
            nginxExtra.put("upstreamResponseTime", entry.getUpstreamResponseTime());
        }

        if (!nginxExtra.isEmpty()) {
            String existingRequestInfo = event.getRequestInfo();
            if (existingRequestInfo != null && !existingRequestInfo.isEmpty() && !"[]".equals(existingRequestInfo)) {
                event.setRequestInfo(existingRequestInfo.substring(0, existingRequestInfo.length() - 1)
                        + ",\"nginx\":" + toJsonQuietly(nginxExtra) + "}");
            } else {
                event.setRequestInfo("{\"nginx\":" + toJsonQuietly(nginxExtra) + "}");
            }
        }
    }

    /**
     * 判断已有事件是否处于去重窗口内
     */
    private boolean isWithinDedupWindow(ErrorEvent existing) {
        if (existing.getLastOccurredAt() == null && existing.getOccurredAt() == null) {
            return false;
        }
        java.time.LocalDateTime lastTime = existing.getLastOccurredAt() != null
                ? existing.getLastOccurredAt() : existing.getOccurredAt();
        return java.time.Duration.between(lastTime, java.time.LocalDateTime.now())
                .getSeconds() < DEDUP_WINDOW_SECONDS;
    }

    /**
     * M4-A06: 按日志来源校验必填字段
     */
    private void validateByLogSource(ErrorLogEntry entry) {
        String source = entry.getLogSource();
        if (source == null || source.isEmpty()) {
            return;
        }
        LogSource logSource = LogSource.fromCode(source);
        switch (logSource) {
            case NGINX_ACCESS:
                if (entry.getHttpStatus() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "NGINX_ACCESS 日志必须提供 httpStatus");
                }
                if (entry.getRequestUri() == null || entry.getRequestUri().isEmpty()) {
                    throw new BizException(ResultCode.PARAM_ERROR, "NGINX_ACCESS 日志必须提供 requestUri");
                }
                if (entry.getRemoteAddr() == null || entry.getRemoteAddr().isEmpty()) {
                    throw new BizException(ResultCode.PARAM_ERROR, "NGINX_ACCESS 日志必须提供 remoteAddr");
                }
                break;
            case NGINX_ERROR:
                if (entry.getRequestUri() == null || entry.getRequestUri().isEmpty()) {
                    throw new BizException(ResultCode.PARAM_ERROR, "NGINX_ERROR 日志必须提供 requestUri");
                }
                break;
            default:
                break;
        }
    }

    private String toJsonQuietly(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }
}
