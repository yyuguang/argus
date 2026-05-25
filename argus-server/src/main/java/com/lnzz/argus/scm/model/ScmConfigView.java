package com.lnzz.argus.scm.model;

/**
 * SCM 配置展示响应。
 *
 * @author lnzz
 * @since 1.0.0
 */
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
        String basePackages,
        String moduleSourceRoots,
        String packageModuleMappings,
        Integer maxRelatedClasses,
        Integer maxContextTokens,
        Integer reviewParallelism,
        Boolean enabled,
        String description,
        Integer wechatNotifyEnabled,
        String wechatNotifyWebhook,
        Integer feishuNotifyEnabled,
        String feishuNotifyWebhook,
        Integer dingtalkNotifyEnabled,
        String dingtalkNotifyWebhook,
        String reviewConfig
) {
}
