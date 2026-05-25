package com.lnzz.argus.codeindex.dto.req;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: AppVersionBindingReqDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 应用版本源码绑定请求，记录应用环境、仓库、分支和提交号的版本关系。
 */
@Data
public class AppVersionBindingReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用映射 ID，来自监控应用与 SCM 配置的关联关系。
     */
    private Long mappingId;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 环境标识，如 dev/test/staging/prod。
     */
    private String environment;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 分支名称。
     */
    private String branchName;

    /**
     * 提交号。
     */
    private String commitSha;

    /**
     * 应用版本名称，如发布单版本、镜像 tag 或构建号。
     */
    private String versionName;

    /**
     * 绑定来源：MANUAL/DEPLOY_CALLBACK/WEBHOOK。
     */
    private String bindingSource;

    /**
     * 备注。
     */
    private String remark;
}
