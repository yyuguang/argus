package com.lnzz.argus.error.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.ai.AnalysisSource;
import com.lnzz.argus.error.ai.ErrorAnalysisEngine;
import com.lnzz.argus.error.ai.ErrorAnalysisPromptBuilder;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.mapper.ErrorAnalysisMapper;
import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.error.parse.SeveritySource;
import com.lnzz.argus.error.service.ErrorAnalysisService;
import com.lnzz.argus.error.service.SourceCodeLocator;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.service.KnowledgeMatcher;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 错误分析编排服务实现
 * <p>串联 Prompt 构建 → AI 分析 → 结果落库 → 严重度回写 → 自动通知流程</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorAnalysisServiceImpl implements ErrorAnalysisService {

    private final ErrorAnalysisMapper analysisMapper;
    private final ErrorEventMapper eventMapper;
    private final ErrorAnalysisEngine analysisEngine;
    private final ErrorAnalysisPromptBuilder promptBuilder;
    private final SourceCodeLocator sourceCodeLocator;
    private final NotificationService notificationService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeMatcher knowledgeMatcher;
    private final VectorKnowledgeService vectorKnowledgeService;

    @Value("${argus.vector.enabled:false}")
    private boolean vectorEnabled;

    @Value("${argus.vector.error-search-topk:5}")
    private int errorSearchTopk;

    @Value("${argus.vector.knowledge-min-score:0.7}")
    private double knowledgeMinScore;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void  analyzeEvent(Long eventId) {
        ErrorEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            log.warn("ErrorEvent 不存在: id={}", eventId);
            return;
        }

        if (Boolean.TRUE.equals(event.getAnalyzed())) {
            log.info("ErrorEvent 已分析过，跳过: id={}", eventId);
            return;
        }

        try {
            // 1. 源码定位
            SourceCodeLocator.SourceLocation location = sourceCodeLocator.locate(event);
            log.info("源码定位完成: eventId={}, found={}", eventId, location.found());

            // 2. M5-B02: 查询历史相似案例
            List<ErrorAnalysis> historyCases = findHistoryCases(event);

            // 3. M5-B01: 构建 Prompt
            String prompt = promptBuilder.buildAnalysisPrompt(event, location, historyCases);

            // 4. M5-B03/B05: AI 分析（含重试降级）
            ErrorAnalysis analysis = analysisEngine.analyze(prompt, event);

            // 5. 落库
            analysisMapper.insert(analysis);
            log.info("AI分析结果已落库: eventId={}, analysisId={}, severity={}, confidence={}",
                    eventId, analysis.getId(), analysis.getFinalSeverity(), analysis.getConfidence());

            // 6. M5-B04: 严重度 AI 校准 — 回写 ErrorEvent
            updateEventSeverity(event, analysis);

            // 7. 自动推送通知
            ErrorEvent updatedEvent = eventMapper.selectById(eventId);
            notificationService.sendErrorAlert(updatedEvent, analysis);
            log.info("错误告警通知已推送: eventId={}, severity={}", eventId, updatedEvent.getSeverity());

            // 7.5 M8-A01: 自动生成知识条目草稿
            try {
                knowledgeService.generateDraft(updatedEvent, analysis);
            } catch (Exception e) {
                log.warn("知识草稿生成失败（不影响主流程）: eventId={}", eventId, e);
            }

            // 8. 标记已分析
            eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                    .eq(ErrorEvent::getId, eventId)
                    .set(ErrorEvent::getAnalyzed, true)
                    .set(ErrorEvent::getNotified, true));

            log.info("错误分析完成: eventId={}, analysisId={}", eventId, analysis.getId());

        } catch (Exception e) {
            log.error("错误分析失败: eventId={}", eventId, e);
            eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                    .eq(ErrorEvent::getId, eventId)
                    .set(ErrorEvent::getAnalyzed, true));
        }
    }

    private List<ErrorAnalysis> findHistoryCases(ErrorEvent event) {
        if (vectorEnabled) {
            List<ErrorAnalysis> vectorCases = findHistoryCasesByVector(event);
            if (!vectorCases.isEmpty()) {
                log.debug("向量检索命中历史案例: eventId={}, count={}", event.getId(), vectorCases.size());
                return vectorCases;
            }
            log.debug("向量检索未命中历史案例，回退字符串匹配: eventId={}", event.getId());
        }

        if (knowledgeMatcher == null) {
            return Collections.emptyList();
        }
        return knowledgeMatcher.findSimilar(event, 3).stream()
                .map(this::convertKnowledgeEntryToHistoryCase)
                .toList();
    }

    private List<ErrorAnalysis> findHistoryCasesByVector(ErrorEvent event) {
        if (vectorKnowledgeService == null) {
            return Collections.emptyList();
        }
        String queryText = buildVectorQueryText(event);
        if (queryText.isBlank()) {
            return Collections.emptyList();
        }
        List<Document> documents = vectorKnowledgeService.searchSimilarErrors(
                queryText, event.getErrorType(), event.getAppName(), errorSearchTopk, 0.0d);
        return documents.stream()
                .filter(Objects::nonNull)
                .filter(this::matchesKnowledgeScore)
                .limit(3)
                .map(this::convertVectorDocumentToHistoryCase)
                .collect(Collectors.toList());
    }

    private boolean matchesKnowledgeScore(Document document) {
        Double score = document.getScore();
        return score == null || score >= knowledgeMinScore;
    }

    private ErrorAnalysis convertVectorDocumentToHistoryCase(Document document) {
        ErrorAnalysis historyCase = new ErrorAnalysis();
        historyCase.setRootCause(readMetadataText(document, "root_cause", document.getText()));
        historyCase.setFixDescription(readMetadataText(document, "fix_suggestion", null));
        historyCase.setPreventionAdvice(readMetadataText(document, "prevention_advice", null));
        historyCase.setFinalSeverity(readMetadataText(document, "severity", null));
        return historyCase;
    }

    private ErrorAnalysis convertKnowledgeEntryToHistoryCase(KnowledgeEntry entry) {
        ErrorAnalysis historyCase = new ErrorAnalysis();
        historyCase.setRootCause(entry.getRootCause() != null ? entry.getRootCause() : entry.getErrorPattern());
        historyCase.setFixDescription(entry.getFixSuggestion());
        historyCase.setPreventionAdvice(entry.getPreventionAdvice());
        return historyCase;
    }

    private String buildVectorQueryText(ErrorEvent event) {
        StringBuilder builder = new StringBuilder();
        if (event.getErrorType() != null) {
            builder.append(event.getErrorType());
        }
        if (event.getAppName() != null) {
            builder.append(' ').append(event.getAppName());
        }
        if (event.getClassName() != null) {
            builder.append(' ').append(event.getClassName());
        }
        if (event.getErrorMessage() != null) {
            builder.append(' ').append(event.getErrorMessage());
        }
        return builder.toString().trim();
    }

    private String readMetadataText(Document document, String key, String defaultValue) {
        Object value = document.getMetadata().get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private void updateEventSeverity(ErrorEvent event, ErrorAnalysis analysis) {
        String aiSeverity = analysis.getFinalSeverity();
        if (aiSeverity == null) return;

        boolean changed = !aiSeverity.equals(event.getSeverity());
        eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                .eq(ErrorEvent::getId, event.getId())
                .set(ErrorEvent::getSeverity, aiSeverity)
                .set(ErrorEvent::getSeveritySource, SeveritySource.AI.getCode())
                .set(changed, ErrorEvent::getSeverityReason,
                        "AI校准: " + (analysis.getRootCause() != null
                                ? analysis.getRootCause().substring(0, Math.min(100, analysis.getRootCause().length()))
                                : "")));

        if (changed) {
            log.info("严重度已校准: eventId={}, {} → {}", event.getId(), event.getSeverity(), aiSeverity);
        }
    }

    @Override
    public ErrorAnalysis getAnalysisByEventId(Long eventId) {
        return analysisMapper.selectOne(
                new LambdaQueryWrapper<ErrorAnalysis>()
                        .eq(ErrorAnalysis::getErrorEventId, eventId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErrorAnalysis supplementManual(Long eventId, String rootCause, String severity,
                                           String fixDescription, String preventionAdvice) {
        ErrorEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "ErrorEvent 不存在: " + eventId);
        }

        ErrorAnalysis existing = getAnalysisByEventId(eventId);
        if (existing != null) {
            if (rootCause != null && !rootCause.isBlank()) {
                existing.setRootCause(rootCause);
            }
            if (severity != null && !severity.isBlank()) {
                String normalized = severity.trim().toUpperCase();
                if (!normalized.matches("^P[0-3]$")) normalized = "P3";
                existing.setFinalSeverity(normalized);
                eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                        .eq(ErrorEvent::getId, eventId)
                        .set(ErrorEvent::getSeverity, normalized)
                        .set(ErrorEvent::getSeveritySource, SeveritySource.MANUAL.getCode()));
            }
            if (fixDescription != null && !fixDescription.isBlank()) {
                existing.setFixDescription(fixDescription);
            }
            if (preventionAdvice != null && !preventionAdvice.isBlank()) {
                existing.setPreventionAdvice(preventionAdvice);
            }
            existing.setSource(AnalysisSource.HYBRID.getCode());
            analysisMapper.updateById(existing);
            log.info("人工结论已补充(更新): eventId={}, analysisId={}", eventId, existing.getId());
            return existing;
        } else {
            ErrorAnalysis manual = new ErrorAnalysis();
            manual.setErrorEventId(eventId);
            manual.setRootCause(rootCause != null ? rootCause : "人工分析，待补充");
            manual.setFinalSeverity(severity != null ? severity : event.getSeverity());
            manual.setFixDescription(fixDescription);
            manual.setPreventionAdvice(preventionAdvice);
            manual.setConfidence(BigDecimal.valueOf(0.9));
            manual.setSource(AnalysisSource.MANUAL.getCode());
            analysisMapper.insert(manual);

            if (severity != null) {
                eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                        .eq(ErrorEvent::getId, eventId)
                        .set(ErrorEvent::getSeverity, severity)
                        .set(ErrorEvent::getSeveritySource, SeveritySource.MANUAL.getCode()));
            }
            eventMapper.update(null, new LambdaUpdateWrapper<ErrorEvent>()
                    .eq(ErrorEvent::getId, eventId)
                    .set(ErrorEvent::getAnalyzed, true));

            log.info("人工分析已创建: eventId={}, analysisId={}", eventId, manual.getId());
            return manual;
        }
    }
}
