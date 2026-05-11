package com.lnzz.argus.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SCM 仓库配置
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_scm_config")
public class ScmConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SCM平台: gitlab/github/gitee */
    private String scmProvider;

    /** 仓库/项目ID */
    private Long projectId;

    /** 仓库/项目名称 */
    private String projectName;

    /** 仓库归属 owner/group */
    private String repoOwner;

    /** 仓库名称 */
    private String repoName;

    /** API 基础地址 */
    private String apiBaseUrl;

    /** Web 基础地址 */
    private String webBaseUrl;

    /** 访问令牌 */
    private String accessToken;

    /** Webhook 密钥 */
    private String webhookSecret;

    /** 基础包前缀列表(JSON数组) */
    private String basePackages;

    /** 模块源码根列表(JSON数组) */
    private String moduleSourceRoots;

    /** 包前缀到源码根映射(JSON数组) */
    private String packageModuleMappings;

    /** 单文件最大关联类数 */
    private Integer maxRelatedClasses;

    /** 单文件最大上下文Token */
    private Integer maxContextTokens;

    /** 评审并发度 */
    private Integer reviewParallelism;

    /** 是否启用 */
    private Boolean enabled;

    /** 配置说明 */
    private String description;

    /** 仓库级企微通知开关 0=关闭 1=开启 */
    private Integer wechatNotifyEnabled;

    /** 仓库级企微 Webhook 地址（为空则用全局默认） */
    private String wechatNotifyWebhook;

    /** 仓库级评审配置 JSON（ReviewConfig 结构） */
    private String reviewConfig;

    /** 仓库级企微通知是否开启 */
    public boolean isWechatNotificationEnabled() {
        return wechatNotifyEnabled == null || wechatNotifyEnabled == 1;
    }
}
