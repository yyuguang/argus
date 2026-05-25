package com.lnzz.argus.scm.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * SCM 配置保存请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class ScmConfigRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SCM 平台: gitlab/github/gitee。 */
    @NotBlank
    private String scmProvider;

    /** 仓库/项目 ID。 */
    private Long projectId;

    /** 仓库显示名称。 */
    private String projectName;

    /** 仓库归属 owner/group。 */
    private String repoOwner;

    /** 仓库名称。 */
    private String repoName;

    /** SCM API 基础地址。 */
    private String apiBaseUrl;

    /** SCM Web 基础地址。 */
    private String webBaseUrl;

    /** SCM 访问 Token。 */
    private String accessToken;

    /** Webhook 密钥。 */
    private String webhookSecret;

    /** 基础包列表 JSON。 */
    private String basePackages;

    /** 模块源码根列表 JSON。 */
    private String moduleSourceRoots;

    /** 包前缀到源码根映射 JSON。 */
    private String packageModuleMappings;

    /** 最大关联类数。 */
    private Integer maxRelatedClasses;

    /** 最大上下文 Token 数。 */
    private Integer maxContextTokens;

    /** 评审并发度。 */
    private Integer reviewParallelism;

    /** 是否启用。 */
    private Boolean enabled;

    /** 配置说明。 */
    private String description;

    /** 仓库级企业微信通知开关: 0-关闭 1-开启。 */
    private Integer wechatNotifyEnabled;

    /** 仓库级企业微信 Webhook。 */
    private String wechatNotifyWebhook;

    /** 仓库级飞书通知开关: 0-关闭 1-开启。 */
    private Integer feishuNotifyEnabled;

    /** 仓库级飞书 Webhook。 */
    private String feishuNotifyWebhook;

    /** 仓库级钉钉通知开关: 0-关闭 1-开启。 */
    private Integer dingtalkNotifyEnabled;

    /** 仓库级钉钉 Webhook。 */
    private String dingtalkNotifyWebhook;

    /** 仓库级评审配置 JSON。 */
    private String reviewConfig;
}
