package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: AppVersionBindingResDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 应用版本源码绑定响应，表达应用环境到仓库提交号和源码索引的绑定结果。
 */
@Data
public class AppVersionBindingResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定 ID。
     */
    private Long bindingId;

    /**
     * 应用映射 ID。
     */
    private Long mappingId;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 环境标识。
     */
    private String environment;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 源码索引 ID。
     */
    private Long indexId;

    /**
     * 分支名称。
     */
    private String branchName;

    /**
     * 提交号。
     */
    private String commitSha;

    /**
     * 应用版本名称。
     */
    private String versionName;

    /**
     * 绑定来源。
     */
    private String bindingSource;

    /**
     * 索引状态。
     */
    private String indexStatus;

    /**
     * 是否当前有效绑定。
     */
    private Boolean active;

    /**
     * 激活时间。
     */
    private LocalDateTime activatedAt;

    /**
     * 最近观测时间。
     */
    private LocalDateTime lastSeenAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 修改时间。
     */
    private LocalDateTime updateTime;
}
