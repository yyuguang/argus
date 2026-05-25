package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: CodeIndexSummaryResDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引摘要响应，承载索引列表和版本绑定展示所需的核心状态字段。
 */
@Data
public class CodeIndexSummaryResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源码索引 ID。
     */
    private Long indexId;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * SCM 提供方。
     */
    private String scmProvider;

    /**
     * SCM 项目 ID。
     */
    private String scmProjectId;

    /**
     * 仓库归属。
     */
    private String repoOwner;

    /**
     * 仓库名称。
     */
    private String repoName;

    /**
     * 分支名称。
     */
    private String branchName;

    /**
     * 提交号。
     */
    private String commitSha;

    /**
     * 索引结构版本。
     */
    private Integer indexVersion;

    /**
     * 扫描状态。
     */
    private String scanStatus;

    /**
     * 扫描类型。
     */
    private String scanType;

    /**
     * 触发类型。
     */
    private String triggerType;

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
     * Java 类型数量。
     */
    private Integer classCount;

    /**
     * 包数量。
     */
    private Integer packageCount;

    /**
     * 存在歧义的包数量。
     */
    private Integer ambiguousPackageCount;

    /**
     * 扫描告警数量。
     */
    private Integer warningCount;

    /**
     * 定位置信度。
     */
    private String confidence;

    /**
     * 是否过期。
     */
    private Boolean stale;

    /**
     * 最近错误信息。
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
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 修改时间。
     */
    private LocalDateTime updateTime;
}
