package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: CodeIndexScanTaskResDTO
 * @author: Fantasy
 * @date: 2026/05/25 08:45
 * @description: 源码索引扫描任务响应，承载任务状态、阶段进度和最终索引结果。
 */
@Data
public class CodeIndexScanTaskResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 扫描任务 ID。
     */
    private Long taskId;

    /**
     * 扫描任务编号。
     */
    private String taskNo;

    /**
     * 任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED/REUSED。
     */
    private String taskStatus;

    /**
     * 当前扫描阶段。
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
     * 响应提示信息。
     */
    private String message;

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
}
