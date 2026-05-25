package com.lnzz.argus.rule.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.dto.req.RuleDocumentPageQueryReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentStatusUpdateReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPageItemResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPreviewResDTO;
import com.lnzz.argus.rule.service.RuleChunkService;
import com.lnzz.argus.rule.service.RuleDocumentService;
import com.lnzz.argus.security.LoginUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * @classname: RuleDocumentServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档服务实现，负责列表、详情、预览和状态流转能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDocumentServiceImpl implements RuleDocumentService {

    private final RuleDocumentMapper ruleDocumentMapper;
    private final RuleChunkService ruleChunkService;
    private final VectorKnowledgeService vectorKnowledgeService;

    @Value("${argus.vector.enabled:false}")
    private boolean vectorEnabled;

    /**
     * 分页查询规则文档列表。
     *
     * @param requestDTO 分页与筛选请求
     * @return 规则文档分页结果
     */
    @Override
    public PageResult<RuleDocumentPageItemResDTO> pageDocuments(RuleDocumentPageQueryReqDTO requestDTO) {
        RuleDocumentPageQueryReqDTO effectiveRequest = requestDTO == null ? new RuleDocumentPageQueryReqDTO() : requestDTO;
        log.info("规则文档分页查询开始, pageNo={}, pageSize={}, category={}, scope={}, scmConfigId={}, status={}",
                effectiveRequest.normalizedPageNo(), effectiveRequest.normalizedPageSize(),
                effectiveRequest.getCategory(), effectiveRequest.getScope(), effectiveRequest.getScmConfigId(),
                effectiveRequest.getStatus());
        Page<RuleDocument> page = ruleDocumentMapper.selectPageByQuery(
                new Page<>(effectiveRequest.normalizedPageNo(), effectiveRequest.normalizedPageSize()),
                normalizeUpper(effectiveRequest.getCategory()),
                normalizeUpper(effectiveRequest.getScope()),
                effectiveRequest.getScmConfigId(),
                normalizeUpper(effectiveRequest.getStatus()),
                normalizeUpper(effectiveRequest.getParseStatus()),
                normalizeUpper(effectiveRequest.getVectorStatus()),
                trimToNull(effectiveRequest.getKeyword()));
        List<RuleDocumentPageItemResDTO> records = page.getRecords().stream()
                .map(this::toPageItemResDTO)
                .toList();
        log.info("规则文档分页查询完成, count={}, total={}", records.size(), page.getTotal());
        return PageResult.of(records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    /**
     * 查询规则文档详情。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档详情
     */
    @Override
    public RuleDocumentDetailResDTO getDocumentDetail(Long documentId) {
        RuleDocument document = requireDocument(documentId);
        return toDetailResDTO(document);
    }

    /**
     * 查询规则文档预览。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档预览
     */
    @Override
    public RuleDocumentPreviewResDTO getDocumentPreview(Long documentId) {
        RuleDocument document = requireDocument(documentId);
        List<RuleDocumentChunk> chunks = ruleChunkService.listChunks(documentId);
        RuleDocumentPreviewResDTO response = new RuleDocumentPreviewResDTO();
        response.setPlainText(document.getContentText());
        response.setParseStatus(document.getParseStatus());
        response.setVectorStatus(document.getVectorStatus());
        response.setLatestErrorMessage(null);
        response.setChunks(chunks.stream().map(this::toChunkPreviewDTO).toList());
        return response;
    }

    /**
     * 更新规则文档状态或触发重建动作。
     *
     * @param requestDTO 状态操作请求
     * @return 更新后的规则文档详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleDocumentDetailResDTO updateDocumentStatus(RuleDocumentStatusUpdateReqDTO requestDTO) {
        if (requestDTO == null || requestDTO.getId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档状态操作参数不能为空");
        }
        String action = normalizeUpper(requestDTO.getAction());
        if (!StringUtils.hasText(action)) {
            throw new BizException(ResultCode.PARAM_ERROR, "操作类型不能为空");
        }
        RuleDocument document = requireDocument(requestDTO.getId());
        String operator = resolveOperator(requestDTO.getOperator());
        log.info("规则文档状态操作开始, documentId={}, action={}, operator={}",
                requestDTO.getId(), action, operator);
        try {
            switch (action) {
                case "ACTIVATE" -> changeStatus(document, "ACTIVE", operator);
                case "DISABLE" -> changeStatus(document, "DISABLED", operator);
                case "REINDEX" -> rebuildDocumentChunks(document, operator);
                default -> throw new BizException(ResultCode.PARAM_ERROR, "不支持的规则文档操作: " + action);
            }
            RuleDocument refreshed = requireDocument(requestDTO.getId());
            log.info("规则文档状态操作成功, documentId={}, action={}, status={}",
                    refreshed.getId(), action, refreshed.getStatus());
            return toDetailResDTO(refreshed);
        } catch (BizException ex) {
            log.error("规则文档状态操作失败, documentId={}, action={}, operator={}",
                    requestDTO.getId(), action, operator, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("规则文档状态操作异常, documentId={}, action={}, operator={}",
                    requestDTO.getId(), action, operator, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "规则文档状态操作失败: " + ex.getMessage());
        }
    }

    /**
     * 根据 ID 查询规则文档实体，不存在时抛出业务异常。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档实体
     */
    @Override
    public RuleDocument requireDocument(Long documentId) {
        RuleDocument document = ruleDocumentMapper.selectNonDeletedById(documentId);
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "规则文档不存在: " + documentId);
        }
        return document;
    }

    private void changeStatus(RuleDocument document, String status, String operator) {
        int affectedRows = ruleDocumentMapper.updateStatusById(document.getId(), status, operator);
        if (affectedRows != 1) {
            throw new BizException(ResultCode.NOT_FOUND, "规则文档不存在或状态已变化: " + document.getId());
        }
        document.setStatus(status);
        if (!vectorEnabled) {
            return;
        }
        if ("DISABLED".equals(status)) {
            vectorKnowledgeService.deleteRuleDocumentChunks(document.getId());
            return;
        }
        if ("ACTIVE".equals(status)) {
            List<RuleDocumentChunk> chunks = ruleChunkService.listChunks(document.getId());
            boolean stored = vectorKnowledgeService.storeRuleDocumentChunks(document, chunks);
            document.setVectorStatus(stored ? "SUCCESS" : "FAILED");
            ruleDocumentMapper.updateById(document);
        }
    }

    private void rebuildDocumentChunks(RuleDocument document, String operator) {
        if (!StringUtils.hasText(document.getContentText())) {
            log.warn("规则文档重建索引被拒绝, documentId={}, reason=未解析出文本", document.getId());
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档尚未解析出可重建的文本");
        }
        int chunkCount = ruleChunkService.rebuildChunks(document.getId(), document.getContentText(), operator);
        if (vectorEnabled) {
            List<RuleDocumentChunk> chunks = ruleChunkService.listChunks(document.getId());
            boolean stored = vectorKnowledgeService.storeRuleDocumentChunks(document, chunks);
            document.setVectorStatus(stored ? "SUCCESS" : "FAILED");
            ruleDocumentMapper.updateById(document);
        }
        log.info("规则文档重建索引完成, documentId={}, chunkCount={}", document.getId(), chunkCount);
    }

    private RuleDocumentPageItemResDTO toPageItemResDTO(RuleDocument document) {
        RuleDocumentPageItemResDTO response = new RuleDocumentPageItemResDTO();
        response.setId(document.getId());
        response.setDocumentCode(document.getDocumentCode());
        response.setDocumentName(document.getDocumentName());
        response.setCategory(document.getCategory());
        response.setScope(document.getScope());
        response.setScmConfigId(document.getScmConfigId());
        response.setStatus(document.getStatus());
        response.setParseStatus(document.getParseStatus());
        response.setVectorStatus(document.getVectorStatus());
        response.setChunkCount(document.getChunkCount());
        response.setLatestErrorMessage(null);
        response.setUpdateTime(document.getUpdateTime());
        return response;
    }

    private RuleDocumentDetailResDTO toDetailResDTO(RuleDocument document) {
        RuleDocumentDetailResDTO response = new RuleDocumentDetailResDTO();
        response.setId(document.getId());
        response.setDocumentCode(document.getDocumentCode());
        response.setDocumentName(document.getDocumentName());
        response.setCategory(document.getCategory());
        response.setScope(document.getScope());
        response.setScmConfigId(document.getScmConfigId());
        response.setSourceType(document.getSourceType());
        response.setFileName(document.getFileName());
        response.setFileExt(document.getFileExt());
        response.setStatus(document.getStatus());
        response.setParseStatus(document.getParseStatus());
        response.setVectorStatus(document.getVectorStatus());
        response.setSummaryText(document.getSummaryText());
        response.setChunkCount(document.getChunkCount());
        response.setVersionNo(document.getVersionNo());
        response.setRemark(document.getRemark());
        response.setLatestErrorMessage(null);
        response.setCreateBy(document.getCreateBy());
        response.setCreateTime(document.getCreateTime());
        response.setUpdateBy(document.getUpdateBy());
        response.setUpdateTime(document.getUpdateTime());
        return response;
    }

    private RuleDocumentPreviewResDTO.ChunkPreviewDTO toChunkPreviewDTO(RuleDocumentChunk chunk) {
        RuleDocumentPreviewResDTO.ChunkPreviewDTO response = new RuleDocumentPreviewResDTO.ChunkPreviewDTO();
        response.setId(chunk.getId());
        response.setChunkNo(chunk.getChunkNo());
        response.setTitle(chunk.getTitle());
        response.setContentText(chunk.getContentText());
        response.setTokenEstimate(chunk.getTokenEstimate());
        response.setStatus(chunk.getStatus());
        return response;
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : LoginUtil.currentUsernameOrSystem();
    }
}
