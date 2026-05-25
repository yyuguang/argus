package com.lnzz.argus.rule.controller;

import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentPageQueryReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentStatusUpdateReqDTO;
import com.lnzz.argus.rule.dto.req.RuleStandardsMigrationReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPageItemResDTO;
import com.lnzz.argus.rule.dto.res.RuleStandardsMigrationResDTO;
import com.lnzz.argus.rule.service.RuleDocumentImportService;
import com.lnzz.argus.rule.service.RuleDocumentService;
import com.lnzz.argus.rule.service.RuleStandardsMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RuleDocumentController - 规则文档接口")
class RuleDocumentControllerTest {

    @Test
    @DisplayName("分页接口直接返回服务层分页结果")
    void pageDocumentsReturnsServicePageResult() {
        RuleDocumentService documentService = mock(RuleDocumentService.class);
        RuleDocumentImportService importService = mock(RuleDocumentImportService.class);
        RuleStandardsMigrationService migrationService = mock(RuleStandardsMigrationService.class);
        RuleDocumentController controller = new RuleDocumentController(documentService, importService, migrationService);
        RuleDocumentPageQueryReqDTO requestDTO = new RuleDocumentPageQueryReqDTO();
        RuleDocumentPageItemResDTO item = new RuleDocumentPageItemResDTO();
        item.setId(1L);
        item.setDocumentName("Java Rule");
        PageResult<RuleDocumentPageItemResDTO> pageResult = PageResult.of(List.of(item), 1, 10, 1);
        when(documentService.pageDocuments(requestDTO)).thenReturn(pageResult);

        Result<PageResult<RuleDocumentPageItemResDTO>> result = controller.pageDocuments(requestDTO);

        assertEquals(0, result.getCode());
        assertEquals(1, result.getData().getRecords().size());
        assertEquals("Java Rule", result.getData().getRecords().get(0).getDocumentName());
    }

    @Test
    @DisplayName("导入接口会透传文件字节与文件名")
    void importDocumentPassesFileBytesAndName() {
        RuleDocumentService documentService = mock(RuleDocumentService.class);
        RuleDocumentImportService importService = mock(RuleDocumentImportService.class);
        RuleStandardsMigrationService migrationService = mock(RuleStandardsMigrationService.class);
        RuleDocumentController controller = new RuleDocumentController(documentService, importService, migrationService);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "java-rule.md",
                "text/markdown",
                "rule-content".getBytes(StandardCharsets.UTF_8));
        RuleDocumentImportReqDTO requestDTO = new RuleDocumentImportReqDTO();
        requestDTO.setCategory("CODING");
        requestDTO.setScope("GLOBAL");
        requestDTO.setDocumentName("Java Rule");
        RuleDocumentDetailResDTO detail = new RuleDocumentDetailResDTO();
        detail.setId(1L);
        detail.setDocumentName("Java Rule");
        when(importService.importDocument(any(), any(), any())).thenReturn(detail);

        Result<RuleDocumentDetailResDTO> result = controller.importDocument(file, requestDTO);

        assertEquals("规则文档导入成功", result.getMessage());
        assertEquals(1L, result.getData().getId());
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RuleDocumentImportReqDTO> requestCaptor = ArgumentCaptor.forClass(RuleDocumentImportReqDTO.class);
        verify(importService).importDocument(bytesCaptor.capture(), fileNameCaptor.capture(), requestCaptor.capture());
        assertArrayEquals("rule-content".getBytes(StandardCharsets.UTF_8), bytesCaptor.getValue());
        assertEquals("java-rule.md", fileNameCaptor.getValue());
        assertEquals("Java Rule", requestCaptor.getValue().getDocumentName());
    }

    @Test
    @DisplayName("启用接口会覆盖请求中的 id 和 action")
    void activateDocumentOverridesIdAndAction() {
        RuleDocumentService documentService = mock(RuleDocumentService.class);
        RuleDocumentImportService importService = mock(RuleDocumentImportService.class);
        RuleStandardsMigrationService migrationService = mock(RuleStandardsMigrationService.class);
        RuleDocumentController controller = new RuleDocumentController(documentService, importService, migrationService);
        RuleDocumentStatusUpdateReqDTO requestDTO = new RuleDocumentStatusUpdateReqDTO();
        requestDTO.setId(999L);
        requestDTO.setAction("DISABLE");
        requestDTO.setOperator("tester");
        RuleDocumentDetailResDTO detail = new RuleDocumentDetailResDTO();
        detail.setId(9L);
        detail.setStatus("ACTIVE");
        when(documentService.updateDocumentStatus(any())).thenReturn(detail);

        Result<RuleDocumentDetailResDTO> result = controller.activateDocument(9L, requestDTO);

        assertEquals("规则文档已启用", result.getMessage());
        ArgumentCaptor<RuleDocumentStatusUpdateReqDTO> captor =
                ArgumentCaptor.forClass(RuleDocumentStatusUpdateReqDTO.class);
        verify(documentService).updateDocumentStatus(captor.capture());
        assertEquals(9L, captor.getValue().getId());
        assertEquals("ACTIVATE", captor.getValue().getAction());
        assertEquals("tester", captor.getValue().getOperator());
    }

    @Test
    @DisplayName("历史规范迁移接口会透传 activeAfterImport 并返回迁移结果")
    void migrateHistoricalStandardsPassesActiveFlag() {
        RuleDocumentService documentService = mock(RuleDocumentService.class);
        RuleDocumentImportService importService = mock(RuleDocumentImportService.class);
        RuleStandardsMigrationService migrationService = mock(RuleStandardsMigrationService.class);
        RuleDocumentController controller = new RuleDocumentController(documentService, importService, migrationService);
        RuleStandardsMigrationReqDTO requestDTO = new RuleStandardsMigrationReqDTO();
        requestDTO.setActiveAfterImport(Boolean.TRUE);
        RuleStandardsMigrationResDTO responseDTO = new RuleStandardsMigrationResDTO();
        responseDTO.setTotalCount(2);
        responseDTO.setImportedCount(2);
        when(migrationService.migrateHistoricalStandards(Boolean.TRUE)).thenReturn(responseDTO);

        Result<RuleStandardsMigrationResDTO> result = controller.migrateHistoricalStandards(requestDTO);

        assertEquals(0, result.getCode());
        assertEquals("历史规范文档迁移完成", result.getMessage());
        assertEquals(2, result.getData().getImportedCount());
        verify(migrationService).migrateHistoricalStandards(Boolean.TRUE);
    }
}
