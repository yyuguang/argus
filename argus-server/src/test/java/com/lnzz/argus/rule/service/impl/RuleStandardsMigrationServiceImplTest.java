package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleStandardsMigrationResDTO;
import com.lnzz.argus.rule.service.RuleDocumentImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RuleStandardsMigrationServiceImpl 单元测试。
 *
 * @author Fantasy
 * @date 2026/05/17 21:46
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleStandardsMigrationServiceImpl - 历史 standards 迁移服务")
class RuleStandardsMigrationServiceImplTest {

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    @Mock
    private RuleDocumentMapper ruleDocumentMapper;

    @Mock
    private RuleDocumentImportService ruleDocumentImportService;

    private RuleStandardsMigrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleStandardsMigrationServiceImpl(
                resourcePatternResolver,
                ruleDocumentMapper,
                ruleDocumentImportService);
    }

    @Test
    @DisplayName("migrateHistoricalStandards 应导入标准目录中的支持文件并映射为 MIGRATION 来源")
    void migrateHistoricalStandardsShouldImportSupportedResources() throws Exception {
        Resource codingResource = namedResource("CODING_STYLE.md", "# 编码规范");
        when(resourcePatternResolver.getResources("classpath:standards/coding/*.*"))
                .thenReturn(new Resource[]{codingResource});
        when(resourcePatternResolver.getResources("classpath:standards/api/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/database/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/security/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/custom/*.*"))
                .thenReturn(new Resource[0]);

        RuleDocumentDetailResDTO imported = new RuleDocumentDetailResDTO();
        imported.setId(101L);
        when(ruleDocumentImportService.importDocument(any(), eq("CODING_STYLE.md"), any(RuleDocumentImportReqDTO.class)))
                .thenReturn(imported);

        RuleStandardsMigrationResDTO response = service.migrateHistoricalStandards(true);

        ArgumentCaptor<RuleDocumentImportReqDTO> captor = ArgumentCaptor.forClass(RuleDocumentImportReqDTO.class);
        verify(ruleDocumentImportService).importDocument(any(), eq("CODING_STYLE.md"), captor.capture());
        RuleDocumentImportReqDTO requestDTO = captor.getValue();
        assertEquals("CODING", requestDTO.getCategory());
        assertEquals("GLOBAL", requestDTO.getScope());
        assertEquals("CODING_STYLE", requestDTO.getDocumentName());
        assertEquals("MIGRATION", requestDTO.getSourceType());
        assertTrue(Boolean.TRUE.equals(requestDTO.getActiveAfterImport()));
        assertTrue(requestDTO.getRemark().contains("standards/coding/CODING_STYLE.md"));
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getImportedCount());
        assertEquals(0, response.getSkippedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals("IMPORTED", response.getItems().get(0).getStatus());
        assertEquals(101L, response.getItems().get(0).getDocumentId());
    }

    @Test
    @DisplayName("migrateHistoricalStandards 遇到已迁移文档时应跳过重复导入")
    void migrateHistoricalStandardsShouldSkipExistingMigrationDocument() throws Exception {
        Resource codingResource = namedResource("CODING_STYLE.md", "# 编码规范");
        when(resourcePatternResolver.getResources("classpath:standards/coding/*.*"))
                .thenReturn(new Resource[]{codingResource});
        when(resourcePatternResolver.getResources("classpath:standards/api/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/database/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/security/*.*"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources("classpath:standards/custom/*.*"))
                .thenReturn(new Resource[0]);

        RuleDocument existing = new RuleDocument();
        existing.setId(88L);
        when(ruleDocumentMapper.selectBySourceTypeAndFileName("MIGRATION", "CODING", "CODING_STYLE.md"))
                .thenReturn(existing);

        RuleStandardsMigrationResDTO response = service.migrateHistoricalStandards(false);

        verify(ruleDocumentImportService, never()).importDocument(any(), any(), any());
        assertEquals(1, response.getTotalCount());
        assertEquals(0, response.getImportedCount());
        assertEquals(1, response.getSkippedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals("SKIPPED", response.getItems().get(0).getStatus());
        assertEquals(88L, response.getItems().get(0).getDocumentId());
    }

    private Resource namedResource(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
