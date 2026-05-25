package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.AppVersionBindingReqDTO;
import com.lnzz.argus.codeindex.dto.res.AppVersionBindingResDTO;
import com.lnzz.argus.codeindex.service.AppVersionBindingService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @classname: AppVersionBindingController
 * @author: Fantasy
 * @date: 2026/05/19 21:00
 * @description: 应用版本源码绑定 API，维护应用环境与 SCM commit、源码索引的当前绑定关系。
 */
@Validated
@RestController
@RequestMapping("/api/v1/code-indexes/app-version-bindings")
@RequiredArgsConstructor
public class AppVersionBindingController {

    private final AppVersionBindingService appVersionBindingService;

    /**
     * 保存并激活应用版本绑定。
     *
     * @param requestDTO 应用版本绑定请求
     * @return 当前激活绑定
     */
    @PostMapping
    public Result<AppVersionBindingResDTO> bind(@RequestBody(required = false) AppVersionBindingReqDTO requestDTO) {
        validateBindRequest(requestDTO);
        if (!hasText(requestDTO.getBindingSource())) {
            requestDTO.setBindingSource(CodeIndexConstants.TriggerType.MANUAL);
        }
        return Result.success("应用源码版本绑定成功", appVersionBindingService.bind(requestDTO));
    }

    /**
     * 查询应用环境当前激活源码版本绑定。
     *
     * @param appName     应用名称
     * @param environment 环境标识
     * @param scmConfigId SCM 配置 ID
     * @return 当前激活绑定
     */
    @GetMapping("/current")
    public Result<AppVersionBindingResDTO> getCurrentBinding(@RequestParam String appName,
                                                             @RequestParam String environment,
                                                             @RequestParam Long scmConfigId) {
        if (!hasText(appName) || !hasText(environment) || scmConfigId == null || scmConfigId <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName、environment、scmConfigId 不能为空");
        }
        return Result.success(appVersionBindingService.getActiveBinding(appName, environment, scmConfigId));
    }

    private void validateBindRequest(AppVersionBindingReqDTO requestDTO) {
        if (requestDTO == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "应用版本绑定请求不能为空");
        }
        if (!hasText(requestDTO.getAppName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName 不能为空");
        }
        if (!hasText(requestDTO.getEnvironment())) {
            throw new BizException(ResultCode.PARAM_ERROR, "environment 不能为空");
        }
        if (requestDTO.getScmConfigId() == null || requestDTO.getScmConfigId() <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "scmConfigId 不能为空");
        }
        if (!hasText(requestDTO.getCommitSha())) {
            throw new BizException(ResultCode.PARAM_ERROR, "commitSha 不能为空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
