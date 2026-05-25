package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @classname: CodeRepositoryIndex
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 仓库源码索引主表实体，记录某个 SCM 仓库在指定 commit 下的一次索引快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_code_repository_index")
public class CodeRepositoryIndex extends BaseEntity {

    /**
     * SCM 配置 ID。
     */
    private Long scmConfigId;

    /**
     * SCM 平台：GITLAB/GITHUB/GITEE。
     */
    private String scmProvider;

    /**
     * SCM 项目标识。
     */
    private String scmProjectId;

    /**
     * 仓库 owner 或 group。
     */
    private String repoOwner;

    /**
     * 仓库名称。
     */
    private String repoName;

    /**
     * 索引分支名称。
     */
    private String branchName;

    /**
     * 索引对应 commit SHA。
     */
    private String commitSha;

    /**
     * 增量扫描基线 commit SHA。
     */
    private String baseCommitSha;

    /**
     * 索引结构版本。
     */
    private Integer indexVersion;

    /**
     * 扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD。
     */
    private String scanType;

    /**
     * 触发类型：FIRST_INIT/WEBHOOK/MANUAL/DEPLOY_CALLBACK/SCHEDULED。
     */
    private String triggerType;

    /**
     * 扫描状态：PENDING/RUNNING/SUCCESS/FAILED。
     */
    private String scanStatus;

    /**
     * 模块数量。
     */
    private Integer moduleCount;

    /**
     * 源码根数量。
     */
    private Integer sourceRootCount;

    /**
     * Java 文件数量。
     */
    private Integer javaFileCount;

    /**
     * 类型数量。
     */
    private Integer classCount;

    /**
     * 包数量。
     */
    private Integer packageCount;

    /**
     * 歧义包数量。
     */
    private Integer ambiguousPackageCount;

    /**
     * 扫描告警数量。
     */
    private Integer warningCount;

    /**
     * 整体置信度：HIGH/MEDIUM/LOW。
     */
    private String confidence;

    /**
     * 是否已过期。
     */
    private Boolean stale;

    /**
     * 最近失败原因。
     */
    private String latestErrorMessage;

    /**
     * 扫描开始时间。
     */
    private LocalDateTime startedAt;

    /**
     * 扫描完成时间。
     */
    private LocalDateTime finishedAt;

    /**
     * 是否软删除。
     */
    @TableLogic(value = "0", delval = "1")
    private Boolean isDeleted;

    /**
     * 乐观锁版本。
     */
    @Version
    private Integer version;
}
