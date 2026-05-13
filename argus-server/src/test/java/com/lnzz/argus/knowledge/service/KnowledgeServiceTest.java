package com.lnzz.argus.knowledge.service;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.entity.KnowledgeAudit;
import com.lnzz.argus.common.enums.KnowledgeAuditAction;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.common.enums.KnowledgeEntryStatus;
import com.lnzz.argus.knowledge.mapper.KnowledgeAuditMapper;
import com.lnzz.argus.knowledge.mapper.KnowledgeEntryMapper;
import com.lnzz.argus.knowledge.service.impl.KnowledgeServiceImpl;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeService - 状态机测试")
class KnowledgeServiceTest {

    @Mock
    private KnowledgeEntryMapper entryMapper;
    @Mock
    private KnowledgeAuditMapper auditMapper;
    @Mock
    private KnowledgeMatcher knowledgeMatcher;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;

    private KnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeServiceImpl(entryMapper, auditMapper, knowledgeMatcher, vectorKnowledgeService);
    }

    @Test
    @DisplayName("generateDraft 落库后同步写入向量库")
    void generateDraftStoresVectorEntry() {
        ErrorEvent event = new ErrorEvent();
        event.setId(11L);
        event.setAppName("order-service");
        event.setErrorType("NULL_POINTER");
        event.setErrorMessage("dto is null");
        event.setOccurrenceCount(2);
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setId(22L);
        analysis.setRootCause("DTO 未判空");
        analysis.setFixDescription("增加参数校验");

        KnowledgeEntry entry = service.generateDraft(event, analysis);

        assertNotNull(entry);
        verify(entryMapper).insert(any(KnowledgeEntry.class));
        verify(vectorKnowledgeService).storeKnowledgeEntry(any(KnowledgeEntry.class));
    }

    @Test
    @DisplayName("generateDraft 同指纹已有知识时回写次数，不重复生成草稿")
    void generateDraftAggregatesReusableFingerprint() {
        ErrorEvent event = new ErrorEvent();
        event.setId(11L);
        event.setAppName("order-service");
        event.setErrorType("NULL_POINTER");
        event.setErrorFingerprint("fp-001");
        event.setOccurrenceCount(3);
        KnowledgeEntry existing = new KnowledgeEntry();
        existing.setId(100L);
        existing.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        existing.setOccurrenceCount(7);
        when(entryMapper.findReusableByFingerprint("fp-001")).thenReturn(existing);

        KnowledgeEntry result = service.generateDraft(event, new ErrorAnalysis());

        assertEquals(100L, result.getId());
        assertEquals(10, result.getOccurrenceCount());
        verify(entryMapper).aggregateKnowledgeOccurrence(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(3),
                any(),
                org.mockito.ArgumentMatchers.eq(11L),
                any());
        verify(entryMapper, never()).insert(any(KnowledgeEntry.class));
        verify(vectorKnowledgeService, never()).storeKnowledgeEntry(any(KnowledgeEntry.class));
    }

    @Test
    @DisplayName("generateDraft 同应用同类型草稿存在时合并为候选")
    void generateDraftMergesSimilarDraftByTypeAndApp() {
        ErrorEvent event = new ErrorEvent();
        event.setId(12L);
        event.setAppName("order-service");
        event.setErrorType("TIMEOUT");
        event.setOccurrenceCount(1);
        KnowledgeEntry draft = new KnowledgeEntry();
        draft.setId(101L);
        draft.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        draft.setOccurrenceCount(2);
        when(entryMapper.findDraftByErrorTypeAndApp("TIMEOUT", "order-service")).thenReturn(draft);

        KnowledgeEntry result = service.generateDraft(event, new ErrorAnalysis());

        assertEquals(101L, result.getId());
        assertEquals(3, result.getOccurrenceCount());
        verify(entryMapper).aggregateKnowledgeOccurrence(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(1),
                any(),
                org.mockito.ArgumentMatchers.eq(12L),
                any());
        verify(entryMapper, never()).insert(any(KnowledgeEntry.class));
    }

    // ======================== DRAFT → CONFIRMED ========================

    @Test
    @DisplayName("DRAFT → CONFIRMED（正常确认）")
    void draftToConfirmed() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.confirm(1L, "user1", "确认有效");

        assertEquals(KnowledgeEntryStatus.CONFIRMED.getCode(), result.getStatus());
        assertEquals("user1", result.getConfirmedBy());
        assertNotNull(result.getConfirmedAt());

        verify(entryMapper).updateById(entry);
        verify(auditMapper).insert(any(KnowledgeAudit.class));
    }

    @Test
    @DisplayName("CONFIRMED → WHITELIST（提升白名单）")
    void confirmedToWhitelist() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.promoteWhitelist(1L, "user1");

        assertEquals(KnowledgeEntryStatus.WHITELIST.getCode(), result.getStatus());
        verify(entryMapper).updateById(entry);
        verify(auditMapper).insert(any(KnowledgeAudit.class));
    }

    @Test
    @DisplayName("WHITELIST → CONFIRMED（降级）")
    void whitelistToConfirmed() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.WHITELIST.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.demoteWhitelist(1L, "user1");

        assertEquals(KnowledgeEntryStatus.CONFIRMED.getCode(), result.getStatus());
        verify(entryMapper).updateById(entry);
        verify(auditMapper).insert(any(KnowledgeAudit.class));
    }

    @Test
    @DisplayName("DRAFT → FALSE_POSITIVE（标记误报）")
    void draftToFalsePositive() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.markFalsePositive(1L, "user1", "测试环境噪声");

        assertEquals(KnowledgeEntryStatus.FALSE_POSITIVE.getCode(), result.getStatus());
        verify(entryMapper).updateById(entry);
    }

    @Test
    @DisplayName("CONFIRMED → FALSE_POSITIVE（已确认也能标记误报）")
    void confirmedToFalsePositive() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.markFalsePositive(1L, "user1", "重新判定误报");

        assertEquals(KnowledgeEntryStatus.FALSE_POSITIVE.getCode(), result.getStatus());
        verify(entryMapper).updateById(entry);
    }

    @Test
    @DisplayName("任意状态 → OUTDATED（忽略/废弃）")
    void anyStatusToOutdated() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        KnowledgeEntry result = service.ignore(1L, "user1", "不再适用");

        assertEquals(KnowledgeEntryStatus.OUTDATED.getCode(), result.getStatus());
        verify(entryMapper).updateById(entry);
    }

    // ======================== 非法状态转换 ========================

    @Test
    @DisplayName("非 CONFIRMED 提升白名单 → 抛异常")
    void nonConfirmedPromoteThrows() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        assertThrows(BizException.class, () -> service.promoteWhitelist(1L, "user1"));
    }

    @Test
    @DisplayName("非 WHITELIST 降级 → 抛异常")
    void nonWhitelistDemoteThrows() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.CONFIRMED.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        assertThrows(BizException.class, () -> service.demoteWhitelist(1L, "user1"));
    }

    // ======================== 操作留痕 ========================

    @Test
    @DisplayName("每次人工操作 → 写 KnowledgeAudit 记录")
    void everyManualOperationWritesAudit() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        when(entryMapper.selectById(1L)).thenReturn(entry);

        service.confirm(1L, "user1", "确认无误");

        ArgumentCaptor<KnowledgeAudit> captor =
                ArgumentCaptor.forClass(KnowledgeAudit.class);
        verify(auditMapper).insert(captor.capture());

        KnowledgeAudit audit = captor.getValue();
        assertEquals(1L, audit.getKnowledgeEntryId());
        assertEquals(KnowledgeAuditAction.CONFIRM.getCode(), audit.getAction());
        assertEquals("user1", audit.getOperator());
        assertEquals("确认无误", audit.getComment());
        assertEquals(KnowledgeEntryStatus.DRAFT.getCode(), audit.getBeforeStatus());
        assertEquals(KnowledgeEntryStatus.CONFIRMED.getCode(), audit.getAfterStatus());
    }

    @Test
    @DisplayName("操作留痕记录操作前后状态")
    void auditRecordsBeforeAfterStatus() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(2L);
        entry.setStatus(KnowledgeEntryStatus.DRAFT.getCode());
        when(entryMapper.selectById(2L)).thenReturn(entry);

        service.markFalsePositive(2L, "user2", "误报");

        ArgumentCaptor<KnowledgeAudit> captor =
                ArgumentCaptor.forClass(KnowledgeAudit.class);
        verify(auditMapper).insert(captor.capture());

        KnowledgeAudit audit = captor.getValue();
        assertEquals(KnowledgeEntryStatus.DRAFT.getCode(), audit.getBeforeStatus());
        assertEquals(KnowledgeEntryStatus.FALSE_POSITIVE.getCode(), audit.getAfterStatus());
    }
}
