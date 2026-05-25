package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentChunkMapper;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.service.RuleChunkService;
import com.lnzz.argus.security.LoginUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @classname: RuleChunkServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档分块服务实现，负责按标题、段落和长度重建规则文档分块。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleChunkServiceImpl implements RuleChunkService {

    private static final int MAX_CHUNK_LENGTH = 1200;

    private final RuleDocumentChunkMapper ruleDocumentChunkMapper;
    private final RuleDocumentMapper ruleDocumentMapper;

    /**
     * 查询指定规则文档下全部有效分块。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档分块列表
     */
    @Override
    public List<RuleDocumentChunk> listChunks(Long documentId) {
        return ruleDocumentChunkMapper.listNonDeletedByDocumentId(documentId);
    }

    /**
     * 按解析文本重建规则文档分块。
     *
     * @param documentId 规则文档 ID
     * @param plainText  解析后的纯文本
     * @param operator   当前操作者
     * @return 重建后的分块数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuildChunks(Long documentId, String plainText, String operator) {
        if (documentId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档 ID 不能为空");
        }
        String normalizedOperator = resolveOperator(operator);
        log.info("规则文档分块重建开始, documentId={}, operator={}", documentId, normalizedOperator);
        try {
            ruleDocumentChunkMapper.hardDeleteByDocumentId(documentId);
            List<ChunkSegment> segments = splitIntoSegments(plainText);
            int chunkNo = 1;
            for (ChunkSegment segment : segments) {
                RuleDocumentChunk chunk = new RuleDocumentChunk();
                chunk.setDocumentId(documentId);
                chunk.setChunkNo(chunkNo++);
                chunk.setTitle(segment.title());
                chunk.setContentText(segment.content());
                chunk.setTokenEstimate(estimateTokens(segment.content()));
                chunk.setStatus("ACTIVE");
                chunk.setIsDeleted(Boolean.FALSE);
                chunk.setVersion(0);
                ruleDocumentChunkMapper.insert(chunk);
            }
            ruleDocumentMapper.updateChunkCountAndVectorStatus(documentId, segments.size(), "PENDING", normalizedOperator);
            log.info("规则文档分块重建成功, documentId={}, chunkCount={}", documentId, segments.size());
            return segments.size();
        } catch (BizException ex) {
            log.error("规则文档分块重建失败, documentId={}, operator={}", documentId, normalizedOperator, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("规则文档分块重建异常, documentId={}, operator={}", documentId, normalizedOperator, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "规则文档分块重建失败: " + ex.getMessage());
        }
    }

    private List<ChunkSegment> splitIntoSegments(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return List.of();
        }
        List<ChunkSegment> segments = new ArrayList<>();
        String[] paragraphs = plainText.replace("\r\n", "\n").split("\\n\\s*\\n");
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();
        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph == null ? null : rawParagraph.trim();
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }
            String paragraphTitle = resolveTitle(paragraph);
            boolean isHeading = isHeadingParagraph(paragraph);
            if (isHeading) {
                flushCurrentSegment(segments, currentTitle, currentContent);
                currentTitle = paragraphTitle;
                currentContent = new StringBuilder(paragraph).append("\n");
                continue;
            }
            if (paragraph.length() > MAX_CHUNK_LENGTH) {
                flushCurrentSegment(segments, currentTitle, currentContent);
                splitOversizedParagraph(segments, paragraphTitle, paragraph);
                currentTitle = null;
                currentContent = new StringBuilder();
                continue;
            }
            if (currentContent.length() + paragraph.length() + 2 > MAX_CHUNK_LENGTH) {
                flushCurrentSegment(segments, currentTitle, currentContent);
                currentTitle = paragraphTitle;
                currentContent = new StringBuilder();
            }
            currentContent.append(paragraph).append("\n\n");
            if (!StringUtils.hasText(currentTitle)) {
                currentTitle = paragraphTitle;
            }
        }
        flushCurrentSegment(segments, currentTitle, currentContent);
        return segments;
    }

    private void flushCurrentSegment(List<ChunkSegment> segments, String title, StringBuilder contentBuilder) {
        if (contentBuilder == null || !StringUtils.hasText(contentBuilder.toString())) {
            return;
        }
        String content = contentBuilder.toString().trim();
        String finalTitle = StringUtils.hasText(title) ? title : resolveTitle(content);
        segments.add(new ChunkSegment(finalTitle, content));
        contentBuilder.setLength(0);
    }

    private void splitOversizedParagraph(List<ChunkSegment> segments, String title, String paragraph) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, paragraph.length());
            String content = paragraph.substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                segments.add(new ChunkSegment(resolveOversizedTitle(title, content), content));
            }
            start = end;
        }
    }

    private String resolveOversizedTitle(String title, String content) {
        if (StringUtils.hasText(title)) {
            return title;
        }
        return resolveTitle(content);
    }

    private boolean isHeadingParagraph(String paragraph) {
        if (!StringUtils.hasText(paragraph)) {
            return false;
        }
        String trimmed = paragraph.trim();
        return trimmed.startsWith("#")
                || trimmed.startsWith("##")
                || trimmed.startsWith("###")
                || trimmed.startsWith("第 ") && trimmed.endsWith(" 页")
                || trimmed.endsWith(":")
                || trimmed.endsWith("：");
    }

    private String resolveTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return "未命名分块";
        }
        String firstLine = content.replace("\r\n", "\n").split("\n")[0].trim();
        if (!StringUtils.hasText(firstLine)) {
            return "未命名分块";
        }
        return firstLine.length() > 120 ? firstLine.substring(0, 120) : firstLine;
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }

    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : LoginUtil.currentUsernameOrSystem();
    }

    /**
     * 分块中间结构。
     *
     * @param title   分块标题
     * @param content 分块内容
     */
    private record ChunkSegment(String title, String content) {
    }
}
