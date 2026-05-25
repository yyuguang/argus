package com.lnzz.argus.codeindex.dao.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @classname: CodeIndexScanTask
 * @author: Fantasy
 * @date: 2026/05/25 08:35
 * @description: 源码索引扫描任务实体，记录一次仓库源码索引扫描尝试和阶段进度。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_code_index_scan_task")
public class CodeIndexScanTask extends BaseEntity {

    /**
     * 扫描任务编号。
     */
    private String taskNo;

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
     * 仓库名称。
     */
    private String repoName;

    /**
     * 扫描目标分支。
     */
    private String branchName;

    /**
     * 扫描目标 commit SHA。
     */
    private String commitSha;

    /**
     * 扫描类型：FULL/INCREMENTAL/MODULE_RESCAN/REBUILD。
     */
    private String scanType;

    /**
     * 触发类型：MANUAL/WEBHOOK/DEPLOY_CALLBACK/SCHEDULED。
     */
    private String triggerType;

    /**
     * 是否强制重建。
     */
    private Boolean forceRebuild;

    /**
     * 任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED/REUSED。
     */
    private String taskStatus;

    /**
     * 扫描阶段：WAITING/SCM_READING/MODULE_SCANNING/SOURCE_ROOT_DISCOVERING/JAVA_PARSING/INDEX_AGGREGATING/INDEX_PERSISTING/COMPLETED/FAILED。
     */
    private String scanStage;

    /**
     * 扫描进度百分比，取值 0-100。
     */
    private Integer progressPercent;

    /**
     * 当前阶段说明。
     */
    private String stageMessage;

    /**
     * 已物化或读取的文件数量。
     */
    private Integer loadedFileCount;

    /**
     * Java 文件总数。
     */
    private Integer totalJavaFileCount;

    /**
     * 已解析 Java 文件数量。
     */
    private Integer parsedFileCount;

    /**
     * 解析失败文件数量。
     */
    private Integer failedFileCount;

    /**
     * 解析出的类型数量。
     */
    private Integer classCount;

    /**
     * 解析出的包数量。
     */
    private Integer packageCount;

    /**
     * 扫描告警数量。
     */
    private Integer warningCount;

    /**
     * 扫描成功后关联的仓库源码索引 ID。
     */
    private Long resultIndexId;

    /**
     * 普通刷新复用的已有成功索引 ID。
     */
    private Long reusedIndexId;

    /**
     * 最近失败原因。
     */
    private String latestErrorMessage;

    /**
     * 触发人。
     */
    private String requestedBy;

    /**
     * 触发原因。
     */
    private String reason;

    /**
     * 任务开始时间。
     */
    private LocalDateTime startedAt;

    /**
     * 任务完成时间。
     */
    private LocalDateTime finishedAt;

    /**
     * 最近进度更新时间。
     */
    private LocalDateTime lastHeartbeatAt;

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
