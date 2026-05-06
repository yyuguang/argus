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

    /** 是否启用 */
    private Boolean enabled;

    /** 配置说明 */
    private String description;
}
