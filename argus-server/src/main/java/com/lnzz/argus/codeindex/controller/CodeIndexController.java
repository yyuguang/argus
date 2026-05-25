package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.CodeClassPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeClassIndexResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexDetailResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.service.CodeIndexScanService;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @classname: CodeIndexController
 * @author: Fantasy
 * @date: 2026/05/19 20:40
 * @description: 源码索引管理 API，提供索引查询、手动扫描和 Java 类型索引分页能力。
 */
@Validated
@RestController
@RequestMapping("/api/v1/code-indexes")
@RequiredArgsConstructor
public class CodeIndexController {

    private final CodeIndexService codeIndexService;
    private final CodeIndexScanService codeIndexScanService;
    private final ScmConfigService scmConfigService;

    /**
     * 查询仓库指定分支最近一次成功源码索引。
     *
     * @param scmConfigId SCM 配置 ID
     * @param branchName  分支名称
     * @return 最新成功源码索引摘要
     */
    @GetMapping("/scm/{scmConfigId}/latest")
    public Result<CodeIndexSummaryResDTO> getLatestIndex(@PathVariable Long scmConfigId,
                                                         @RequestParam(required = false) String branchName) {
        requireId(scmConfigId, "scmConfigId");
        return Result.success(codeIndexService.getLatestSuccessfulIndex(
                scmConfigId,
                hasText(branchName) ? branchName.trim() : CodeIndexConstants.DEFAULT_BRANCH));
    }

    /**
     * 分页查询源码索引历史。
     *
     * @param requestDTO 分页查询请求
     * @return 源码索引摘要分页结果
     */
    @PostMapping("/page")
    public Result<PageResult<CodeIndexSummaryResDTO>> pageIndexes(
            @RequestBody(required = false) CodeIndexPageReqDTO requestDTO) {
        return Result.success(codeIndexService.pageIndexes(requestDTO));
    }

    /**
     * 查询源码索引详情。
     *
     * @param indexId 源码索引 ID
     * @return 源码索引详情
     */
    @GetMapping("/{indexId}")
    public Result<CodeIndexDetailResDTO> getIndexDetail(@PathVariable Long indexId) {
        requireId(indexId, "indexId");
        CodeIndexDetailResDTO detail = codeIndexService.getIndexDetail(indexId);
        if (detail == null) {
            throw new BizException(ResultCode.NOT_FOUND, "源码索引不存在: " + indexId);
        }
        return Result.success(detail);
    }

    /**
     * 手动触发源码索引扫描。
     *
     * @param scmConfigId SCM 配置 ID
     * @param requestDTO  扫描请求
     * @return 扫描后的源码索引摘要
     */
    @PostMapping("/scm/{scmConfigId}/scan")
    public Result<CodeIndexSummaryResDTO> scanRepository(@PathVariable Long scmConfigId,
                                                         @RequestBody(required = false) CodeIndexScanReqDTO requestDTO) {
        requireId(scmConfigId, "scmConfigId");
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        CodeIndexScanReqDTO effectiveRequest = requestDTO == null ? new CodeIndexScanReqDTO() : requestDTO;
        CodeIndexSummaryResDTO summary = shouldUseIncremental(effectiveRequest)
                ? codeIndexScanService.scanIncremental(scmConfig, effectiveRequest, List.of())
                : codeIndexScanService.scanFull(scmConfig, effectiveRequest);
        return Result.success(resolveScanMessage(summary), summary);
    }

    /**
     * 分页查询源码索引内 Java 类型。
     *
     * @param indexId    源码索引 ID
     * @param requestDTO 类型索引分页请求
     * @return Java 类型索引分页结果
     */
    @PostMapping("/{indexId}/classes/page")
    public Result<PageResult<CodeClassIndexResDTO>> pageClasses(@PathVariable Long indexId,
                                                                @RequestBody(required = false) CodeClassPageReqDTO requestDTO) {
        requireId(indexId, "indexId");
        CodeClassPageReqDTO effectiveRequest = requestDTO == null ? new CodeClassPageReqDTO() : requestDTO;
        effectiveRequest.setIndexId(indexId);
        return Result.success(codeIndexService.pageClasses(indexId, effectiveRequest));
    }

    private boolean shouldUseIncremental(CodeIndexScanReqDTO requestDTO) {
        String scanType = requestDTO.getScanType();
        if (CodeIndexConstants.ScanType.REBUILD.equals(scanType) || Boolean.TRUE.equals(requestDTO.getForceRebuild())) {
            return false;
        }
        return CodeIndexConstants.ScanType.INCREMENTAL.equals(scanType)
                || CodeIndexConstants.ScanType.MODULE_RESCAN.equals(scanType)
                || (requestDTO.getDeletedFilePaths() != null && !requestDTO.getDeletedFilePaths().isEmpty());
    }

    private String resolveScanMessage(CodeIndexSummaryResDTO summary) {
        if (summary == null) {
            return "源码索引扫描失败";
        }
        if (CodeIndexConstants.ScanStatus.FAILED.equals(summary.getScanStatus())) {
            return "源码索引扫描失败";
        }
        if (summary.getWarningCount() != null && summary.getWarningCount() > 0) {
            return "源码索引扫描完成，存在扫描告警";
        }
        return "源码索引扫描完成";
    }

    private void requireId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, fieldName + " 不能为空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
