package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.dto.req.RuleDocumentStatusUpdateReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.service.RuleChunkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleDocumentServiceImpl - 规则文档状态流转")
class RuleDocumentServiceImplTest {

    @Mock
    private RuleDocumentMapper ruleDocumentMapper;
    @Mock
    private RuleChunkService ruleChunkService;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;

    private RuleDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleDocumentServiceImpl(ruleDocumentMapper, ruleChunkService, vectorKnowledgeService);
    }

    @Test
    @DisplayName("启用文档时会写入规则分块向量")
    void activateShouldStoreRuleDocumentChunksWhenVectorEnabled() {
        RuleDocument document = createDocument(1L);
        List<RuleDocumentChunk> chunks = List.of(createChunk(1L, 1));
        RuleDocumentStatusUpdateReqDTO requestDTO = createStatusRequest(1L, "ACTIVATE");
        ReflectionTestUtils.setField(service, "vectorEnabled", true);

        when(ruleDocumentMapper.selectNonDeletedById(1L)).thenReturn(document, document);
        when(ruleDocumentMapper.updateStatusById(1L, "ACTIVE", "tester")).thenReturn(1);
        when(ruleChunkService.listChunks(1L)).thenReturn(chunks);
        when(vectorKnowledgeService.storeRuleDocumentChunks(document, chunks)).thenReturn(true);
        when(ruleDocumentMapper.updateById(any(RuleDocument.class))).thenReturn(1);

        RuleDocumentDetailResDTO response = service.updateDocumentStatus(requestDTO);

        assertEquals("ACTIVE", response.getStatus());
        assertEquals("SUCCESS", response.getVectorStatus());
        verify(vectorKnowledgeService).storeRuleDocumentChunks(document, chunks);
        ArgumentCaptor<RuleDocument> captor = ArgumentCaptor.forClass(RuleDocument.class);
        verify(ruleDocumentMapper).updateById(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getVectorStatus());
    }

    @Test
    @DisplayName("停用文档时会删除规则分块向量")
    void disableShouldDeleteRuleDocumentVectorsWhenVectorEnabled() {
        RuleDocument document = createDocument(1L);
        RuleDocumentStatusUpdateReqDTO requestDTO = createStatusRequest(1L, "DISABLE");
        ReflectionTestUtils.setField(service, "vectorEnabled", true);

        when(ruleDocumentMapper.selectNonDeletedById(1L)).thenReturn(document, document);
        when(ruleDocumentMapper.updateStatusById(1L, "DISABLED", "tester")).thenReturn(1);

        RuleDocumentDetailResDTO response = service.updateDocumentStatus(requestDTO);

        assertEquals("DISABLED", response.getStatus());
        verify(vectorKnowledgeService).deleteRuleDocumentChunks(1L);
        verify(ruleChunkService, never()).listChunks(1L);
        verify(ruleDocumentMapper, never()).updateById(any(RuleDocument.class));
    }

    @Test
    @DisplayName("重建索引会重建分块并刷新向量状态")
    void reindexShouldRebuildChunksAndRefreshVectorStatus() {
        RuleDocument document = createDocument(1L);
        List<RuleDocumentChunk> chunks = List.of(createChunk(1L, 1), createChunk(1L, 2));
        RuleDocumentStatusUpdateReqDTO requestDTO = createStatusRequest(1L, "REINDEX");
        ReflectionTestUtils.setField(service, "vectorEnabled", true);

        when(ruleDocumentMapper.selectNonDeletedById(1L)).thenReturn(document, document);
        when(ruleChunkService.rebuildChunks(1L, document.getContentText(), "tester")).thenReturn(2);
        when(ruleChunkService.listChunks(1L)).thenReturn(chunks);
        when(vectorKnowledgeService.storeRuleDocumentChunks(document, chunks)).thenReturn(false);
        when(ruleDocumentMapper.updateById(any(RuleDocument.class))).thenReturn(1);

        RuleDocumentDetailResDTO response = service.updateDocumentStatus(requestDTO);

        assertEquals("FAILED", response.getVectorStatus());
        verify(ruleChunkService).rebuildChunks(1L, document.getContentText(), "tester");
        verify(vectorKnowledgeService).storeRuleDocumentChunks(document, chunks);
        ArgumentCaptor<RuleDocument> captor = ArgumentCaptor.forClass(RuleDocument.class);
        verify(ruleDocumentMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getVectorStatus());
    }

    @Test
    @DisplayName("重建索引前若无解析文本则拒绝执行")
    void reindexShouldRejectWhenContentTextMissing() {
        RuleDocument document = createDocument(1L);
        document.setContentText(" ");
        RuleDocumentStatusUpdateReqDTO requestDTO = createStatusRequest(1L, "REINDEX");

        when(ruleDocumentMapper.selectNonDeletedById(1L)).thenReturn(document);

        BizException exception = assertThrows(BizException.class,
                () -> service.updateDocumentStatus(requestDTO));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("规则文档尚未解析出可重建的文本", exception.getMessage());
        verify(ruleChunkService, never()).rebuildChunks(eq(1L), any(), any());
        verify(vectorKnowledgeService, never()).storeRuleDocumentChunks(any(), any());
    }

    private RuleDocumentStatusUpdateReqDTO createStatusRequest(Long documentId, String action) {
        RuleDocumentStatusUpdateReqDTO requestDTO = new RuleDocumentStatusUpdateReqDTO();
        requestDTO.setId(documentId);
        requestDTO.setAction(action);
        requestDTO.setOperator("tester");
        return requestDTO;
    }

    private RuleDocument createDocument(Long id) {
        RuleDocument document = new RuleDocument();
        document.setId(id);
        document.setDocumentCode("RULE-" + id);
        document.setDocumentName("Java Service Rule");
        document.setCategory("CODING");
        document.setScope("GLOBAL");
        document.setSourceType("UPLOAD");
        document.setFileName("java-service-rule.md");
        document.setFileExt("md");
        document.setStatus("DRAFT");
        document.setParseStatus("SUCCESS");
        document.setVectorStatus("PENDING");
        document.setContentText("# Rule\n\nService method should validate input.");
        document.setChunkCount(1);
        document.setVersionNo(1);
        return document;
    }

    private RuleDocumentChunk createChunk(Long documentId, int chunkNo) {
        RuleDocumentChunk chunk = new RuleDocumentChunk();
        chunk.setId((long) chunkNo);
        chunk.setDocumentId(documentId);
        chunk.setChunkNo(chunkNo);
        chunk.setTitle("Chunk " + chunkNo);
        chunk.setContentText("chunk-content-" + chunkNo);
        chunk.setTokenEstimate(32);
        chunk.setStatus("ACTIVE");
        return chunk;
    }
}
