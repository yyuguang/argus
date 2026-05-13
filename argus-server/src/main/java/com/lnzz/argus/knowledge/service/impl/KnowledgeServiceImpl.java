package com.lnzz.argus.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.entity.KnowledgeAudit;
import com.lnzz.argus.common.enums.KnowledgeAuditAction;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.common.enums.KnowledgeEntryStatus;
import com.lnzz.argus.knowledge.mapper.KnowledgeAuditMapper;
import com.lnzz.argus.knowledge.mapper.KnowledgeEntryMapper;
import com.lnzz.argus.knowledge.service.KnowledgeMatcher;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库服务实现（M8）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeEntryMapper entryMapper;
    private final KnowledgeAuditMapper auditMapper;
    private final KnowledgeMatcher knowledgeMatcher;
    private final VectorKnowledgeService vectorKnowledgeService;

    // ======================== M8-A01: 草稿生成 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry generateDraft(ErrorEvent event, ErrorAnalysis analysis) {
        // 同指纹已有草稿/确认/白名单条目时，回写发生次数，避免同一故障模式无限生成草稿。
        String fingerprint = event.getErrorFingerprint();
        if (fingerprint != null && !fingerprint.isEmpty()) {
            KnowledgeEntry reusable = entryMapper.findReusableByFingerprint(fingerprint);
            if (reusable != null) {
                aggregateKnowledgeOccurrence(reusable, event, analysis);
                log.debug("同指纹知识条目已复用并更新发生次数: entryId={}, fingerprint={}",
                        reusable.getId(), fingerprint.substring(0, Math.min(16, fingerprint.length())));
                return reusable;
            }
        }

        // 同应用同类型草稿作为相似故障模式候选合并，避免近似问题不断生成新草稿。
        if (event.getErrorType() != null && event.getAppName() != null) {
            KnowledgeEntry similarDraft = entryMapper.findDraftByErrorTypeAndApp(event.getErrorType(), event.getAppName());
            if (similarDraft != null) {
                aggregateKnowledgeOccurrence(similarDraft, event, analysis);
                log.debug("同应用同类型知识草稿已合并: entryId={}, appName={}, errorType={}",
                        similarDraft.getId(), event.getAppName(), event.getErrorType());
                return similarDraft;
            }
        }

        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setErrorFingerprint(fingerprint);
        entry.setErrorType(event.getErrorType());
        entry.setAppName(event.getAppName());
        entry.setTitle(buildTitle(event));
        entry.setErrorPattern(buildErrorPattern(event));
        entry.setRootCause(analysis != null && analysis.getRootCause() != null
                ? analysis.getRootCause() : "待AI分析补充");
        entry.setFixSuggestion(analysis != null ? analysis.getFixDescription() : null);
        entry.setPreventionAdvice(analysis != null ? analysis.getPreventionAdvice() : null);
        entry.setSourceEventId(event.getId());
        entry.setSourceAnalysisId(analysis != null ? analysis.getId() : null);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        entry.setSource("AUTO");
        entry.setOccurrenceCount(event.getOccurrenceCount() != null ? event.getOccurrenceCount() : 1);
        entry.setLastOccurredAt(event.getLastOccurredAt() != null ? event.getLastOccurredAt() : event.getOccurredAt());
        entry.setTagsJson(buildTags(event, analysis));

        entryMapper.insert(entry);
        vectorKnowledgeService.storeKnowledgeEntry(entry);
        log.info("知识条目草稿已生成: id={}, title={}, errorType={}", entry.getId(), entry.getTitle(), event.getErrorType());
        return entry;
    }

    private String buildTitle(ErrorEvent event) {
        String app = event.getAppName() != null ? event.getAppName() : "未知应用";
        String type = event.getErrorType() != null ? event.getErrorType() : "未知错误";
        String className = event.getClassName();
        if (className != null && className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        return "[" + app + "] " + type + (className != null ? " - " + className : "");
    }

    private String buildErrorPattern(ErrorEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("错误类型: ").append(event.getErrorType()).append("\n");
        if (event.getErrorMessage() != null) {
            String msg = event.getErrorMessage();
            sb.append("错误消息: ").append(msg.length() > 500 ? msg.substring(0, 500) + "..." : msg).append("\n");
        }
        if (event.getClassName() != null) {
            sb.append("发生类: ").append(event.getClassName());
            if (event.getMethodName() != null) {
                sb.append(".").append(event.getMethodName()).append("()");
            }
            if (event.getLineNumber() != null) {
                sb.append(" [行 ").append(event.getLineNumber()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void aggregateKnowledgeOccurrence(KnowledgeEntry entry, ErrorEvent event, ErrorAnalysis analysis) {
        int delta = event.getOccurrenceCount() != null && event.getOccurrenceCount() > 0
                ? event.getOccurrenceCount()
                : 1;
        LocalDateTime lastOccurredAt = event.getLastOccurredAt() != null
                ? event.getLastOccurredAt()
                : event.getOccurredAt();
        entryMapper.aggregateKnowledgeOccurrence(entry.getId(), delta, lastOccurredAt,
                event.getId(), analysis != null ? analysis.getId() : null);
        entry.setOccurrenceCount((entry.getOccurrenceCount() == null ? 0 : entry.getOccurrenceCount()) + delta);
        if (entry.getLastOccurredAt() == null
                || (lastOccurredAt != null && lastOccurredAt.isAfter(entry.getLastOccurredAt()))) {
            entry.setLastOccurredAt(lastOccurredAt);
        }
    }

    private String buildTags(ErrorEvent event, ErrorAnalysis analysis) {
        StringBuilder sb = new StringBuilder("[");
        appendTag(sb, "sourceEventId:" + event.getId());
        if (analysis != null) {
            appendTag(sb, "sourceAnalysisId:" + analysis.getId());
            if (analysis.getConfidence() != null) {
                appendTag(sb, "confidence:" + analysis.getConfidence());
            }
        }
        if (event.getSeverity() != null) {
            appendTag(sb, "severity:" + event.getSeverity());
        }
        sb.append("]");
        return sb.toString();
    }

    private void appendTag(StringBuilder sb, String value) {
        if (value == null || value.endsWith(":null")) {
            return;
        }
        if (sb.length() > 1) {
            sb.append(",");
        }
        sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
    }

    // ======================== M8-A02: 相似检索 ========================

    @Override
    public List<KnowledgeEntry> findSimilar(ErrorEvent event, int maxResults) {
        return knowledgeMatcher.findSimilar(event, maxResults);
    }

    // ======================== M8-A03: 人工操作 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry confirm(Long entryId, String operator, String comment) {
        KnowledgeEntry entry = requireEntry(entryId);
        String beforeStatus = entry.getStatus();
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        entry.setConfirmedBy(operator);
        entry.setConfirmedAt(LocalDateTime.now());
        entryMapper.updateById(entry);

        writeAudit(entryId, KnowledgeAuditAction.CONFIRM, operator, comment,
                beforeStatus, KnowledgeEntryStatus.CONFIRMED.getCode());
        log.info("知识条目已确认: id={}, operator={}", entryId, operator);
        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry markFalsePositive(Long entryId, String operator, String comment) {
        KnowledgeEntry entry = requireEntry(entryId);
        String beforeStatus = entry.getStatus();
        entry.setStatus(KnowledgeEntryStatus.FALSE_POSITIVE.getCode());
        entryMapper.updateById(entry);

        writeAudit(entryId, KnowledgeAuditAction.MARK_FALSE_POSITIVE, operator, comment,
                beforeStatus, KnowledgeEntryStatus.FALSE_POSITIVE.getCode());
        log.info("知识条目已标记为误报: id={}, operator={}", entryId, operator);
        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry ignore(Long entryId, String operator, String comment) {
        KnowledgeEntry entry = requireEntry(entryId);
        String beforeStatus = entry.getStatus();
        entry.setStatus(KnowledgeEntryStatus.OUTDATED.getCode());
        entryMapper.updateById(entry);

        writeAudit(entryId, KnowledgeAuditAction.IGNORE, operator, comment,
                beforeStatus, KnowledgeEntryStatus.OUTDATED.getCode());
        log.info("知识条目已忽略: id={}, operator={}", entryId, operator);
        return entry;
    }

    // ======================== M8-A05: 白名单管理 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry promoteWhitelist(Long entryId, String operator) {
        KnowledgeEntry entry = requireEntry(entryId);
        if (!KnowledgeEntryStatus.CONFIRMED.getCode().equals(entry.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "仅已确认条目可提升为白名单，当前状态: " + entry.getStatus());
        }
        String beforeStatus = entry.getStatus();
        entry.setStatus(KnowledgeEntryStatus.WHITELIST.getCode());
        entryMapper.updateById(entry);

        writeAudit(entryId, KnowledgeAuditAction.PROMOTE_WHITELIST, operator, "提升为白名单",
                beforeStatus, KnowledgeEntryStatus.WHITELIST.getCode());
        log.info("知识条目已提升为白名单: id={}, operator={}", entryId, operator);
        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeEntry demoteWhitelist(Long entryId, String operator) {
        KnowledgeEntry entry = requireEntry(entryId);
        if (!KnowledgeEntryStatus.WHITELIST.getCode().equals(entry.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "仅白名单条目可降级，当前状态: " + entry.getStatus());
        }
        String beforeStatus = entry.getStatus();
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        entryMapper.updateById(entry);

        writeAudit(entryId, KnowledgeAuditAction.DEMOTE_WHITELIST, operator, "从白名单降级",
                beforeStatus, KnowledgeEntryStatus.CONFIRMED.getCode());
        log.info("知识条目已从白名单降级: id={}, operator={}", entryId, operator);
        return entry;
    }

    @Override
    public List<KnowledgeEntry> findWhitelistCandidates(int minOccurrence) {
        return entryMapper.findWhitelistCandidates(minOccurrence);
    }

    // ======================== 查询 ========================

    @Override
    public KnowledgeEntry getById(Long id) {
        return entryMapper.selectById(id);
    }

    @Override
    public List<KnowledgeEntry> listByStatus(String status) {
        return entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .eq(KnowledgeEntry::getStatus, status)
                        .orderByDesc(KnowledgeEntry::getOccurrenceCount));
    }

    @Override
    public List<KnowledgeEntry> listByErrorType(String errorType) {
        return entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .eq(KnowledgeEntry::getErrorType, errorType)
                        .in(KnowledgeEntry::getStatus,
                                KnowledgeEntryStatus.CONFIRMED.getCode(),
                                KnowledgeEntryStatus.WHITELIST.getCode())
                        .orderByDesc(KnowledgeEntry::getOccurrenceCount));
    }

    @Override
    public List<KnowledgeEntry> listEntries(String status, String errorType, String appName) {
        LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<KnowledgeEntry>()
                .orderByDesc(KnowledgeEntry::getOccurrenceCount)
                .orderByDesc(KnowledgeEntry::getLastOccurredAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeEntry::getStatus, status);
        }
        if (errorType != null && !errorType.isBlank()) {
            wrapper.eq(KnowledgeEntry::getErrorType, errorType);
        }
        if (appName != null && !appName.isBlank()) {
            wrapper.eq(KnowledgeEntry::getAppName, appName);
        }
        if ((status == null || status.isBlank())
                && (errorType == null || errorType.isBlank())
                && (appName == null || appName.isBlank())) {
            wrapper.in(KnowledgeEntry::getStatus,
                    KnowledgeEntryStatus.CONFIRMED.getCode(),
                    KnowledgeEntryStatus.WHITELIST.getCode());
        }
        return entryMapper.selectList(wrapper);
    }

    // ======================== M8-A04: 操作留痕 ========================

    @Override
    public List<KnowledgeAudit> getAuditLog(Long entryId) {
        return auditMapper.findByEntryId(entryId);
    }

    // ======================== 内部工具 ========================

    private KnowledgeEntry requireEntry(Long id) {
        KnowledgeEntry entry = entryMapper.selectById(id);
        if (entry == null) {
            throw new BizException(ResultCode.NOT_FOUND, "知识条目不存在: " + id);
        }
        return entry;
    }

    private void writeAudit(Long entryId, KnowledgeAuditAction action, String operator, String comment,
                            String beforeStatus, String afterStatus) {
        KnowledgeAudit audit = new KnowledgeAudit();
        audit.setKnowledgeEntryId(entryId);
        audit.setAction(action.getCode());
        audit.setOperator(operator);
        audit.setComment(comment);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        auditMapper.insert(audit);
    }
}
