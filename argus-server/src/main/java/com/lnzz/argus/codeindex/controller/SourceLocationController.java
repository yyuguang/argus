package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.service.SourceLocationService;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @classname: SourceLocationController
 * @author: Fantasy
 * @date: 2026/05/19 21:00
 * @description: 源码定位 API，基于源码索引定位 Java 类或源码文件。
 */
@Validated
@RestController
@RequestMapping("/api/v1/code-indexes")
@RequiredArgsConstructor
public class SourceLocationController {

    private final SourceLocationService sourceLocationService;

    /**
     * 定位源码文件。
     *
     * @param requestDTO 源码定位请求
     * @return 源码定位结果
     */
    @PostMapping("/locate")
    public Result<SourceLocateResDTO> locate(@RequestBody(required = false) SourceLocateReqDTO requestDTO) {
        if (requestDTO == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "源码定位请求不能为空");
        }
        return Result.success(sourceLocationService.locate(requestDTO));
    }
}
