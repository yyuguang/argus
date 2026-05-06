package com.lnzz.argus.scm.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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
        config.setEnabled(request.getEnabled());
        config.setDescription(request.getDescription());
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
                config.getEnabled(),
                config.getDescription()
        );
    }

    @Data
    public static class ScmConfigRequest {
        @NotBlank
        private String scmProvider;
        private Long projectId;
        private String projectName;
        private String repoOwner;
        private String repoName;
        private String apiBaseUrl;
        private String webBaseUrl;
        private String accessToken;
        private String webhookSecret;
        private Boolean enabled;
        private String description;
    }

    public record ScmConfigView(
            Long id,
            String scmProvider,
            Long projectId,
            String projectName,
            String repoOwner,
            String repoName,
            String apiBaseUrl,
            String webBaseUrl,
            String accessToken,
            String webhookSecret,
            Boolean enabled,
            String description
    ) {
    }
}
