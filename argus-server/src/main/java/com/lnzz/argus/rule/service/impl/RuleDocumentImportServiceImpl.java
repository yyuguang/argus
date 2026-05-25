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
import com.lnzz.argus.rule.service.RuleDocumentImportService;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.security.LoginUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @classname: RuleDocumentImportServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档导入服务实现，负责文档解析、持久化和分块触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDocumentImportServiceImpl implements RuleDocumentImportService {

    private final RuleDocumentMapper ruleDocumentMapper;
    private final RuleChunkService ruleChunkService;
    private final DocumentParser documentParser;
    private final ScmConfigService scmConfigService;
    private final VectorKnowledgeService vectorKnowledgeService;

    @Value("${argus.vector.enabled:false}")
    private boolean vectorEnabled;

    /**
     * 导入规则文档并触发解析、分块流程。
     *
     * @param fileBytes  上传文件字节数组
     * @param fileName   原始文件名
     * @param requestDTO 规则文档导入请求
     * @return 导入后的规则文档详情
     */
    @Override
    @Transactional(noRollbackFor = BizException.class)
    public RuleDocumentDetailResDTO importDocument(byte[] fileBytes, String fileName, RuleDocumentImportReqDTO requestDTO) {
        validateImportRequest(fileBytes, fileName, requestDTO);
        String operator = LoginUtil.currentUsernameOrSystem();
        RuleDocument document = initDocument(fileName, requestDTO);
        log.info("规则文档导入开始, documentCode={}, documentName={}, scope={}, scmConfigId={}, fileName={}",
                document.getDocumentCode(), document.getDocumentName(), document.getScope(),
                document.getScmConfigId(), fileName);
        try {
            ruleDocumentMapper.insert(document);
            String plainText = parseDocument(fileBytes, fileName);
            document.setContentText(plainText);
            document.setSummaryText(buildSummary(plainText));
            document.setParseStatus("SUCCESS");
            document.setVectorStatus("PENDING");
            document.setStatus(Boolean.TRUE.equals(requestDTO.getActiveAfterImport()) ? "ACTIVE" : "DRAFT");
            document.setUpdateBy(operator);
            ruleDocumentMapper.updateById(document);
            int chunkCount = ruleChunkService.rebuildChunks(document.getId(), plainText, operator);
            document.setChunkCount(chunkCount);
            refreshVectorIndex(document);
            log.info("规则文档导入成功, documentId={}, documentCode={}, chunkCount={}",
                    document.getId(), document.getDocumentCode(), chunkCount);
            return toDetailResDTO(document);
        } catch (BizException ex) {
            markImportFailed(document, operator, ex.getMessage());
            log.error("规则文档导入失败, documentCode={}, fileName={}", document.getDocumentCode(), fileName, ex);
            throw ex;
        } catch (Exception ex) {
            markImportFailed(document, operator, ex.getMessage());
            log.error("规则文档导入异常, documentCode={}, fileName={}", document.getDocumentCode(), fileName, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "规则文档导入失败: " + ex.getMessage());
        }
    }

    private void validateImportRequest(byte[] fileBytes, String fileName, RuleDocumentImportReqDTO requestDTO) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档文件不能为空");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档文件名不能为空");
        }
        if (requestDTO == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档导入请求不能为空");
        }
        if (!StringUtils.hasText(requestDTO.getCategory())) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档分类不能为空");
        }
        if (!StringUtils.hasText(requestDTO.getScope())) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档作用域不能为空");
        }
        if (!StringUtils.hasText(requestDTO.getDocumentName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档名称不能为空");
        }
        String normalizedScope = normalizeUpper(requestDTO.getScope());
        if ("SCM".equals(normalizedScope) && requestDTO.getScmConfigId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "SCM 作用域规则文档必须关联 scmConfigId");
        }
        if ("SCM".equals(normalizedScope)) {
            scmConfigService.requireById(requestDTO.getScmConfigId());
        }
    }

    private RuleDocument initDocument(String fileName, RuleDocumentImportReqDTO requestDTO) {
        RuleDocument document = new RuleDocument();
        document.setDocumentCode(generateDocumentCode());
        document.setDocumentName(requestDTO.getDocumentName().trim());
        document.setCategory(normalizeUpper(requestDTO.getCategory()));
        document.setScope(normalizeUpper(requestDTO.getScope()));
        document.setScmConfigId("SCM".equals(normalizeUpper(requestDTO.getScope())) ? requestDTO.getScmConfigId() : null);
        document.setSourceType(resolveSourceType(requestDTO.getSourceType()));
        document.setFileName(fileName);
        document.setFileExt(resolveFileExt(fileName));
        document.setStatus("DRAFT");
        document.setParseStatus("PENDING");
        document.setVectorStatus("PENDING");
        document.setChunkCount(0);
        document.setVersionNo(1);
        document.setRemark(trimToNull(requestDTO.getRemark()));
        document.setIsDeleted(Boolean.FALSE);
        document.setVersion(0);
        return document;
    }

    private String parseDocument(byte[] fileBytes, String fileName) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return documentParser.parse(inputStream, fileName);
        } catch (IOException ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "规则文档解析失败: " + ex.getMessage());
        }
    }

    private void markImportFailed(RuleDocument document, String operator, String message) {
        if (document == null || document.getId() == null) {
            return;
        }
        document.setParseStatus("FAILED");
        document.setVectorStatus("FAILED");
        document.setSummaryText(buildSummary(message));
        document.setUpdateBy(operator);
        ruleDocumentMapper.updateById(document);
    }

    private void refreshVectorIndex(RuleDocument document) {
        if (!vectorEnabled) {
            log.info("规则文档向量能力关闭，跳过向量写入, documentId={}", document.getId());
            return;
        }
        List<RuleDocumentChunk> chunks = ruleChunkService.listChunks(document.getId());
        boolean stored = vectorKnowledgeService.storeRuleDocumentChunks(document, chunks);
        document.setVectorStatus(stored ? "SUCCESS" : "FAILED");
        ruleDocumentMapper.updateById(document);
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

    private String buildSummary(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private String generateDocumentCode() {
        return "RULE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String resolveFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String resolveSourceType(String sourceType) {
        String normalized = normalizeUpper(sourceType);
        return StringUtils.hasText(normalized) ? normalized : "UPLOAD";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
