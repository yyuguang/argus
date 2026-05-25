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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CodeIndexController - 源码索引管理接口")
class CodeIndexControllerTest {

    @Test
    @DisplayName("查询最新索引时默认使用 main 分支")
    void getLatestIndexShouldUseDefaultBranch() {
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        CodeIndexController controller = controller(codeIndexService, mock(CodeIndexScanService.class), mock(ScmConfigService.class));
        CodeIndexSummaryResDTO summary = summary(100L);
        when(codeIndexService.getLatestSuccessfulIndex(1L, CodeIndexConstants.DEFAULT_BRANCH)).thenReturn(summary);

        Result<CodeIndexSummaryResDTO> result = controller.getLatestIndex(1L, null);

        assertEquals(0, result.getCode());
        assertEquals(100L, result.getData().getIndexId());
        verify(codeIndexService).getLatestSuccessfulIndex(1L, CodeIndexConstants.DEFAULT_BRANCH);
    }

    @Test
    @DisplayName("分页查询会透传查询请求")
    void pageIndexesShouldDelegateRequest() {
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        CodeIndexController controller = controller(codeIndexService, mock(CodeIndexScanService.class), mock(ScmConfigService.class));
        CodeIndexPageReqDTO requestDTO = new CodeIndexPageReqDTO();
        requestDTO.setScmConfigId(1L);
        PageResult<CodeIndexSummaryResDTO> pageResult = PageResult.of(List.of(summary(1L)), 1, 10, 1);
        when(codeIndexService.pageIndexes(requestDTO)).thenReturn(pageResult);

        Result<PageResult<CodeIndexSummaryResDTO>> result = controller.pageIndexes(requestDTO);

        assertEquals(1, result.getData().getRecords().size());
        verify(codeIndexService).pageIndexes(requestDTO);
    }

    @Test
    @DisplayName("查询不存在的索引详情会抛出 NOT_FOUND")
    void getIndexDetailShouldThrowWhenNotFound() {
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        CodeIndexController controller = controller(codeIndexService, mock(CodeIndexScanService.class), mock(ScmConfigService.class));
        when(codeIndexService.getIndexDetail(404L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> controller.getIndexDetail(404L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals("源码索引不存在: 404", exception.getMessage());
    }

    @Test
    @DisplayName("手动全量扫描会先校验 SCM 配置并调用 scanFull")
    void scanRepositoryShouldCallFullScan() {
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexController controller = controller(mock(CodeIndexService.class), scanService, scmConfigService);
        ScmConfig scmConfig = scmConfig();
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
        when(scanService.scanFull(eq(scmConfig), eq(requestDTO))).thenReturn(summary(1L));

        Result<CodeIndexSummaryResDTO> result = controller.scanRepository(1L, requestDTO);

        assertEquals("源码索引扫描完成", result.getMessage());
        assertEquals(1L, result.getData().getIndexId());
        verify(scanService).scanFull(eq(scmConfig), eq(requestDTO));
    }

    @Test
    @DisplayName("手动扫描失败时响应消息明确标记失败")
    void scanRepositoryShouldReturnFailureMessageWhenScanFailed() {
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexController controller = controller(mock(CodeIndexService.class), scanService, scmConfigService);
        ScmConfig scmConfig = scmConfig();
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        CodeIndexSummaryResDTO failed = summary(null);
        failed.setScanStatus(CodeIndexConstants.ScanStatus.FAILED);
        failed.setLatestErrorMessage("未读取到可扫描文件");
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
        when(scanService.scanFull(eq(scmConfig), eq(requestDTO))).thenReturn(failed);

        Result<CodeIndexSummaryResDTO> result = controller.scanRepository(1L, requestDTO);

        assertEquals("源码索引扫描失败", result.getMessage());
        assertEquals(CodeIndexConstants.ScanStatus.FAILED, result.getData().getScanStatus());
    }

    @Test
    @DisplayName("手动增量扫描会调用 scanIncremental")
    void scanRepositoryShouldCallIncrementalScan() {
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexController controller = controller(mock(CodeIndexService.class), scanService, scmConfigService);
        ScmConfig scmConfig = scmConfig();
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setScanType(CodeIndexConstants.ScanType.INCREMENTAL);
        when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
        when(scanService.scanIncremental(eq(scmConfig), eq(requestDTO), eq(List.of()))).thenReturn(summary(2L));

        Result<CodeIndexSummaryResDTO> result = controller.scanRepository(1L, requestDTO);

        assertEquals(2L, result.getData().getIndexId());
        verify(scanService).scanIncremental(eq(scmConfig), eq(requestDTO), eq(List.of()));
    }

    @Test
    @DisplayName("class 分页会把路径 indexId 写入请求")
    void pageClassesShouldSetIndexIdIntoRequest() {
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        CodeIndexController controller = controller(codeIndexService, mock(CodeIndexScanService.class), mock(ScmConfigService.class));
        CodeClassPageReqDTO requestDTO = new CodeClassPageReqDTO();
        CodeClassIndexResDTO classIndex = new CodeClassIndexResDTO();
        classIndex.setQualifiedName("com.example.Demo");
        when(codeIndexService.pageClasses(eq(9L), any(CodeClassPageReqDTO.class)))
                .thenReturn(PageResult.of(List.of(classIndex), 1, 10, 1));

        Result<PageResult<CodeClassIndexResDTO>> result = controller.pageClasses(9L, requestDTO);

        assertEquals("com.example.Demo", result.getData().getRecords().get(0).getQualifiedName());
        ArgumentCaptor<CodeClassPageReqDTO> captor = ArgumentCaptor.forClass(CodeClassPageReqDTO.class);
        verify(codeIndexService).pageClasses(eq(9L), captor.capture());
        assertEquals(9L, captor.getValue().getIndexId());
    }

    @Test
    @DisplayName("缺失路径参数时不会调用服务层")
    void missingPathIdShouldThrowBeforeServiceCall() {
        CodeIndexService codeIndexService = mock(CodeIndexService.class);
        CodeIndexScanService scanService = mock(CodeIndexScanService.class);
        ScmConfigService scmConfigService = mock(ScmConfigService.class);
        CodeIndexController controller = controller(codeIndexService, scanService, scmConfigService);

        BizException exception = assertThrows(BizException.class, () -> controller.getLatestIndex(0L, "main"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(codeIndexService, scanService, scmConfigService);
    }

    private CodeIndexController controller(CodeIndexService codeIndexService,
                                           CodeIndexScanService scanService,
                                           ScmConfigService scmConfigService) {
        return new CodeIndexController(codeIndexService, scanService, scmConfigService);
    }

    private CodeIndexSummaryResDTO summary(Long indexId) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setIndexId(indexId);
        response.setScanStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return response;
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        return scmConfig;
    }
}
