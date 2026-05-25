package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentChunkMapper;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

/**
 * RuleChunkServiceImpl 单元测试。
 *
 * @author Fantasy
 * @date 2026/05/17 21:45
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleChunkServiceImpl - 规则文档分块服务")
class RuleChunkServiceImplTest {

    @Mock
    private RuleDocumentChunkMapper ruleDocumentChunkMapper;

    @Mock
    private RuleDocumentMapper ruleDocumentMapper;

    private RuleChunkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleChunkServiceImpl(ruleDocumentChunkMapper, ruleDocumentMapper);
    }

    @Test
    @DisplayName("rebuildChunks 遇到多标题 Markdown 时应按标题分块")
    void rebuildChunksShouldSplitByMarkdownHeading() {
        when(ruleDocumentChunkMapper.hardDeleteByDocumentId(1L)).thenReturn(0);
        when(ruleDocumentChunkMapper.insert(any(RuleDocumentChunk.class))).thenReturn(1);
        when(ruleDocumentMapper.updateChunkCountAndVectorStatus(eq(1L), anyInt(), eq("PENDING"), eq("tester")))
                .thenReturn(1);

        String plainText = "# 第一章\n\n规范一：命名应清晰。\n\n## 第二章\n\n规范二：异常必须记录。";

        int chunkCount = service.rebuildChunks(1L, plainText, "tester");

        ArgumentCaptor<RuleDocumentChunk> captor = ArgumentCaptor.forClass(RuleDocumentChunk.class);
        verify(ruleDocumentChunkMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<RuleDocumentChunk> chunks = captor.getAllValues();
        assertEquals(2, chunkCount);
        assertEquals("# 第一章", chunks.get(0).getTitle());
        assertTrue(chunks.get(0).getContentText().contains("规范一"));
        assertEquals("## 第二章", chunks.get(1).getTitle());
        assertTrue(chunks.get(1).getContentText().contains("规范二"));
        InOrder inOrder = inOrder(ruleDocumentChunkMapper);
        inOrder.verify(ruleDocumentChunkMapper).hardDeleteByDocumentId(1L);
        inOrder.verify(ruleDocumentChunkMapper, org.mockito.Mockito.times(2)).insert(any(RuleDocumentChunk.class));
    }

    @Test
    @DisplayName("rebuildChunks 遇到超长段落时应拆为多个长度受控分块")
    void rebuildChunksShouldSplitOversizedParagraph() {
        when(ruleDocumentChunkMapper.hardDeleteByDocumentId(2L)).thenReturn(0);
        when(ruleDocumentChunkMapper.insert(any(RuleDocumentChunk.class))).thenReturn(1);
        when(ruleDocumentMapper.updateChunkCountAndVectorStatus(eq(2L), anyInt(), eq("PENDING"), eq("tester")))
                .thenReturn(1);

        String plainText = "a".repeat(2500);

        int chunkCount = service.rebuildChunks(2L, plainText, "tester");

        ArgumentCaptor<RuleDocumentChunk> captor = ArgumentCaptor.forClass(RuleDocumentChunk.class);
        verify(ruleDocumentChunkMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<RuleDocumentChunk> chunks = captor.getAllValues();
        assertEquals(3, chunkCount);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContentText().length() <= 1200));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getTokenEstimate() > 0));
    }

    @Test
    @DisplayName("rebuildChunks 重建前应先物理删除旧分块以释放唯一键")
    void rebuildChunksShouldHardDeleteExistingChunksBeforeInsert() {
        when(ruleDocumentChunkMapper.hardDeleteByDocumentId(3L)).thenReturn(2);
        when(ruleDocumentChunkMapper.insert(any(RuleDocumentChunk.class))).thenReturn(1);
        when(ruleDocumentMapper.updateChunkCountAndVectorStatus(eq(3L), anyInt(), eq("PENDING"), eq("tester")))
                .thenReturn(1);

        int chunkCount = service.rebuildChunks(3L, "规则一\n\n规则二", "tester");

        assertEquals(1, chunkCount);
        InOrder inOrder = inOrder(ruleDocumentChunkMapper, ruleDocumentMapper);
        inOrder.verify(ruleDocumentChunkMapper).hardDeleteByDocumentId(3L);
        inOrder.verify(ruleDocumentChunkMapper).insert(any(RuleDocumentChunk.class));
        inOrder.verify(ruleDocumentMapper).updateChunkCountAndVectorStatus(3L, 1, "PENDING", "tester");
    }
}
