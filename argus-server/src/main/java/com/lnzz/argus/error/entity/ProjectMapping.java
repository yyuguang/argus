package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 应用-SCM项目映射实体（M5-A01）。
 * <p>
 * 一条记录表示一个服务级 appName 到 SCM 仓库源码根的映射；同一个 SCM 仓库可以绑定多个微服务 appName。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_project_mapping")
public class ProjectMapping extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 应用名称 */
    private String appName;

    /** SCM平台: gitlab/github/gitee */
    private String scmProvider;

    /** 仓库/项目ID */
    private Long scmProjectId;

    /** 服务源码根目录，例如 order-service/src/main/java */
    private String sourceRoot;

    /** 基础包名 */
    private String basePackage;

    /** 默认分支 */
    private String defaultBranch;
}
