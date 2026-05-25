package com.lnzz.argus.rule.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentPageQueryReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentStatusUpdateReqDTO;
import com.lnzz.argus.rule.dto.req.RuleStandardsMigrationReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPageItemResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPreviewResDTO;
import com.lnzz.argus.rule.dto.res.RuleStandardsMigrationResDTO;
import com.lnzz.argus.rule.service.RuleDocumentImportService;
import com.lnzz.argus.rule.service.RuleDocumentService;
import com.lnzz.argus.rule.service.RuleStandardsMigrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 规则文档管理 Controller。
 *
 * @author Fantasy
 * @date 2026/05/18 00:35
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/rules/documents")
@RequiredArgsConstructor
public class RuleDocumentController {

    private final RuleDocumentService ruleDocumentService;
    private final RuleDocumentImportService ruleDocumentImportService;
    private final RuleStandardsMigrationService ruleStandardsMigrationService;

    /**
     * 分页查询规则文档。
     *
     * @param requestDTO 分页查询请求
     * @return 规则文档分页结果
     */
    @PostMapping("/page")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<PageResult<RuleDocumentPageItemResDTO>> pageDocuments(
            @RequestBody(required = false) RuleDocumentPageQueryReqDTO requestDTO) {
        return Result.success(ruleDocumentService.pageDocuments(requestDTO));
    }

    /**
     * 上传并导入规则文档。
     *
     * @param file       上传文件
     * @param requestDTO 导入请求
     * @return 导入后的规则文档详情
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_IMPORT)
    public Result<RuleDocumentDetailResDTO> importDocument(@RequestPart("file") MultipartFile file,
                                                           @Valid @ModelAttribute RuleDocumentImportReqDTO requestDTO) {
        RuleDocumentDetailResDTO response = ruleDocumentImportService.importDocument(
                readFileBytes(file),
                resolveFileName(file),
                requestDTO);
        return Result.success("规则文档导入成功", response);
    }

    /**
     * 查询规则文档详情。
     *
     * @param id 规则文档 ID
     * @return 规则文档详情
     */
    @GetMapping("/{id}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<RuleDocumentDetailResDTO> getDocumentDetail(@PathVariable Long id) {
        return Result.success(ruleDocumentService.getDocumentDetail(id));
    }

    /**
     * 预览规则文档解析结果。
     *
     * @param id 规则文档 ID
     * @return 规则文档预览结果
     */
    @GetMapping("/{id}/preview")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<RuleDocumentPreviewResDTO> getDocumentPreview(@PathVariable Long id) {
        return Result.success(ruleDocumentService.getDocumentPreview(id));
    }

    /**
     * 启用规则文档。
     *
     * @param id         规则文档 ID
     * @param requestDTO 可选操作参数
     * @return 更新后的规则文档详情
     */
    @PostMapping("/{id}/activate")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_ACTIVATE)
    public Result<RuleDocumentDetailResDTO> activateDocument(@PathVariable Long id,
                                                             @RequestBody(required = false) RuleDocumentStatusUpdateReqDTO requestDTO) {
        return Result.success("规则文档已启用", ruleDocumentService.updateDocumentStatus(
                buildActionRequest(id, "ACTIVATE", requestDTO)));
    }

    /**
     * 停用规则文档。
     *
     * @param id         规则文档 ID
     * @param requestDTO 可选操作参数
     * @return 更新后的规则文档详情
     */
    @PostMapping("/{id}/disable")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_DISABLE)
    public Result<RuleDocumentDetailResDTO> disableDocument(@PathVariable Long id,
                                                            @RequestBody(required = false) RuleDocumentStatusUpdateReqDTO requestDTO) {
        return Result.success("规则文档已停用", ruleDocumentService.updateDocumentStatus(
                buildActionRequest(id, "DISABLE", requestDTO)));
    }

    /**
     * 重建规则文档索引。
     *
     * @param id         规则文档 ID
     * @param requestDTO 可选操作参数
     * @return 更新后的规则文档详情
     */
    @PostMapping("/{id}/reindex")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_REINDEX)
    public Result<RuleDocumentDetailResDTO> reindexDocument(@PathVariable Long id,
                                                            @RequestBody(required = false) RuleDocumentStatusUpdateReqDTO requestDTO) {
        return Result.success("规则文档索引重建成功", ruleDocumentService.updateDocumentStatus(
                buildActionRequest(id, "REINDEX", requestDTO)));
    }

    /**
     * 触发历史 standards 目录迁移。
     *
     * @param requestDTO 迁移请求
     * @return 迁移结果
     */
    @PostMapping("/migrations/standards")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_IMPORT)
    public Result<RuleStandardsMigrationResDTO> migrateHistoricalStandards(
            @RequestBody(required = false) RuleStandardsMigrationReqDTO requestDTO) {
        Boolean activeAfterImport = requestDTO != null ? requestDTO.getActiveAfterImport() : null;
        RuleStandardsMigrationResDTO response = ruleStandardsMigrationService.migrateHistoricalStandards(activeAfterImport);
        return Result.success("历史规范文档迁移完成", response);
    }

    private RuleDocumentStatusUpdateReqDTO buildActionRequest(Long documentId,
                                                              String action,
                                                              RuleDocumentStatusUpdateReqDTO requestDTO) {
        RuleDocumentStatusUpdateReqDTO effectiveRequest = requestDTO == null
                ? new RuleDocumentStatusUpdateReqDTO()
                : requestDTO;
        effectiveRequest.setId(documentId);
        effectiveRequest.setAction(action);
        return effectiveRequest;
    }

    private byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则文档文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.error("读取规则文档上传文件失败, fileName={}", file.getOriginalFilename(), ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "读取规则文档文件失败: " + ex.getMessage());
        }
    }

    private String resolveFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename)) {
            return originalFilename.trim();
        }
        return file.getName();
    }
}
