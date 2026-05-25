package com.lnzz.argus.scm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.ScmConfigPageRequest;
import com.lnzz.argus.scm.model.ScmConfigRequest;
import com.lnzz.argus.scm.model.ScmConfigView;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SCM 配置管理 API
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@Validated
@RequestMapping("/api/v1/scm/configs")
@RequiredArgsConstructor
public class ScmConfigController {

    private final ScmConfigService scmConfigService;
    private final ScmReviewConfigSupport scmReviewConfigSupport;

    /**
     * 分页查询 SCM 配置。
     *
     * @param request SCM 配置分页查询请求
     * @return SCM 配置分页结果
     */
    @PostMapping("/page")
    public Result<PageResult<ScmConfigView>> pageConfigs(@RequestBody(required = false) ScmConfigPageRequest request) {
        ScmConfigPageRequest safeRequest = request == null ? new ScmConfigPageRequest() : request;
        Page<ScmConfig> page = scmConfigService.pageConfigs(
                safeRequest.normalizedPageNo(),
                safeRequest.normalizedPageSize(),
                safeRequest.getScmProvider(),
                safeRequest.getEnabled(),
                safeRequest.getKeyword());
        return Result.success(PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()));
    }

    @GetMapping
    public Result<List<ScmConfigView>> listConfigs() {
        return Result.success(scmConfigService.listAll().stream().map(this::toView).toList());
    }

    @GetMapping("/{id}")
    public Result<ScmConfigView> getConfig(@PathVariable Long id) {
        return Result.success(toView(scmConfigService.requireById(id)));
    }

    @PostMapping
    public Result<ScmConfigView> createConfig(@RequestBody @Validated ScmConfigRequest request) {
        ScmConfig saved = scmConfigService.saveOrUpdate(toEntity(null, request));
        return Result.success("SCM 配置创建成功", toView(saved));
    }

    @PutMapping("/{id}")
    public Result<ScmConfigView> updateConfig(@PathVariable Long id,
                                              @RequestBody @Validated ScmConfigRequest request) {
        ScmConfig saved = scmConfigService.saveOrUpdate(toEntity(id, request));
        return Result.success("SCM 配置更新成功", toView(saved));
    }

    private ScmConfig toEntity(Long id, ScmConfigRequest request) {
        ScmConfig config = new ScmConfig();
        config.setId(id);
        config.setScmProvider(request.getScmProvider());
        config.setProjectId(request.getProjectId());
        config.setProjectName(request.getProjectName());
        config.setRepoOwner(request.getRepoOwner());
        config.setRepoName(request.getRepoName());
        config.setApiBaseUrl(request.getApiBaseUrl());
        config.setWebBaseUrl(request.getWebBaseUrl());
        config.setAccessToken(request.getAccessToken());
        config.setWebhookSecret(request.getWebhookSecret());
        config.setBasePackages(request.getBasePackages());
        config.setModuleSourceRoots(request.getModuleSourceRoots());
        config.setPackageModuleMappings(request.getPackageModuleMappings());
        config.setMaxRelatedClasses(request.getMaxRelatedClasses());
        config.setMaxContextTokens(request.getMaxContextTokens());
        config.setReviewParallelism(request.getReviewParallelism());
        config.setEnabled(request.getEnabled());
        config.setDescription(request.getDescription());
        config.setWechatNotifyEnabled(request.getWechatNotifyEnabled());
        config.setWechatNotifyWebhook(request.getWechatNotifyWebhook());
        config.setFeishuNotifyEnabled(request.getFeishuNotifyEnabled());
        config.setFeishuNotifyWebhook(request.getFeishuNotifyWebhook());
        config.setDingtalkNotifyEnabled(request.getDingtalkNotifyEnabled());
        config.setDingtalkNotifyWebhook(request.getDingtalkNotifyWebhook());
        config.setReviewConfig(request.getReviewConfig());
        return config;
    }

    private ScmConfigView toView(ScmConfig config) {
        return new ScmConfigView(
                config.getId(),
                config.getScmProvider(),
                config.getProjectId(),
                config.getProjectName(),
                config.getRepoOwner(),
                config.getRepoName(),
                config.getApiBaseUrl(),
                config.getWebBaseUrl(),
                scmConfigService.maskSecret(config.getAccessToken()),
                scmConfigService.maskSecret(config.getWebhookSecret()),
                config.getBasePackages(),
                config.getModuleSourceRoots(),
                config.getPackageModuleMappings(),
                config.getMaxRelatedClasses(),
                config.getMaxContextTokens(),
                config.getReviewParallelism(),
                config.getEnabled(),
                config.getDescription(),
                config.getWechatNotifyEnabled(),
                scmConfigService.maskSecret(config.getWechatNotifyWebhook()),
                config.getFeishuNotifyEnabled(),
                scmConfigService.maskSecret(config.getFeishuNotifyWebhook()),
                config.getDingtalkNotifyEnabled(),
                scmConfigService.maskSecret(config.getDingtalkNotifyWebhook()),
                scmReviewConfigSupport.maskReviewConfigSecrets(config)
        );
    }

}
