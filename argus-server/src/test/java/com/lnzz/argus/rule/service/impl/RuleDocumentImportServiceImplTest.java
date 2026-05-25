package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.ai.DocumentParser;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.service.RuleChunkService;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleDocumentImportServiceImpl - 规则文档导入")
class RuleDocumentImportServiceImplTest {

    @Mock
    private RuleDocumentMapper ruleDocumentMapper;
    @Mock
    private RuleChunkService ruleChunkService;
    @Mock
    private DocumentParser documentParser;
    @Mock
    private ScmConfigService scmConfigService;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;

    private RuleDocumentImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleDocumentImportServiceImpl(
                ruleDocumentMapper,
                ruleChunkService,
                documentParser,
                scmConfigService,
                vectorKnowledgeService);
    }

    @Test
    @DisplayName("导入成功时完成解析、分块并回写激活状态")
    void importDocumentSuccess() throws Exception {
        RuleDocumentImportReqDTO requestDTO = createRequest();
        requestDTO.setActiveAfterImport(true);
        requestDTO.setSourceType("migration");
        byte[] fileBytes = "# rule".getBytes();
        RuleDocumentChunk chunk = new RuleDocumentChunk();
        chunk.setId(1L);
        chunk.setDocumentId(100L);
        chunk.setChunkNo(1);
        chunk.setTitle("Rule");
        chunk.setContentText("Service should validate input.");
        chunk.setStatus("ACTIVE");
        ReflectionTestUtils.setField(service, "vectorEnabled", true);

        doAnswer(invocation -> {
            RuleDocument document = invocation.getArgument(0);
            document.setId(100L);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            return 1;
        }).when(ruleDocumentMapper).insert(any(RuleDocument.class));
        when(documentParser.parse(any(InputStream.class), anyString()))
                .thenReturn("# Rule\n\nService should validate input.");
        when(ruleChunkService.rebuildChunks(100L, "# Rule\n\nService should validate input.", "system"))
                .thenReturn(1);
        when(ruleChunkService.listChunks(100L)).thenReturn(List.of(chunk));
        when(vectorKnowledgeService.storeRuleDocumentChunks(any(RuleDocument.class), any()))
                .thenReturn(true);
        when(ruleDocumentMapper.updateById(any(RuleDocument.class))).thenReturn(1);

        RuleDocumentDetailResDTO response = service.importDocument(fileBytes, "java-rule.md", requestDTO);

        assertEquals(100L, response.getId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("SUCCESS", response.getParseStatus());
        assertEquals("SUCCESS", response.getVectorStatus());
        assertEquals(1, response.getChunkCount());
        assertEquals("MIGRATION", response.getSourceType());
        verify(ruleChunkService).rebuildChunks(100L, "# Rule\n\nService should validate input.", "system");
        verify(vectorKnowledgeService).storeRuleDocumentChunks(any(RuleDocument.class), any());
    }

    @Test
    @DisplayName("解析失败时会回写失败状态并抛出业务异常")
    void importDocumentParseFailureMarksFailed() throws Exception {
        RuleDocumentImportReqDTO requestDTO = createRequest();
        byte[] fileBytes = "bad".getBytes();

        doAnswer(invocation -> {
            RuleDocument document = invocation.getArgument(0);
            document.setId(101L);
            return 1;
        }).when(ruleDocumentMapper).insert(any(RuleDocument.class));
        when(documentParser.parse(any(InputStream.class), anyString()))
                .thenThrow(new IOException("parse boom"));
        when(ruleDocumentMapper.updateById(any(RuleDocument.class))).thenReturn(1);

        BizException exception = assertThrows(BizException.class,
                () -> service.importDocument(fileBytes, "broken.docx", requestDTO));

        assertEquals(ResultCode.SYSTEM_ERROR.getCode(), exception.getCode());
        assertEquals("规则文档解析失败: parse boom", exception.getMessage());
        ArgumentCaptor<RuleDocument> captor = ArgumentCaptor.forClass(RuleDocument.class);
        verify(ruleDocumentMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getParseStatus());
        assertEquals("FAILED", captor.getValue().getVectorStatus());
        verify(ruleChunkService, never()).rebuildChunks(any(), any(), any());
    }

    @Test
    @DisplayName("SCM 作用域缺少 scmConfigId 时拒绝导入")
    void importDocumentRejectsScmScopeWithoutScmConfigId() {
        RuleDocumentImportReqDTO requestDTO = createRequest();
        requestDTO.setScope("SCM");
        requestDTO.setScmConfigId(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.importDocument("x".getBytes(), "java-rule.md", requestDTO));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("SCM 作用域规则文档必须关联 scmConfigId", exception.getMessage());
        verify(scmConfigService, never()).requireById(any());
    }

    @Test
    @DisplayName("SCM 作用域导入前会校验仓库配置存在")
    void importDocumentValidatesScmConfigWhenScopeIsScm() throws Exception {
        RuleDocumentImportReqDTO requestDTO = createRequest();
        requestDTO.setScope("SCM");
        requestDTO.setScmConfigId(66L);
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(66L);

        doAnswer(invocation -> {
            RuleDocument document = invocation.getArgument(0);
            document.setId(102L);
            return 1;
        }).when(ruleDocumentMapper).insert(any(RuleDocument.class));
        when(scmConfigService.requireById(66L)).thenReturn(scmConfig);
        when(documentParser.parse(any(InputStream.class), anyString())).thenReturn("rule-content");
        when(ruleChunkService.rebuildChunks(102L, "rule-content", "system")).thenReturn(1);
        when(ruleDocumentMapper.updateById(any(RuleDocument.class))).thenReturn(1);

        service.importDocument("ok".getBytes(), "scm-rule.md", requestDTO);

        verify(scmConfigService).requireById(66L);
    }

    private RuleDocumentImportReqDTO createRequest() {
        RuleDocumentImportReqDTO requestDTO = new RuleDocumentImportReqDTO();
        requestDTO.setCategory("coding");
        requestDTO.setScope("global");
        requestDTO.setDocumentName("Java Rule");
        requestDTO.setRemark("  demo remark  ");
        requestDTO.setActiveAfterImport(false);
        return requestDTO;
    }
}
